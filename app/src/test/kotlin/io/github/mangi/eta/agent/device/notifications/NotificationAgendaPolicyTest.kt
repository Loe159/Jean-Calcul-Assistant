package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAgendaPolicyTest {
    private val date = LocalDate.of(2026, 11, 18)
    private val cfg = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.DISMISS)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI indéterminé")

    private fun item(text: String, category: String? = Notification.CATEGORY_EVENT,
                     topic: String = "calendar", pkg: String = "com.google.android.calendar") =
        NotificationPriorityInput(pkg, "Agenda", text, androidCategory = category, topic = topic)

    @Test fun immediateEventsChangesAndReplyRequests() {
        val fixtures = listOf(
            "Rendez-vous aujourd'hui à 16h" to AgendaNotificationKind.TODAY_EVENT,
            "Réunion annulée demain" to AgendaNotificationKind.CHANGED_OR_CANCELLED,
            "Rendez-vous reporté à demain" to AgendaNotificationKind.CHANGED_OR_CANCELLED,
            "Invitation nécessitant une réponse" to AgendaNotificationKind.REPLY_REQUIRED,
            "Meeting at 2026-11-18 14:00" to AgendaNotificationKind.TODAY_EVENT,
        )
        fixtures.forEach { (body, expected) ->
            assertEquals(body, expected,
                NotificationAgendaPolicy.classify("com.google.android.calendar", "Agenda",
                    body, Notification.CATEGORY_EVENT, "calendar", date))
            if (body.contains("2026-11-18") || body.contains("aujourd'hui")) {
                assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(item(body)))
                assertEquals(NotificationFilterDecision.KEEP,
                    NotificationPriorityPolicy.automaticAction(item(body), cfg, base, 14).decision)
            }
        }
    }

    @Test fun deferredConfirmedAppointments() {
        val fixtures = listOf(
            "Rendez-vous confirmé le 2026-11-20",
            "Réunion confirmée le 20/11/2026",
            "Rendez-vous prévu la semaine prochaine",
            "Entretien demain à 09:30",
        )
        fixtures.forEach { body ->
            assertEquals(body, AgendaNotificationKind.FUTURE_CONFIRMED,
                NotificationAgendaPolicy.classify("com.google.android.calendar", "Agenda",
                    body, Notification.CATEGORY_EVENT, "calendar", date))
        }
        val input = item("Rendez-vous confirmé demain")
        assertEquals(NotificationPriority.TODAY, NotificationPriorityPolicy.classify(input))
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationPriorityPolicy.automaticAction(input, cfg, base, 14).decision)
    }

    @Test fun unwantedSuggestionsAndExplicitlyExpiredReminders() {
        val fixtures = listOf(
            "Événements suggérés près de chez vous" to AgendaNotificationKind.SUGGESTED_EVENT,
            "Recommended events for your calendar" to AgendaNotificationKind.SUGGESTED_EVENT,
            "Rappel expiré : faire le point" to AgendaNotificationKind.EXPIRED_REMINDER,
        )
        fixtures.forEach { (body, expected) ->
            assertEquals(body, expected,
                NotificationAgendaPolicy.classify("com.google.android.calendar", "Agenda",
                    body, Notification.CATEGORY_REMINDER, "calendar", date))
        }
        val input = item("Événements suggérés près de chez vous")
        assertEquals(NotificationPriority.LOW, NotificationPriorityPolicy.classify(input))
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationPriorityPolicy.automaticAction(input, cfg, base, 14).decision)
    }

    @Test fun ambiguousOrUnrelatedNotificationsAreNeverDowngraded() {
        assertEquals(AgendaNotificationKind.UNKNOWN,
            NotificationAgendaPolicy.classify("com.google.android.calendar", "Agenda",
                "Rendez-vous important", Notification.CATEGORY_EVENT, "calendar", date))
        assertEquals(AgendaNotificationKind.UNKNOWN,
            NotificationAgendaPolicy.classify("com.google.android.calendar", "Agenda",
                "Reminder for meeting 2026-11-10", Notification.CATEGORY_REMINDER, "calendar", date))
        assertEquals(AgendaNotificationKind.UNKNOWN,
            NotificationAgendaPolicy.classify("com.example.news", "Offre",
                "Événements suggérés", Notification.CATEGORY_PROMO, "other", date))
        assertEquals(NotificationPriority.NOW, NotificationPriorityPolicy.classify(
            item("Rendez-vous important")))
    }

    @Test fun explicitPreferencesAndQuietHoursAreRespected() {
        val input = item("Rendez-vous confirmé demain")
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input, cfg.copy(mode = NotificationFilterMode.OFF),
                base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input,
                cfg.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 7),
                base, 14).decision)
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationPriorityPolicy.automaticAction(input.copy(personalInterest =
                NotificationPersonalization.Interest.WANTED), cfg, base, 14).decision)
    }
}
