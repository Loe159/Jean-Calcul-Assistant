package io.github.mangi.eta.ui.screens.home.brief

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
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

internal fun briefHour(time: ZonedDateTime): String = briefHour(time.minuteOfDay)

private val ZonedDateTime.minuteOfDay: Int get() = hour * 60 + minute

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

internal data class BriefDemoAgenda(
    val date: LocalDate,
    val nowMinute: Int?,
    val countdown: BriefAgendaCountdown,
    val departure: ZonedDateTime?,
) {
    val tomorrow get() = nowMinute == null
    val events get() = BriefDemoData.agenda
    val gaps get() = briefAgendaGaps(events, from = nowMinute ?: 0)
}

private fun ZonedDateTime.atMinute(date: LocalDate, minute: Int): ZonedDateTime =
    date.atTime(minute / 60, minute % 60).atZone(zone)

internal fun briefDemoAgenda(now: ZonedDateTime): BriefDemoAgenda {
    val today = now.toLocalDate()
    val minute = now.minuteOfDay
    val events = BriefDemoData.agenda
    val tomorrow = briefMoment(now.toLocalTime()) in listOf(BriefMoment.EVENING, BriefMoment.NIGHT)
    val current = if (tomorrow) null else briefAgendaCountdown(events, minute)
    val date = if (current == null) today.plusDays(1) else today
    val countdown = current ?: events.first().let { first ->
        BriefAgendaCountdown(first, Duration.between(now, now.atMinute(date, first.start)).toMinutes().toInt(), false)
    }
    val departure = countdown.event.arrivalLeadMinutes?.takeUnless { countdown.inProgress }?.let {
        maxOf(now, now.atMinute(date, countdown.event.start).minusMinutes(it.toLong()))
    }
    return BriefDemoAgenda(date, minute.takeIf { current != null }, countdown, departure)
}

internal fun briefBarSuggestion(now: ZonedDateTime, agenda: BriefDemoAgenda): BriefBarSuggestion {
    val minute = now.minuteOfDay
    val moment = briefMoment(now.toLocalTime())
    if (moment == BriefMoment.NIGHT) {
        val bedtime = now.plusMinutes(15)
        val wake = bedtime.plusMinutes(450)
        return BriefBarSuggestion(BriefCardId.HEALTH, "Coucher conseillé à ${briefHour(bedtime)}",
            "Réveil à ${briefHour(wake)} · 7 h 30 de sommeil", briefProgress((minute + 120) % 1440, 420))
    }
    val lunch = BriefDemoData.lunch
    if (moment == BriefMoment.DAY && minute < lunch.start) {
        return BriefBarSuggestion(BriefCardId.AGENDA, "Déjeuner à ${briefHour(lunch.start)}",
            "${BriefDemoData.lunchWalkMinutes} min à pied", 1f - briefProgress(lunch.start - minute, 60))
    }
    val next = agenda.countdown
    return BriefBarSuggestion(
        BriefCardId.AGENDA,
        if (agenda.tomorrow) "Demain · ${next.event.title}"
        else if (next.inProgress) next.event.title
        else "${next.event.title} à ${briefHour(next.event.start)}",
        if (next.inProgress) "En cours · encore ${briefDuration(next.minutes)}"
        else "${next.event.place} · dans ${briefDuration(next.minutes)}",
        next.progress,
    )
}

internal fun briefDemoSuggestions(agenda: BriefDemoAgenda): List<BriefDemoSuggestion> {
    val reply = BriefDemoSuggestion("reply", "Camille attend une réponse", "Pense à répondre à son dernier message.",
        "Répondre", BriefDemoTone.Blue)
    val sport = agenda.gaps.firstOrNull()?.let { gap ->
        BriefDemoSuggestion(
            "sport", "Vingt minutes pour bouger",
            "Créneau libre ${if (agenda.tomorrow) "demain " else ""}de ${briefHour(gap.start)} à ${briefHour(gap.end)}.",
            "Planifier", BriefDemoTone.Green,
        )
    }
    val merge = BriefDemoSuggestion("merge", "Regroupe deux tâches",
        "Prépare la facture et le point projet de l’atelier ensemble.", "Regrouper", BriefDemoTone.Violet)
    return listOfNotNull(reply, sport, merge)
}

internal val BriefRibbonTicks = (7..21 step 2).map { it * 60 }

/** Positions measured labels on the ribbon, keeping both endpoints whenever they fit. */
internal fun briefRibbonLabelPositions(
    width: Int,
    minutes: List<Int>,
    labelWidths: List<Int>,
    margin: Int,
): List<Int?> {
    val duration = minutes.last() - minutes.first()
    val positions = minutes.mapIndexed { index, minute ->
        val label = labelWidths[index]
        if (label > width) null else {
            val center = width * (minute - minutes.first()).toFloat() / duration
            (center - label / 2f).roundToInt().coerceIn(0, width - label)
        }
    }
    var nextLeft = 0
    return positions.mapIndexed { index, left ->
        val right = (left ?: 0) + labelWidths[index]
        val lastLeft = positions.last()
        if (left == null || left < nextLeft ||
            (index in 1 until minutes.lastIndex && lastLeft != null && right + margin > lastLeft)
        ) null else {
            nextLeft = right + margin
            left
        }
    }
}

internal data class BriefRibbonSegment(val event: BriefDemoEvent, val start: Float, val end: Float)
internal data class BriefRibbon(val segments: List<BriefRibbonSegment>, val now: Float?)

internal fun briefAgendaRibbon(
    events: List<BriefDemoEvent>,
    now: Int?,
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
    return BriefRibbon(segments, now?.takeIf { it in start..end }?.let(::position))
}

internal const val BriefMinimumAgendaGap = 75

internal data class BriefAgendaGap(val start: Int, val end: Int) {
    val minutes get() = end - start
}

internal fun briefAgendaGaps(
    events: List<BriefDemoEvent>,
    minimumMinutes: Int = BriefMinimumAgendaGap,
    from: Int = 0,
): List<BriefAgendaGap> =
    buildList {
        var occupiedUntil: Int? = null
        for (event in events.filter { it.end > it.start }.sortedBy { it.start }) {
            val previousEnd = occupiedUntil?.coerceAtLeast(from)
            if (previousEnd != null && event.start > previousEnd && event.start - previousEnd >= minimumMinutes) {
                add(BriefAgendaGap(previousEnd, event.start))
            }
            occupiedUntil = maxOf(previousEnd ?: event.end, event.end)
        }
    }
