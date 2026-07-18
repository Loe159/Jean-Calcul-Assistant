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

## Glass material and real Android limits

Compose has no portable backdrop-blur primitive equivalent to the CSS effect shown in the Stitch
references. `blurEnabled` is therefore a capability/preference switch; it does not claim that
Jean Calcul samples and blurs pixels behind a component. `GlassSurface` builds a credible Android
approximation from a tonal base, controlled translucency, a subtle directional gradient, a
separate top reflection, weaker side and bottom edges, and diffuse optical elevation.

Panel, card, interactive, selected, overlay, navigation and modal variants use distinct tonal and
elevation levels. Violet is contextual: focused and selected focal surfaces may use it, while
ordinary panels and cards keep blue/neutral reflections. The opaque fallback keeps its edge,
contrast and elevation, so disabling translucency does not turn the UI into a uniform grey
Material card.

## Effects and accessibility modes

`VisualEffects` receives the user or system preference:

- Full effects allow deterministic Canvas gradients, tonal translucency and motion.
- `reduceMotion` removes looping animation and Canvas gradients. Static tonal layers and short
  state transitions remain usable.
- `blurEnabled = false` selects opaque tonal surfaces; `shadersEnabled = false` selects flat,
  layered Canvas rendering. These switches may be combined for the strict fallback.
- High contrast selects opaque tonal surfaces and stronger optical separation.

No control, message or state depends on blur, a gradient, animation or color alone.

`GradientOrb` and `VoiceWave` accept normalized amplitude and deterministic progression. They
never access audio. Their microphone-active labels appear only for their `Listening` states.
All pressable components have a minimum 48 dp height or size, and text components wrap rather
than carry a fixed text width.

The orb remains blue at its core. Cyan supplies an internal highlight, while violet is reserved
for thinking, proposal and approval emphasis. `OrbMotionMode.Static`, `Reduced` and `NoShader`
make screenshots and constrained-device rendering independent from wall-clock animation.

## Visible integration

The design system is not preview-only:

- `AssistantRoleOnboarding` uses the obsidian background, a restrained ambient glow, a stateful
  orb, a glass card and Jean Calcul buttons while retaining its existing role/permission logic.
- `TransparentAssistantSessionContent` uses its existing session state and callbacks to drive a
  bottom-anchored glass composition, `GradientOrb`, `VoiceWave`, transcription, privacy/status
  surfaces and compact actions.

Functional settings, audit and diagnostics remain presentation previews in `core-ui`; their
business implementations belong to later issues.

## Validation assets

`DesignSystemPreviews.kt` covers foundations, assistant home, conversation, listening session,
compact overlay, simple and biometric confirmation, settings, audit and diagnostics. Main
compositions include dark, light, reduced-effect and no-blur variants. Additional previews cover
large font, long French copy, error and offline states.

`DesignGoldenSpecTest` locks palette, depth, fallback contrast, 48 dp targets, deterministic state
contracts and the absence of a feature/business project dependency from `core-ui`.
`DesignScreenshotTest` renders complete components at fixed density, font scale, size, amplitude
and progression. It analyses multiple regions, tonal variance and blue/violet pixel families for
dark glass, opaque fallback, idle/listening/thinking orbs, compact overlay, approval, light theme,
reduced effects and large font. The tests intentionally avoid a fragile binary golden and never
depend on real-time audio or animation. Instrumented renders still require an Android device or
emulator; assembling the Android test APK alone does not execute those assertions.

On 2026-07-19, all 11 instrumented render tests passed on the Samsung target `SM-S942B`
(Android 16). The Core Debug onboarding was also installed and visually inspected on that device.
Direct shell invocation of the privileged voice-interaction session was denied by Android's
`ACCESS_VOICE_INTERACTION_SERVICE` protection; the session composition is therefore covered by
module compilation and deterministic component renders, while a human invocation remains the
final validation for its real translucent window context.
