# Phase 1 Android MVP tool validation

Issue: #31

## Automated coverage

- JVM tests validate all strict schemas, risk levels, default policies, lock-screen filtering, missing
  permission decisions, installed-package checks, all executor results, and audit emission.
- Android tests validate battery and local-time adapters, baseline capability discovery, and persistent
  local-task replay across store recreation.

## Samsung / One UI device checklist

Run on the phase-0 device matrix before closing phase 1:

1. Start and pause media with an active media session.
2. Open each declared settings panel and verify that no setting changes automatically.
3. Launch an installed application and reject a nonexistent package.
4. Deny `CAMERA`, verify the explicit permission decision, grant it, then enable and disable the torch.
5. Compare reported battery percentage, charging state, local time, and time zone with One UI.
6. Create a local task, restart the app, and verify that replaying the same action does not duplicate it.
7. Verify success, refusal, cancellation, and Android failure entries in the Audit screen.
8. Lock the device and verify that only battery and local-time tools remain discoverable.

Device-dependent results belong in the phase-1 exit report produced by issue #33.
