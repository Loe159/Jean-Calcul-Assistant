package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSocialPolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI non concluant")

    @Test fun validatedSocialPreferencesAreDistinct() {
        val samples = listOf(
            Triple("Eva vous a envoyé un Chat", SocialNotificationKind.DIRECT, NotificationPriority.NOW),
            Triple("Eva vous a mentionné dans une story", SocialNotificationKind.MENTION, NotificationPriority.TODAY),
            Triple("Théo vous a envoyé une demande d'ami", SocialNotificationKind.INVITATION, NotificationPriority.TODAY),
            Triple("Eva est en train d'écrire", SocialNotificationKind.ACTIVITY, NotificationPriority.LOW),
            Triple("Découvrez Spotlight et ses contenus", SocialNotificationKind.SUGGESTION, NotificationPriority.LOW),
            Triple("RUN&MIX a publié une nouvelle activité", SocialNotificationKind.POST, NotificationPriority.LOW),
        )
        samples.forEach { (text, kind, priority) ->
            val pkg = if (text.contains("RUN&MIX")) "com.strava" else "com.snapchat.android"
            assertEquals(text, kind, NotificationSocialPolicy.classify(pkg, "Notification", text,
                Notification.CATEGORY_MESSAGE))
            assertEquals(text, priority, NotificationPriorityPolicy.classify(
                NotificationPriorityInput(pkg, "Notification", text,
                    androidCategory = Notification.CATEGORY_MESSAGE, topic = "social")))
            assertEquals(text, if (priority == NotificationPriority.NOW)
                NotificationFilterDecision.KEEP else NotificationFilterDecision.DISMISS,
                NotificationPriorityPolicy.automaticAction(NotificationPriorityInput(pkg,
                    "Notification", text, androidCategory = Notification.CATEGORY_MESSAGE,
                    topic = "social"), config, base, 12).decision)
        }
    }

    @Test fun directMessagesRemainUrgentEvenWhenTheyMentionSocialContent() {
        val text = "Eva vous a envoyé un Chat : tu as aimé ma story ?"
        assertEquals(SocialNotificationKind.DIRECT,
            NotificationSocialPolicy.classify("com.snapchat.android", "Eva", text))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(NotificationPriorityInput(
                "com.snapchat.android", "Eva", text, topic = "social"),
                config, base, 18).decision)
    }

    @Test fun unknownSocialAlertCannotBeAssignedAnExplicitRemovalRule() {
        val pkg = "com.snapchat.android"
        assertEquals(SocialNotificationKind.UNKNOWN,
            NotificationSocialPolicy.classify(pkg, "Snapchat", "Votre compte est prêt"))
        assertNull(NotificationSocialPolicy.decision(SocialNotificationKind.UNKNOWN, config,
            NotificationPersonalization.Interest.NEUTRAL, 15))
        assertEquals(SocialNotificationKind.UNKNOWN,
            NotificationSocialPolicy.classify("com.google.android.gm",
                "Eva", "Théo vous a envoyé une demande d'ami"))
    }

    @Test fun realMessageAndOtherDirectAlertsAreNeverDeletedFromMessageCategory() {
        val pkg = "com.snapchat.android"
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(
            NotificationPriorityInput(pkg, "Eva", "Salut, tu viens ?",
                androidCategory = Notification.CATEGORY_MESSAGE, topic = "messages")))
        assertEquals(NotificationFilterDecision.KEEP, NotificationPriorityPolicy.automaticAction(
            NotificationPriorityInput(pkg, "Eva", "Salut, tu viens ?",
                androidCategory = Notification.CATEGORY_MESSAGE, topic = "messages"),
            config, base, 20).decision)
    }

    @Test fun socialRulesRespectGlobalOffQuietHoursAndWantedFeedback() {
        val input = NotificationPriorityInput("com.instagram.android", "Instagram",
            "Nouvelle publication d'un abonnement", topic = "social")
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(mode = NotificationFilterMode.OFF), base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 7), base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input.copy(personalInterest =
                NotificationPersonalization.Interest.WANTED), config, base, 14).decision)
    }
}
