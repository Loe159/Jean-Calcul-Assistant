# Cuisine — surface brief

The Cuisine module (recipes, pantry, shopping list, meal planning, step-by-step cooking) follows the owner's Claude Design prototype. Owner decision, 2026-11-25: the prototype is the binding direction; this file records where the app adapts it.

**Reference (binding):** `docs/design/references/cuisine-mockup.html` (open in a browser: views *Prototype*, *Composants et états*, *Parcours et handoff*; the handoff view lists the 6 required journeys, the navigation rules, the 17 composables and the transitions). Static captures of the prototype in `docs/design/references/cuisine/` (`recettes`, `fiche`, `cuisson`, `fin`, `frigo`, `courses`, `planning`, `vide-horsligne`, `brief-*`, `composants*.png`). Mockup data is fictitious.

**Mode:** Operate. Short, practical jobs with one hand, often with wet or busy hands in the kitchen.

## Adaptations to the app (decided)

| Prototype | App | Why |
| --- | --- | --- |
| Root floating bar Brief / Cuisine / Agenda / Réglages | **No root tab bar.** Cuisine keeps its entry in the main menu shortcuts and its contextual entries from the Now Brief cards and notifications. The four internal tabs (Recettes, Frigo, Courses, Planning) live at the top of the Cuisine page. | The app is chat-centred with side panes (menu, Brief); the prototype's spec allows this fallback. |
| « Cuisine en cours » banner above the root bar | Banner docked at the bottom of every Cuisine route except cooking mode and the finish page; the Now Brief shows the active-cooking card instead. | Same session id everywhere: banner, Brief card, timer notification. |
| Copy in *tu* | Interface copy in *vous* (`PRODUCT.md`); assistant lines, Brief summaries and recipe step text (server content) stay in *tu*. | Product voice rule. |
| Photo recognition, scanner | Manual entry first; scanner and photo arrive in separate lots and stay hidden until they work for real. | No fake capabilities. |
| « Générer mon planning » | Preview of the proposed slots, then explicit apply. Only empty future slots are filled. | Never overwrite the owner's plan silently. |
| Undo | Snackbar « Annuler » for list contributions and local actions; a confirmed stock movement is not undone from the snackbar. | Grocy writes are real. |
| Demo marker « DÉMO » | Only in previews and the explicit demo source; production shows real data, cached data with its date, or an explicit empty/offline state. | Data honesty. |

## Allowed deviations from strict COUI (this surface only)

Like the Now Brief (`docs/design/now-brief.md`), Cuisine may use, exactly as the prototype does:

- **Food photography**: full-bleed recipe hero with a readability gradient under the title (contrast ≥ 4.5:1), thumbnails in cards and meal slots, and the neutral no-photo placeholder from the prototype when there is no image.
- **Semantic tints**, always doubled by an icon and a text: availability (green all available, orange partial / missing some, red missing, neutral unknown/unverified), expiry (neutral, soon = orange, today/expired = red), timers (running = primary, finished = orange/red alarm). Tinted containers at the prototype's opacity (12–26 % in light, darker container tokens in dark), from `COUITheme.colorScheme` palette tokens only.
- **Shortcut tiles** at the top of Recettes (stock count, shopping remaining, tonight's meal) with the Now Brief radial tint halo.
- **Cooking mode** on a plain solid background (no blur behind instructions), instruction text ≥ 22 sp following the system font scale, quantities in bold with a tinted underline, navigation buttons ≥ 56 dp.

Still binding: `primary` is the only accent for actions; cards have no shadow (only the banner, floating CTA, sheets, alarm and snackbar float); one icon family (COUIIcons, missing glyphs drawn on the COUI 24 dp grid — fridge, cart, pot, timer, chef hat…); no emoji; dark mode keeps the pure black window and 10 % white cards; every color from `COUITheme.colorScheme`, every text style from `COUITheme.textStyles`; sheets are `OverlayBottomSheet` in the page `overlay` slot; touch targets ≥ 48 dp (≥ 56 dp in cooking mode).

## Navigation rules (from the handoff, adapted)

- Back in cooking mode **minimizes** (session, step, servings, timers kept; banner appears). Stopping is an explicit action with confirmation.
- System back closes an open sheet first, then pops; from a Cuisine tab other than Recettes it returns to Recettes; from Recettes it returns to the caller (conversation, or the Brief when entered from it).
- One cooking session at a time on the device: starting another recipe opens a conflict sheet (resume the current one, or replace it after confirmation).
- Finish page: back = « Terminer sans modifier le stock ».
- The stock changes only after an explicit confirmation (finish page, « Ranger au stock »). Checking a shopping item puts it in the basket, never in stock.
- Tabs switch by tap and keep their scroll position and filters.

## Motion

Container transform card → recipe (~450 ms, `ease-move`) where the anchor allows it, otherwise the standard COUI push; push into cooking mode; horizontal slide between steps (button and swipe call the same transition); spring indicator on tabs; drawn checkmark with light haptic; descending timer alarm with a swinging bell and short vibration; banner entrance with its progress ring. Everything becomes a fade (or instant) when animations are off. Details in `docs/design/coui-motion.md`.

## Accessibility

Status never by color alone; screen-reader labels per composable (see the handoff table); cooking progress exposed as a progress bar (« Étape 3 sur 7 »); timers announce name and remaining time without announcing every second; local text-size option in cooking mode (« Texte normal / grand »); keyboard keeps fields visible; insets respected.

## Verification

Every Cuisine change renders its previews (`@CouiPhonePreviews` + `@CouiStressPreviews` for screens, `@CouiComponentPreviews` for components; light/dark, 200 % font, 320 dp) with `scripts/preview` and is compared side by side with the matching capture in `references/cuisine/` before being shown to the owner. Motion, haptics, alarms, back gestures and the banner over real insets are checked on the synthetic emulator (`docs/agents/DEVICE.md`) or listed as not verified.
