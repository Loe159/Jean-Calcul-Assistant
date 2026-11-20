package io.github.mangi.eta.ui.screens.home.brief

import java.time.LocalDateTime

internal data class BriefSummaryInput(
    val now: LocalDateTime,
    // Null while loading or when the Notifications card is hidden.
    val urgentNotifications: Int? = null,
)

internal data class BriefSummaryPart(val text: String, val highlighted: Boolean = false)

internal fun interface BriefSummarySource {
    suspend fun summarize(input: BriefSummaryInput): List<BriefSummaryPart>
}

internal object LocalBriefSummary : BriefSummarySource {
    override suspend fun summarize(input: BriefSummaryInput): List<BriefSummaryPart> =
        localBriefSummary(input)
}

internal fun localBriefSummary(input: BriefSummaryInput): List<BriefSummaryPart> = buildList {
    when (val count = input.urgentNotifications) {
        null -> Unit
        0 -> add(BriefSummaryPart("Aucune notification urgente.", highlighted = true))
        else -> {
            add(BriefSummaryPart("Tu as"))
            val label = if (count == 1) "notification urgente" else "notifications urgentes"
            add(BriefSummaryPart("$count $label.", highlighted = true))
        }
    }
    val (start, highlight) = when (briefMoment(input.now.toLocalTime())) {
        BriefMoment.MORNING -> "Retrouve tes repères pour" to "ce matin."
        BriefMoment.DAY -> "Retrouve tes repères pour" to "cet après-midi."
        BriefMoment.EVENING -> "Prépare" to "la journée de demain."
        BriefMoment.NIGHT -> "Fais le point avant" to "la nuit."
    }
    add(BriefSummaryPart(start))
    add(BriefSummaryPart(highlight, highlighted = true))
}
