# COUI motion and haptics

COUI animates with **springs**, not fixed durations. Library components already carry their motion; this file is for custom animations (Now Brief, assistant, transitions you write).

## Springs in Compose

A COUI spring is described by **response** (period, s) and **bounce** (0 = critically damped). Convert:

```kotlin
// stiffness = (2π / response)², dampingRatio = 1 − bounce
fun couiSpring(response: Float, bounce: Float = 0f) = spring<Float>(
    dampingRatio = 1f - bounce,
    stiffness = (2f * PI.toFloat() / response).let { it * it },
)
```

| Element | COUI spring | Compose |
| --- | --- | --- |
| Press / hover / focus layers, button tint, pressed card | damping 1, stiffness 438.65 (response 0.3 s) | `spring(1f, 438.65f)` |
| Switch toggle | response 0.4 s, bounce 0.3 | `spring(0.7f, 246.74f)` |
| Slider track ×1.4 | damping 1, stiffness 157.91 | |
| Slider thumb ×4/3 | damping 0.7, stiffness 157.91 | |
| Slider finger follow | damping 1, stiffness 1754.6 | |
| SegmentButton cursor | response 0.4 s, bounce 0.1; width 0.6 s bounce 0.5 | |
| List popup open / close | 0.8 / 322.27 ; 1 / 438.65 | from scale 0.6 |
| Tooltip | scale 0.8 / 246.74 ; alpha 0.8 / 438.65 | from scale 0.8 |
| Snackbar in / out | 1 / 438.65 ; 1 / 631.65 | |
| Bottom sheet pull bounce | 0.69 / 979; settle response 0.4 s | |
| Floating navigation bar | response 0.55 s, bounce 0.35 | |
| "Jelly" press on material cards | press 1 / 631.65; release 0.4 / 157.91; target scale 0.85–0.97 | |

## Easing curves (tweens)

| Token | Curve | Use |
| --- | --- | --- |
| `ease-move` | `CubicBezierEasing(0.3f, 0f, 0.1f, 1f)` | entries, moves, checkbox tick, dialog in, tab text |
| `ease-standard` | `CubicBezierEasing(0.33f, 0f, 0.67f, 1f)` | fades, colors, tab indicator, title collapse |
| `ease-in` | `CubicBezierEasing(0f, 0f, 0.1f, 1f)` | nav bar appearing, fields |
| `ease-out` | `CubicBezierEasing(0.3f, 0f, 1f, 1f)` | exits: dialog, nav bar hiding |
| `ease-sin-out` | sin(πt/2) | popup scrim (300 ms in, 150 ms out) |

The library exposes its own easings in `io.github.suqi8.coui.kmp.anim` — prefer them when available.

## Durations

- Dialog: in 250 ms (`ease-move`); out 150 ms centered, 250 ms toward the bottom (`ease-out`).
- TextField: focus line 250 ms, floating label 200 ms, error color 217 ms, error shake 450 ms (5.33 dp).
- TabRow indicator: 150 ms + 50 ms per tab crossed, max 300 ms; text color 300 ms.
- FAB: 350 ms (scale 0.6 → 1); menu 300 ms, 50 ms stagger.
- Badge width 517 ms (`ease-move`), number fade 150 ms.
- Search bar rise 450 ms (`ease-move`); rotating hints every 3 s, 250 ms.
- Large title collapse snap 180 ms (decelerate).
- Page transitions: `coui-nav` defaults; the in-app destination fade is 180 ms.

## Rules

- **Reduced motion:** every custom animation must degrade to an instant cut or a crossfade when the system animator scale is 0 (`Settings.Global.ANIMATOR_DURATION_SCALE`); Compose `tween`/`spring` already respect it — do not bypass it with manual frame loops.
- **Motion is material:** one orchestrated entrance per surface (e.g. Now Brief cards: fade + 16 dp rise, 55 ms stagger, `ease-move`), not scattered effects.
- **Never animate layout in a way that moves content under the user's finger** (no auto-scrolling feed on update).
- Static previews cannot prove fluidity: check motion on a device (see `docs/agents/DEVICE.md`) and say so in reports.

## Haptics

Library controls fire their own haptics (Switch, Slider steps, SegmentButton, scroll end). For custom interactions use the COUI haptic API (`CouiHapticEffect`, `SinkFeedback` for press feedback on custom surfaces) rather than raw `HapticFeedbackType` when an equivalent exists. Haptics can only be verified on hardware.
