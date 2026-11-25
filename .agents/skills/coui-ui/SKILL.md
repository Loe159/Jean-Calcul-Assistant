---
name: coui-ui
description: Use for ANY change to what the owner sees in Jean-Calcul — new or reworked Compose screens, components, Now Brief cards, dialogs, settings rows, on-screen copy, visual bugs, design reviews. Drives the work through impeccable's design process with COUI 2.0 (ColorOS 17) as the platform language instead of Material, and a render → look → fix loop on headless previews before anything is shown.
---

# Jean-Calcul UI work

The owner judges every screen on their phone next to native ColorOS apps and rejects anything that looks hand-made, generic or "AI". Your job is not to make it compile; it is to make it look like it shipped with the phone, and to prove it with rendered images.

## 0. Load context (once per task)

1. Read `DESIGN.md` and `PRODUCT.md` (repo root). For the Now Brief also `docs/design/now-brief.md` and look at `docs/design/references/*.png`.
2. Skim `docs/design/coui-components.md` for the components you will touch and `docs/design/coui-pitfalls.md`.
3. Load the **impeccable** skill (Claude Code: the `impeccable` skill / `/impeccable …`; it runs `impeccable context`, which reads `PRODUCT.md` and `DESIGN.md` and sees `Platform: android`). If impeccable is not installed in your harness, follow this file and the docs above directly — the process below is the same.

**Platform override (critical).** impeccable's `android.md` and native audit assume Material 3. In this repo COUI replaces Material entirely (`DESIGN.md` → Overview). Translate every rule: "Material component" → the COUI component; "color role" → `COUITheme.colorScheme.<name>`; "type scale" → `COUITheme.textStyles.<role>`; "Dynamic Color" → Monet via `AppearanceSettings`; "48 dp touch target" → 40 dp IconButton / 44 dp buttons / 48 dp rows; "snackbar not toast" holds. Never add `androidx.compose.material3`. impeccable's own rule "the brief wins" makes `DESIGN.md` binding over its defaults.

## 1. Pick the impeccable command

| Situation | Command |
| --- | --- |
| New screen, feature or flow | `shape <feature>` → confirm the brief with the owner → build (app screens are code-first; no world workshop: the COUI world is established) |
| Existing screen feels wrong / "not great" | `critique <target>` on rendered previews first, then the targeted fixes it names |
| Spacing, rhythm, hierarchy | `layout` |
| Text hierarchy | `typeset` |
| Copy, labels, errors (French, *vous*; assistant *tu*) | `clarify` |
| Too busy | `distill` / `quieter` |
| Empty, error, loading, long-text, offline states | `harden`; first-run and empty states `onboard` |
| Small screens, 200 % font, landscape | `adapt` (native variant) |
| Motion | `animate` (with `docs/design/coui-motion.md`) |
| Technical a11y/perf/platform check | `audit` (native variant `audit.native.md`, from source) |
| Final pass before showing | `polish` |
| Design system drift | `document` (refresh `DESIGN.md` from shipped code; never overwrite silently) |

Ask the owner (structured question) only for decisions that are genuinely theirs: scope, which of 2–3 materially different layouts, copy they must approve. Never ask about tokens or components — those are decided.

## 2. Build the COUI way

- Screen = stateless `XScreen(state, on…)` + wiring. Use the page wrappers (`CouiScaffoldPage`, `CouiLazyScaffoldPage`, `CouiScaffold`) and the components table; extend a wrapper rather than forking a look-alike.
- Settings: `PreferenceCategory` → `Card(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp))` → `CardListGroup { item { … } }` with `ArrowPreference` / `SwitchPreference` / `BasicComponent` / `OverlayDropdownPreference`.
- Colors `COUITheme.colorScheme.*`, text `COUITheme.textStyles.*`, shapes squircle; no hex, no `fontSize = N.sp`, no card shadows, one accent; dialogs/sheets in the page `overlay` slot.
- Copy in French, in resources for new strings; sentence case; verbs on buttons; no emoji.
- Read `docs/design/coui-pitfalls.md` before writing the first line, again before showing.

## 3. Render, look, fix (mandatory loop)

1. Add/update previews (`docs/agents/PREVIEWS.md`): `@CouiPhonePreviews` + `@CouiStressPreviews` for screens, `@CouiComponentPreviews` for components; one preview per meaningful state (empty, loading, populated, long text, error).
2. `scripts/preview render <YourPreviewName>` and **open every `.sheet.png`** it prints with your image tool. Fix every `!` lint line.
3. Critique what you see against this checklist, then batch all fixes before rendering again:
   - Does it look like a ColorOS system screen (Settings, Notes, Weather)? Would the owner call any part "fait main"?
   - Hierarchy: one clear title, one primary action per area, summaries quieter than titles, values right-aligned in `labelSecondary`.
   - Rhythm: 16 dp edges and card gaps, rows ≥ 48 dp, no doubled paddings or dividers, nothing touching an edge.
   - Dark mode: pure black window, 10 % white cards, no element that disappears or glows wrongly.
   - 200 % font and 320 dp: no clipped or overlapping text, no truncated button labels, rows wrap instead of cutting.
   - Color: only `primary` as accent (Now Brief tints excepted), tertiary text never carries information.
   - States: empty/error/loading designed, not blank; data honest (no fake values outside demo/previews).
   - Compare with `docs/design/references/` (Now Brief) or the specimens (`scripts/preview render Specimen`).
4. Repeat until the checklist passes (usually 2–4 renders). Stop and ask if two attempts at the same problem fail.
5. If you delegate implementation to another agent, it must render too — and you open the sheets yourself before reporting.

## 4. Device-only aspects

Motion, haptics, blur, IME, insets, back gestures, overlays over other apps: check on the synthetic emulator (`docs/agents/DEVICE.md`) when the change touches them, otherwise list them as NOT VERIFIED. Never touch the owner's phone unless asked.

## 5. Present to the owner

Show, don't describe: give the sheet paths (light/dark/stress) for each changed screen, a one-line rationale per visible decision, and what remains unverified. For a direction choice, render 2–3 variants as separate previews and show them side by side. Then run the build checks from `AGENTS.md`.

## 6. Leave the system better

New reusable pattern → add a specimen in `ui/preview/CouiSpecimenPreviews.kt` and a line in `docs/design/coui-components.md`. New trap → `docs/design/coui-pitfalls.md`. Changed rule → `DESIGN.md` in the same change.
