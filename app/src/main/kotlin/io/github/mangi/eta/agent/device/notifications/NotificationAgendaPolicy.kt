package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** A calendar notification should not interrupt unless it concerns today's plans or a change. */
internal enum class AgendaNotificationKind {
    CHANGED_OR_CANCELLED, REPLY_REQUIRED, TODAY_EVENT, FUTURE_CONFIRMED,
    SUGGESTED_EVENT, EXPIRED_REMINDER, UNKNOWN,
}

internal object NotificationAgendaPolicy {
    private val calendarPackages = setOf(
        "com.google.android.calendar", "com.samsung.android.calendar",
        "com.android.calendar", "com.microsoft.office.outlook",
        "com.google.android.gm", "com.anydo", "com.todoist",
    )
    private val calendarContext = Regex(
        "(?iu)(rendez.vous|appointment|meeting|r[ée]union|agenda|calendrier|calendar|" +
            "[ée]v[ée]nement|event|visioconf[ée]rence|invitation|rappel|reminder|" +
            "entretien|consultation|convocation|date et heure)"
    )
    private val changes = Regex(
        "(?iu)(rendez.vous|appointment|meeting|r[ée]union|[ée]v[ée]nement|event|invitation|" +
            "horaire|schedule).{0,90}(?:annul[ée]|cancelled|canceled|report[ée]|d[ée]cal[ée]|" +
            "d[ée]plac[ée]|reprogramm[ée]|rescheduled|postponed|time changed|modifi[ée]|" +
            "changement d['’]horaire|nouvel horaire)|" +
            "(?:annul[ée]|cancelled|canceled|report[ée]|d[ée]cal[ée]|d[ée]plac[ée]|" +
            "reprogramm[ée]|rescheduled|postponed|time changed|modifi[ée]|" +
            "changement d['’]horaire|nouvel horaire).{0,90}(?:rendez.vous|appointment|meeting|" +
            "r[ée]union|[ée]v[ée]nement|event|invitation|horaire|schedule)"
    )
    private val reply = Regex(
        "(?iu)(r[ée]pond(?:re|ez) [àa] (?:l['’])?invitation|" +
            "(?:invitation|meeting|r[ée]union|rendez.vous).{0,45}(?:r[ée]ponse (?:attendue|requise)|" +
            "[àa] (?:accepter|refuser|confirmer)|RSVP)|" +
            "(?:invitation (?:n[ée]cessitant|demandant) une r[ée]ponse|" +
            "r[ée]ponse (?:requise|attendue) (?:pour|[àa]) (?:l['’])?invitation|" +
            "RSVP|response required|awaiting (?:your )?response|accept or decline|" +
            "please respond to (?:the )?invitation))"
    )
    private val suggestion = Regex(
        "(?iu)([ée]v[ée]nements? (?:sugg[ée]r[ée]s?|recommand[ée]s?|[àa] d[ée]couvrir)|" +
            "(?:suggested|recommended|discover|nearby) events?|" +
            "[ée]v[ée]nements? (?:pr[èe]s de chez vous|qui pourraient vous int[ée]resser)|" +
            "suggestions? de sorties|sorties? recommand[ée]es?)"
    )
    private val expired = Regex(
        "(?iu)(rappel (?:expir[ée]|p[ée]rim[ée]|ancien)|" +
            "(?:rendez.vous|[ée]v[ée]nement|event|meeting) (?:pass[ée]|termin[ée]|expir[ée])|" +
            "(?:event|reminder) (?:expired|already ended|has passed)|" +
            "missed (?:calendar )?(?:event|reminder))"
    )
    private val todayWords = Regex(
        "(?iu)(aujourd['’]hui|ce matin|cet apr[èe]s.midi|ce soir|today|this morning|" +
            "this afternoon|this evening|(?:commence|d[ée]bute|starts?) dans (?:\\d{1,3}) " +
            "(?:minutes?|min|heures?|hours?|hrs?)|(?:in|dans) (?:\\d{1,3}) (?:mins?|minutes?|hours?|hrs?))"
    )
    private val futureWords = Regex(
        "(?iu)(demain|apr[èe]s.demain|tomorrow|day after tomorrow|" +
            "(?:la |semaine )?semaine prochaine|next week|next month|mois prochain|" +
            "(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche) prochain|" +
            "next (?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|" +
            "(?:dans|in) (?:[2-9]|[1-9]\\d+) (?:jours?|days?|semaines?|weeks?))"
    )
    private val isoDate = Regex("\\b(20\\d{2}-[01]\\d-[0-3]\\d)\\b")
    private val frenchDate = Regex("\\b([0-3]\\d/[01]\\d/20\\d{2})\\b")
    private val frenchFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.FRANCE)

    private fun explicitDate(text: String): LocalDate? {
        for (token in isoDate.findAll(text)) {
            try { return LocalDate.parse(token.groupValues[1]) } catch (_: DateTimeParseException) { }
        }
        for (token in frenchDate.findAll(text)) {
            try { return LocalDate.parse(token.groupValues[1], frenchFormat) }
            catch (_: DateTimeParseException) { }
        }
        return null
    }

    fun classify(packageName: String, title: String, body: String,
                 androidCategory: String? = null, topic: String = "other",
                 today: LocalDate = LocalDate.now()): AgendaNotificationKind {
        val text = "$title $body"
        // An emailed promotion mentioning the word 'event' is not a calendar invitation.
        val calendarCategory = androidCategory in setOf(Notification.CATEGORY_EVENT,
            Notification.CATEGORY_REMINDER)
        val recognized = topic in setOf("calendar", "tasks") || calendarCategory ||
            packageName in calendarPackages && calendarContext.containsMatchIn(text)
        if (!recognized) return AgendaNotificationKind.UNKNOWN
        if (changes.containsMatchIn(text)) return AgendaNotificationKind.CHANGED_OR_CANCELLED
        if (reply.containsMatchIn(text)) return AgendaNotificationKind.REPLY_REQUIRED
        if (suggestion.containsMatchIn(text)) return AgendaNotificationKind.SUGGESTED_EVENT
        if (expired.containsMatchIn(text)) return AgendaNotificationKind.EXPIRED_REMINDER
        if (todayWords.containsMatchIn(text)) return AgendaNotificationKind.TODAY_EVENT
        val date = explicitDate(text)
        if (date != null) return when {
            date.isAfter(today) -> AgendaNotificationKind.FUTURE_CONFIRMED
            date.isEqual(today) -> AgendaNotificationKind.TODAY_EVENT
            // A past date alone doesn't prove an alert is stale or safe to dismiss.
            else -> AgendaNotificationKind.UNKNOWN
        }
        if (futureWords.containsMatchIn(text)) return AgendaNotificationKind.FUTURE_CONFIRMED
        // Unknown timing must not downgrade a potentially imminent appointment.
        return AgendaNotificationKind.UNKNOWN
    }

    fun priority(kind: AgendaNotificationKind): NotificationPriority? = when (kind) {
        AgendaNotificationKind.CHANGED_OR_CANCELLED, AgendaNotificationKind.REPLY_REQUIRED,
        AgendaNotificationKind.TODAY_EVENT -> NotificationPriority.NOW
        AgendaNotificationKind.FUTURE_CONFIRMED -> NotificationPriority.TODAY
        AgendaNotificationKind.SUGGESTED_EVENT, AgendaNotificationKind.EXPIRED_REMINDER ->
            NotificationPriority.LOW
        AgendaNotificationKind.UNKNOWN -> null
    }
}
