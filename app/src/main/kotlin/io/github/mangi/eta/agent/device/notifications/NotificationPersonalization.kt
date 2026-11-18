package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import java.security.MessageDigest
import java.util.Locale

/** Topic prediction is separate from the NLI interest/deletion decision. No second ONNX pass. */
internal object NotificationTopics {
    val titles = linkedMapOf(
        "messages" to "Messages et conversations",
        "work" to "Travail",
        "calendar" to "Rendez-vous et agenda",
        "tasks" to "Tâches et rappels",
        "finance" to "Finances et banques",
        "shopping" to "Achats et livraisons",
        "news" to "Actualités et informations",
        "promotions" to "Promotions et publicités",
        "social" to "Réseaux sociaux",
        "games" to "Jeux",
        "system" to "Système",
        "other" to "Autres",
    )

    fun classify(pkg: String, channel: String?, androidCategory: String?, title: String,
                 body: String, nliLabel: String?, isGame: Boolean = false): String {
        val s = "$title $body".lowercase(Locale.ROOT)
        if (isGame) {
            val kind = NotificationGamePolicy.classify(title, body, androidCategory)
            return if (androidCategory == Notification.CATEGORY_MESSAGE && kind != GameNotificationKind.INVITATION)
                "messages" else "games"
        }
        if (androidCategory == Notification.CATEGORY_MESSAGE ||
            androidCategory == Notification.CATEGORY_EMAIL ||
            androidCategory == Notification.CATEGORY_MISSED_CALL ||
            nliLabel == "a personal message") return "messages"
        if (androidCategory == Notification.CATEGORY_CALL) return "messages"
        if (androidCategory == Notification.CATEGORY_EVENT ||
            androidCategory == Notification.CATEGORY_ALARM ||
            nliLabel == "an appointment reminder" ||
            Regex("(?i)rendez.vous|appointment|meeting|réunion|agenda").containsMatchIn(s)) return "calendar"
        if (androidCategory == Notification.CATEGORY_REMINDER ||
            Regex("(?i)rappel|todo|to.do|task|tâche|deadline").containsMatchIn(s)) return "tasks"
        if (nliLabel == "a security alert" || androidCategory == Notification.CATEGORY_SYSTEM ||
            androidCategory == Notification.CATEGORY_SERVICE) return "system"
        if (Regex("(?i)banque|bank|virement|facture|invoice|paiement|payment|transaction|solde|budget").containsMatchIn(s)) return "finance"
        if (nliLabel == "a delivery update" ||
            Regex("(?i)livraison|livré|colis|delivery|shipment|commande|order shipped|expédié").containsMatchIn(s)) return "shopping"
        if (nliLabel == NotificationFilterPolicy.AD || nliLabel == NotificationFilterPolicy.SPAM ||
            Regex("(?i)promotion|soldes|réduction|code promo|offre spéciale|% de remise").containsMatchIn(s)) return "promotions"
        if (Regex("(?i)linkedin|entreprise|projet|collègue|client|workspace|travail").containsMatchIn(s) &&
            (pkg.contains("linkedin") || channel?.contains("work", true) == true)) return "work"
        if (androidCategory == Notification.CATEGORY_SOCIAL ||
            Regex("(?i)like|follow|abonné|commentaire|story|publication").containsMatchIn(s)) return "social"
        if (androidCategory == Notification.CATEGORY_PROMO) return "promotions"
        if (Regex("(?i)actualités|breaking news|journal|article|information").containsMatchIn(s)) return "news"
        return "other"
    }
}

internal enum class NotificationUserFeedback(val weight: Double, val wanted: Boolean?) {
    INTERESTING(4.0, true), UNWANTED(4.0, false),
    OPENED(1.0, true), ARCHIVED(0.35, false), RESTORED(2.0, true),
    WRONG_REMOVAL(6.0, true), CATEGORY_CORRECTED(0.0, null),
}

/** Persist hashed word features only; never persist additional sensitive text. */
internal object NotificationPersonalization {
    const val VERSION = 1
    const val MIN_FEEDBACKS = 3

    fun features(pkg: String, channel: String?, label: String?, text: String): List<String> = buildList {
        add("package:$pkg")
        if (!channel.isNullOrBlank()) add("app-channel:$pkg|$channel")
        if (!label.isNullOrBlank()) {
            add("app-label:$pkg|$label")
            add("label:$label")
        }
        val words = Regex("[\\p{L}]{4,}").findAll(text.lowercase(Locale.ROOT))
            .map { it.value }.distinct().take(8)
        words.forEach { add("token:" + sha(it).take(16)) }
    }

    private fun sha(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    data class Evidence(val wanted: Double, val unwanted: Double, val examples: Int) {
        val unwantedRatio: Double get() = (unwanted + 1) / (wanted + unwanted + 2)
    }
    enum class Interest { NEUTRAL, WANTED, UNWANTED }

    /** Single most specific sufficiently trained feature prevents correlated votes from inflating evidence. */
    fun interest(evidence: List<Pair<String, Evidence>>, minExamples: Int = MIN_FEEDBACKS): Interest {
        val qualified = evidence.filter { (_, e) -> e.examples >= minExamples }
        val best = qualified.maxWithOrNull(compareBy<Pair<String, Evidence>> {
            when {
                it.first.startsWith("app-channel:") -> 5
                it.first.startsWith("app-label:") -> 4
                it.first.startsWith("token:") -> 3
                it.first.startsWith("package:") -> 2
                else -> 1
            }
        }.thenBy { it.second.examples })?.second ?: return Interest.NEUTRAL
        return when {
            best.wanted >= 2.0 && best.unwantedRatio <= .30 -> Interest.WANTED
            best.unwanted >= 3.0 && best.unwantedRatio >= .80 -> Interest.UNWANTED
            else -> Interest.NEUTRAL
        }
    }

    /** Learned negatives can only strengthen a promotional/spam classification, not delete conversations. */
    fun safeDecision(base: NotificationFilterEvaluation, interest: Interest, label: String,
                     config: NotificationFilterConfig): NotificationFilterEvaluation = when {
        interest == Interest.WANTED -> NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Préférence personnelle : conserver")
        interest == Interest.UNWANTED && config.mode == NotificationFilterMode.PERSONALIZED &&
            label in setOf(NotificationFilterPolicy.AD, NotificationFilterPolicy.SPAM) &&
            config.labelRules.any { it.label == label && it.enabled } &&
            (!config.onlyQuietHours || base.reason != "Hors plage horaire") &&
            base.decision == NotificationFilterDecision.KEEP &&
            base.reason.startsWith("Score insuffisant") -> {
            val action = when (config.action) {
                NotificationFilterAction.DISMISS -> NotificationFilterDecision.DISMISS
                NotificationFilterAction.SNOOZE_1H -> NotificationFilterDecision.SNOOZE_1H
                NotificationFilterAction.SNOOZE_24H -> NotificationFilterDecision.SNOOZE_24H
            }
            NotificationFilterEvaluation(action, "Publicité confirmée par les préférences locales")
        }
        else -> base
    }
}
