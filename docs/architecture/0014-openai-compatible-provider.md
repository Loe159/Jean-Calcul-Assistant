# 0014 - OpenAI-compatible direct model provider

Status: accepted
Issue: #22
Provider contract: 1.0.0

## Decision

`core-network` implements `OpenAiCompatibleProvider` as a direct `ModelProvider`. It targets the
OpenAI chat-completions and models routes relative to a user-configured API base URL. The adapter is
created from a `ProviderConnection`; model identity and generation parameters remain in
`ModelProfile` and `ChatRequest`.

The configured base URL is authoritative. For example, a base URL ending in `/v1/` produces
`/v1/chat/completions` and `/v1/models`. This preserves compatibility with hosted APIs, reverse
proxies, and local servers without hard-coding an OpenAI hostname.

## Streaming and tools

The provider sends streaming chat-completions requests and converts server-sent events to the
versioned domain stream:

- text chunks become `TextDelta` events;
- usage becomes `UsageUpdated`;
- tool argument fragments become `ToolCallArgumentsDelta`;
- complete, valid JSON tool arguments become `ToolCallReady`;
- the provider finish reason becomes `Completed`.

Tool definitions reuse the strict input JSON schemas from `core-domain`. Tool output schemas,
Android permissions, policies, and executors are never exposed as provider authority. A returned
tool call remains untrusted and must still pass the registry and Policy Engine.

Flow cancellation cancels the active OkHttp call. Explicit cancellation by request identifier uses
the same transport cancellation path. The streaming client has no whole-call deadline, while the
configured OkHttp connect and read timeouts continue to protect connection setup and stalled
streams.

## Configuration and model discovery

The adapter can query the standard models route or return an explicit configured model catalog for
servers that do not implement discovery. Capability overrides are configured per model and are
checked before a request is sent. Connection validation always contacts the models route so a
manual catalog cannot hide an unavailable server.

## Errors and sensitive data

HTTP 401, 403, 404, 408, 429, and 5xx responses are mapped to the shared `ProviderError` categories.
Network failures, timeouts, cancellation, malformed SSE, and malformed tool arguments are also
normalized. Raw response bodies and exception messages are not copied into domain errors.

API keys are resolved only through `SecretStore` while the HTTP request is built. The adapter does
not log request bodies, response bodies, prompts, tool arguments, or authentication headers.
