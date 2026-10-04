import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { getAccessToken } from "../src/auth.js";
import { credentialPath, writePrivateJson } from "../src/storage.js";

const scopes = [
  "openid",
  "profile",
  "email",
  "offline_access",
  "resource.invoke",
  "chatgpt.tokens.use.direct",
];

async function writeExpiredCredential(home) {
  await writePrivateJson(credentialPath(home), {
    version: 1,
    active_client_id: "oaiapp_test",
    accounts: [
      {
        label: "Test",
        client_id: "oaiapp_test",
        access_token: "expired",
        refresh_token: "refresh-1",
        id_token: "retained-id-token",
        token_type: "Bearer",
        expires_at: 0,
        scopes,
      },
    ],
  });
}

function refreshedResponse() {
  return new Response(
    JSON.stringify({
      access_token: "access-2",
      refresh_token: "refresh-2",
      token_type: "Bearer",
      expires_in: 3600,
    }),
    {
      status: 200,
      headers: { "content-type": "application/json" },
    },
  );
}

test("concurrent token requests perform one rotating refresh", async () => {
  const home = await mkdtemp(join(tmpdir(), "jean-refresh-"));
  try {
    await writeExpiredCredential(home);
    let calls = 0;
    const fetchImpl = async () => {
      calls += 1;
      await new Promise((resolve) => setTimeout(resolve, 20));
      return refreshedResponse();
    };

    const [first, second] = await Promise.all([
      getAccessToken(home, fetchImpl),
      getAccessToken(home, fetchImpl),
    ]);

    assert.equal(first, "access-2");
    assert.equal(second, "access-2");
    assert.equal(calls, 1);
  } finally {
    await rm(home, { recursive: true, force: true });
  }
});

test("refresh lock is released after a failed refresh", async () => {
  const home = await mkdtemp(join(tmpdir(), "jean-refresh-failure-"));
  try {
    await writeExpiredCredential(home);
    await assert.rejects(
      getAccessToken(home, async () => {
        throw new Error("network down");
      }),
      /network down/,
    );

    const token = await getAccessToken(home, async () => refreshedResponse());
    assert.equal(token, "access-2");
  } finally {
    await rm(home, { recursive: true, force: true });
  }
});
