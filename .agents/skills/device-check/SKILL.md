---
name: device-check
description: Verify Jean-Calcul on a real Android runtime — the synthetic e2e emulator (e2e/run.py, visual-review.json) or, only when the owner explicitly asks, their own phone over ADB. Use for motion, haptics, blur, keyboard/IME, insets, back gestures, assistant overlay, notifications and end-to-end flows that headless previews cannot show.
---

# Device checks

Full guide and mandatory privacy rules: `docs/agents/DEVICE.md`. Read it before running anything.

- Default: `nix develop -c python3 e2e/run.py --run-id <topic> --attempt 1`, then open every PNG yourself, fill `visual-review.json` (PASS/FAIL/NOT_RUN with concrete observations; the `regression_fixture` must be FAIL), and `nix develop -c python3 e2e/report.py e2e-artifacts/<topic>/attempt-1`. Max 3 attempts per change, same run-id, keep failures.
- Interactive inspection: `android emulator …`, `android run`, `android screen capture [--annotate]`, `android layout --pretty`, `android input …` (see the `android-cli` skill if installed).
- The owner's phone: never without an explicit request in this conversation; ask before every `pm clear`; animation scales to 0 and restored in a trap; never capture when another app is on top.
- No vision API, no automatic approval or merge. State which device produced each piece of evidence and what stays NOT_RUN (haptics and fluidity need hardware).
