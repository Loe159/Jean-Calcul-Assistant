# COUI components — what to use, how it must look

Library: `io.github.suqi8.coui.kmp` **2.0.0** (Android artifacts `coui-*-android`), ColorOS 17 / COUI 17. Visual specs below are the exact values of the owner's design system artifact (built from `suqi8/coui` sources); the API names are verified against the pinned jars.

Read order for any UI task: `DESIGN.md` → this file → `coui-pitfalls.md` → the component's real signature.

## Finding a real signature

There is no sources jar in the Gradle cache. Before calling an API you have not used in this repo:

1. Look for an existing call site: `rg -n "SwitchPreference\(" app/src/main/kotlin`.
2. Read the library source at the pinned tag: `gh api "repos/suqi8/coui/contents/<path>?ref=v2.0.0" -H "Accept: application/vnd.github.raw"`. Components live in `coui-ui/src/commonMain/kotlin/io/github/suqi8/coui/kmp/<package>/`, preferences in `coui-preference/src/commonMain/kotlin/io/github/suqi8/coui/kmp/preference/`. The KDoc names the ColorOS widget each component ports.
3. If offline: `javap -cp <classes.jar from the .aar in ~/.gradle/caches/modules-2/files-2.1/io.github.suqi8.coui.kmp/…> io.github.suqi8.coui.kmp.basic.<File>Kt` inside `nix develop`.

Packages: `basic` (components), `overlay` / `window` (dialogs, sheets, popups), `layout` (`DialogButtonBar`, `DialogDefaults`, `BottomSheetDefaults`), `theme` (`COUITheme`, `ThemeController`, `COUIRoundCorner`), `effect` (`LocalMaterialBackdrop`, `LocalPopupMaterialBlur`, `LocalCOUIMaterialEffectEnabled`), `utils` (`overScrollVertical`, `scrollEndHaptic`, `SinkFeedback`, haptics), `icon` (`COUIIcons`), `preference`, `menu`, `popup`, `blur`, `squircle`, `nav.core`, `nav.transition`.

## Project wrappers (use these first)

All in `app/src/main/kotlin/io/github/mangi/eta/ui/components/coui/`. They encode the recipes below; do not re-derive them.

| Need | Use | Notes |
| --- | --- | --- |
| Secondary page, scrolling column | `CouiScaffoldPage(title, onBack) { … }` | collapsing large title, COUI 17 glow, page backdrop for blur, insets, scroll haptics; `overlay = { }` slot for dialogs/sheets |
| Secondary page, long/dynamic list | `CouiLazyScaffoldPage(title, onBack, listState) { … }` | same chrome over a `LazyColumn` |
| Custom container page (pager, editor) | `CouiScaffold(title, onBack) { padding, raw, scroll, side -> }` | you place content; `sidePadding` is the 16/24/40 dp margin |
| Back / top-bar actions | `CouiBackButton`, `CouiTopAppBarIconButton(icon, contentDescription, onClick)`, `CouiTopAppBarTextButton` | |
| Settings section with free rows | `PreferenceSection(title) { … }` | category title + padded `Card` |
| Value at the end of a row | `PreferenceValueText(value)` inside `endActions` | COUI assignment text style |
| Note under a card | `PreferenceFooter(text)` | |
| Button | `CouiButton(text, onClick, role = Primary/Secondary/Destructive/DestructiveFilled)` | capsule, COUI press |
| Labeled input | `CouiTextField(value, onValueChange, label = …)` | |
| Inline search | `CouiSearchBarBox(query, onQueryChange, placeholder)` | |
| Capsule tabs bound to keys | `CouiChipTabs(items, selectedKey, onSelectedKeyChange)` | wraps `ChipTabRow` |
| Confirm / notice / input dialogs | `CouiConfirmDialog(visible, title, message, confirmText, onConfirm, onDismissRequest, destructive = …)`, `CouiNoticeDialog`, `CouiInputDialog` | `OverlayDialog` + `DialogButtonBar` with the page blur |
| Blur plumbing | `rememberPageBackdrop`, `rememberPopupMaterialBlur`, `panelBlur` (`CouiBlur.kt`) | already wired by the scaffolds |
| Previews | `CouiPreviewTheme`, `@CouiPhonePreviews`, `@CouiStressPreviews`, `@CouiComponentPreviews` (`ui/preview/`) | see `docs/agents/PREVIEWS.md` |

If a wrapper is missing something, extend the wrapper — never fork a look-alike next to it.

## ColorOS widget → COUI component

### Page structure
| ColorOS | COUI |
| --- | --- |
| Page with collapsing title | `CouiScaffoldPage` (library: `Scaffold` + `TopAppBar(scrollBehavior = …)`) |
| Pinned small bar | `SmallTopAppBar` (`compactTitle = true` on the wrappers) |
| Search under the top bar (Settings-style, in place) | `TopAppBarSearchBar` + `TopAppBarSearchState`, linked to the bar; never a second row swapped in, never a separate search page |
| Bottom tabs (COUI 17) | `FloatingNavigationBar` + items; classic `NavigationBar` |
| Tabs | `TabRow`; capsule tabs `ChipTabRow`; 2–4 views of one content `SegmentButton` |
| Floating toolbar | `FloatingToolbar` |
| FAB | `FloatingActionButton` (one per list screen, the creation action only) |
| Navigation + system back | `coui-nav` (`NavDisplay`, `NavKey`), predictive back |

### Lists and settings
| ColorOS | COUI |
| --- | --- |
| Rounded card group | `Card { CardListGroup { item { … } } }` |
| Section header | `PreferenceCategory(title)`; plain caption `SmallTitle` |
| Plain row / action row | `BasicComponent(title, summary, startAction, endActions, onClick)` (no chevron) |
| Row that opens a page | `ArrowPreference` |
| On/off | `SwitchPreference` (applies immediately, no Save button) |
| Pick one of a few | `OverlayDropdownPreference`; long choices: `RadioButtonPreference` rows on their own page |
| Check-mark list | `MarkPreference`; multi-select `CheckboxPreference` |
| Level / number | `SliderPreference`; bounded integer `Stepper` in `endActions` |
| Text entry row | `InputPreference` |
| Expandable section | `ExpandableGroupHeader` + content |
| Long dynamic list | separate lazy items with `cardListPosition = cardListPositionOf(index, count)` |

### Controls
`Button`/`TextButton` (via `CouiButton`), `Switch`, `Checkbox`, `RadioButton`, `Slider`, `SegmentButton`, `Stepper`, `Chip`/`ChipGroup`, `TextField`, `SearchBar` + `InputField` + `CancelButton`, pickers (`DatePicker`, `TimePicker`, `NumberPicker`).

### Overlays and feedback
| ColorOS | COUI |
| --- | --- |
| Alert / confirm dialog | `OverlayDialog` + `DialogButtonBar` (via `CouiConfirmDialog`) |
| Input dialog | `OverlayInputDialog` (via `CouiInputDialog`) |
| Bottom sheet | `OverlayBottomSheet` |
| Overflow / dropdown menu | `OverlayIconDropdownMenu` (entries `DropdownEntry`/`DropdownItem`), `OverlayListPopup` |
| Long-press menu | `OverlayContextMenu` |
| Toast-like confirmation | Snackbar through `SnackbarHostState.showSnackbar`, with Undo when the action is reversible |
| Inline notice | `TopTips` |
| Empty / done | `EmptyState` (project: `ListEmptyState`), `CompleteState` |
| Progress | `LinearProgressIndicator`, `CircularProgressIndicator`, `InfiniteProgressIndicator` |
| Badges, tags, dots | `Badge`, `TagView`, `PageIndicator`, `Tooltip` |

`Overlay*` draw in the root Scaffold's popup host and can use the page blur. `Window*` variants open a real platform window: only outside any Scaffold (e.g. the assistant overlay window), never with the page blur.

## Visual specs (exact)

dp/sp values; colors are `COUITheme.colorScheme` names (light/dark values in `coui-tokens.json`).

**Button.** Capsule 44 dp tall, ≥ 132 dp wide, `buttonL`. Secondary (default) `secondaryButton`/`labelPrimary`; primary `primary`/`onPrimary`; transparent (on imagery) `transparentButton`; borderless `primary` text. Disabled: `disabledSecondaryButton`/`disabledOnSecondaryButton`, primary `disable`/`disabledOnPrimaryButton`. Small: 28 dp, ≥ 52 dp, 14 sp. Full-width pair: two buttons, 16 dp gap; single full-width ≤ 296 dp on phones. Press: `press` layer on a 0.3 s critically damped spring, no scale. One primary per area; Cancel stays secondary.

**Card.** `cardBackground`, 24 dp squircle (fallback 20 dp), no padding imposed (16 dp usual), no shadow. Pressable → `cardPressed`.

**CardListGroup.** Section title then a card 16 dp from the screen edges; 8 dp between sections (the card's 16 dp bottom padding in this app). Dividers drawn automatically between rows, never after the last.

**List row (BasicComponent / preferences).** ≥ 48 dp, padding 10 × 16 dp, title `headlineXS` (16 sp, 500) `labelPrimary`, summary `bodyS` `labelSecondary` right under it, start icon 24 dp with 16 dp gap, end value `bodyM`/14 sp `labelSecondary`, chevron 12 × 24 dp `labelTertiary`. Disabled: everything `disabledNeutral`. Selected: `focus` layer. Divider 0.33 dp `divider` from the title start to 16 dp before the edge.

**Section title (PreferenceCategory / SmallTitle).** 12 sp, 500, `labelSecondary`, padding 8 × 32 dp (16 page + 16 card). Sentence case, no final punctuation.

**Switch.** Track 44 × 24 dp, thumb 18 dp at 3 dp. On `primary`, off `dragHandle`; disabled `disable`/`secondaryContainer`. Spring response 0.4 s, bounce 0.3; thumb stretches 30 % while pressed. At the end of a row; applies immediately.

**Checkbox.** 24 dp box; ring 16.4 dp (radius 3.2, stroke 1.3, `labelTertiary`); checked 18 dp `primary` square (radius 4) with `onPrimary` tick drawn with `ease-move`, 1.08 pop over 2 × 150 ms.

**RadioButton.** 24 dp; ring r 9.2 stroke 1.3 `labelTertiary`; selected disc r 10 `primary`, white center r 6.

**Slider.** 20 dp thick capsule track `sliderBackground`, `primary` progress enclosing a 12 dp white thumb; ×1.4 track and ×4/3 thumb on press. In settings: value at the end of the row title, slider under it.

**SegmentButton.** 40 dp capsule (32 small), 4 dp padding, `segmentTrack` with a `segmentIndicator` cursor and soft shadow; labels `buttonM` `labelPrimary`; segments ≥ 52 dp. Not for navigating between screens.

**Chip.** 32 dp, radius 16, padding 12, `bodyM`; `container8`, selected `primary`/`onPrimary`; icon 16 dp. Suggestion: 28 dp, radius 14, 12 sp. `ChipGroup`: 8 dp gaps, wraps. Disabled text `disabledChipText`.

**ChipTabRow.** 32 dp chips, radius 16, `container4`; active `surfaceContainerHighest` with 0.6 dp `chipTabStroke` and soft shadow; `buttonM`; scrolling row, padding 8 × 16, 8 dp gaps.

**TabRow.** 48 dp, `bodyM`; active `labelPrimary` 500 with 2 dp `labelPrimary` indicator (≥ 32 dp), inactive `labelSecondary`; 20 dp between tabs.

**TextField.** `bodyXL`, `primary` cursor. Rectangle: 0.33 dp `divider` outline, radius 10, padding 12 × 16, ≥ 62 dp, focus 1 dp `primary` (page forms). Line: underline with a 1 dp `primary` stroke growing from the start in 250 ms (dialogs, sheets). None: inside a card. Floating label 10 sp `labelSecondary`; placeholder `hintNeutral`; error outline/text `error` plus 5.33 dp shake in 450 ms; counter 10 sp `labelTertiary`.

**SearchBar.** 40 dp capsule on `bar`, magnifier 20 dp `labelSecondary` 12 dp from the edge, 15 sp text, Cancel (14 sp `labelPrimary`) appears on focus; under a large title with 6 dp vertical / 16 dp side padding; clear glyph 18 dp `labelTertiary`.

**TopAppBar.** 52 dp action bar; large: 32 sp 600 title below the actions, 110 dp expanded (root and secondary pages); small: 24 sp next to Back; center: 18 sp medium (full-screen dialogs, editors). Subtitle 14 sp `labelSecondaryVariant`. Collapse: fade + scale 0.7 with `ease-standard`, 0.33 dp divider appears. Margins 16/24/40 dp.

**IconButton.** 40 × 40 dp, radius 20, 24 dp icon `labelPrimary`, no background at rest (press layer only), 0 dp between actions. Content description mandatory.

**NavigationBar.** `bar`, 56 dp items, 24 dp icon, 10 sp 500 label; monochrome: active `labelPrimary`, inactive `labelSecondary` — no blue. **FloatingNavigationBar:** 56 dp capsule 16 dp from the edges, blurred `floatingBarFill`, floating shadow; items 70 × 48 dp; inactive `labelPrimaryVariant`.

**FloatingActionButton.** 56 dp (tiny 48), `primary`, 24 dp `onPrimary` icon, floating shadow; bottom-right 16 dp, above the nav bar; appears with scale 0.6 → 1 in 350 ms. One per screen, creation only.

**Dialog.** ≤ 360 dp, `surface`, 45 dp squircle (fallback 32), `mask` scrim; title `headlineS` centered, message `bodyS` `labelSecondary` centered, padding 24 dp. `DialogButtonBar`: 44 dp capsules `dialogButton`, `buttonL`; recommended action filled `primary`; destructive in the neutral slot (`error` text); stacks beyond two buttons; cancel first. Bottom-anchored on phones (16 dp sides, 32 dp bottom), centered ≥ 840 dp. In 250 ms `ease-move`, out 150 ms `ease-out`.

**BottomSheet.** `surface`, top corners 45 dp squircle, drag handle 36 × 4 dp `dragHandle` 12 dp from the top, 52 dp title bar with 18 sp 500 centered title; ≤ 540 dp wide. For short choices/forms tied to the current screen; content usually a `CardListGroup`.

**List popup / menus.** `backgroundTop` with material blur, 28 dp squircle, 168–256 dp wide, 8 dp vertical padding, popup shadow; options ≥ 40 dp, padding 20, `bodyXL`, summary `bodyXS`; group divider 12 dp. Anchored 8 dp below the trigger, ≥ 16 dp from screen edges. Wrap the trigger and its popup in their own `Box`.

**Snackbar.** Radius 16, padding 10 × 16, `backgroundTop`, 14 sp 500 `labelPrimary`, action `primaryTextOnPopup`; bottom, above the nav bar, 16 dp from edges. Confirms an action and offers Undo.

**TopTips.** `container4`, padding 12 × 16, 24 dp `primary` icon, `bodyS` `labelSecondary`; one line: capsule; with title (`headlineXS`) or actions: 28 dp squircle; close cross `labelTertiary`.

**EmptyState.** Centered: 120 × 100 dp illustration (or 72 dp `labelTertiary` icon), title `headlineXS`, subtitle `bodyXS` `labelSecondary` ≤ 280 dp, text action `buttonM` `primary`.

**Progress.** Linear 4 dp capsule, track `divider`, fill `primary`. Circular 30 dp/stroke 3 or 40/5, track `secondary`, `primary` round-capped arc. Spinner 18 dp/2.67 (26/3.33 large), `labelTertiary` arc on `divider`, one turn per second.

**Badge / Tag / PageIndicator / Tooltip.** Badge `red`: 6 dp dot or 16 dp counter in 10 sp white, "999+". Tag 10 sp 500, padding 3, radius 4, `primary` or outline. Page dots 6 dp, 3 dp gap, inactive `labelTertiary`, active `labelPrimary`, ≤ 6 visible. Tooltip radius 17, padding 12, ≤ 328 dp; default `primary`/`onPrimary`, info `backgroundTop`/`labelSecondary`.

**Icons.** `COUIIcons` (Regular weight, 24 dp grid, single ink). Default tint `labelPrimary`; chevrons `labelTertiary`; search glyph `labelSecondary`; `primary` only for an active state or a tip. Settings entry badges: white glyph on a tint (`blue`, `green`, `orange`…) with 8 dp radius, or the glyph tinted directly as the existing settings rows do. Material Icons Extended (Rounded) only when COUIIcons has no glyph — same size, single ink. No emoji.

## Not in COUI 2.0's design system artifact (compose from tokens, carefully)

Camera, lock screen, color pickers, `NavigationRail`, `SideNavigationBar`, list-detail layouts, alphabet index, breadcrumbs, swipe actions, pull-to-refresh visuals, rolling numbers, marquee, shader effects. The library may still have some of them: check the source first.

The five component constants `segmentTrack`, `segmentIndicator`, `tabTrack`, `chipTabStroke`, `floatingBarFill` are **not** `colorScheme` properties; they live in each component's `*Defaults`.
