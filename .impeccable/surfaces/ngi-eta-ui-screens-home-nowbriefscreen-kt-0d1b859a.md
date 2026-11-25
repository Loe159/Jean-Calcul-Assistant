---
version: 1
slug: "ngi-eta-ui-screens-home-nowbriefscreen-kt-0d1b859a"
primary_target: "app/src/main/kotlin/io/github/mangi/eta/ui/screens/home/NowBriefScreen.kt"
related_targets: ["app/src/main/kotlin/io/github/mangi/eta/ui/screens/home/brief","app/src/main/kotlin/io/github/mangi/eta/ui/screens/home/brief/cards"]
---

# Surface brief: Now Brief

**Scope:** the Now Brief page next to the chat — `ui/screens/home/NowBriefScreen.kt`, `ui/screens/home/brief/**`, `ui/screens/home/brief/cards/**`, `ui/screens/home/effect/**`.

**Mode:** Operate — glanceable synthesis with one-thumb actions.

**Audience and job:** the owner, several glances a day: "what matters now, and what do I do about it".

**Direction (owner decision, 2026-11-24, binding):** the owner's mockup `docs/design/references/now-brief-mockup.html` (captures `now-brief-dark-soir.png`, `now-brief-light-matin.png`). Full surface contract, allowed deviations from strict COUI (per-card tint halos, gradients on sky/weather/car/music/assistant cards, four densities, *tu* in assistant lines) and their limits: `docs/design/now-brief.md`. Deviations apply to this surface only.

**Constraints:** COUI components and tokens otherwise; cards without shadows; no emoji; honest data (demo data only behind a demo marker and in previews); motion static when animations are off.

**Open decisions:** importance score thresholds per card; hidden-cards drawer interaction.
