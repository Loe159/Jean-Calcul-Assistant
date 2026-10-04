import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import test from "node:test";
import { buildAuthorizationUrl, verifyIdToken } from "../src/auth.js";

function part(value) {
  return Buffer.from(JSON.stringify(value)).toString("base64url");
}

test("authorization URL follows ChatGPT plan PKCE requirements", () => {
  const url = buildAuthorizationUrl({
    clientId: "dynamic_agent_client",
    hostId: "urn:uuid:test-host",
    redirectUri: "http://127.0.0.1:1455/auth/callback",
    state: "state",
    nonce: "nonce",
    codeChallenge: "challenge",
    isNewRegistration: true,
  });
  assert.equal(url.origin, "https://auth.openai.com");
  assert.equal(url.pathname, "/api/accounts/authorize");
  assert.equal(url.searchParams.get("client_id"), "dynamic_agent_client");
  assert.equal(url.searchParams.get("ext_agent_host_id"), "urn:uuid:test-host");
  assert.equal(url.searchParams.get("code_challenge_method"), "S256");
  assert.match(url.searchParams.get("scope"), /chatgpt\.tokens\.use\.direct/);
  assert.equal(url.searchParams.get("resource"), "https://api.openai.com/v1");
});

test("ID token validation verifies signature, issuer, audience and nonce", async () => {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const header = part({ alg: "RS256", kid: "test-key", typ: "JWT" });
  const payload = part({
    iss: "https://auth.openai.com",
    aud: "oaiapp_test",
    sub: "account",
    email: "person@example.test",
    nonce: "nonce-value",
    exp: Math.floor(Date.now() / 1000) + 300,
  });
  const signingInput = `${header}.${payload}`;
  const signature = sign("RSA-SHA256", Buffer.from(signingInput), privateKey).toString("base64url");
  const token = `${signingInput}.${signature}`;
  const jwk = publicKey.export({ format: "jwk" });
  jwk.kid = "test-key";
  jwk.alg = "RS256";

  const claims = await verifyIdToken(token, {
    clientId: "oaiapp_test",
    nonce: "nonce-value",
    fetchImpl: async () => new Response(JSON.stringify({ keys: [jwk] }), { status: 200 }),
  });
  assert.equal(claims.sub, "account");

  await assert.rejects(
    verifyIdToken(token, {
      clientId: "oaiapp_test",
      nonce: "wrong",
      fetchImpl: async () => new Response(JSON.stringify({ keys: [jwk] }), { status: 200 }),
    }),
    /Nonce/,
  );
});
