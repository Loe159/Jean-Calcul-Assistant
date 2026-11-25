# COUI pitfalls (each one happened)

Run through this list before showing any UI change. Upstream items come from the `coui-app` skill of `suqi8/coui` (Apache-2.0); project items from this repo's history.

## Layout
- **Cards touching the screen edge.** `Card` has no outer margin. Every card: `Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)`.
- **Hand-made dividers.** `CardListGroup` draws COUI dividers (starting at the title, hidden next to a selected row). A `HorizontalDivider` between rows draws a second, wrong one.
- **Extra icon gap in rows.** `startAction` is already spaced from the title; give the icon `Modifier.size(24.dp)` only.
- **Shadows clipped.** `Card` clips (squircle surface). Shadowed children (FABs, floating bars) belong on the page background.
- **Bottom bar covering the last rows.** Lists under a bottom/floating bar need the bar height in their bottom content padding.
- **Double search margin.** `TopAppBarSearchSlot` already pads 16 dp; putting a whole `SearchBar` in it doubles it.
- **Static search under the top bar.** A capsule that filters in place without moving the title is not the ColorOS search: use `TopAppBarSearchBar` linked to the bar.
- **Search hides the page list for good.** Hoist the page `LazyListState`, otherwise leaving search scrolls back to the top.
- **Hand-drawn "COUI-like" surfaces.** `Box` + `background` + `RoundedCornerShape` imitations of cards, rows, chips or page indicators were rejected on device as "fait main". Use the component, or `SquircleShape` + tokens + `SinkFeedback` when there truly is none.

## Overlays and blur
- **Overlay without Scaffold** shows nothing. Every page that opens an `Overlay*` is inside a COUI `Scaffold` (the page wrappers provide it).
- **Dialog inside a lazy item** disappears when the item scrolls away. Put dialogs and sheets in the page's `overlay = { }` slot.
- **List popup as a sibling in a row.** `OverlayListPopup` adds a zero-size child to its parent and anchors to it: always `Box { IconButton(…); OverlayListPopup(…) }`.
- **Pushed pages lose the blur** if only the root shell records a backdrop: each page scaffold records its own (the wrappers do).
- **Self-sampling.** An element inside the recorded layer must not use the page backdrop as its material.
- **Window\* + page blur.** Window variants live in another window: leave them opaque.
- **No shader support.** `isRuntimeShaderSupported()` false → providers return `null` → COUI's opaque fallback. Never fake blur with alpha.

## Text and state
- **English library defaults.** `CancelButton` defaults to "Cancel", the search clear button to "Clear text": pass localized text wherever a parameter exists. (The search clear button's label is fixed in COUI 2.0 — known exception.)
- **`stringResource` outside composition** (lazy list builders, `remember {}` initializers, coroutines) goes stale: resolve inside `item {}`, use `context.getString` in coroutines.
- **A dark mode switch that changes nothing.** `ThemeController` is immutable; it is rebuilt from `AppearanceSettings` in `AgentAppTheme`.
- **Display text as state.** Store ids/indices, not labels.
- **Hard-coded French strings in composables.** Some screens still inline copy; new copy goes to `res/values/strings.xml`.

## Project-specific
- **The assistant overlay** is a system window over other apps: it keeps an opaque theme fill (no page backdrop to sample) and `SinkFeedback` presses. Do not "fix" it toward page blur.
- **Interface scale.** `AgentAppTheme` can rewrite display density (interface scale setting); previews use `applyInterfaceScale = false`. Check that dialogs/popups (separate windows) still match on device.
- **IME.** The chat input uses the Foundation `TextFieldState` field inside the bottom slot with `imePadding`; do not reintroduce `value/onValueChange` fields there (they pan the whole window).
- **Fixed-size bar titles.** COUI keeps toolbar/page title sizes in dp by design; body text must still follow the font scale.
- **Blue rim around the first button in a preview.** Layoutlib gives initial focus to the first focusable element, and COUI `Button`/`IconButton` draw `couiFocusStroke` when focused, whatever the `LocalIndication`. A touch-mode device never shows it. Do not restyle the button to hide it.

## Code style (Compose rules)
- `modifier: Modifier = Modifier` right after the required parameters, applied to the root; one root emitter per composable.
- Never call the same content lambda in two branches.
- `@Immutable` only on classes without lambdas; `@Stable` for classes holding lambdas or snapshot state.
- Screens are split `XScreen(state, onEvent…)` (stateless, previewable) / route wiring (stores, `Context`, effects).
