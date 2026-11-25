---
name: Jean-Calcul
description: A personal Android assistant that looks and behaves like a ColorOS 17 system app (COUI 2.0).
# Values are COUI 2.0 (suqi8/coui, ColorOS 17), light theme first, then `-dark`.
# Token names ARE the Compose names: `primary` -> COUITheme.colorScheme.primary,
# `bodyM` -> COUITheme.textStyles.bodyM. Code never writes these hex values.
# Full set (116 colors, both themes, usage notes): docs/design/coui-tokens.json
colors:
  primary: "#0080ff"
  primary-dark: "#1a8cff"
  onPrimary: "#ffffff"
  error: "#db382c"
  error-dark: "#ff6c61"
  success: "#00bd13"
  success-dark: "#24b232"
  backgroundWithCard: "#f0f1f2"
  backgroundWithCard-dark: "#000000"
  background: "#ffffff"
  background-dark: "#000000"
  cardBackground: "#ffffff"
  cardBackground-dark: "#ffffff1a"
  cardPressed: "#e6e6e6"
  cardPressed-dark: "#ffffff33"
  surface: "#ffffff"
  surface-dark: "#1e1e1e"
  backgroundTop: "#ffffff"
  backgroundTop-dark: "#333333"
  bar: "#fafafa"
  bar-dark: "#1e1e1e"
  labelPrimary: "#000000e6"
  labelPrimary-dark: "#ffffffe6"
  labelSecondary: "#0000008a"
  labelSecondary-dark: "#ffffff8a"
  labelTertiary: "#00000042"
  labelTertiary-dark: "#ffffff4d"
  divider: "#0000001f"
  divider-dark: "#ffffff33"
  press: "#0000001f"
  press-dark: "#ffffff33"
  container4: "#0000000a"
  container4-dark: "#ffffff1a"
  container8: "#00000014"
  container8-dark: "#ffffff1a"
  secondaryButton: "#ebebeb"
  secondaryButton-dark: "#353535"
  mask: "#00000033"
  mask-dark: "#00000099"
  red: "#eb3b2f"
  orange: "#ff7700"
  yellow: "#ffb200"
  green: "#00bd13"
  mint: "#00cc88"
  cyan: "#1dbacc"
  azure: "#00a1ff"
  blue: "#0080ff"
  violet: "#5a47ff"
  purple: "#a723ff"
typography:
  displayS:
    fontFamily: "system (OPPO Sans on ColorOS, Roboto elsewhere)"
    fontSize: "32sp"
    fontWeight: 600
    lineHeight: "46.21sp"
  displayXS:
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "33.75sp"
  headlineS:
    fontSize: "18sp"
    fontWeight: 550
    lineHeight: "24.11sp"
  headlineXS:
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "21.71sp"
  bodyXL:
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "21.71sp"
  bodyL:
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "19.9sp"
  bodyM:
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20.09sp"
  bodyS:
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "17.88sp"
  bodyXS:
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16.07sp"
  buttonL:
    fontSize: "16sp"
    fontWeight: 500
    lineHeight: "21.71sp"
  buttonM:
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20.09sp"
  caption:
    fontSize: "10sp"
    fontWeight: 400
    lineHeight: "14.06sp"
rounded:
  tag: "4dp"
  textfield: "10dp"
  snackbar: "16dp"
  tooltip: "17dp"
  card: "24dp"
  popup: "28dp"
  dialog: "45dp"
  full: "9999dp"
spacing:
  xxs: "2dp"
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  page: "16dp"
  lg: "24dp"
  xl: "32dp"
  xxl: "40dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    typography: "{typography.buttonL}"
    rounded: "{rounded.full}"
    height: "44dp"
  button-secondary:
    backgroundColor: "{colors.secondaryButton}"
    textColor: "{colors.labelPrimary}"
    typography: "{typography.buttonL}"
    rounded: "{rounded.full}"
    height: "44dp"
  card:
    backgroundColor: "{colors.cardBackground}"
    rounded: "{rounded.card}"
  list-item:
    textColor: "{colors.labelPrimary}"
    typography: "{typography.headlineXS}"
    padding: "10dp 16dp"
    height: "48dp"
  section-title:
    textColor: "{colors.labelSecondary}"
    typography: "{typography.bodyXS}"
    padding: "8dp 32dp"
  chip:
    backgroundColor: "{colors.container8}"
    textColor: "{colors.labelPrimary}"
    typography: "{typography.bodyM}"
    rounded: "{rounded.full}"
    height: "32dp"
  chip-selected:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
  dialog:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.dialog}"
    width: "360dp"
  snackbar:
    backgroundColor: "{colors.backgroundTop}"
    textColor: "{colors.labelPrimary}"
    rounded: "{rounded.snackbar}"
    padding: "10dp 16dp"
  icon-button:
    textColor: "{colors.labelPrimary}"
    size: "40dp"
---

# Design System: Jean-Calcul

## Overview

**Creative North Star: "The Native Companion"**

Jean-Calcul must pass for an app that shipped with the phone. A fluent ColorOS user opening any settings page, list, dialog or menu should find nothing to trip on: the same rows, cards, spacing, springs and blur as the system Settings app. That fidelity is the brand; expression is spent only where the product is genuinely a synthesis — the **Now Brief** and the **assistant** — and nowhere else.

The platform design language is **COUI 2.0 (ColorOS 17)** through the Compose library `io.github.suqi8.coui.kmp` (version pinned in `gradle/libs.versions.toml`). **It replaces Material 3 entirely.** Wherever a generic Android guideline (including impeccable's `android.md`) says "Material", read "COUI": COUI components, COUI color roles, COUI type scale, COUI motion. There is no Material 3 dependency in this app and none may be added.

Density is that of a system Settings app: calm neutral surfaces, one blue accent, cards on a grey (light) or pure black (dark) window, generous 48 dp rows. Depth comes from color, never from card shadows.

**Key Characteristics:**
- Every widget is the COUI component that ports the matching ColorOS widget; nothing is redrawn from `Box`/`Text`.
- One accent (`primary`), neutral everything else; tint palette only for category identity.
- Cards on `backgroundWithCard`, 16 dp from the screen edge and from each other, 24 dp squircle corners, no shadow.
- Dark mode is its own design: pure black window, cards in 10 % white, text at fixed white opacities.
- Springs, not durations; haptics on every control.
- Two scoped exceptions, documented below: the Now Brief and the assistant overlay.

## Colors

A neutral system palette with a single blue accent; color only means "action", "state" or "category".

All values live in `docs/design/coui-tokens.json` (both themes, usage notes). In code, colors come **only** from `COUITheme.colorScheme.<name>`; the names in the frontmatter are those properties. `AgentAppTheme` builds the scheme (`ThemeController`, `ThemeColorSpec.Spec2025`; Monet optional, off by default).

### Primary
- **ColorOS Blue** (`primary` #0080FF / #1A8CFF): primary button, checked Switch/Checkbox/Radio, active Slider track, selected Chip, progress, links and text buttons. Text on it: `onPrimary`.

### Neutral
- **Window Grey** (`backgroundWithCard` #F0F1F2 / #000000): page background whenever the page holds cards (almost always).
- **Plain Window** (`background` #FFFFFF / #000000): pages without cards (chat, reading).
- **Card White** (`cardBackground` #FFFFFF / white 10 %): cards and list groups; pressed `cardPressed`.
- **Floating Surface** (`surface`, `backgroundTop`): dialogs and sheets (`surface`); snackbar, popups, tooltips (`backgroundTop`).
- **Ink** (`labelPrimary` 90 %, `labelSecondary` 54 %, `labelTertiary` 26–30 %): titles and body; summaries and values; chevrons and hints only.
- **Hairline** (`divider`, 0.33 dp): drawn by `CardListGroup`, never by hand between rows.

### Tertiary — tint palette
- `red`, `orange`, `yellow`, `green`, `mint`, `cyan`, `azure`, `blue`, `violet`, `purple`, each with `…Variant` and `container…`. Fixed across Monet. Use for settings icon badges, charts and category identity (Now Brief cards), **never** for system states.

### Named Rules
**The One Voice Rule.** `primary` is the only accent outside the Now Brief. If a screen has two competing blues or a second accent color, it is wrong.

**The Token-Only Rule.** No `Color(0x…)` in UI code. The only exceptions are brand artwork and illustrations (`Peugeot208Illustration`, `BriefSky`, avatars), each with a comment saying why. Existing debt (`SettingsIconColors`, `StatusSuccess`/`StatusWarning`) migrates to `colorScheme.blue/green/orange/yellow` when those screens are touched.

**The Low-Contrast Rule.** `labelTertiary` and `hintNeutral` (26–30 %) never carry information someone must read. `primary` on white is 3.8:1: only for controls and ≥ 14 sp medium text.

## Typography

**Display/Body Font:** the system face (OPPO Sans on ColorOS devices, the OEM/Roboto face elsewhere) — never a bundled brand font.

**Character:** one family, hierarchy by weight and size; row titles at weight 500 are the signature of COUI 17.

### Hierarchy
- **displayS** (600, 32 sp): large collapsing top bar title (`CouiScaffoldPage`).
- **displayXS** (600, 24 sp): collapsed/small title, big figure in a card.
- **headlineS** (550, 18 sp): dialog and bottom-sheet titles.
- **headlineXS** (500, 16 sp): list row titles, EmptyState and TopTips titles.
- **bodyXL / bodyL** (400, 16 / 15 sp): input text, popup options / long text, search field.
- **bodyM** (400, 14 sp): running text, chips, snackbar, values at the end of a row.
- **bodyS** (400, 13 sp): row summaries, dialog messages, footers.
- **bodyXS** (12 sp; 500 for section headers): section titles (`PreferenceCategory`), subtitles.
- **buttonL / buttonM** (500, 16 / 14 sp): button labels.
- **caption / tag** (10 sp, 400 / 500): legends, nav labels, tags.

### Named Rules
**The Role Rule.** Text styles come from `COUITheme.textStyles.<role>`; never `fontSize = N.sp` in UI code. All sizes are sp and must survive 200 % font scale (verified in the `Font 200%` preview).

**The Plain Type Rule.** No italics, no all-caps, no added letter-spacing. Tabular figures for aligned numbers.

## Layout

- **Page margins:** 16 dp under 600 dp width, 24 dp up to 840 dp, 40 dp beyond (`CouiScaffold` computes `sidePadding`).
- **Settings rhythm:** `PreferenceCategory` title (8 × 32 dp padding) → `Card` with `Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)` → `CardListGroup { item { row } }`. One topic per card. Footer notes with `PreferenceFooter`.
- **Rows:** 48 dp minimum, 10 × 16 dp padding, 24 dp start icon spaced 16 dp from the title by the row itself.
- **Bars:** top bar 52 dp, large title 32 sp, 110 dp expanded; bottom bar 56 dp; floating navigation bar 56 dp capsule 16 dp from the edges.
- **Touch targets:** 40 dp minimum (IconButton), 44 dp buttons, 48 dp rows.
- **Insets:** edge-to-edge; system bars, cutout and IME insets are applied by the scaffold. Content never sits under the gesture bar or keyboard.
- **Reference phone:** 393 × 852 dp; also verified at 320 × 640 dp and 200 % font.

## Elevation & Depth

Flat by default: surfaces separate by color (`cardBackground` on `backgroundWithCard`), not shadow. Only floating elements are lifted, and in COUI 17 they also blur what is behind them (material blur, `coui-blur`, Android 13+ with `RuntimeShader`; opaque fallback otherwise).

### Shadow Vocabulary
- **popup** (`0 12 48 #00000018, 0 0 4 #0000000a`): menus and dropdowns (library default).
- **snackbar** (`0 4 26 #00000024`), **tooltip** (`0 3.33 26.66 #00000023`), **floating bar** (`0 8 24 #0000001f`): library defaults.
- **thumbs** (switch, slider): library defaults.

### Named Rules
**The No-Shadow Card Rule.** A `Card` never has a shadow or border. If it needs to stand out, it is the wrong surface or the wrong page background.

**The Blur Is Plumbing Rule.** Bars, dialogs, sheets and menus blur only through the page backdrop that `CouiScaffoldPage` records (`ui/components/coui/CouiBlur.kt`). Never fake blur with translucent fills.

## Shapes

Corners are **squircles** (Oplus superellipse), not circular arcs: `Card` and COUI surfaces draw them; custom surfaces use `SquircleShape`/`squircleClip` from `coui-squircle`, not `RoundedCornerShape`.

- Cards and list groups: 24 dp (smoothing 2.5).
- Dialogs and bottom sheets: 45 dp (smoothing 3).
- Popups and TopTips: 28 dp. Snackbar: 16 dp. Text field: 10 dp. Tooltip: 17 dp. Tag: 4 dp.
- Buttons, switches, sliders, search, chips, floating bars: full capsule.
- Settings icon badges (white glyph on a tint): 8 dp.

## Components

Per-component specs, the ColorOS → COUI map and the project's wrappers are in `docs/design/coui-components.md`. Rendered references: `ui/preview/CouiSpecimenPreviews.kt` (`scripts/preview render Specimen`).

### Buttons
- **Shape:** capsule, 44 dp tall, 132 dp minimum width (`size small`: 28 dp).
- **Primary:** `primary` / `onPrimary`, one per area. **Secondary** (default): `secondaryButton` / `labelPrimary`. **Destructive:** `error` text, or filled `error` for the final confirmation.
- **Press:** `press` overlay on a critically damped 0.3 s spring; no scale change.
- **In code:** `CouiButton(text, onClick, role = CouiButtonRole.Primary | Secondary | Destructive | DestructiveFilled)`; top bar text action `CouiTopAppBarTextButton`.

### Cards and list groups
- **Settings:** `PreferenceCategory` + `Card` + `CardListGroup { item { … } }`, rows = `ArrowPreference` (navigates), `SwitchPreference` (toggles immediately), `BasicComponent` (action/info, value via `PreferenceValueText`), `OverlayDropdownPreference` (pick one), `SliderPreference`, `CheckboxPreference`.
- **Free content card:** `Card` with 16 dp inner padding; pressable cards switch to `cardPressed`.

### Chips and tabs
- **Chip:** 32 dp capsule, `container8`, selected `primary`. **Suggestion chip:** 28 dp. **ChipTabRow:** category tabs under the bar. **SegmentButton:** 2–4 views of the same content.

### Inputs
- **TextField:** rectangle (forms on a page), line (dialogs/sheets), none (inside a card); `bodyXL`; error shakes 5.33 dp.
- **SearchBar:** 40 dp capsule; a search under a top bar is linked with the bar (see components doc).

### Navigation
- Pushed pages: `CouiScaffoldPage(title, onBack)` with collapsing large title and system/predictive Back. Floating bottom navigation where content is full-bleed.

### Overlays
- **Dialog:** `OverlayDialog` + `DialogButtonBar`, bottom-anchored on phones, cancel first, destructive action in the neutral (red) slot.
- **Bottom sheet:** `OverlayBottomSheet`; **menus:** `OverlayIconDropdownMenu`, `OverlayContextMenu`; **feedback:** snackbar via `SnackbarHostState`, never a Toast for confirmable actions.
- Every `Overlay*` must render inside a COUI `Scaffold` (the page scaffold provides it).

### Now Brief (scoped exception)
The Now Brief is the one surface allowed to be expressive: per-card tint halo, tinted icon chips, gradients on a few cards, four card densities, contextual ordering. Its contract, references and limits are in `docs/design/now-brief.md`; nothing from it leaks into other screens.

## Do's and Don'ts

### Do:
- **Do** find the COUI component first (`docs/design/coui-components.md`, then the library), and only compose a custom surface when none exists — then with tokens, squircles and the library's press feedback.
- **Do** keep screens stateless (`Screen(state, onEvent)`) so they get a `@CouiPhonePreviews` preview, and render it with `scripts/preview` before showing anyone.
- **Do** check light, dark, 200 % font and 320 dp width on every changed screen.
- **Do** put the card padding (16 dp sides, 16 dp bottom) on every `Card`; let rows, dividers and icons keep library spacing.
- **Do** give every icon-only control a content description and a 40 dp target.

### Don't:
- **Don't** use Material 3 (or Material components/patterns copied from generic Android guidance): no `androidx.compose.material3`, no FAB-per-screen habit, no Material dialogs or snackbars.
- **Don't** write hex colors, `fontSize = N.sp`, `RoundedCornerShape` for COUI surfaces, or hand-drawn dividers between rows.
- **Don't** add shadows or borders to cards, gradients outside the Now Brief, or a second accent color.
- **Don't** use emoji or icons from another family than `COUIIcons` (Material icons extended only where COUIIcons has no glyph, same weight, single ink).
- **Don't** fake an overlay without a Scaffold, put a dialog inside a lazy item, or blur by translucency.
- **Don't** claim a screen is done from code alone: no rendered preview looked at, no done.
