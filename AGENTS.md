# Jean-Calcul — agent instructions

Personal Android assistant (Kotlin, Jetpack Compose, single `app` module) derived from ETA (GPL-3.0, source pinned in `docs/eta/UPSTREAM.md`; the takeover and feature selection were explicitly requested on 2026-11-14). UI is **COUI 2.0 (ColorOS 17)** — not Material.

## Read first

| Task | Read |
| --- | --- |
| Anything | `PRODUCT.md` (product truth, voice), `docs/ARCHITECTURE.md` |
| **Anything visible on screen** | skill `coui-ui` → `DESIGN.md`, `docs/design/` |
| Look at a composable | skill `compose-preview` → `docs/agents/PREVIEWS.md` |
| Emulator / phone checks | skill `device-check` → `docs/agents/DEVICE.md` |
| Show the owner work in progress, ask them to choose or approve | `docs/agents/DEV-PREVIEW.md` (`scripts/jc-preview push / ask --wait`) |
| A feature (Brief, food, notifications, car, self-improvement, updates) | `docs/features/` |
| Scope questions | `docs/eta/SELECTION.md`, `product/JeanCalculProfile.kt` |

Skills live in `.agents/skills/` (`.claude/skills` points there). The design process is **impeccable** (`impeccable` skill: `shape`, `critique`, `polish`, `audit`…), with the COUI override described in `coui-ui`.

## Non-negotiables

- **UI:** COUI components and `COUITheme` tokens only; no Material 3, no hex colors, no hand-made look-alikes. Every UI change is rendered with `scripts/preview` and looked at before it is shown or reported done.
- **Scope:** enforced by `app/src/main/kotlin/io/github/mangi/eta/product/JeanCalculProfile.kt` — conversation providers are ChatGPT/Codex and vLLM only; voice engines (Android local, OpenAI, vLLM) are separate. Do not re-expose excluded blocks; upstream Room/navigation compatibility types may stay.
- **Reuse** upstream runtime, components, services and tests; no parallel reimplementation.
- **Identity:** distributed as `fr.loevan.jeancalcul.v2`; the Kotlin/JNI namespace `io.github.mangi.eta` stays (never rename native classes by global replace).
- **Capabilities:** root/LSPosed optional; check availability and permissions before any action.
- **Data:** no secrets, personal accounts or real data in tests, previews, screenshots or docs.
- **Licenses:** keep `LICENSE` and attributions (`docs/eta/UPSTREAM.md`, `docs/design/ATTRIBUTION.md`).
- **Owner's control:** never merge, and never install on or touch the owner's phone without an explicit request. Publishing to devhub (`scripts/jc-preview`) is allowed: the owner installs from Preview Hub. PRs target `feature/jean-calcul-v2-foundation`, never `main`.

## Build and verify

NixOS host: there is no JDK or python3 on the bare `PATH`; everything runs in the flake shell.

```sh
nix develop -c ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug   # JDK 25, SDK 37
scripts/preview render <Preview>      # headless UI check (enters nix develop itself)
nix develop -c python3 e2e/run.py --run-id <topic> --attempt 1   # synthetic emulator, see DEVICE.md
```

- Don't pipe `./gradlew -q` through grep outside `nix develop`: "JAVA_HOME is not set" looks like a clean build.
- `-Pjeancalcul.emulator=true` builds the x86_64 test variant (no ARM64 PTY/root validation). The distributed APK is ARM64, Android 13+.
- Product tests: `:app:testDebugUnitTest --tests 'io.github.mangi.eta.product.*'`.
- Report real results: PASS/FAIL/NOT_RUN per check, which device or renderer produced visual evidence, what was not verified. Green CI is not visual validation.

## Tooling

- **Android CLI** (`android`, Google, v1.x): installed through the flake (`nix profile add .#android-cli`, already in `nix develop`). `android preview` renders Compose previews headlessly; `android screen capture`, `android layout`, `android run`, `android emulator`, `android docs search` work on devices and docs. Run `android init` once per machine for the official `android-cli` skill.
- **COUI library sources:** https://github.com/suqi8/coui at tag `v2.0.0` (`docs/design/coui-components.md` explains how to read a signature).

## Sensitive areas

OAuth and provider secrets (`agent/auth`, `ProviderCredentialRepository`); Room migrations in `data/db` (never drop tables to remove a UI entry); phone actions and permissions at execution time; LSPosed subset limited to `system`; the in-app self-improvement agent reads this file (`agent/development`), so keep it accurate and concise.
