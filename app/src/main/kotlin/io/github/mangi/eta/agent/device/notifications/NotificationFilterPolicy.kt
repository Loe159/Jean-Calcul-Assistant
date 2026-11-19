package io.github.mangi.eta.agent.device.notifications

import kotlin.math.abs

internal enum class NotificationFilterDecision { KEEP, OBSERVE, SNOOZE_1H, SNOOZE_24H, DISMISS }

internal data class NotificationFilterCandidate(
    val app: String,
    val safe: Boolean,
    val scores: List<Pair<String, Double>>,
    val localHour: Int,
)

internal data class NotificationFilterEvaluation(
    val decision: NotificationFilterDecision,
    val reason: String,
)

/** NLI scores are relative softmax values, NOT calibrated probabilities.
 * Every removal is also revalidated by the Android notification listener.
 */
internal object NotificationFilterPolicy {
    const val AD = "a promotional advertisement"
    const val SPAM = "spam or a scam"

    fun decide(config: NotificationFilterConfig, candidate: NotificationFilterCandidate): NotificationFilterDecision =
        evaluate(config, candidate).decision

    fun evaluate(config: NotificationFilterConfig, candidate: NotificationFilterCandidate): NotificationFilterEvaluation {
        fun keep(reason: String) = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, reason)
        if (!candidate.safe) return keep("Blocage de sécurité")
        if (config.mode == NotificationFilterMode.OFF) return keep("Filtrage désactivé")
        if (config.mode == NotificationFilterMode.OBSERVE)
            return NotificationFilterEvaluation(NotificationFilterDecision.OBSERVE, "Mode observation")
        if (config.mode == NotificationFilterMode.PERSONALIZED &&
            config.onlyQuietHours && !config.inQuietHours(candidate.localHour))
            return keep("Hors plage horaire")

        val scores = candidate.scores
        val labels = config.inferenceLabels()
        if (scores.size != labels.size || scores.map { it.first }.toSet() != labels.toSet() ||
            scores.any { !it.second.isFinite() || it.second !in 0.0..1.0 } ||
            abs(scores.sumOf { it.second } - 1.0) > 0.005
        ) return keep("Scores invalides ou labels modifiés")

        val ranked = scores.sortedByDescending { it.second }
        val first = ranked[0]
        val margin = first.second - ranked[1].second
        val threshold: Double
        val marginRequired: Double
        when (config.mode) {
            NotificationFilterMode.CAUTIOUS -> {
                if (first.first != AD) return keep("Mode prudent : seule la publicité est filtrée")
                threshold = 0.995
                marginRequired = 0.40
            }
            NotificationFilterMode.PERSONALIZED -> {
                val rule = config.labelRules.firstOrNull { it.label == first.first }
                    ?: return keep("Catégorie inconnue")
                if (!rule.enabled) return keep("Catégorie non sélectionnée pour le filtrage")
                threshold = rule.minimumScore / 100.0
                marginRequired = rule.minimumMargin / 100.0
            }
            else -> return keep("Mode inactif")
        }
        if (first.second < threshold)
            return keep("Score insuffisant : %.1f %% < %d %%".format(
                java.util.Locale.FRANCE, first.second * 100, (threshold * 100).toInt()))
        if (margin < marginRequired)
            return keep("Écart insuffisant : %.1f < %.0f points".format(
                java.util.Locale.FRANCE, margin * 100, marginRequired * 100))
        if (config.mode == NotificationFilterMode.CAUTIOUS)
            return NotificationFilterEvaluation(NotificationFilterDecision.SNOOZE_1H, "Publicité filtrable (mode prudent)")
        val decision = when (config.action) {
            NotificationFilterAction.SNOOZE_1H -> NotificationFilterDecision.SNOOZE_1H
            NotificationFilterAction.SNOOZE_24H -> NotificationFilterDecision.SNOOZE_24H
            NotificationFilterAction.DISMISS -> NotificationFilterDecision.DISMISS
        }
        return NotificationFilterEvaluation(decision, "Seuil et écart atteints")
    }
}
