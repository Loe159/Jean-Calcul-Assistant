# 0008 - Central assistant state machine

Status: accepted
Issue: #19

## Decision

`core-domain` owns the platform-neutral assistant lifecycle. `AssistantStateMachine`
exposes its current `AssistantState` through `StateFlow` and accepts explicit
`AssistantEvent` values. The pure reducer returns an `AssistantTransition` containing
the next state and a list of `AssistantEffect` values.

The state machine contains no Android, network, model, tool, timer, or UI calls.
`assistant-session` interprets effects for the current voice prototype. Future text,
conversation, provider, and tool integrations must send events to the same machine
instead of creating a parallel lifecycle enum.

Each timed state declares an `AssistantTimeout`. Scheduling and cancellation remain
external effects so unit tests can validate the transition table without real time.

## UI mapping

`assistant-session` maps every domain state to the phase 1 design system: orb, voice
wave, status badge, microphone indicator, processing destination, and action card.
The microphone is shown as active only in `Listening`. Action proposal, approval,
and execution states reuse the action components introduced by #18.

## Recovery

Cancellation enters `Cancelled`; failures and matching timeouts enter `Error`.
Both states are recoverable through `Recover` to `Idle`. Interruption is distinct:
it cancels active external work and returns to `Invoked`, allowing a new input in
the same visible session.
