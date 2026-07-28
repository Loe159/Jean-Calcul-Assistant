# 0015 - Anthropic, OpenRouter and Ollama direct model providers

Status: accepted
Issues: #23, #24, #25
Provider contract: 1.1.0

## Decision

`core-network` contains separate provider packages for Anthropic, OpenRouter and Ollama. Each
adapter implements `ModelProvider`, resolves credentials only through `SecretStore`, emits the
normalized domain stream and cancels its active OkHttp call when collection or a request is
cancelled.

No adapter can execute an Android tool. Returned calls remain untrusted `ToolCall` values and must
still pass the versioned registry, schema validation, Policy Engine and audit path.

## Anthropic

The Anthropic adapter uses the native Messages API rather than an OpenAI compatibility layer. It
maps system content, messages, client tools, tool results and generation parameters to the native
request. SSE text deltas, input JSON deltas, complete tool calls, usage and stop reasons are mapped
without copying response bodies into errors.

Model discovery uses the native Models API. HTTP 401, 429 and 529 responses are normalized as
authentication, rate-limit and service-unavailable failures. Mid-stream error events follow the
same safe categories.

## OpenRouter

The OpenRouter adapter specializes the existing OpenAI-compatible transport. It adds application
headers, ordered model fallbacks and usage inclusion while preserving the existing streaming,
tool-call and cancellation behavior.

The model catalog maps advertised input/output modalities, tool support and token limits. A
provider-reported `usage.cost` is retained as an exact USD decimal in `ProviderUsage.cost`. Missing
cost metadata remains `null`; the client never invents a price.

## Ollama

The Ollama adapter uses `/api/tags`, `/api/show` and `/api/chat`. It streams newline-delimited JSON,
normalizes local network failures, and derives vision, tool and context capabilities from
`/api/show`. Unsupported capabilities are disabled instead of guessed.

HTTP is supported for an explicitly configured Ollama connection because typical LAN deployments
do not terminate TLS. The UI and connection validation expose a persistent cleartext warning. The
provider can reject HTTP through `allowInsecureHttp`. HTTPS always uses Android/OkHttp system trust;
self-signed certificates are not silently trusted and no trust-all TLS implementation exists.

## Android network surface

`core-network` contributes the `INTERNET` permission. Cleartext transport is enabled at the Android
manifest level so a user-selected LAN Ollama host can work, but application code only uses it for a
configured provider and surfaces the warning above. No new personal-data permission is added.

## Validation

MockWebServer tests cover native Anthropic streaming, tool JSON reassembly, errors, cancellation and
model discovery; OpenRouter headers, fallbacks, model capabilities, costs and unavailable models;
and Ollama discovery, capabilities, NDJSON streaming, tools, cancellation, server absence and HTTP
policy. Secret values are absent from request bodies and normalized errors.
