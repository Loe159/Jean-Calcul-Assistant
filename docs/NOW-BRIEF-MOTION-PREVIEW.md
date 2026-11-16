# Now Brief — experimental motion preview

This revision is a **UX experiment in PR #70**, not the final motion design. No configuration or notification contents are stored by the briefing.

## Navigation discoverability
- **B Peek**: slim clickable rail at the right edge of the home chat.
- **C Intro**: single 17%-width peek animation on the first resumed home visit; preference `now_brief_discovery.seen` prevents repeats. Interaction cancels the nudge.
- **D Indicator**: two capsule segments on the home screen; their width and opacity interpolate with the real drag offset.

## Motion matrix
- **A1**: COUI title reveals using opacity + vertical offset upon opening.
- **A4**: cards stagger 75 ms apart.
- **A6**: numeric counters increment with easing.
- **B1**: subtle 16s gradient, **first open**.
- **B2**: slowly moving radial halo, **second open**.
- **B4**: capped vertical parallax of ambient background as list scrolls.
- **B6**: 12 translucent drifting particles, **third open**.
- **C1**: category bars animate to real proportions.
- **C2**: recent notification rows enter from above, then fade in.
- **C4**: hero card compresses slightly on press and opens factual action details.
- **D1**: 28dp max horizontal parallax during the side-pane drag.
- **D2**: same COUI `LargeTopAppBar` collapsing material as Settings.
- **D4**: one light haptic when the page settles into a different anchor.
- **D5**: live two-segment position indicator.
- **D6**: lazy cards appear only the first time they are scrolled into view within an opening.

## Cycle and safety

**Each opening rotates B1 → B2 → B6 → B1**, while the finite interaction effects remain available. The brief labels the active ambiance `Ambiance 1/3 · Dégradé`, etc., to make comparisons reproducible.

No infinite backdrop is composed when the brief is closed or when Android battery saver is enabled. The system's animator duration scale of 0 disables experimental motion. The backdrop has no GPU blur or full-resolution bitmap allocation; particle count is bounded to 12. Motion does not initiate local inference or network calls.

## Validation

The `brief` instrumentation scenario checks for the right edge hint, position indicator, left and right swipes, and two ambient cycles. `NowBriefMotionTest` covers deterministic cycling. Run Android build, JVM tests, lint, and E2E before distributing the APK. Retain the PR in draft until visual review by a person.
