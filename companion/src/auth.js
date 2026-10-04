import http from "node:http";
import { createHash, createPublicKey, randomBytes, constants, verify as verifySignature } from "node:crypto";
import { spawn } from "node:child_process";
import { credentialPath, loadOrCreateHostId, readJson, writePrivateJson } from "./storage.js";

const AUTHORIZE_URL = "https://auth.openai.com/api/accounts/authorize";
const TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token";
const OIDC_CONFIG_URL = "https://auth.openai.com/.well-known/openid-configuration";
const JWKS_URL = "https://auth.openai.com/.well-known/jwks.json";
const RESOURCE = "https://api.openai.com/v1";
const ISSUER = "https://auth.openai.com";
const SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";
const REFRESH_SKEW_MS = 5 * 60 * 1000;
const LOGIN_TIMEOUT_MS = 5 * 60 * 1000;

function base64url(buffer) {
  return Buffer.from(buffer).toString("base64url");
}

function randomValue(bytes = 32) {
  return base64url(randomBytes(bytes));
}

export function buildAuthorizationUrl({
  clientId,
  hostId,
  redirectUri,
  state,
  nonce,
  codeChallenge,
  isNewRegistration,
  idTokenHint,
  loginHint,
}) {
  const url = new URL(AUTHORIZE_URL);
  const params = {
    client_id: clientId,
    ext_agent_host_id: hostId,
    response_type: "code",
    redirect_uri: redirectUri,
    scope: SCOPES,
    resource: RESOURCE,
    state,
    nonce,
    code_challenge_method: "S256",
    code_challenge: codeChallenge,
  };
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  if (isNewRegistration) {
    url.searchParams.set("agent_name_hint", "Jean Calcul Assistant");
  } else {
    if (idTokenHint) url.searchParams.set("id_token_hint", idTokenHint);
    if (loginHint) url.searchParams.set("login_hint", loginHint);
  }
  return url;
}

function decodeJsonPart(value) {
  return JSON.parse(Buffer.from(value, "base64url").toString("utf8"));
}

async function fetchJwk(kid, fetchImpl) {
  const response = await fetchImpl(JWKS_URL, { signal: AbortSignal.timeout(10_000) });
  if (!response.ok) throw new Error("Impossible de charger les clés de signature OpenAI.");
  const jwks = await response.json();
  const jwk = jwks.keys?.find((candidate) => candidate.kid === kid);
  if (!jwk) throw new Error("Clé de signature OpenAI inconnue.");
  return jwk;
}

function verifyJwtSignature(algorithm, signingInput, signature, key) {
  const data = Buffer.from(signingInput, "utf8");
  const sig = Buffer.from(signature, "base64url");
  if (algorithm === "RS256") return verifySignature("RSA-SHA256", data, key, sig);
  if (algorithm === "RS384") return verifySignature("RSA-SHA384", data, key, sig);
  if (algorithm === "RS512") return verifySignature("RSA-SHA512", data, key, sig);
  if (algorithm === "PS256") {
    return verifySignature("sha256", data, { key, padding: constants.RSA_PKCS1_PSS_PADDING, saltLength: 32 }, sig);
  }
  throw new Error(`Algorithme ID token non autorisé: ${algorithm}`);
}

export async function verifyIdToken(idToken, { clientId, nonce, fetchImpl = fetch }) {
  const parts = String(idToken || "").split(".");
  if (parts.length !== 3) throw new Error("ID token ChatGPT invalide.");
  const header = decodeJsonPart(parts[0]);
  const claims = decodeJsonPart(parts[1]);
  if (!header.kid || !header.alg) throw new Error("En-tête ID token incomplet.");
  const jwk = await fetchJwk(header.kid, fetchImpl);
  const key = createPublicKey({ key: jwk, format: "jwk" });
  if (!verifyJwtSignature(header.alg, `${parts[0]}.${parts[1]}`, parts[2], key)) {
    throw new Error("Signature ID token ChatGPT invalide.");
  }
  const now = Math.floor(Date.now() / 1000);
  const audience = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
  if (claims.iss !== ISSUER) throw new Error("Émetteur ID token ChatGPT inattendu.");
  if (!audience.includes(clientId)) throw new Error("Audience ID token ChatGPT inattendue.");
  if (!Number.isFinite(claims.exp) || claims.exp <= now) throw new Error("ID token ChatGPT expiré.");
  if (claims.nonce !== nonce) throw new Error("Nonce ID token ChatGPT invalide.");
  if (typeof claims.sub !== "string" || !claims.sub) throw new Error("Identité ChatGPT absente.");
  return claims;
}

async function loadAccountStore(home) {
  return (await readJson(credentialPath(home))) || { version: 1, active_client_id: null, accounts: [] };
}

async function saveAccountStore(home, store) {
  await writePrivateJson(credentialPath(home), store);
}

function hasPlanScope(scopes) {
  return scopes.includes("chatgpt.tokens.use.direct") && scopes.includes("resource.invoke");
}

function openBrowser(url) {
  if (process.env.JEAN_CALCUL_NO_BROWSER === "1") return;
  const value = url.toString();
  if (process.platform === "win32") {
    spawn("cmd", ["/c", "start", "", value], { detached: true, stdio: "ignore" }).unref();
  } else if (process.platform === "darwin") {
    spawn("open", [value], { detached: true, stdio: "ignore" }).unref();
  } else {
    spawn("xdg-open", [value], { detached: true, stdio: "ignore" }).unref();
  }
}

async function callbackListener() {
  let resolveCallback;
  let rejectCallback;
  const callback = new Promise((resolve, reject) => {
    resolveCallback = resolve;
    rejectCallback = reject;
  });
  const server = http.createServer((request, response) => {
    try {
      const url = new URL(request.url, "http://127.0.0.1");
      if (url.pathname !== "/auth/callback") {
        response.writeHead(404).end("Not found");
        return;
      }
      const result = Object.fromEntries(url.searchParams.entries());
      response.writeHead(200, { "content-type": "text/plain; charset=utf-8" });
      response.end("Connexion reçue. Vous pouvez fermer cette fenêtre et revenir au compagnon Jean Calcul.");
      resolveCallback(result);
    } catch (error) {
      rejectCallback(error);
    }
  });
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const port = server.address().port;
  const timer = setTimeout(() => rejectCallback(new Error("Délai de connexion ChatGPT dépassé.")), LOGIN_TIMEOUT_MS);
  return {
    redirectUri: `http://127.0.0.1:${port}/auth/callback`,
    wait: () => callback.finally(() => {
      clearTimeout(timer);
      server.close();
    }),
    close: () => server.close(),
  };
}

async function exchangeCode({ clientId, code, verifier, redirectUri, fetchImpl }) {
  const body = new URLSearchParams({
    grant_type: "authorization_code",
    client_id: clientId,
    code,
    code_verifier: verifier,
    redirect_uri: redirectUri,
    resource: RESOURCE,
  });
  const response = await fetchImpl(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
    signal: AbortSignal.timeout(20_000),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Échange OAuth ChatGPT refusé (${payload.error || response.status}).`);
  return payload;
}

function normalizeScopes(payload, callbackScope, existing = []) {
  const raw = payload.scope || callbackScope;
  if (typeof raw === "string") return [...new Set(raw.split(/\s+/).filter(Boolean))].sort();
  return [...existing];
}

function credentialFromToken({ token, claims, clientId, hostId, scopes, previous }) {
  const expiresIn = Number(token.expires_in || 3600);
  return {
    label: claims.email || previous?.label || `ChatGPT ${clientId.slice(-6)}`,
    email: claims.email || previous?.email || null,
    subject: claims.sub,
    issuer: claims.iss,
    client_id: clientId,
    ext_agent_host_id: hostId,
    id_token: token.id_token,
    access_token: token.access_token,
    refresh_token: token.refresh_token,
    token_type: token.token_type || "Bearer",
    expires_in: expiresIn,
    expires_at: Date.now() + expiresIn * 1000,
    scopes,
    saved_at: new Date().toISOString(),
  };
}

export async function loginWithChatGpt(home, { newAccount = false, fetchImpl = fetch, browser = openBrowser } = {}) {
  const store = await loadAccountStore(home);
  const selected = newAccount ? null : store.accounts.find((item) => item.client_id === store.active_client_id) || null;
  const isNewRegistration = !selected;
  const clientId = selected?.client_id || "dynamic_agent_client";
  const hostId = await loadOrCreateHostId(home);
  const verifier = randomValue(48);
  const challenge = base64url(createHash("sha256").update(verifier).digest());
  const state = randomValue();
  const nonce = randomValue();
  const listener = await callbackListener();
  const authorizationUrl = buildAuthorizationUrl({
    clientId,
    hostId,
    redirectUri: listener.redirectUri,
    state,
    nonce,
    codeChallenge: challenge,
    isNewRegistration,
    idTokenHint: selected?.id_token,
    loginHint: selected?.email,
  });

  console.log("Ouvrez cette URL si le navigateur ne se lance pas automatiquement:");
  console.log(selected?.id_token ? "[URL masquée car elle contient un id_token_hint]" : authorizationUrl.toString());
  browser(authorizationUrl);
  const callback = await listener.wait();
  if (callback.state !== state) throw new Error("État OAuth invalide; connexion abandonnée.");
  if (callback.error) throw new Error(`Connexion ChatGPT refusée: ${callback.error}`);
  if (!callback.code) throw new Error("Code OAuth ChatGPT absent.");

  let issuedClientId = selected?.client_id || callback.client_id;
  if (isNewRegistration && (!issuedClientId || issuedClientId === "dynamic_agent_client")) {
    throw new Error("Enregistrement ChatGPT incomplet: client_id émis absent.");
  }
  if (selected && callback.client_id && callback.client_id !== selected.client_id) {
    throw new Error("Le client_id retourné ne correspond pas au compte sélectionné.");
  }

  const token = await exchangeCode({
    clientId: issuedClientId,
    code: callback.code,
    verifier,
    redirectUri: listener.redirectUri,
    fetchImpl,
  });
  if (!token.id_token || !token.access_token || !token.refresh_token) {
    throw new Error("Réponse OAuth ChatGPT incomplète.");
  }
  const claims = await verifyIdToken(token.id_token, { clientId: issuedClientId, nonce, fetchImpl });
  if (selected?.subject && selected.subject !== claims.sub) {
    throw new Error("Le compte ChatGPT retourné ne correspond pas au compte sélectionné.");
  }
  const scopes = normalizeScopes(token, callback.scope, selected?.scopes);
  if (!hasPlanScope(scopes)) throw new Error("L'autorisation d'utiliser le forfait ChatGPT n'a pas été accordée.");

  const credential = credentialFromToken({ token, claims, clientId: issuedClientId, hostId, scopes, previous: selected });
  const accounts = store.accounts.filter((item) => item.client_id !== issuedClientId).concat(credential);
  await saveAccountStore(home, { version: 1, active_client_id: issuedClientId, accounts });
  return { clientId: issuedClientId, label: credential.label };
}

let refreshPromise = null;

async function refreshCredential(home, credential, fetchImpl) {
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    client_id: credential.client_id,
    refresh_token: credential.refresh_token,
    resource: RESOURCE,
  });
  const response = await fetchImpl(TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body,
    signal: AbortSignal.timeout(20_000),
  });
  const token = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`Renouvellement ChatGPT refusé (${token.error || response.status}).`);
  if (!token.access_token) throw new Error("Access token renouvelé absent.");
  const store = await loadAccountStore(home);
  const current = store.accounts.find((item) => item.client_id === credential.client_id);
  if (!current) throw new Error("Compte ChatGPT local introuvable.");
  const scopes = normalizeScopes(token, null, current.scopes);
  if (!hasPlanScope(scopes)) throw new Error("Le forfait ChatGPT n'est plus autorisé pour ce compagnon.");
  const expiresIn = Number(token.expires_in || 3600);
  const updated = {
    ...current,
    access_token: token.access_token,
    refresh_token: token.refresh_token || current.refresh_token,
    id_token: token.id_token || current.id_token,
    token_type: token.token_type || current.token_type || "Bearer",
    expires_in: expiresIn,
    expires_at: Date.now() + expiresIn * 1000,
    scopes,
    saved_at: new Date().toISOString(),
  };
  await saveAccountStore(home, {
    ...store,
    accounts: store.accounts.map((item) => item.client_id === updated.client_id ? updated : item),
  });
  return updated;
}

export async function activeCredential(home) {
  const store = await loadAccountStore(home);
  return store.accounts.find((item) => item.client_id === store.active_client_id) || null;
}

export async function getAccessToken(home, fetchImpl = fetch) {
  let credential = await activeCredential(home);
  if (!credential?.access_token || !credential?.refresh_token || !hasPlanScope(credential.scopes || [])) {
    throw new Error("Authentification ChatGPT requise. Exécutez npm run login dans companion/.");
  }
  if ((credential.expires_at || 0) <= Date.now() + REFRESH_SKEW_MS) {
    if (!refreshPromise) {
      refreshPromise = refreshCredential(home, credential, fetchImpl).finally(() => {
        refreshPromise = null;
      });
    }
    credential = await refreshPromise;
  }
  return credential.access_token;
}

export async function authStatus(home) {
  const credential = await activeCredential(home);
  return {
    authenticated: Boolean(
      credential?.access_token &&
      credential?.refresh_token &&
      hasPlanScope(credential?.scopes || [])
    ),
    label: credential?.label || null,
    clientId: credential?.client_id || null,
  };
}

export async function logoutChatGpt(home, fetchImpl = fetch) {
  const store = await loadAccountStore(home);
  const active = store.accounts.find((item) => item.client_id === store.active_client_id);
  if (!active) return { revoked: true };
  let revoked = false;
  if (active.refresh_token) {
    try {
      const discovery = await fetchImpl(OIDC_CONFIG_URL, { signal: AbortSignal.timeout(10_000) });
      const config = await discovery.json();
      const response = await fetchImpl(config.revocation_endpoint, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          token: active.refresh_token,
          token_type_hint: "refresh_token",
          client_id: active.client_id,
        }),
        signal: AbortSignal.timeout(15_000),
      });
      revoked = response.ok;
    } catch {
      revoked = false;
    }
  }
  const cleared = {
    ...active,
    access_token: null,
    refresh_token: null,
    id_token: null,
    expires_at: 0,
    saved_at: new Date().toISOString(),
  };
  await saveAccountStore(home, {
    ...store,
    accounts: store.accounts.map((item) => item.client_id === active.client_id ? cleared : item),
  });
  return { revoked };
}
