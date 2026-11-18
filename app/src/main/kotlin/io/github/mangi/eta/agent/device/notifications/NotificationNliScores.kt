package io.github.mangi.eta.agent.device.notifications

import kotlin.math.exp

/**
 * Pure NLI score decoding matching transformers zero-shot-classification:
 * label 0 = not_entailment, label 1 = entailment.
 *
 * A high NLI score is NOT a calibrated probability of notification importance.
 * Neither this class nor its callers are allowed to cancel notifications.
 */
internal object NotificationNliScores {
    const val MODEL_ID = "Horizon-Labs/multilingual-zeroshot-small"
    const val MODEL_REVISION = "17af5f8a728d101ce33a9bffad227b0cd005e95e"
    const val HYPOTHESIS_TEMPLATE = "This notification is {}."

    val validatedLabels = listOf(
        "a promotional advertisement",
        "a personal message",
        "a security alert",
        "a delivery update",
        "an appointment reminder",
        "a useful service update",
        "spam or a scam",
    )

    fun hypothesis(label: String): String = HYPOTHESIS_TEMPLATE.replace("{}", label)

    /**
     * @param logits Per-label float logits [not_entailment, entailment].
     * @param multiLabel When false, softmax over entailment logits across labels;
     *   when true, independent two-class softmax for each label.
     */
    fun scores(logits: List<List<Float>>, multiLabel: Boolean): List<Double> {
        require(logits.isNotEmpty()) { "At least one hypothesis is required" }
        require(logits.all { it.size == 2 && it.all(Float::isFinite) }) {
            "Expected two finite NLI logits per label"
        }
        return if (multiLabel) {
            logits.map { softmax(listOf(it[0].toDouble(), it[1].toDouble()))[1] }
        } else {
            softmax(logits.map { it[1].toDouble() })
        }
    }

    private fun softmax(logits: List<Double>): List<Double> {
        val max = logits.maxOrNull() ?: error("No logits")
        val expValues = logits.map { exp(it - max) }
        val sum = expValues.sum()
        require(sum > 0 && sum.isFinite()) { "Invalid logits" }
        return expValues.map { it / sum }
    }
}

/**
 * Intentionally conservative, platform-independent decision layer.
 * Phase A contains no Android notification cancellation path.
 */
internal enum class NotificationDecision {
    KEEP,
    REVIEW,
}

internal data class NotificationSafetyInput(
    val hasUsableText: Boolean,
    val isFromSelf: Boolean = false,
    val isConversation: Boolean = false,
    val isSystemOrOngoing: Boolean = false,
    val isSecurityOrOtp: Boolean = false,
    val isFinanceOrDeliveryOrCalendar: Boolean = false,
    val wasModified: Boolean = false,
    val isModelReady: Boolean = false,
    val predictedLabel: String? = null,
)

internal object NotificationDecisionPolicy {
    fun decide(input: NotificationSafetyInput): NotificationDecision {
        if (!input.hasUsableText || input.isFromSelf ||
            input.isConversation || input.isSystemOrOngoing ||
            input.isSecurityOrOtp || input.isFinanceOrDeliveryOrCalendar ||
            input.wasModified || !input.isModelReady
        ) return NotificationDecision.KEEP

        return when (input.predictedLabel) {
            "a promotional advertisement", "spam or a scam" -> NotificationDecision.REVIEW
            else -> NotificationDecision.KEEP
        }
    }
}
