# Design system Compose - Jean Calcul Lumina

## Scope

`core-ui` contains reusable presentation primitives only. It has no dependency on a model
provider, agent backend, tool registry, Android permission, policy decision or feature module.
Hosts provide text, callbacks and presentational state. They remain responsible for actual
microphone capture, policy, execution, biometrics and data routing.

## Stitch to Compose mapping

| Stitch reference | Compose token or primitive |
| --- | --- |
| `#101415` obsidian surface | `JeanCalculDarkColorScheme.background` |
| `#0B0F10` deepest layer | documented tonal fallback base for overlays |
| `#181C1D` to `#313536` surfaces | Material 3 `surface` and `surfaceVariant` plus `GlassSurface` variants |
| `#E0E3E4` and `#C6C6CB` content | `onSurface` and `onSurfaceVariant` |
| silver, blue and violet accents | `primary`, `secondary`, `tertiary` |
| violet/blue ambient lights | `AmbientGlow`, `GradientOrb` Canvas gradients |
| 3 percent glass / 15 percent edge | `JeanCalculOpacity.glass` and `topHighlight` |
| 8 dp rhythm and 24 dp screen margin | `JeanCalculSpacing` |
| 4, 8, 12, 16, 24 dp radii | `JeanCalculShapes` |
| Hanken Grotesk / Inter / Geist roles | display/body/label roles in `JeanCalculTypography` |

The named Stitch fonts are not downloaded at runtime and no unlicensed font files are bundled.
The current implementation uses platform Sans Serif and Monospace fallbacks. A licensed font
pack may replace those families without changing component APIs.

## Effects and accessibility

`VisualEffects` receives the user or system preference. `reduceMotion` removes looping
animation and Canvas gradients, while preserving short state changes. `blurEnabled = false`
uses the opaque tonal treatment in `GlassSurface`. `shadersEnabled = false` uses flat Canvas
circles. High contrast also selects the tonal fallback. No control, message or state depends on
blur, a gradient, animation or color alone.

`GradientOrb` and `VoiceWave` accept normalized amplitude and deterministic progression. They
never access audio. Their microphone-active labels appear only for their `Listening` states.
All pressable components have a minimum 48 dp height or size, and text components wrap rather
than carry a fixed text width.

## Validation assets

`DesignSystemPreviews.kt` covers the foundation library, assistant home, conversation, listening
session, compact overlay, confirmation, settings, audit and diagnostics. The foundation previews
include dark, light, reduced-effect and no-blur variants. `DesignGoldenSpecTest` locks the core
palette and deterministic visual contracts without a device or timing source.
