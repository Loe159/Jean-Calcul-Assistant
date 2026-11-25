# Seeing your UI: headless Compose previews

You must look at every UI change before showing it. The fast path needs no emulator, no device and no APK install: Google's **Android CLI** (`android preview`) renders `@Preview` functions with the real app theme straight to PNG, plus a component tree (bounds, semantics, source lines).

Typical cost: first run in a fresh checkout ~5 min (full Kotlin compile); afterwards ~30–50 s per render (incremental compile + renderer daemon).

## One command

```sh
scripts/preview list [PATTERN]          # FQNs of all @Preview functions, filtered (case-insensitive)
scripts/preview render PATTERN|FQN ...  # render every matching preview
```

`scripts/preview` enters `nix develop` itself (JDK 25, Android SDK, Android CLI, ImageMagick). For each preview it prints:

```
## io.github.mangi.eta.ui.preview.CouiSpecimenPreviewsKt.SettingsPageSpecimenPreview
  1. light 393x852dp font 1   build/previews/.../SettingsPageSpecimenPreview_0.png
  2. dark 393x852dp font 1    …_1.png
  3. light 393x852dp font 2   …_2.png
  4. light 320x640dp font 1   …_3.png
  sheet (variants 1..4 left to right): build/previews/SettingsPageSpecimenPreview.sheet.png
  ! [3] touch target 32x32dp < 40dp  IconButton @ MyScreen.kt:120
```

- **Open the `.sheet.png` with your image tool** (Read on Claude Code, `view_image` on Codex): all variants side by side in one image. Open single variants when you need detail.
- `!` lines are mechanical findings from the component tree: touch targets < 40 dp, clickable without text/content description, text running past the right edge. They point at the nearest source line in this repo. They do not replace looking.
- Outputs live in `build/previews/` (git-ignored). Re-rendering overwrites them.

Raw CLI, if you need it: `android preview list :app` and `android preview render :app --daemon --component-tree --fqn=<FQN> [--fqn=…] --output=<dir>` (inside `nix develop`). Docs: `android help preview`, https://developer.android.com/tools/agents/android-cli/commands/preview.

## Writing previews

Kit: `app/src/main/kotlin/io/github/mangi/eta/ui/preview/CouiPreviewKit.kt`.

| Annotation | Variants | For |
| --- | --- | --- |
| `@CouiPhonePreviews` | light + dark, 393 × 852 dp | every screen |
| `@CouiStressPreviews` | 200 % font + 320 × 640 dp | every screen you changed |
| `@CouiComponentPreviews` | light + dark, wrap height | components, cards, rows |

```kotlin
@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun ExampleScreenPreview() {
    CouiPreviewTheme {
        ExampleScreen(state = ExamplePreviewData.populated, onBack = {}, onDelete = {})
    }
}

@CouiComponentPreviews
@Composable
private fun PermissionHealthCardPreview() {
    CouiPreviewTheme(fullScreen = false) {
        PermissionHealthCard(state = FakeAgentUiStates.permissionHealth, onOpenPermissions = {})
    }
}
```

Rules:
- `private`, named `<Thing>Preview`, at the bottom of the file of the composable it previews. Design-system specimens live in `ui/preview/CouiSpecimenPreviews.kt`.
- Always `CouiPreviewTheme` (the real `AgentAppTheme`, COUI palette, dark from `uiMode`). Never a bare `COUITheme`, never a hand-made background.
- Only stateless composables: pass state and lambdas. If a screen reads stores, `Context` singletons, DataStore, Room or the network, split it: `XScreen(state, on…)` renders, the route/wiring composable collects state and calls it. Preview `XScreen`.
- Synthetic data only: `FakeAgentUiStates`, `BriefDemoData`, a `<Feature>PreviewData` object next to the screen. No real names, accounts, messages or secrets.
- Cover the states that matter: empty, loading, populated, long text, error, disabled. One preview function per state (`…EmptyPreview`, `…ErrorPreview`), each with the phone multipreview.
- Overlays (`OverlayDialog`, sheets, menus) need a COUI `Scaffold`: preview them inside the page that hosts them (e.g. a `CouiScaffoldPage` with the dialog `visible = true` in its `overlay` slot).

## What previews cannot show

Layoutlib renders a single frame: no animation, no haptics, no real blur backdrop sampling (material blur may fall back to its opaque version), no IME, no system bars or insets of a real window, no predictive back. Those need the device pass in `docs/agents/DEVICE.md`. Say which one produced your evidence.

The Cuisine sheet previews present the production sheet content in its host page with a static COUI surface. Layoutlib captures the native sheet before its entrance finishes.

## Troubleshooting

- `android: command not found`: you are outside `nix develop`; use `scripts/preview`, or `nix develop -c android …`. Inside the devShell the Nix SDK also ships a legacy `android` tool; the flake puts the CLI first on `PATH`.
- `kspDebugKotlin … StreamCorruptedException`: a KSP cache written by another Gradle run. Run `nix develop -c ./gradlew :app:kspDebugKotlin` once, then retry.
- Gradle error in a render: read the `Build log: /tmp/build-log-….log` path printed by the CLI; it is a normal compile error in your code.
- Preview renders blank or crashes: something in the tree touches Android services, files or singletons. Hoist it out of the previewed composable.
- New machine: install the CLI with `nix profile add .#android-cli` (NixOS) or Google's installer, then `android init` once (installs the official `android-cli` agent skill).
