import { randomUUID } from "node:crypto";
import { getAccessToken } from "./auth.js";

const MODELS_URL = "https://api.openai.com/v1/models";
const RESPONSES_URL = "https://api.openai.com/v1/responses";
const MAX_EVENTS_PER_SESSION = 2000;

function normalizeRole(role) {
  switch (String(role || "").toLowerCase()) {
    case "system": return "developer";
    case "assistant": return "assistant";
    case "user": return "user";
    default: return null;
  }
}

export function toResponsesInput(messages) {
  return (Array.isArray(messages) ? messages : [])
    .map((message) => ({
      role: normalizeRole(message.role),
      content: typeof message.text === "string" ? message.text.trim() : "",
    }))
    .filter((message) => message.role && message.content);
}

export function parseSsePayloads(raw) {
  return raw
    .replace(/\r\n/g, "\n")
    .split("\n\n")
    .map((block) => block.split("\n").filter((line) => line.startsWith("data:")).map((line) => line.slice(5).trim()).join("\n"))
    .filter((data) => data && data !== "[DONE]")
    .map((data) => JSON.parse(data));
}

async function consumeSse(body, onEvent) {
  if (!body) throw new Error("Flux Responses absent.");
  const decoder = new TextDecoder();
  let buffer = "";
  for await (const chunk of body) {
    buffer += decoder.decode(chunk, { stream: true }).replace(/\r\n/g, "\n");
    let boundary;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const data = block
        .split("\n")
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.slice(5).trim())
        .join("\n");
      if (!data || data === "[DONE]") continue;
      onEvent(JSON.parse(data));
    }
  }
  buffer += decoder.decode();
  const tail = buffer.trim();
  if (tail) {
    for (const event of parseSsePayloads(tail)) onEvent(event);
  }
}

function errorCode(event, fallback) {
  return event?.response?.error?.code || event?.error?.code || fallback;
}

function safeErrorMessage(code) {
  switch (code) {
    case "subscription_sharing_usage_limit_exceeded":
      return "La limite d'utilisation partagée du forfait ChatGPT est atteinte.";
    case "subscription_sharing_usage_unavailable":
      return "L'usage du forfait ChatGPT est temporairement indisponible.";
    case "invalid_model":
    case "model_not_found":
      return "Le modèle sélectionné n'est pas disponible pour ce compte ChatGPT.";
    default:
      return "La requête ChatGPT n'a pas pu être terminée.";
  }
}

export class ChatGptPlanClient {
  constructor(home, fetchImpl = fetch) {
    this.home = home;
    this.fetchImpl = fetchImpl;
  }

  async listModels() {
    const token = await getAccessToken(this.home, this.fetchImpl);
    const response = await this.fetchImpl(MODELS_URL, {
      headers: { authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) throw new Error(`Impossible de charger les modèles ChatGPT (HTTP ${response.status}).`);
    const payload = await response.json();
    return (payload.models || [])
      .filter((model) => model.visibility === "list" && model.slug)
      .map((model) => ({ id: model.slug, display_name: model.display_name || model.slug }));
  }

  async streamResponse({ model, messages, signal, onEvent }) {
    const token = await getAccessToken(this.home, this.fetchImpl);
    const input = toResponsesInput(messages);
    if (!input.length) throw new Error("Aucun message exploitable à envoyer.");
    const response = await this.fetchImpl(RESPONSES_URL, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        model,
        input,
        store: false,
        stream: true,
      }),
      signal,
    });
    if (!response.ok) {
      const status = response.status;
      const code = status === 401 ? "chatgpt_authentication_failed" : status === 429 ? "rate_limited" : "http_error";
      const error = new Error(safeErrorMessage(code));
      error.code = code;
      error.status = status;
      throw error;
    }
    let completed = false;
    await consumeSse(response.body, (event) => {
      if (event.type === "response.output_text.delta" && typeof event.delta === "string") {
        onEvent({ type: "text_delta", text: event.delta });
      } else if (event.type === "response.completed") {
        const usage = event.response?.usage;
        if (usage) {
          onEvent({
            type: "usage",
            input_tokens: usage.input_tokens ?? null,
            output_tokens: usage.output_tokens ?? null,
          });
        }
        completed = true;
        onEvent({ type: "completed", finish_reason: "STOP" });
      } else if (event.type === "response.failed" || event.type === "response.incomplete") {
        const code = errorCode(event, event.type === "response.incomplete" ? "response_incomplete" : "response_failed");
        completed = true;
        onEvent({ type: "failed", code, message: safeErrorMessage(code) });
      } else if (event.type === "error") {
        const code = errorCode(event, "stream_error");
        completed = true;
        onEvent({ type: "failed", code, message: safeErrorMessage(code) });
      }
    });
    if (!completed) {
      onEvent({
        type: "failed",
        code: "stream_interrupted",
        message: "Le flux ChatGPT s'est interrompu avant la fin de la réponse.",
      });
    }
  }
}

function sessionRecord(id, model) {
  return {
    id,
    model,
    sequence: 0,
    events: [],
    active: null,
    waiters: new Set(),
  };
}

export class InferenceRuntime {
  constructor(client) {
    this.client = client;
    this.sessions = new Map();
  }

  createSession(model) {
    if (!model?.trim()) throw new Error("Modèle requis.");
    const id = randomUUID();
    this.sessions.set(id, sessionRecord(id, model.trim()));
    return { id, model: model.trim(), resumable: true };
  }

  resumeSession(id, model) {
    if (!id?.trim() || !model?.trim()) throw new Error("Session et modèle requis.");
    const existing = this.sessions.get(id);
    if (existing) {
      existing.model = model.trim();
      return { id, model: existing.model, resumable: true };
    }
    this.sessions.set(id, sessionRecord(id, model.trim()));
    return { id, model: model.trim(), resumable: true };
  }

  getSession(id) {
    const session = this.sessions.get(id);
    if (!session) {
      const error = new Error("Session inconnue.");
      error.code = "session_not_found";
      throw error;
    }
    return session;
  }

  startRun(id, requestId, messages) {
    const session = this.getSession(id);
    if (session.active) {
      const error = new Error("Une requête est déjà active dans cette session.");
      error.code = "run_in_progress";
      throw error;
    }
    if (!requestId?.trim()) throw new Error("requestId requis.");
    const runId = randomUUID();
    const controller = new AbortController();
    session.active = { runId, requestId, controller };
    this.push(session, { type: "started", request_id: requestId });
    this.execute(session, { runId, requestId, messages, controller }).catch(() => {});
    return { id: runId, session_id: id, request_id: requestId, status: "RUNNING" };
  }

  async execute(session, run) {
    try {
      await this.client.streamResponse({
        model: session.model,
        messages: run.messages,
        signal: run.controller.signal,
        onEvent: (event) => this.push(session, { ...event, request_id: run.requestId }),
      });
    } catch (error) {
      if (run.controller.signal.aborted) {
        this.push(session, { type: "completed", request_id: run.requestId, finish_reason: "CANCELLED" });
      } else {
        this.push(session, {
          type: "failed",
          request_id: run.requestId,
          code: error?.code || "companion_error",
          message: error?.message || "Erreur compagnon.",
        });
      }
    } finally {
      if (session.active?.runId === run.runId) session.active = null;
      this.wake(session);
    }
  }

  cancel(id, runId) {
    const session = this.getSession(id);
    if (session.active?.runId === runId) {
      session.active.controller.abort();
      return true;
    }
    return false;
  }

  push(session, event) {
    const normalized = { ...event, sequence: ++session.sequence };
    session.events.push(normalized);
    if (session.events.length > MAX_EVENTS_PER_SESSION) {
      session.events.splice(0, session.events.length - MAX_EVENTS_PER_SESSION);
    }
    this.wake(session);
    return normalized;
  }

  wake(session) {
    for (const resolve of session.waiters) resolve();
    session.waiters.clear();
  }

  eventsAfter(id, after = 0) {
    const session = this.getSession(id);
    return session.events.filter((event) => event.sequence > after);
  }

  async waitForEvents(id, after = 0, timeoutMs = 15_000) {
    const session = this.getSession(id);
    const current = this.eventsAfter(id, after);
    if (current.length) return current;
    await new Promise((resolve) => {
      const timer = setTimeout(() => {
        session.waiters.delete(done);
        resolve();
      }, timeoutMs);
      const done = () => {
        clearTimeout(timer);
        session.waiters.delete(done);
        resolve();
      };
      session.waiters.add(done);
    });
    return this.eventsAfter(id, after);
  }
}
