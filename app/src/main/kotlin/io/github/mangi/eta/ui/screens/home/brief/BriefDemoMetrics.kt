package io.github.mangi.eta.ui.screens.home.brief

import java.util.Locale
import kotlin.math.roundToInt

internal fun briefProgress(completed: Int, target: Int): Float =
    if (target <= 0) 0f else (completed.toFloat() / target).coerceIn(0f, 1f)

internal fun briefPercent(progress: Float): Int = (progress * 100).roundToInt()

internal fun briefNumber(value: Int): String =
    String.format(Locale.FRANCE, "%,d", value).replace('\u202f', '\u00a0')

internal fun briefHour(minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

internal fun briefDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> String.format(Locale.ROOT, "%d h %02d", minutes / 60, minutes % 60)
}

internal data class BriefAgendaCountdown(val event: BriefDemoEvent, val minutes: Int, val inProgress: Boolean) {
    val progress: Float
        get() = if (inProgress) briefProgress(event.end - event.start - minutes, event.end - event.start)
        else 1f - briefProgress(minutes, 6 * 60)
}

internal fun briefAgendaCountdown(events: List<BriefDemoEvent>, now: Int): BriefAgendaCountdown? {
    val event = events.filter { it.end > now && it.end > it.start }.minByOrNull { it.start } ?: return null
    val inProgress = now >= event.start
    return BriefAgendaCountdown(event, (if (inProgress) event.end else event.start) - now, inProgress)
}

internal val BriefRibbonTicks = listOf(7 * 60, 11 * 60, 15 * 60, 19 * 60, 21 * 60)

internal data class BriefRibbonSegment(val event: BriefDemoEvent, val start: Float, val end: Float)
internal data class BriefRibbon(val segments: List<BriefRibbonSegment>, val now: Float?)

internal fun briefAgendaRibbon(
    events: List<BriefDemoEvent>,
    now: Int,
    start: Int = BriefRibbonTicks.first(),
    end: Int = BriefRibbonTicks.last(),
): BriefRibbon {
    if (end <= start) return BriefRibbon(emptyList(), null)
    fun position(minute: Int) = briefProgress(minute - start, end - start)
    val segments = events.mapNotNull { event ->
        val left = position(event.start)
        val right = position(event.end)
        if (right > left) BriefRibbonSegment(event, left, right) else null
    }
    return BriefRibbon(segments, now.takeIf { it in start..end }?.let(::position))
}

internal const val BriefMinimumAgendaGap = 75

internal data class BriefAgendaGap(val start: Int, val end: Int) {
    val minutes get() = end - start
}

internal fun briefAgendaGaps(events: List<BriefDemoEvent>, minimumMinutes: Int = BriefMinimumAgendaGap): List<BriefAgendaGap> =
    buildList {
        var occupiedUntil: Int? = null
        for (event in events.filter { it.end > it.start }.sortedBy { it.start }) {
            val previousEnd = occupiedUntil
            if (previousEnd != null && event.start > previousEnd && event.start - previousEnd >= minimumMinutes) {
                add(BriefAgendaGap(previousEnd, event.start))
            }
            occupiedUntil = maxOf(previousEnd ?: event.end, event.end)
        }
    }
