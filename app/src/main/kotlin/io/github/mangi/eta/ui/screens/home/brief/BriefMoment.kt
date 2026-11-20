package io.github.mangi.eta.ui.screens.home.brief

import java.time.LocalTime

internal enum class BriefMoment(val greeting: String) {
    MORNING("Bonjour"),
    DAY("Bon après-midi"),
    EVENING("Bonsoir"),
    NIGHT("Bonne nuit"),
}

internal fun briefMoment(time: LocalTime): BriefMoment = when (time.hour) {
    in 5..11 -> BriefMoment.MORNING
    in 12..17 -> BriefMoment.DAY
    in 18..21 -> BriefMoment.EVENING
    else -> BriefMoment.NIGHT
}
