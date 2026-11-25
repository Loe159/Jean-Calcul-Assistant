# Now Brief — surface brief

The Now Brief is the right-hand page next to the chat: a contextual feed of cards answering "what matters now". It is the **only** surface where Jean-Calcul is allowed visual expression beyond strict COUI (owner decision, 2026-11-24). Everything here is scoped to `ui/screens/home/` (`NowBriefScreen.kt`, `brief/`, `brief/cards/`, `effect/`).

**Mode:** Operate (glanceable synthesis + one-thumb actions), with the warmth of a home screen.

**Reference (binding):** the owner's interactive mockup, vendored at `docs/design/references/now-brief-mockup.html` (open it in a browser: views *Écran*, *États des cartes*, *Spécifications*; scenarios Matin/Journée/Soir/Nuit; light/dark). Static captures: `references/now-brief-dark-soir.png`, `references/now-brief-light-matin.png`. Live artifact: https://claude.ai/artifact/DdGPw6iY5guXQaV7Um7r5o. Mockup data is fictitious; the app shows real or explicitly demo data only.

## Principles

- **A brief, not widgets.** Time of day, day, place and agenda choose the cards, their order and their density. Secondary cards fold into tiles or move to a "hidden cards" drawer.
- **Four densities per card:** tile (half width, xs), compact (s), medium (m), expanded (l). The card header chevron steps to the next density with an animated height change.
- **One identity per card.** Each card has its own composition (rings, hour ribbon, car illustration, animated sky, thermostat dial, waveform, glowing outline for the assistant) — never a generic list in a card.
- **One hand.** Quick actions sit at the bottom of cards, within thumb reach. Floating navigation and the Now Bar (current media/activity) at the bottom of the screen.
- **What matters first.** An importance score per card (urgency × time proximity × impact) decides density: above a threshold the card expands, below it folds, then joins the drawer. Current ordering per moment: `briefFeedOrder()` in `brief/BriefFeed.kt`.

## Allowed deviations from COUI (this surface only)

| COUI rule | Now Brief | Why |
| --- | --- | --- |
| One blue accent | Each card gets a tint from the palette as a discreet radial halo (12–26 % at the top-left corner) and in its icon chip and pills. `primary` stays the color of the main action. | Recognize a card at a glance |
| No gradients | Gradients only on the header sky, car, weather, music and assistant cards; radial halo on the others. | Depth for a synthesis screen |
| *Vous* | The assistant's lines and summaries use *tu* ("Tu as 2 messages importants"). | Personal assistant voice |

Still binding: cards have **no shadow** (only the Now Bar, floating navigation, sheets and snackbar float); one icon family (COUIIcons + missing glyphs drawn on the COUI 24 dp grid: car, weather, steps, fuel…); no emoji; dark mode keeps the pure black window and 10 % white cards; all colors from `COUITheme.colorScheme` (tint palette included).

### Card tints
| Tint | Cards |
| --- | --- |
| `blue` | Agenda |
| `red` | Notifications |
| `azure` | Car, weather |
| `orange` | Sport and health |
| `violet` | Tasks, assistant suggestions |
| `yellow` | Home |
| `green` | Finances |
| `purple` | News, habits |

## Layout

- Content width: 392 dp screen − 2 × 16 dp margins = 358 dp. Two tiles side by side with an 8 dp gap; tapping a tile makes it full width.
- Card sizes: tile 168 × 168 dp; compact 358 × 64–80 dp; medium 358 × 200–330 dp; expanded 358 × 380–720 dp.
- Card: 24 dp squircle, 16 dp padding (14 dp for tiles), header row = 30 dp tinted icon chip (radius 10) + title `bodyM` 500 + meta `bodyXS` `labelSecondary` + density chevron (30 dp target inside a 40 dp touch area).
- Section labels between groups ("À traiter maintenant", "Pour bien partir"): 12 sp 500 `labelSecondary` with a trailing hairline.
- Header: greeting at display size (35 sp, ~640 weight) over the animated sky, date + weather pill, a summary paragraph where key facts are bold with a tinted underline highlight, then horizontally scrolling tinted shortcut chips.

## Contextual order

- **Morning:** weather and commute first, today's agenda, tasks due before a deadline.
- **Day:** next appointment large, messages, tasks; car and home fold.
- **Evening:** waiting messages, car if refuel needed, sport, day summary.
- **Night:** tomorrow first, bedtime, home, security alerts.

Each scenario in the mockup's "Pourquoi cet ordre" panel explains its order; when changing ordering logic, keep it explainable in one line per card.

## Motion

- Card entrance: fade + 16 dp rise, 55 ms stagger, `ease-move`; first opening per process only (see `docs/features/NOW-BRIEF-NOTIFICATION-ACTIVITY.md` for live updates).
- Density change: height animated over 500 ms, content cross-fades.
- Checkbox: drawn tick, 1.08 pop, progress ring follows. Switches: spring response 0.4 s.
- Rings and gauges draw on appearance, then transition on change.
- Snackbar with Undo after every notification action.
- Everything static when animations are off or battery saver is on.

## Data honesty

Cards show real sources when connected (notifications, Mastra brief/food, car session, planning), demo data only behind an explicit demo marker and in previews (`BriefDemoData.kt`, `DemoCarSource.kt`). Never fabricate values in production paths. See `docs/features/` for each source.

## Verification

Every Brief change renders its card previews (all densities, light/dark, 200 % font) with `scripts/preview` and is compared side by side with the mockup references before being shown to the owner.
