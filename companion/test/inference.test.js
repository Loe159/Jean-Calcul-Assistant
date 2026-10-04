import assert from "node:assert/strict";
import test from "node:test";
import { InferenceRuntime, parseSsePayloads, toResponsesInput } from "../src/inference.js";

test("message conversion never sends tool messages and maps system to developer", () => {
  assert.deepEqual(
    toResponsesInput([
      { role: "SYSTEM", text: "Rules" },
      { role: "USER", text: "Hello" },
      { role: "TOOL", text: "hidden" },
      { role: "ASSISTANT", text: "Hi" },
    ]),
    [
      { role: "developer", content: "Rules" },
      { role: "user", content: "Hello" },
      { role: "assistant", content: "Hi" },
    ],
  );
});

test("SSE parser extracts Responses events", () => {
  const events = parseSsePayloads(
    'event: response.output_text.delta\ndata: {"type":"response.output_text.delta","delta":"Bon"}\n\n' +
    'data: {"type":"response.completed","response":{"usage":{"input_tokens":2,"output_tokens":1}}}\n\n',
  );
  assert.equal(events.length, 2);
  assert.equal(events[0].delta, "Bon");
  assert.equal(events[1].type, "response.completed");
});

test("runtime persists ordered stream events and supports resume", async () => {
  const client = {
    async streamResponse({ onEvent }) {
      onEvent({ type: "text_delta", text: "Bon" });
      onEvent({ type: "text_delta", text: "jour" });
      onEvent({ type: "completed", finish_reason: "STOP" });
    },
  };
  const runtime = new InferenceRuntime(client);
  const session = runtime.createSession("gpt-test");
  const run = runtime.startRun(session.id, "request-1", [{ role: "USER", text: "Bonjour" }]);
  assert.equal(run.status, "RUNNING");

  let events = [];
  for (let attempts = 0; attempts < 10 && !events.some((event) => event.type === "completed"); attempts += 1) {
    events = await runtime.waitForEvents(session.id, 0, 50);
  }
  assert.deepEqual(events.map((event) => event.type), ["started", "text_delta", "text_delta", "completed"]);
  assert.deepEqual(events.map((event) => event.sequence), [1, 2, 3, 4]);

  const resumed = runtime.resumeSession(session.id, "gpt-test-2");
  assert.equal(resumed.id, session.id);
  assert.equal(resumed.model, "gpt-test-2");
});
