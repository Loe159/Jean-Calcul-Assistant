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
