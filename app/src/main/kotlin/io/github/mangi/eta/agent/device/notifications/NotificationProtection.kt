package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

/** Conservative platform guards. False negatives are preferable to lost alerts. */
internal object NotificationProtection {
    private val protectedCategory = setOf(
        Notification.CATEGORY_CALL, Notification.CATEGORY_MESSAGE,
        Notification.CATEGORY_EMAIL, Notification.CATEGORY_ALARM,
        Notification.CATEGORY_EVENT, Notification.CATEGORY_REMINDER,
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_SERVICE, Notification.CATEGORY_ERROR,
        Notification.CATEGORY_PROGRESS, Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_LOCATION_SHARING, Notification.CATEGORY_MISSED_CALL,
    )

    // Protect OTP, authentication, payments, deliveries, medical and appointments
    // even if a third-party app sets category=null.
    private val sensitive = Regex(
        "(?iu)(\\b(otp|2fa|mfa|code de v[eé]rification|code de connexion|code de s[eé]curit[eé]|authentification|authentication|connexion|" +
            "sécurit[eé]|security|password|mot de passe|virement|bank|banque|" +
            "paiement|payment|facture|invoice|transaction|carte bancaire|" +
            "livraison|livr[eé]|delivery|colis|shipment|rendez.vous|appointment|" +
            "urgence|urgent|alerte|alert|medecin|m[eé]decin|doctolib|sant[eé]|health|" +
            "meeting|r[eé]union|confirmation|ticket|billet|reservation|r[eé]servation)\\b|" +
            "\\b[0-9]{5,8}\\b)"
    )
    // This is deliberately narrower than the generic legacy protection:
    // a routine "Connexion au serveur" or "Information" must be dismissible,
    // while OTP, finance, medical and account-security alerts remain guarded.
    private val stronglySensitive = Regex(
        "(?iu)(\\b(?:otp|2fa|mfa|code de (?:v[ée]rification|connexion|s[ée]curit[ée])|" +
            "authentification|authentication|s[ée]curit[ée]|security|password|mot de passe|" +
            "paiement|payment|virement|transaction|carte bancaire|" +
            "livraison|colis|delivery|rendez.vous|appointment|" +
            "urgence|urgent|m[ée]decin|sant[ée]|health|doctolib|" +
            "r[ée]servation|reservation)\\b|\\b[0-9]{5,8}\\b)"
    )

    // Appointment words are allowed only for clearly deferred agenda updates;
    // security, payment, medical and authentication warnings never bypass protection.
    private val criticalSensitive = Regex(
        "(?iu)(\\b(?:otp|2fa|mfa|code de (?:v[ée]rification|connexion|s[ée]curit[ée])|" +
            "authentification|authentication|s[ée]curit[ée]|security|password|mot de passe|" +
            "paiement|payment|virement|transaction|carte bancaire|" +
            "livraison|colis|delivery|urgence|urgent|m[ée]decin|sant[ée]|health|doctolib|" +
            "r[ée]servation|reservation)\\b|\\b[0-9]{5,8}\\b)"
    )

    private val protectedPackages = Regex(
        "(?i)(^android$|^com\\.android\\.|^com\\.google\\.android\\.gms$|" +
            "^com\\.samsung\\.android\\.|^com\\.sec\\.android\\.|" +
            "bank|banque|wallet|pay|auth|calendar|mail|sms|message|" +
            "signal|whatsapp|telegram|discord|outlook|gmail|health|medical|doctolib)"
    )

    fun safe(context: Context, sbn: StatusBarNotification,
             allowRoutineBotEmail: Boolean = false, allowSocialSuggestion: Boolean = false): Boolean {
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        // A specifically identified GitHub bot notification can be cleared if it
        // requests no action. Other mail (including human messages) stays protected.
        val routineBot = allowRoutineBotEmail &&
            sbn.packageName in setOf("com.microsoft.office.outlook", "com.google.android.gm") &&
            n.category == Notification.CATEGORY_EMAIL &&
            NotificationPriorityPolicy.githubBot(
                NotificationPriorityInput(sbn.packageName, title, body, n.channelId, n.category)) &&
            !NotificationPriorityPolicy.botRequiresAction(
                NotificationPriorityInput(sbn.packageName, title, body, n.channelId, n.category))
        val socialSuggestion = allowSocialSuggestion &&
            sbn.packageName in setOf("com.snapchat.android", "com.linkedin.android", "com.instagram.android") &&
            n.category == Notification.CATEGORY_MESSAGE &&
            NotificationPriorityPolicy.recognizedSocialSuggestion(title, body)
        if (!sbn.isClearable ||
            sbn.packageName == context.packageName ||
            (!routineBot && protectedPackages.containsMatchIn(sbn.packageName)) ||
            (n.category in protectedCategory &&
                !(routineBot && n.category == Notification.CATEGORY_EMAIL) &&
                !(socialSuggestion && n.category == Notification.CATEGORY_MESSAGE)) ||
            n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or
                Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_NO_CLEAR) != 0 ||
            n.shortcutId != null ||
            n.extras.containsKey(Notification.EXTRA_MESSAGES) ||
            n.extras.containsKey(Notification.EXTRA_PEOPLE_LIST) ||
            n.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true
        ) return false
        val appInfo = runCatching {
            context.packageManager.getApplicationInfo(sbn.packageName, 0)
        }.getOrNull() ?: return false // Unknown app: not safe to filter.
        if (!routineBot && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0)) return false
        val text = body
        val bigText = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if ((title + text).trim().length < 8) return false
        if (sensitive.containsMatchIn("$title $text $bigText")) return false
        return true
    }

    /** Priority-tier aware clearance, rather than an unconditional app/category block.
     * Calls, conversations, authentication and non-clearable service notifications
     * are always retained, independently of model output and topic overrides.
     */
    fun safeForBrief(context: Context, sbn: StatusBarNotification,
                     input: NotificationPriorityInput): Boolean {
        if (NotificationPriorityPolicy.classify(input) == NotificationPriority.NOW) return false
        val n = sbn.notification
        val pkg = sbn.packageName
        val nonUrgentEmail = n.category == Notification.CATEGORY_EMAIL
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val agendaKind = NotificationAgendaPolicy.classify(pkg, title, text, n.category, input.topic)
        val deferredAgenda = agendaKind in setOf(
            AgendaNotificationKind.FUTURE_CONFIRMED, AgendaNotificationKind.SUGGESTED_EVENT,
            AgendaNotificationKind.EXPIRED_REMINDER)
        if (!sbn.isClearable || pkg == context.packageName ||
            n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or
                Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_NO_CLEAR) != 0 ||
            (n.shortcutId != null && !nonUrgentEmail) ||
            n.extras.containsKey(Notification.EXTRA_MESSAGES) ||
            (n.extras.containsKey(Notification.EXTRA_PEOPLE_LIST) && !nonUrgentEmail) ||
            (!nonUrgentEmail && n.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true)
        ) return false
        if (n.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_MISSED_CALL,
                Notification.CATEGORY_ALARM, Notification.CATEGORY_TRANSPORT,
                Notification.CATEGORY_ERROR, Notification.CATEGORY_PROGRESS,
                Notification.CATEGORY_NAVIGATION, Notification.CATEGORY_LOCATION_SHARING)) return false
        if (n.category in setOf(Notification.CATEGORY_EVENT, Notification.CATEGORY_REMINDER) &&
            !deferredAgenda) return false
        if ((title + text).trim().length < 8 ||
            (if (deferredAgenda) criticalSensitive.containsMatchIn("$title $text $bigText")
                else stronglySensitive.containsMatchIn("$title $text $bigText"))) return false
        // Some social platforms mark mentions, friend requests, and suggestions
        // CATEGORY_MESSAGE. Clear only explicitly recognized non-DM social events.
        // MessagingStyle, people and inline replies remain blocked above.
        if (n.category == Notification.CATEGORY_MESSAGE) {
            val socialKind = NotificationSocialPolicy.classify(pkg, title, text, n.category)
            val workKind = NotificationWorkPolicy.classify(pkg, title, text, n.category)
            val routineWorkUpdate = workKind in setOf(WorkNotificationKind.AGENT_COMPLETED,
                WorkNotificationKind.BUILD_SUCCEEDED, WorkNotificationKind.BOT_COMMENT,
                WorkNotificationKind.BOT_ACTIVITY) &&
                (pkg == "com.openai.chatgpt" || pkg.contains("github", ignoreCase = true))
            if (!routineWorkUpdate && socialKind !in setOf(SocialNotificationKind.MENTION,
                    SocialNotificationKind.INVITATION, SocialNotificationKind.ACTIVITY,
                    SocialNotificationKind.SUGGESTION, SocialNotificationKind.POST)) return false
        }
        val appInfo = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }
            .getOrNull() ?: return false
        val privileged = protectedPackages.containsMatchIn(pkg) ||
            appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (privileged) {
            val email = pkg in setOf("com.microsoft.office.outlook", "com.google.android.gm") &&
                n.category == Notification.CATEGORY_EMAIL
            // No blanket removal of all privileged Android or Samsung notifications.
            // Only an unambiguous software update, or a non-urgent email, may pass.
            val update = NotificationPriorityPolicy.isRoutineSystemUpdate(title, text) &&
                n.category in setOf(Notification.CATEGORY_SYSTEM, Notification.CATEGORY_SERVICE, null)
            val calendar = deferredAgenda && pkg in setOf(
                "com.google.android.calendar", "com.samsung.android.calendar",
                "com.android.calendar", "com.microsoft.office.outlook", "com.google.android.gm")
            if (!email && !update && !calendar) return false
        }
        // Clearable third-party service updates are eligible. Privileged Android
        // and Samsung system packages still require a known, routine update.
        return true
    }

    fun text(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return "$title. $body".trim().take(512)
    }

    /** Ephemeral equality token; never log, store, or send the text. */
    fun fingerprint(sbn: StatusBarNotification): String {
        val n = sbn.notification
        val value = listOf(
            sbn.key, sbn.packageName, sbn.id.toString(), sbn.tag.orEmpty(),
            sbn.postTime.toString(), n.`when`.toString(),
            n.flags.toString(), n.category.orEmpty(), n.group.orEmpty(),
            n.channelId.orEmpty(),
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            n.actions?.size.toString(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
