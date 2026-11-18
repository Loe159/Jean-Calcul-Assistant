package io.github.mangi.eta.agent.device.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNliScoresTest {
    @Test
    fun singleLabelSoftmaxIsAcrossEntailmentLogits() {
        val scores = NotificationNliScores.scores(
            listOf(listOf(10f, 1f), listOf(-10f, 3f), listOf(1f, 0f)),
            multiLabel = false,
        )
        assertEquals(1.0, scores.sum(), 1e-12)
        assertEquals(1, scores.indices.maxByOrNull { scores[it] })
        assertTrue(scores[1] > scores[0])
    }

    @Test
    fun multiLabelUsesIndependentTwoClassSoftmax() {
        val scores = NotificationNliScores.scores(
            listOf(listOf(0f, 0f), listOf(-5f, 5f)),
            multiLabel = true,
        )
        assertEquals(0.5, scores[0], 1e-12)
        assertTrue(scores[1] > 0.9999)
        assertTrue(scores.sum() > 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedLogitsFailClosed() {
        NotificationNliScores.scores(listOf(listOf(1f)), multiLabel = false)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteLogitsFailClosed() {
        NotificationNliScores.scores(listOf(listOf(Float.NaN, 1f)), multiLabel = true)
    }

    @Test
    fun hypothesisAndLabelsAreFixedToValidatedDemo() {
        assertEquals("This notification is a security alert.",
            NotificationNliScores.hypothesis("a security alert"))
        assertEquals(7, NotificationNliScores.validatedLabels.size)
    }

    @Test
    fun protectedAndUncertainNotificationsAreAlwaysKept() {
        val base = NotificationSafetyInput(
            hasUsableText = true,
            isModelReady = true,
            predictedLabel = "a promotional advertisement",
        )
        assertEquals(NotificationDecision.REVIEW, NotificationDecisionPolicy.decide(base))
        listOf(
            base.copy(isConversation = true),
            base.copy(isSecurityOrOtp = true),
            base.copy(isSystemOrOngoing = true),
            base.copy(isFinanceOrDeliveryOrCalendar = true),
            base.copy(isFromSelf = true),
            base.copy(wasModified = true),
            base.copy(isModelReady = false),
            base.copy(hasUsableText = false),
        ).forEach { input ->
            assertEquals(NotificationDecision.KEEP, NotificationDecisionPolicy.decide(input))
        }
        assertEquals(NotificationDecision.KEEP,
            NotificationDecisionPolicy.decide(base.copy(predictedLabel = null)))
    }

    @Test
    fun noAutomaticDeletionDecisionExists() {
        assertEquals(setOf(NotificationDecision.KEEP, NotificationDecision.REVIEW),
            NotificationDecision.entries.toSet())
    }
}
