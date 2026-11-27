# Checking on a device

Headless previews (`docs/agents/PREVIEWS.md`) are the default visual check. Go to a device only for what a single frame cannot show: motion, haptics, real blur, IME/keyboard, insets and system bars, back gestures, overlays over other apps, real data flows. Always say which surface produced your evidence.

## 1. Synthetic emulator (default device)

`e2e/run.py` creates its **own blank AVD** (API 35, x86_64, no account, network cut), builds the `-Pjeancalcul.emulator=true` variant, runs the instrumented scenarios and captures PNGs with SHA-256 hashes. Details: `e2e/README.md`.

```sh
nix develop -c python3 e2e/run.py --run-id <issue-or-topic> --attempt 1   # add --software-emulator without KVM
nix develop -c python3 e2e/report.py e2e-artifacts/<run-id>/attempt-1
```

- Open each PNG yourself and fill `visual-review.json` (PASS/FAIL/NOT_RUN with a concrete observation, `opened_with`); `regression_fixture` must be FAIL — it is a deliberate defect that proves detection.
- At most 3 attempts per change, same run-id; keep failures. Boot 240 s, build 1500 s, scenario 120 s limits.
- Captures can show motion end states only; never claim fluidity or haptics from them.

Interactive poking on the emulator with the Android CLI (inside `nix develop`):

```sh
android emulator list | start <name> | stop <name>
android run --apks=app/build/outputs/apk/debug/app-debug.apk --device=emulator-5580
android screen capture --output=build/device/now.png [--annotate]   # --annotate numbers UI elements
android layout --pretty --output=build/device/layout.json            # UI tree with bounds
android input tap|swipe|text …                                       # see `android help input`
```

## 2. The owner's phone (only when asked)

The owner sometimes asks for checks on their own phone (Samsung, Android 17) over wireless ADB through Tailscale. Rules, all mandatory:

- Never install, `pm clear`, or run anything on it without an explicit request in this conversation. `pm clear` wipes their real Jean-Calcul data: ask before each run (AskUserQuestion with a "Oui" option); any attempt beyond 3 needs explicit authorization.
- The address/port changes when wireless debugging restarts; ask for it, ping first.
- `e2e/run.py` refuses existing devices: install both APKs, run `am instrument … VisualScenarioTest#capture -e scenario <name>`, pull PNGs with `run-as`.
- Set the three global animation scales to 0 for the run and restore 1.0 in a trap.
- Before any manual dump/tap/capture: `adb shell dumpsys activity activities | grep topResumedActivity`. If another app is on top, do not capture — their screen is private. Delete any capture that shows another app.
- Never reverse-scroll inside sheets or at the top of the Brief (it closes them).

## 3. Preview APKs

For the owner to see work in progress quickly, push to **Jean-Calcul Preview** instead (`DEV-PREVIEW.md`): a separate app, a local build, no CI wait.


Pushes to the foundation branch build a signed ARM64 preview that the owner installs through the in-app updater (`docs/features/PREVIEW-UPDATES.md`). That is the owner's own acceptance test, not your evidence.
