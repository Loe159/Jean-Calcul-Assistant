package io.github.mangi.eta.agent.device.notifications

import kotlinx.coroutines.flow.StateFlow

/**
 * Dashboard-only projection. Never includes notification titles, content, identifiers or keys.
 * All values are held in RAM and reset if the app process is killed.
 */
internal enum class NotificationBriefOutcome {
    KEPT, OBSERVED, PROPOSED, SNOOZED_1H, SNOOZED_24H, DISMISSED,
}

internal data class NotificationBriefEvent(
    val packageName: String,
    val category: String,
    val score: Double,
    val outcome: NotificationBriefOutcome,
    val timestampMs: Long,
)

internal data class NotificationBriefSnapshot(
    val analyzed: Int = 0,
    val kept: Int = 0,
    val proposed: Int = 0,
    val requested: Int = 0,
    val snoozed: Int = 0,
    val dismissed: Int = 0,
    val filteredAds: Int = 0,
    val filteredSpam: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
    val events: List<NotificationBriefEvent> = emptyList(),
) {
    val filtered: Int get() = snoozed + dismissed
}

internal interface NotificationBriefSource {
    val summary: StateFlow<NotificationBriefSnapshot>
}
