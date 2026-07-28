# 0010 - Separate model and agent provider contracts

Status: accepted
Issue: #21
Contract version: 1.1.0

## Decision

`core-domain` exposes `ModelProvider` for direct model calls and `AgentBackend` for
session-owning agents. They do not share a parent provider interface and accept different request
types (`ChatRequest` and `AgentRequest`). An agent backend therefore cannot be routed through the
direct-model path by accident.

Both contracts use Kotlin `Flow` for normalized streaming and require upstream work to stop when
collection is cancelled. Explicit cancellation methods cover callers that retain only identifiers.
Direct model events cannot include agent-only approval or long-job events. Agent event sequences
support stream resumption after a disconnection.

## Capabilities and routing

Capabilities are queried before a request. Text, image, audio, tools, cancellation, session resume,
skills, approvals, long jobs, and relevant limits are represented explicitly. The deterministic
selector considers only enabled, available profiles satisfying the request requirements.

Fallback preserves configured order. It is allowed for unavailable or incompatible services,
network failures, timeouts, rate limits, and missing models. Authentication, permission, invalid
request, content rejection, cancellation, protocol, and unknown failures stop routing so a request
is not silently sent to another destination.

Profiles contain only indirect connection references. Credentials and tokens are not part of these
contracts.

## Errors and test support

Adapters convert external failures to `ProviderError` and stable `ProviderErrorCategory` values.
Streaming failures use `StreamEvent.Failed`; failures before a stream is available may throw
`ProviderException` containing the same normalized error.

`ProviderUsage` may preserve a provider-reported cost as an exact decimal string, an ISO currency
code and an `estimated` marker. The decimal string avoids floating-point rounding and remains
optional for providers that only expose token counts.

`FakeModelProvider` and `FakeAgentBackend` are deterministic, in-memory test implementations with
scripted event streams. They perform no network or Android work.

## Versioning

`ProviderContractVersion.CURRENT` is `1.1.0` and requests carry that version. Backward-compatible
additions increment the minor version. Clarifications and fixes increment the patch version.
Removing or changing existing fields, event meaning, cancellation semantics, or routing guarantees
requires a new major version and an explicit migration for stored profiles and active sessions.
