package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationPersonalizationTest {
    @Test fun packageAndChannelAreSeparateFeaturesEvenForIdenticalText() {
        val a = NotificationPersonalization.features("fr.leboncoin", "promos", "a promotional advertisement", "Offre spéciale")
        val b = NotificationPersonalization.features("fr.leboncoin", "messages", "a promotional advertisement", "Offre spéciale")
        val c = NotificationPersonalization.features("org.other", "promos", "a promotional advertisement", "Offre spéciale")
        assertTrue(a.contains("app-channel:fr.leboncoin|promos"))
        assertTrue(a != b && b != c && a != c)
        assertTrue(a.any { it.startsWith("token:") })
    }

    @Test fun fewerThanThreeHumanEventsCannotOverrideAnyNotification() {
        val x = listOf("app-channel:fr.leboncoin|promos" to
            NotificationPersonalization.Evidence(0.0, 8.0, 2))
        assertEquals(NotificationPersonalization.Interest.NEUTRAL, NotificationPersonalization.interest(x))
        assertEquals(NotificationPersonalization.Interest.UNWANTED,
            NotificationPersonalization.interest(x, minExamples = 2))
    }

    @Test fun strongContradictoryFeedbackNeutralizesPreference() {
        val x = listOf("app-label:fr.leboncoin|a promotional advertisement" to
            NotificationPersonalization.Evidence(8.0, 8.0, 4))
        assertEquals(NotificationPersonalization.Interest.NEUTRAL, NotificationPersonalization.interest(x))
    }

    @Test fun protectedMessageDoesNotBecomeDeletableFromLearnedDislikes() {
        val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Mode prudent : seule la publicité est filtrée")
        val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
            action = NotificationFilterAction.DISMISS)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPersonalization.safeDecision(base, NotificationPersonalization.Interest.UNWANTED,
                "a personal message", config).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPersonalization.safeDecision(base, NotificationPersonalization.Interest.WANTED,
                NotificationFilterPolicy.AD, config).decision)
    }

    @Test fun leboncoinBuyerMessagesAndAdsAreDifferentTopics() {
        assertEquals("messages", NotificationTopics.classify("fr.leboncoin", "chat",
            Notification.CATEGORY_MESSAGE, "L'acheteur vous a écrit", "Bonjour", "a personal message"))
        assertEquals("promotions", NotificationTopics.classify("fr.leboncoin", "marketing",
            Notification.CATEGORY_PROMO, "Offre spéciale", "-20%", NotificationFilterPolicy.AD))
    }
}
