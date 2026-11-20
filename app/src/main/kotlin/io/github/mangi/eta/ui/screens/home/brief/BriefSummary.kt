package io.github.mangi.eta.ui.screens.home.brief

import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefCounts
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs

internal data class BriefSummaryInput(
    val now: ZonedDateTime,
    val locale: String,
    val counts: MastraBriefCounts? = null,
    val showNotificationCount: Boolean = true,
) {
    val scope = BriefSummaryScope(
        now.toLocalDate(), MastraNotificationSync.contractZone(now.zone), locale, briefMoment(now.toLocalTime()), counts,
    )
}

internal data class BriefSummaryScope(
    val date: LocalDate,
    val timeZone: String,
    val locale: String,
    val moment: BriefMoment,
    val counts: MastraBriefCounts?,
) {
    fun accepts(other: BriefSummaryScope): Boolean {
        if (date != other.date || timeZone != other.timeZone || locale != other.locale || moment != other.moment) {
            return false
        }
        val previous = counts ?: return other.counts == null
        val current = other.counts ?: return false
        return previous.now == current.now &&
            stableCount(previous.today, current.today) && stableCount(previous.low, current.low)
    }

    private fun stableCount(before: Int, after: Int): Boolean =
        (before == 0) == (after == 0) && abs(before - after) < 3
}

internal data class BriefSummaryPart(val text: String, val highlighted: Boolean = false)

internal data class BriefSummary(
    val parts: List<BriefSummaryPart>,
    val remote: RemoteBriefValidity? = null,
) {
    val text = parts.joinToString("") { it.text }
}

internal data class RemoteBriefValidity(val scope: BriefSummaryScope, val until: Instant) {
    fun accepts(input: BriefSummaryInput, now: Instant): Boolean =
        now.isBefore(until) && scope.accepts(input.scope)
}

internal fun interface BriefSummarySource {
    suspend fun summarize(input: BriefSummaryInput): BriefSummary
}

internal fun localBriefSummary(input: BriefSummaryInput): BriefSummary = BriefSummary(buildList {
    when (val count = input.counts?.now?.takeIf { input.showNotificationCount }) {
        null -> Unit
        0 -> add(BriefSummaryPart("Aucune notification urgente.", highlighted = true))
        else -> {
            add(BriefSummaryPart("Tu as "))
            val label = if (count == 1) "notification urgente" else "notifications urgentes"
            add(BriefSummaryPart("$count $label.", highlighted = true))
        }
    }
    val (start, highlight) = when (input.scope.moment) {
        BriefMoment.MORNING -> "Retrouve tes repères pour " to "ce matin."
        BriefMoment.DAY -> "Retrouve tes repères pour " to "cet après-midi."
        BriefMoment.EVENING -> "Prépare " to "la journée de demain."
        BriefMoment.NIGHT -> "Fais le point avant " to "la nuit."
    }
    if (isNotEmpty()) add(BriefSummaryPart(" "))
    add(BriefSummaryPart(start))
    add(BriefSummaryPart(highlight, highlighted = true))
})
