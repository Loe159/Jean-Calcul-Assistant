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
