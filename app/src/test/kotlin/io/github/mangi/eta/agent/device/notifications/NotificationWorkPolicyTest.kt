package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationWorkPolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI non concluant")

    @Test fun sevenApprovedCasesHaveDistinctPrioritiesAndAndroidActions() {
        data class Case(val pkg: String, val title: String, val body: String, val category: String?,
            val kind: WorkNotificationKind, val priority: NotificationPriority)
        val cases = listOf(
            Case("com.microsoft.office.outlook", "chatgpt-codex-connector[bot]",
                "Your review requested on PR #72", Notification.CATEGORY_EMAIL,
                WorkNotificationKind.ACTION_REQUIRED, NotificationPriority.NOW),
            Case("com.github.android", "GitHub Actions", "CI failed: tests failed on PR #72",
                null, WorkNotificationKind.CI_FAILED, NotificationPriority.NOW),
            Case("com.microsoft.office.outlook", "Adeline", "Bonjour, peux-tu valider la proposition ?",
                Notification.CATEGORY_EMAIL, WorkNotificationKind.COLLEAGUE_MESSAGE, NotificationPriority.NOW),
            Case("com.openai.chatgpt", "ChatGPT", "Votre tâche terminée : rapport prêt",
                Notification.CATEGORY_MESSAGE, WorkNotificationKind.AGENT_COMPLETED, NotificationPriority.TODAY),
            Case("com.github.android", "GitHub Actions", "Build successful. All checks passed",
                null, WorkNotificationKind.BUILD_SUCCEEDED, NotificationPriority.TODAY),
            Case("com.google.android.gm", "chatgpt-codex-connector[bot]",
                "Added a comment to PR #72", Notification.CATEGORY_EMAIL,
                WorkNotificationKind.BOT_COMMENT, NotificationPriority.LOW),
            Case("com.github.android", "dependabot[bot]", "Updated dependency on PR #72",
                null, WorkNotificationKind.BOT_ACTIVITY, NotificationPriority.LOW),
        )
        cases.forEach { c ->
            val kind = NotificationWorkPolicy.classify(c.pkg, c.title, c.body, c.category)
            assertEquals(c.title + " " + c.body, c.kind, kind)
            val input = NotificationPriorityInput(c.pkg, c.title, c.body,
                androidCategory = c.category, topic = "development")
            assertEquals(c.title + " " + c.body, c.priority, NotificationPriorityPolicy.classify(input))
            assertEquals(c.title + " " + c.body,
                if (c.priority == NotificationPriority.NOW) NotificationFilterDecision.KEEP
                else NotificationFilterDecision.DISMISS,
                NotificationPriorityPolicy.automaticAction(input, config, base, 15).decision)
        }
    }

    @Test fun botCommentWithActionRequiredBecomesImmediate() {
        val input = NotificationPriorityInput("com.microsoft.office.outlook", "chatgpt-codex-connector[bot]",
            "Commented on PR #72: changes requested, please review",
            androidCategory = Notification.CATEGORY_EMAIL)
        assertEquals(WorkNotificationKind.ACTION_REQUIRED,
            NotificationWorkPolicy.classify(input.packageName, input.title, input.body, input.androidCategory))
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(input))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input, config, base, 15).decision)
    }

    @Test fun nonDirectedEmailsAndNewslettersAreNotMistakenForColleagueMessages() {
        assertEquals(WorkNotificationKind.UNKNOWN, NotificationWorkPolicy.classify(
            "com.microsoft.office.outlook", "Adeline", "Agile Laval s'installe demain",
            Notification.CATEGORY_EMAIL))
        assertEquals(WorkNotificationKind.UNKNOWN, NotificationWorkPolicy.classify(
            "com.microsoft.office.outlook", "Newsletter",
            "Bonjour, newsletter : code promo, merci de répondre", Notification.CATEGORY_EMAIL))
        assertEquals(WorkNotificationKind.UNKNOWN, NotificationWorkPolicy.classify(
            "com.snapchat.android", "Adeline", "Bonjour, peux-tu regarder ceci ?",
            Notification.CATEGORY_MESSAGE))
    }

    @Test fun safeFallbackRespectsUserPreferencesAndDisabledFiltering() {
        val input = NotificationPriorityInput("com.openai.chatgpt", "ChatGPT", "Task completed",
            androidCategory = Notification.CATEGORY_MESSAGE)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(mode = NotificationFilterMode.OFF), base, 15).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                config.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 6), base, 15).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input.copy(
                personalInterest = NotificationPersonalization.Interest.WANTED),
                config, base, 15).decision)
    }

    @Test fun sendersAloneDoNotCauseDangerousClassification() {
        assertEquals(WorkNotificationKind.UNKNOWN,
            NotificationWorkPolicy.classify("com.openai.chatgpt", "ChatGPT", "Ouvrez l'application"))
        assertEquals(WorkNotificationKind.UNKNOWN,
            NotificationWorkPolicy.classify("com.github.android", "GitHub", "Nouvelle notification"))
        assertFalse(NotificationWorkPolicy.isDeveloperSource("com.snapchat.android"))
        assertTrue(NotificationWorkPolicy.isDeveloperSource("com.github.android"))
    }
}
