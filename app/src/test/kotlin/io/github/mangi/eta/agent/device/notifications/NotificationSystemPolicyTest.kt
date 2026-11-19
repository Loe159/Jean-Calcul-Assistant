package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSystemPolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI indéterminé")

    @Test fun sevenApprovedSystemCasesUseTheirPriorities() {
        data class Fixture(val packageName: String, val message: String,
                           val kind: SystemNotificationKind, val priority: NotificationPriority)
        val cases = listOf(
            Fixture("com.android.settings", "Batterie critique : 2 %",
                SystemNotificationKind.CRITICAL, NotificationPriority.NOW),
            Fixture("com.android.settings", "Stockage saturé, libérez de l'espace",
                SystemNotificationKind.CRITICAL, NotificationPriority.NOW),
            Fixture("com.android.settings", "Panne importante du système",
                SystemNotificationKind.CRITICAL, NotificationPriority.NOW),
            Fixture("com.samsung.android.scloud", "Échec de sauvegarde du téléphone",
                SystemNotificationKind.BACKUP_FAILED, NotificationPriority.NOW),
            Fixture("com.android.settings", "Mise à jour du système disponible",
                SystemNotificationKind.UPDATE_AVAILABLE, NotificationPriority.TODAY),
            Fixture("com.google.android.apps.docs", "Synchronisation terminée avec succès",
                SystemNotificationKind.BACKUP_OR_SYNC_SUCCEEDED, NotificationPriority.TODAY),
            Fixture("com.android.vending", "Mise à jour de l'application terminée",
                SystemNotificationKind.APP_UPDATE_COMPLETE, NotificationPriority.LOW),
            Fixture("com.android.settings", "Conseils Android : découvrez vos fonctionnalités",
                SystemNotificationKind.TIPS_AND_SUGGESTIONS, NotificationPriority.LOW),
            Fixture("com.android.settings", "Vérification périodique terminée",
                SystemNotificationKind.ROUTINE_TECHNICAL, NotificationPriority.LOW),
        )
        for (c in cases) {
            assertEquals(c.message, c.kind,
                NotificationSystemPolicy.classify(c.packageName, "Système", c.message,
                    Notification.CATEGORY_SYSTEM, "system"))
            val input = NotificationPriorityInput(c.packageName, "Système", c.message,
                androidCategory = Notification.CATEGORY_SYSTEM, topic = "system")
            assertEquals(c.message, c.priority, NotificationPriorityPolicy.classify(input))
            assertEquals(c.message, if (c.priority == NotificationPriority.NOW)
                NotificationFilterDecision.KEEP else NotificationFilterDecision.DISMISS,
                NotificationPriorityPolicy.automaticAction(input, config, base, 12).decision)
        }
    }

    @Test fun dangerousSystemEventsAreNeverDemoted() {
        val cases = listOf("Backup failed", "Battery critically low", "Storage full",
            "Surchauffe du téléphone", "Sauvegarde impossible")
        cases.forEach { body ->
            val input = NotificationPriorityInput("com.android.settings", "Système", body,
                androidCategory = Notification.CATEGORY_SYSTEM, topic = "system")
            assertEquals(body, NotificationPriority.NOW, NotificationPriorityPolicy.classify(input))
            assertEquals(NotificationFilterDecision.KEEP,
                NotificationPriorityPolicy.automaticAction(input, config, base, 12).decision)
        }
    }

    @Test fun unrelatedSocialMessagesCannotBecomeSystemUpdates() {
        assertEquals(SystemNotificationKind.UNKNOWN, NotificationSystemPolicy.classify(
            "com.snapchat.android", "Eva", "Sauvegarde terminée dans mon jeu",
            Notification.CATEGORY_MESSAGE, "messages"))
        assertEquals(SystemNotificationKind.UNKNOWN, NotificationSystemPolicy.classify(
            "com.example.marketing", "Promotion", "Nouvelle mise à jour système disponible",
            Notification.CATEGORY_PROMO, "promotions"))
        assertEquals(SystemNotificationKind.UNKNOWN, NotificationSystemPolicy.classify(
            "com.android.settings", "Système", "Opération en cours",
            Notification.CATEGORY_SERVICE, "system"))
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(
            NotificationPriorityInput("com.android.settings", "Système", "Alerte de sécurité : connexion inconnue",
                androidCategory = Notification.CATEGORY_SYSTEM, topic = "system")))
    }

    @Test fun filterSwitchQuietHoursAndPositiveLearningStillApply() {
        val input = NotificationPriorityInput("com.android.settings", "Système",
            "Mise à jour logicielle disponible", androidCategory = Notification.CATEGORY_SYSTEM, topic = "system")
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(mode = NotificationFilterMode.OFF), base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 7), base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input.copy(
                personalInterest = NotificationPersonalization.Interest.WANTED), config, base, 14).decision)
    }
}
