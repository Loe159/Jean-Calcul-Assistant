# 0011 - Versioned Android tool registry

Status: accepted
Issue: #27
Tool contract version: 1.0.0

## Decision

`core-domain` owns platform-independent tool metadata: exact name and semantic version, model-facing
description, strict input and output JSON Schemas, risk level, Android permission names, device and lock
screen constraints, and the default policy hint. A `ToolRegistration` in `tool-bridge` associates that
metadata with an executor so executable Android code never enters `core-domain` or provider payloads.

`ToolRegistry` is the only discovery and execution entry point. Discovery evaluates the current device
capabilities, granted permissions, and lock state. Execution resolves an exact name and version, checks
availability and expiration, validates input, invokes the deterministic executor, validates output, and
returns a structured result. Unknown tools and versions never reach an executor.

Every object schema must set `additionalProperties` to `false`. Validation uses JSON Schema draft
2020-12 through the validator already provisioned by issue #36. Validation messages are not copied into
tool results or audit messages, which avoids reflecting untrusted parameter values.

## Idempotence and audit boundary

An action carries a non-empty idempotency key, defaulting to its action id. The registry keeps a bounded
in-memory history of terminal executor results. Replaying the same name, version, and arguments returns
the stored result without another Android call. Reusing a key with different content is rejected. This
protects a running process from duplicate writes; durable replay protection remains a future persistence
concern.

Audit events include the exact tool version for request, validation, replay, result, and error stages.
Issue #32 will persist these events. The registry deliberately does not make a policy decision: issue
#28 inserts the Policy Engine after schema and availability checks and before executor invocation.

## Volume migration

`audio.get_volume` and `audio.set_volume` are registered at version `1.0.0`. Their schemas, risk levels,
lock-screen constraints, and availability metadata are advertised from `core-domain`; their
`AudioManager` implementation stays in `tool-bridge`. The assistant session now calls the registry and
has no direct path to the volume executor.

The read tool is R0 and may be discovered while locked. The write tool is R2, has a default confirmation
hint, and is unavailable while locked. No new Android permission, persisted data, or personal data flow
is introduced by this decision.
