package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPriorityPolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI: no evidence")

    @Test fun githubBotOnlyRequestsImmediateActionWhenExplicit() {
        val bot = NotificationPriorityInput("com.microsoft.office.outlook", "chatgpt-codex-connector[bot]",
            "Re: [Loe159/Jean-Calcul-Assistant] feat: classification adaptative", androidCategory = Notification.CATEGORY_EMAIL,
            topic = "development")
        assertEquals(NotificationPriority.LOW, NotificationPriorityPolicy.classify(bot))
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationPriorityPolicy.automaticAction(bot, config, base, 14).decision)
        val failed = bot.copy(body = "Review requested: build failed, changes requested")
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(failed))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(failed, config, base, 14).decision)
        val human = bot.copy(title = "FOUCAULT Adeline", body = "Bonjour, j'ai une question")
        assertFalse(NotificationPriorityPolicy.githubBot(human))
        assertEquals(NotificationPriority.TODAY, NotificationPriorityPolicy.classify(human))
    }

    @Test fun directMessagesSecurityAndAppointmentsAlwaysComeFirst() {
        val inputs = listOf(
            NotificationPriorityInput("com.snapchat.android", "Eva", "vous a envoyé un Chat", topic = "messages"),
            NotificationPriorityInput("com.google.android.gm", "Google", "Alerte de sécurité", topic = "security"),
            NotificationPriorityInput("com.microsoft.office.outlook", "Campus Tour", "Rendez-vous à 14:00", topic = "calendar"),
            NotificationPriorityInput("com.supercell.brawlstars", "Brawl Stars", "Alex invited you to play", topic = "games", game = true),
        )
        inputs.forEach {
            assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(it))
            assertEquals(NotificationFilterDecision.KEEP,
                NotificationPriorityPolicy.automaticAction(it, config, base, 14).decision)
        }
    }

    @Test fun marketingSuggestionsAndGamesAreCollapsedToTheBottom() {
        val input = NotificationPriorityInput("com.snapchat.android", "Loë, découvrez les contenus de Spotlight",
            "Quand tes potes font une blague", androidCategory = Notification.CATEGORY_MESSAGE, topic = "social")
        assertTrue(NotificationPriorityPolicy.recognizedSocialSuggestion(input.title, input.body))
        assertEquals(NotificationPriority.LOW, NotificationPriorityPolicy.classify(input))
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationPriorityPolicy.automaticAction(input, config, base, 14).decision)
        assertEquals(NotificationPriority.LOW, NotificationPriorityPolicy.classify(
            NotificationPriorityInput("com.supercell.brawlstars", "Brawl Stars", "De nouvelles quêtes sont disponibles !",
                game = true, topic = "games")))
    }

    @Test fun systemUpdatesAreNonUrgentAndNewsArePreserved() {
        val update = NotificationPriorityInput("com.android.updater", "Redémarrage de votre téléphone",
            "Màj logicielle disponible", topic = "system")
        assertEquals(NotificationPriority.TODAY, NotificationPriorityPolicy.classify(update))
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationPriorityPolicy.automaticAction(update, config, base, 14).decision)
        val news = NotificationPriorityInput("com.example.news", "Actualités", "Nouveauté satellite", topic = "news")
        assertEquals(NotificationPriority.TODAY, NotificationPriorityPolicy.classify(news))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(news, config, base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(update, config.copy(mode = NotificationFilterMode.OFF), base, 14).decision)
    }

    @Test fun manualPreferenceNeverDeletesAnImportantItem() {
        val msg = NotificationPriorityInput("com.snapchat.android", "Eva", "vous a envoyé un Chat",
            topic = "messages", personalInterest = NotificationPersonalization.Interest.WANTED)
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(msg))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(msg, config, base, 14).decision)
        val bot = NotificationPriorityInput("com.microsoft.office.outlook", "chatgpt-codex-connector[bot]",
            "Re: PR status", topic = "development", personalInterest = NotificationPersonalization.Interest.WANTED)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(bot, config, base, 14).decision)
    }
}
