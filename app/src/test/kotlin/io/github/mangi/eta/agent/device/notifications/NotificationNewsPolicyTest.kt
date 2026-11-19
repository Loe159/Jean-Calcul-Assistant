package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationNewsPolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI indéterminé")

    @Test fun approvedNewsTopicsMapToTheThreeBriefTiers() {
        data class Case(val pkg: String, val body: String,
            val type: NewsNotificationKind, val tier: NotificationPriority)
        val cases = listOf(
            Case("com.radiofrance.franceinfo", "ALERTE INFO : séisme majeur, évacuation en cours",
                NewsNotificationKind.MAJOR_BREAKING, NotificationPriority.NOW),
            Case("com.feedly", "Nouvelle architecture LLM : les modèles IA évoluent",
                NewsNotificationKind.AI_TECH_CYBER, NotificationPriority.TODAY),
            Case("com.feedly", "Cyberattaque majeure contre une entreprise européenne",
                NewsNotificationKind.AI_TECH_CYBER, NotificationPriority.TODAY),
            Case("com.lemonde.androidapp", "Inflation : les taux d'intérêt poursuivent leur évolution",
                NewsNotificationKind.ECONOMY_POLITICS, NotificationPriority.TODAY),
            Case("com.radiofrance.franceinfo", "Route fermée : travaux routiers dans votre commune",
                NewsNotificationKind.LOCAL_USEFUL, NotificationPriority.TODAY),
            Case("com.feedly", "Nouvel article : une enquête sur le secteur spatial",
                NewsNotificationKind.FOLLOWED_MEDIA, NotificationPriority.TODAY),
            Case("com.feedly", "Articles recommandés pour vous : nos meilleures lectures",
                NewsNotificationKind.UNSOLICITED_RECOMMENDATION, NotificationPriority.LOW),
            Case("com.lemonde.androidapp", "Vous ne devinerez jamais ce qui arrive ensuite !",
                NewsNotificationKind.CLICKBAIT, NotificationPriority.LOW),
        )
        cases.forEach { c ->
            val actual = NotificationNewsPolicy.classify(c.pkg, "Actualités", c.body, "news")
            assertEquals(c.body, c.type, actual)
            val input = NotificationPriorityInput(c.pkg, "Actualités", c.body,
                topic = "news", androidCategory = Notification.CATEGORY_RECOMMENDATION)
            assertEquals(c.body, c.tier, NotificationPriorityPolicy.classify(input))
            assertEquals(c.body, if (c.tier == NotificationPriority.NOW)
                NotificationFilterDecision.KEEP else NotificationFilterDecision.DISMISS,
                NotificationPriorityPolicy.automaticAction(input, config, base, 13).decision)
        }
    }

    @Test fun urgentFramingWithoutMajorEventDoesNotCreateAnInterruption() {
        assertEquals(NotificationPriority.TODAY,
            NotificationPriorityPolicy.classify(NotificationPriorityInput("com.feedly",
                "Breaking news", "Nouvelles du secteur de la robotique",
                topic = "news")))
        assertEquals(NewsNotificationKind.MAJOR_BREAKING,
            NotificationNewsPolicy.classify("com.feedly", "Dernière minute",
                "Résultats des élections : nouveau gouvernement annoncé", "news"))
    }

    @Test fun repeatedHeadlinesAreLowButMajorAlertsAlwaysStayUrgent() {
        val input = NotificationPriorityInput("com.feedly",
            "Le secteur du logiciel adopte de nouveaux standards",
            "Nouvel article sur les technologies", topic = "news", repeatedNewsHeadline = true)
        assertEquals(NewsNotificationKind.REPEATED_HEADLINE,
            NotificationNewsPolicy.classify(input.packageName, input.title,
                input.body, input.topic, repeatedHeadline = true))
        assertEquals(NotificationPriority.LOW, NotificationPriorityPolicy.classify(input))
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(input.copy(
            title = "Dernière minute : attentat", body = "Évacuation immédiate après un attentat")))
    }

    @Test fun unrelatedMessagesRemainProtected() {
        assertEquals(NewsNotificationKind.UNKNOWN,
            NotificationNewsPolicy.classify("fr.leboncoin", "Acheteur",
                "Bonjour, votre produit IA est toujours disponible ?"))
        assertEquals(NewsNotificationKind.UNKNOWN,
            NotificationNewsPolicy.classify("com.google.android.gm", "Banque",
                "Votre virement concernant l'économie est confirmé"))
        assertEquals(NewsNotificationKind.UNKNOWN,
            NotificationNewsPolicy.classify("com.feedly", "Feedly", "Synchronisation terminée"))
        assertFalse(NotificationNewsPolicy.isNewsApp("com.snapchat.android"))
        assertTrue(NotificationNewsPolicy.isNewsApp("com.feedly"))
    }

    @Test fun manualPositiveFeedbackAndDisabledFilteringAreRespected() {
        val input = NotificationPriorityInput("com.feedly", "Feedly",
            "Articles recommandés pour vous", topic = "news")
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input, config.copy(mode = NotificationFilterMode.OFF),
                base, 16).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 7), base, 16).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input.copy(personalInterest =
                NotificationPersonalization.Interest.WANTED), config, base, 16).decision)
    }
}
