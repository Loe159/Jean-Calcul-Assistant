# Now Brief — refined COUI motion

The right-side Brief page is fully offscreen while chat is at rest. No peek,
floating right-edge affordance, or initial discovery nudge remains. The two
segments near the bottom of the chat still track horizontal swipe progress.
The spring transition, temporary parallax and light haptic settling remain.

Only the moving halo ambient background remains. The cards use restrained,
opposite-corner radial light pools to compare palettes: green/orange on the
hero, orange/yellow on filtering, and yellow/green on activity and future cards.
Cards and rows have 16dp outer horizontal gutters and 18–24dp inner padding.

The title, counts, bars and card entrances play on the first opening of the
brief **per Android process**. They do not replay on later openings; lazy
content not viewed during the first session is shown without entrance effects
on subsequent openings. Process termination resets the state; merely
backgrounding the app does not. New incoming notifications can still animate.

The ambient halo stops when the brief is hidden; battery saver or disabled
Android animations prevents the ambient effect. Notifications remain local.

## Reference-inspired visual refinement

Following the user-provided dark dashboard reference: quieter navy/violet
surfaces (hero #2B3041, secondary cards #292936), 20dp side gutters, and
muted sage/apricot/yellow corner tints rather than saturated light pools.
Card-halo opacity is 0.065 (dark) / 0.035 (light), down from 0.21 / 0.14.
The moving backdrop halo is similarly reduced, without adding continuous
animations or GPU blur.

The initial entrance is deliberately short: 230ms heading, 190ms card fade
with at most 80ms of stagger, 300ms counters, and 280ms progress bars.
The first viewport should be readable within roughly 300ms rather than
waiting for a long cascade. Entrances still occur only once per app process;
new notification events may animate independently.


## Glassmorphism proportions — visual reference

The new mockup is a **style reference**, not real weather, map, health, news,
or music data. The existing notification summaries continue to use real on-device
values, and extension slots remain empty until actual providers are connected.

The large bright color blobs are now rendered exclusively as **three gentle
behind-the-content ambient lights** (sage green upper left, apricot lower right,
soft violet center), animated slowly only while the Brief is open. In
battery-saver/reduced-motion mode the same background is rendered statically.

Cards are compact (28dp outer gutters, 14–16dp vertical content padding) and
composited with a translucent COUI glass gradient. The only card highlights
are narrow green-to-white-to-orange glass-edge reflections, **not** solid
color fills or per-card halos. Both light and dark themes are supported.
The existing COUI collapsing/frosted scroll header remains unchanged.

The first-open-only reveal and dashboard content safety guarantees are retained.


## COUI menu cards & finger-driven halo (PR #71)

Briefing cards use near-black COUI-menu-inspired smooth shapes with a subtle
shadow and **86% dark-mode opacity** (90% in light mode), so the atmospheric
halo is faintly visible through the cards without sacrificing contrast.
There is no A/B/C theme selector.

The backdrop alpha follows the actual `AnchoredDraggableState.offset`:
`progress = clamp(-offset / viewportWidth, 0, 1)`. While chat is at rest
the halo is transparent (0); at half swipe it is 50% visible; at the fully
open Brief it is 100% visible. Closing reverses the same path, including
spring settling and gesture cancellation. The opacity is read in
`graphicsLayer` without recomposing dashboard cards on pointer movement.

During drag the halo remains static to save battery. Its gentle infinite
animation only runs once the Brief is open, with phase continuity to avoid
a secondary visual pop. The app still respects battery-saver and Android
reduced-motion settings. No notification data or filtering behavior changed.
