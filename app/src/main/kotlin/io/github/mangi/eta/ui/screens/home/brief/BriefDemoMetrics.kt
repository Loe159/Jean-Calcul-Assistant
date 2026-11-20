package io.github.mangi.eta.ui.screens.home.brief

import java.util.Locale
import kotlin.math.roundToInt

internal fun briefProgress(completed: Int, target: Int): Float =
    if (target <= 0) 0f else (completed.toFloat() / target).coerceIn(0f, 1f)

internal fun briefPercent(progress: Float): Int = (progress * 100).roundToInt()

internal fun briefNumber(value: Int): String =
    String.format(Locale.FRANCE, "%,d", value).replace('\u202f', '\u00a0')

internal fun briefDecimal(value: Double, decimals: Int = 1, signed: Boolean = false): String {
    val flags = if (signed && value != 0.0) "+," else ","
    return String.format(Locale.FRANCE, "%$flags.${decimals}f", value)
        .replace('\u202f', '\u00a0').replace('-', '−')
}

internal fun briefEuro(cents: Long, decimals: Int = 2, signed: Boolean = false): String =
    "${briefDecimal(cents / 100.0, decimals, signed)}\u00a0€"

internal data class BriefBudgetPace(
    val spentFraction: Double,
    val elapsedFraction: Double,
    val remainingCents: Long,
    val aheadOfPace: Boolean,
) {
    val differencePoints get() = (spentFraction - elapsedFraction) * 100
}

internal fun briefBudgetPace(spentCents: Long, budgetCents: Long, day: Int, days: Int): BriefBudgetPace {
    val elapsedDays = day.coerceIn(0, days.coerceAtLeast(0))
    val spent = if (budgetCents > 0) spentCents.toDouble() / budgetCents else 0.0
    val elapsed = if (days > 0) elapsedDays.toDouble() / days else 0.0
    // Add five percentage points before division to keep exact threshold values on pace.
    val aheadOfPace = days > 0 && budgetCents > 0 && spent > (100L * elapsedDays + 5L * days).toDouble() / (100L * days)
    return BriefBudgetPace(spent, elapsed, budgetCents - spentCents, aheadOfPace)
}

internal data class BriefValueChange(val cents: Long, val percent: Double?)

internal fun briefValueChange(currentCents: Long, previousCents: Long): BriefValueChange {
    val difference = currentCents - previousCents
    return BriefValueChange(difference, if (previousCents > 0) 100.0 * difference / previousCents else null)
}

internal fun briefPortfolioChange(positions: List<BriefDemoPosition>): BriefValueChange =
    briefValueChange(positions.sumOf { it.valueCents }, positions.sumOf { it.previousValueCents })

internal fun briefHour(minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

internal fun briefDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> String.format(Locale.ROOT, "%d h %02d", minutes / 60, minutes % 60)
}

internal enum class BriefWeatherCondition(val label: String) {
    Rain("Pluie"), Cloudy("Nuageux"), ClearDay("Éclaircies"), ClearNight("Ciel dégagé"),
}

internal data class BriefWeatherHour(val hour: Int, val temperature: Int, val rainPercent: Int) {
    val condition: BriefWeatherCondition
        get() = when {
            rainPercent >= 50 -> BriefWeatherCondition.Rain
            rainPercent >= 25 -> BriefWeatherCondition.Cloudy
            hour in 7..17 -> BriefWeatherCondition.ClearDay
            else -> BriefWeatherCondition.ClearNight
        }
}

internal fun briefWeatherForecast(hour: Int): List<BriefWeatherHour> = (hour..hour + 6).map { index ->
    BriefWeatherHour(index % 24, BriefDemoData.hourlyTemperatures[index], BriefDemoData.hourlyRain[index])
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
