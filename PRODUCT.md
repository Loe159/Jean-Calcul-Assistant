# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

One primary user: the owner, who builds and uses Jean-Calcul daily on their own phone (Samsung, Android 17, One UI with a ColorOS-style app). They open it many times a day for short, practical jobs: read the Now Brief (what matters now), talk to the assistant (text, voice, long-press Power), let it act on the phone, check the car, cook, triage notifications. They are also the product owner and reviewer: they judge every screen on the device and reject anything that looks "hand-made" next to native COUI.

## Product Purpose

A personal Android assistant derived from ETA (GPL-3.0, pinned in `docs/eta/UPSTREAM.md`), rebranded Jean-Calcul. It combines:
- **Conversation** with ChatGPT/Codex (subscription sign-in) and self-hosted vLLM servers (OpenAI-compatible API), with tools that can act on the phone (accessibility, virtual display, alarms, Android data), optional root/LSPosed.
- **Voice**: recognition and speech with three engines only — Android local (default), OpenAI, vLLM.
- **Now Brief**: a contextual home feed of cards (notifications, agenda, car, weather, tasks, health, food, news…), ordered by what matters now, fed by the homelab Mastra service.
- **Food** (recipes, pantry, cooking timers), **intelligent notifications**, **in-app self-improvement** (Power → describe a change → PR → preview APK).

Success: the owner reaches for Jean-Calcul instead of the stock assistant, and no screen feels foreign on the phone.

## Positioning

A single-user assistant that looks like a ColorOS system app and runs on the owner's own models and homelab — not a SaaS product. It is allowed to be personal, opinionated and dense with the owner's real context.

## Operating Context

- One-handed use, often on the move; quick glances at the Now Brief; voice through long-press Power with the assistant overlay on top of other apps.
- Builds are distributed as signed preview APKs through the in-app update channel (`docs/features/PREVIEW-UPDATES.md`); the owner installs and judges on device.
- The homelab runs Mastra (notifications, brief cards, food) and vLLM; `127.0.0.1` is the phone, not the server.

## Capabilities and Constraints

- Scope is enforced by `app/src/main/kotlin/io/github/mangi/eta/product/JeanCalculProfile.kt` and `docs/eta/SELECTION.md`. Excluded blocks (Miuix, Linux/Kimi, characters, backups/exports, vendor integrations) stay hidden even where compatibility types remain.
- Distributed identity `fr.loevan.jeancalcul.v2`; the Kotlin/JNI namespace stays `io.github.mangi.eta`.
- ARM64 APK, Android 13+ (minSdk 33), compileSdk 37, JDK 25.
- Root and LSPosed are optional: every action checks availability and permissions first.
- Single UI system: **COUI 2.0** (`io.github.suqi8.coui.kmp`). No Material 3, no second theme.

## Brand Commitments

- **Design language: COUI 2.0 / ColorOS 17, binding.** The app must be indistinguishable from a ColorOS system app outside the Now Brief and assistant surfaces. Material Design guidance does not apply. Reference: `DESIGN.md`, the owner's design system artifact (https://claude.ai/artifact/RANbrf9sJZDFkyvBNZ2cdv, vendored as `docs/design/coui-tokens.json`).
- **Now Brief direction, binding on that surface:** the owner's mockup (https://claude.ai/artifact/DdGPw6iY5guXQaV7Um7r5o, vendored in `docs/design/references/`) and `docs/design/now-brief.md`.
- **Voice and language:** user-facing copy is French. Interface text uses *vous* ("Activez la sauvegarde"); the assistant and the Now Brief summaries it writes use *tu* ("Tu as 2 messages importants"). ColorOS writing rules: short sentences, sentence case, no final period in titles and rows, verbs on buttons ("Enregistrer", "Supprimer"), questions in dialog titles ("Supprimer 12 photos ?"), no emoji.
- Name: Jean-Calcul. Keep upstream LICENSE and attributions (`docs/eta/UPSTREAM.md`, `docs/design/ATTRIBUTION.md`).

## Evidence on Hand

- Demonstration data for previews and tests: `app/src/main/kotlin/io/github/mangi/eta/ui/preview/FakeAgentUiStates.kt`, Brief demo sources, `app/src/test/resources/*`. All synthetic.
- Never put real accounts, conversations, notifications, car data or secrets in previews, tests, screenshots or docs.

## Product Principles

1. **Native before novel.** If ColorOS has the widget, use the COUI component; invent only where the product has no native equivalent.
2. **What matters now.** Surfaces order and size information by urgency and time, then fold the rest away.
3. **Act, don't just answer.** The assistant and the Brief offer the next action at thumb reach, and confirm with an undoable snackbar.
4. **Honest about capability.** Unavailable tools, missing permissions and offline sources are shown plainly, never faked.
5. **Private by default.** The owner's data stays on the phone or the homelab.

## Accessibility & Inclusion

Every screen must work at 200 % font scale, in dark and light themes, with TalkBack labels on icon-only controls, 40 dp minimum touch targets, and with animations removed (system animator scale 0).
