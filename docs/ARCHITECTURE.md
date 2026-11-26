# Architecture

One Android module `app`, Kotlin + Jetpack Compose. Internal namespace `io.github.mangi.eta`, applicationId `fr.loevan.jeancalcul.v2`. Sources under `app/src/main/kotlin/io/github/mangi/eta/`.

- `ui/` — screens and stores on COUI 2.0 (`io.github.suqi8.coui.kmp`: ui, preference, blur, nav, icons) with a single theme, `ui/app/AgentAppTheme.kt`.
  - `ui/components/coui/` — the app's COUI compositions only: page scaffolds (collapsing bar, COUI 17 glow, recorded backdrop for blur), blur bridge, app dialogs, buttons/fields, preference parts missing from the library. See `docs/design/coui-components.md`.
  - `ui/preview/` — preview kit (`CouiPreviewTheme`, multipreview annotations), design-system specimens, synthetic UI states. See `docs/agents/PREVIEWS.md`.
  - `ui/screens/home/` — chat home and the Now Brief (`brief/`, `brief/cards/`, `effect/` OS2 background). See `docs/design/now-brief.md`.
- `agent/runtime`, `agent/model` — loop, streaming, sessions, cancellation, providers and tool schemas.
- `agent/tool`, `agent/accessibility`, `agent/virtualdisplay` — Android execution with availability/permission checks.
- `agent/voice`, `agent/auth`, `agent/mcp`, `agent/skill`, `agent/subagent`, `agent/terminal` — upstream engines kept for the selected blocks.
- `agent/device/notifications` — notification listening and Mastra decisions. See `docs/features/MASTRA-NOTIFICATIONS.md`.
- `agent/development` — in-app self-improvement (Power capture → PR → preview APK). See `docs/features/SELF-IMPROVEMENT.md`.
- `agent/majordome` — delegation of household requests to the homelab Majordome (`ask_majordome`). See `docs/features/MAJORDOME.md`.
- `data/` — Room, DataStore, encrypted secrets. Upstream compatibility tables/types are not exposed features. `data/car/` feeds the Brief car card (`docs/features/NOW-BRIEF-CAR.md`).
- `food/` — Mastra client, local cooking sessions, Room caches, Android timers (`docs/features/FOOD.md`).
- `product/JeanCalculProfile.kt` — immutable selection: filters providers and catalogs, refuses excluded tool calls.
- LSPosed module: only `system` is declared (accessibility / virtual display); vendor/Google hooks are not installed.

Upstream technical documentation is kept in `docs/upstream/eta/` as reference only; it does not define product scope.
