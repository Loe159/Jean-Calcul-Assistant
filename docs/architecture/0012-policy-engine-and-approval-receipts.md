# ADR 0012 - Deterministic policy engine and approval receipts

## Status

Accepted for phase 1 issue #28.

## Context

The versioned tool registry validates tool identity, availability, JSON schemas, idempotency, and results. It did not previously prove that a policy decision and any required user approval occurred before execution. The phase 0 volume path could therefore call a reversible write directly.

## Decision

- Keep policy rules and records in `core-domain`, with no Android dependency.
- Evaluate the exact tool name, version, parameters, risk, profile, origin, lock state, foreground state, Android permissions, and matching user preference.
- Return one of `ALLOW`, `CONFIRM`, `BIOMETRIC`, `OPEN_SYSTEM_PANEL`, or `DENY` with a stable reason and justification.
- Never lower R3 below confirmation, R4 below biometric, or R5 below denial.
- Deny interactive or R2+ actions while the application is not in the foreground.
- Allow only lock-screen-safe R0 tools while the device is locked.
- Issue a short-lived receipt bound to the complete `ActionProposal` after automatic authorization or valid approval.
- Require that receipt at the tool registry boundary. Re-check lock and foreground state at execution time.
- Emit parameter-free policy audit events. Persistent audit storage remains owned by issue #32.
- Present the exact ordered JSON parameter values in the existing `ApprovalSheet` before confirmation.

## Consequences

All valid tool executions now require Policy Engine authorization. The local volume write path changed from immediate execution to explicit confirmation, including absolute volume requests. Biometric decisions and missing-permission decisions are represented and testable; concrete protected tools and their Android panel destinations remain owned by their feature issues.
