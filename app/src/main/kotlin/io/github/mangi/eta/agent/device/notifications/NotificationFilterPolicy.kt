package io.github.mangi.eta.agent.device.notifications

import kotlin.math.abs

internal enum class NotificationFilterDecision { KEEP, OBSERVE, SNOOZE_1H, SNOOZE_24H, DISMISS }

internal data class NotificationFilterCandidate(
    val app: String,
    val safe: Boolean,
    val scores: List<Pair<String, Double>>,
    val localHour: Int,
)

/** Fail closed, with independent score and margin requirements.
 * NLI scores are not calibrated probabilities of importance.
 */
internal object NotificationFilterPolicy {
    const val AD = "a promotional advertisement"
    const val SPAM = "spam or a scam"

    fun decide(config: NotificationFilterConfig, candidate: NotificationFilterCandidate): NotificationFilterDecision {
        if (!candidate.safe || config.mode == NotificationFilterMode.OFF) return NotificationFilterDecision.KEEP
        if (config.mode == NotificationFilterMode.OBSERVE) return NotificationFilterDecision.OBSERVE
        if (config.mode == NotificationFilterMode.PERSONALIZED) {
            if (config.onlyQuietHours && !config.inQuietHours(candidate.localHour)) return NotificationFilterDecision.KEEP
        }

        val scores = candidate.scores
        if (scores.size != NotificationNliScores.validatedLabels.size ||
            scores.map { it.first }.toSet() != NotificationNliScores.validatedLabels.toSet() ||
            scores.any { !it.second.isFinite() || it.second !in 0.0..1.0 } ||
            abs(scores.sumOf { it.second } - 1.0) > 0.005
        ) return NotificationFilterDecision.KEEP

        val ranked = scores.sortedByDescending { it.second }
        val first = ranked[0]
        val margin = first.second - ranked[1].second
        val threshold: Double
        val marginRequired: Double
        when (config.mode) {
            NotificationFilterMode.CAUTIOUS -> {
                if (first.first != AD) return NotificationFilterDecision.KEEP
                threshold = 0.995
                marginRequired = 0.40
            }
            NotificationFilterMode.PERSONALIZED -> {
                if ((first.first == AD && !config.filterAds) ||
                    (first.first == SPAM && !config.filterSpam) ||
                    first.first !in setOf(AD, SPAM)) return NotificationFilterDecision.KEEP
                threshold = if (config.action == NotificationFilterAction.DISMISS) 0.999 else 0.990
                marginRequired = if (config.action == NotificationFilterAction.DISMISS) 0.50 else 0.30
            }
            else -> return NotificationFilterDecision.KEEP
        }
        if (first.second < threshold || margin < marginRequired) return NotificationFilterDecision.KEEP
        if (config.mode == NotificationFilterMode.CAUTIOUS) return NotificationFilterDecision.SNOOZE_1H
        return when (config.action) {
            NotificationFilterAction.SNOOZE_1H -> NotificationFilterDecision.SNOOZE_1H
            NotificationFilterAction.SNOOZE_24H -> NotificationFilterDecision.SNOOZE_24H
            NotificationFilterAction.DISMISS -> NotificationFilterDecision.DISMISS
        }
    }
}
