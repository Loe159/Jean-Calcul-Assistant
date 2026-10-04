import https from "node:https";
import { URL } from "node:url";
import { authStatus } from "./auth.js";
import { bearerToken, ensurePairingToken, verifyPairingToken } from "./pairing.js";
import { ensureTlsMaterial } from "./tls.js";
import { ChatGptPlanClient, InferenceRuntime } from "./inference.js";

const MAX_BODY_BYTES = 1024 * 1024;

function json(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
  });
  response.end(body);
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      const error = new Error("Corps de requête trop volumineux.");
      error.code = "body_too_large";
      throw error;
    }
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function routeSession(pathname) {
  const match = /^\/v1\/sessions\/([^/]+)(?:\/(resume|messages|events|cancel))?$/.exec(pathname);
  return match ? { id: decodeURIComponent(match[1]), action: match[2] || null } : null;
}

function errorStatus(error) {
  if (error?.code === "session_not_found") return 404;
  if (error?.code === "run_in_progress") return 409;
  if (error?.code === "body_too_large") return 413;
  return 400;
}

export async function createCompanionServer({
  home,
  host = "127.0.0.1",
  port = 43120,
  fetchImpl = fetch,
  logger = console,
} = {}) {
  if (!home) throw new Error("home requis.");
  const tls = await ensureTlsMaterial(home);
  const firstPairingToken = await ensurePairingToken(home);
  const runtime = new InferenceRuntime(new ChatGptPlanClient(home, fetchImpl));

  const server = https.createServer({ key: tls.key, cert: tls.cert }, async (request, response) => {
    try {
      const url = new URL(request.url, "https://companion.invalid");
      if (request.method === "GET" && url.pathname === "/v1/health") {
        json(response, 200, { service: "jean-calcul-companion", version: 1 });
        return;
      }

      const token = bearerToken(request.headers.authorization);
      if (!(await verifyPairingToken(home, token))) {
        json(response, 401, { code: "pairing_required", message: "Appairage compagnon requis." });
        return;
      }

      if (request.method === "GET" && url.pathname === "/v1/status") {
        const status = await authStatus(home);
        json(response, 200, {
          state: status.authenticated ? "AVAILABLE" : "OFFLINE",
          authenticated: status.authenticated,
          message: status.authenticated ? "Forfait ChatGPT connecté." : "Connexion ChatGPT requise sur le compagnon.",
        });
        return;
      }

      if (request.method === "GET" && url.pathname === "/v1/models") {
        const models = await runtime.client.listModels();
        json(response, 200, { models });
        return;
      }

      if (request.method === "POST" && url.pathname === "/v1/sessions") {
        const body = await readJson(request);
        json(response, 201, runtime.createSession(body.model));
        return;
      }

      const sessionRoute = routeSession(url.pathname);
      if (sessionRoute) {
        if (request.method === "POST" && sessionRoute.action === "resume") {
          const body = await readJson(request);
          json(response, 200, runtime.resumeSession(sessionRoute.id, body.model));
          return;
        }
        if (request.method === "POST" && sessionRoute.action === "messages") {
          const body = await readJson(request);
          const run = runtime.startRun(sessionRoute.id, body.request_id, body.messages || []);
          json(response, 202, run);
          return;
        }
        if (request.method === "GET" && sessionRoute.action === "events") {
          const after = Math.max(0, Number(url.searchParams.get("after") || 0) || 0);
          const events = await runtime.waitForEvents(sessionRoute.id, after);
          json(response, 200, { events });
          return;
        }
        if (request.method === "POST" && sessionRoute.action === "cancel") {
          const body = await readJson(request);
          const cancelled = runtime.cancel(sessionRoute.id, body.run_id);
          json(response, cancelled ? 200 : 409, { cancelled });
          return;
        }
      }

      json(response, 404, { code: "not_found", message: "Route inconnue." });
    } catch (error) {
      logger.error?.(`Companion request failed: ${error?.code || error?.name || "error"}`);
      json(response, errorStatus(error), {
        code: error?.code || "invalid_request",
        message: error?.message || "Requête invalide.",
      });
    }
  });

  return {
    host,
    port,
    pin: tls.pin,
    firstPairingToken,
    server,
    start: () => new Promise((resolve, reject) => {
      server.once("error", reject);
      server.listen(port, host, () => resolve(server.address()));
    }),
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}
