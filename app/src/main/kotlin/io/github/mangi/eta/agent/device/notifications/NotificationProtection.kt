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
    private val protectedPackages = Regex(
        "(?i)(^android$|^com\\.android\\.|^com\\.google\\.android\\.gms$|" +
            "^com\\.samsung\\.android\\.|^com\\.sec\\.android\\.|" +
            "bank|banque|wallet|pay|auth|calendar|mail|sms|message|" +
            "signal|whatsapp|telegram|discord|outlook|gmail|health|medical|doctolib)"
    )

    fun safe(context: Context, sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (!sbn.isClearable ||
            sbn.packageName == context.packageName ||
            protectedPackages.containsMatchIn(sbn.packageName) ||
            n.category in protectedCategory ||
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
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
            appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) return false
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if ((title + text).trim().length < 8) return false
        if (sensitive.containsMatchIn("$title $text $bigText")) return false
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
