package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import java.util.Locale

/** The briefing priority is separate from topic classification and deletion safety. */
internal enum class NotificationPriority { NOW, TODAY, LOW }

internal data class NotificationPriorityInput(
    val packageName: String,
    val title: String,
    val body: String,
    val channelId: String? = null,
    val androidCategory: String? = null,
    val topic: String = "other",
    val predictedLabel: String? = null,
    val game: Boolean = false,
    val personalInterest: NotificationPersonalization.Interest = NotificationPersonalization.Interest.NEUTRAL,
)

/** Conservative, deterministic classification. Unknowns stay in Today, not automatically deleted. */
internal object NotificationPriorityPolicy {
    private val actionRequired = Regex(
        "(?iu)(review requested|changes requested|action required|requires (?:your )?(?:action|attention)|" +
            "approval needed|approve (?:this|the)|please review|requested your review|" +
            "waiting for (?:your|approval)|assigned to you|" +
            "merge conflict|build failed|checks? failed|pipeline failed|workflow failed|deployment failed|" +
            "failed to deploy|critical (?:error|failure)|blocked by|cannot merge|" +
            "intervention n[ée]cessaire|action (?:requise|n[ée]cessaire)|" +
            "[ée]chec (?:du|de la|des) (?:build|compilation|tests?|d[ée]ploiement)|" +
            "validation (?:requise|demand[ée]e)|revue demand[ée]e|conflit de fusion)"
    )
    private val marketing = Regex(
        "(?iu)(spotlight|suggestion d['’]ami|suggested friend|pour vous|" +
            "d[ée]couvrez (?:les |nos |un |une )?(?:contenus|offres|produits)|" +
            "anniversaire professionnel|offre sp[ée]ciale|r[ée]duction|promotion|" +
            "code promo|vente flash|invit(?:ez|e) vos amis|nouveau souvenir|" +
            "publication de |a publi[ée]|new post|follow (?:us|them)|" +
            "les (?:reels|stories) que|4 nouveaux styles d['’]image)"
    )
    private val security = Regex(
        "(?iu)(alerte de s[ée]curit[ée]|security alert|code de (?:connexion|v[ée]rification)|" +
            "2fa|mfa|authentification|suspicious (?:sign.in|activity)|" +
            "mot de passe|virement|paiement suspect|transaction refus[ée]e|" +
            "connexion inconnue|r[ée]servation confirm[ée]e)"
    )
    private val directMessages = Regex(
        "(?iu)(vous a envoy[ée] (?:un |une )?(?:message|chat)|sent you a (?:message|chat)|" +
            "nouveau (?:message|chat)|new (?:direct )?message|vous a [ée]crit|" +
            "a r[ée]pondu [àa] votre message|replied to your message)"
    )
    private val urgency = Regex("(?iu)(rendez.vous|appointment|meeting at|r[ée]union [àa]|commence dans|starts in)")
    private val systemUpdates = Regex(
        "(?iu)(mise [àa] jour (?:logicielle|du syst[èe]me|disponible)|" +
            "red[ée]marrage de votre t[ée]l[ée]phone|software update|system update)"
    )

    fun githubBot(input: NotificationPriorityInput): Boolean {
        val title = input.title.lowercase(Locale.ROOT)
        return title.contains("chatgpt-codex-connector[bot]") ||
            title.contains("github-actions[bot]") || title.contains("dependabot[bot]") ||
            title.contains("renovate[bot]") || title == "github[bot]" ||
            (input.packageName.contains("github", true) && title.endsWith("[bot]"))
    }

    fun isRoutineSystemUpdate(title: String, body: String): Boolean =
        systemUpdates.containsMatchIn("$title $body")

    fun recognizedSocialSuggestion(title: String, body: String): Boolean =
        Regex("(?iu)spotlight|suggestion d['’]ami|suggested friend|" +
            "anniversaire professionnel|new recommended post|d[ée]couvrez les contenus de spotlight")
            .containsMatchIn("$title $body")

    fun botRequiresAction(input: NotificationPriorityInput): Boolean =
        githubBot(input) && actionRequired.containsMatchIn("${input.title} ${input.body}")

    fun classify(input: NotificationPriorityInput): NotificationPriority {
        val text = "${input.title} ${input.body}"
        // Security alerts override every social/media heuristic, including mentions.
        if (security.containsMatchIn(text) || input.topic == "security")
            return NotificationPriority.NOW
        val workKind = NotificationWorkPolicy.classify(input.packageName,
            input.title, input.body, input.androidCategory)
        if (workKind != WorkNotificationKind.UNKNOWN) {
            val priority = NotificationWorkPolicy.priority(workKind)!!
            return if (input.personalInterest == NotificationPersonalization.Interest.WANTED &&
                priority == NotificationPriority.LOW) NotificationPriority.TODAY else priority
        }
        if (githubBot(input)) return when {
            botRequiresAction(input) -> NotificationPriority.NOW
            input.personalInterest == NotificationPersonalization.Interest.WANTED -> NotificationPriority.TODAY
            else -> NotificationPriority.LOW
        }
        if (input.packageName.contains("github", ignoreCase = true)) {
            return if (actionRequired.containsMatchIn(text)) NotificationPriority.NOW
                else NotificationPriority.TODAY
        }
        val socialKind = NotificationSocialPolicy.classify(input.packageName, input.title,
            input.body, input.androidCategory)
        if (socialKind != SocialNotificationKind.UNKNOWN) {
            // Explicit positive feedback may elevate a low-priority item, never a direct DM down.
            return if (input.personalInterest == NotificationPersonalization.Interest.WANTED &&
                socialKind != SocialNotificationKind.DIRECT) NotificationPriority.TODAY
            else NotificationSocialPolicy.priority(socialKind)!!
        }
        if (input.game || input.topic == "games") {
            return when (NotificationGamePolicy.classify(input.title, input.body, input.androidCategory)) {
                GameNotificationKind.INVITATION -> NotificationPriority.NOW
                GameNotificationKind.REWARD, GameNotificationKind.STREAK, GameNotificationKind.EVENT,
                GameNotificationKind.PUZZLE -> if (input.personalInterest == NotificationPersonalization.Interest.WANTED)
                    NotificationPriority.TODAY else NotificationPriority.LOW
                GameNotificationKind.UNKNOWN -> NotificationPriority.TODAY
            }
        }
        if (security.containsMatchIn(text) || input.predictedLabel == "a personal message" ||
            input.topic in setOf("security", "finance") ||
            input.androidCategory in setOf(Notification.CATEGORY_CALL,
                Notification.CATEGORY_MISSED_CALL, Notification.CATEGORY_ALARM,
                Notification.CATEGORY_EVENT, Notification.CATEGORY_REMINDER) ||
            input.topic in setOf("calendar", "tasks") || urgency.containsMatchIn(text)) {
            return NotificationPriority.NOW
        }
        if (directMessages.containsMatchIn(text)) return NotificationPriority.NOW
        if (input.personalInterest == NotificationPersonalization.Interest.WANTED)
            return NotificationPriority.TODAY
        if (marketing.containsMatchIn(text) && input.topic in setOf("social", "promotions"))
            return NotificationPriority.LOW
        if (input.androidCategory == Notification.CATEGORY_MESSAGE ||
            input.topic == "messages" && input.androidCategory != Notification.CATEGORY_EMAIL) {
            return NotificationPriority.NOW
        }
        if (input.androidCategory == Notification.CATEGORY_EMAIL) return NotificationPriority.TODAY
        if (marketing.containsMatchIn(text) || input.topic in setOf("promotions", "social") ||
            input.androidCategory == Notification.CATEGORY_PROMO ||
            input.predictedLabel in setOf(NotificationFilterPolicy.AD, NotificationFilterPolicy.SPAM) &&
                input.topic == "promotions") return NotificationPriority.LOW
        if (input.topic == "system" || systemUpdates.containsMatchIn(text)) return NotificationPriority.TODAY
        // Unrecognized email, technical and news notifications may matter. No automatic deletion.
        return NotificationPriority.TODAY
    }

    /** After classification, the user's policy is explicit: NOW stays on Android,
     * TODAY and LOW are removed from the shade and retained in the private Brief.
     * Platform restrictions and content safety are checked separately at action time.
     */
    fun automaticAction(
        input: NotificationPriorityInput,
        config: NotificationFilterConfig,
        base: NotificationFilterEvaluation,
        localHour: Int,
    ): NotificationFilterEvaluation {
        val socialKind = NotificationSocialPolicy.classify(input.packageName,
            input.title, input.body, input.androidCategory)
        if (socialKind != SocialNotificationKind.UNKNOWN) {
            if (config.mode == NotificationFilterMode.OFF) return base
            return NotificationSocialPolicy.decision(socialKind, config,
                input.personalInterest, localHour) ?: base
        }
        if (config.mode == NotificationFilterMode.OFF) return base
        if (classify(input) == NotificationPriority.NOW) {
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "À traiter maintenant")
        }
        if (input.personalInterest == NotificationPersonalization.Interest.WANTED) {
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Conserver selon vos préférences")
        }
        if (config.mode != NotificationFilterMode.PERSONALIZED ||
            config.onlyQuietHours && !config.inQuietHours(localHour)) return base
        val reason = when (classify(input)) {
            NotificationPriority.TODAY -> "Conservée dans le Brief pour aujourd'hui"
            NotificationPriority.LOW -> "Sans importance · conservée dans le Brief"
            NotificationPriority.NOW -> return NotificationFilterEvaluation(
                NotificationFilterDecision.KEEP, "À traiter maintenant")
        }
        return NotificationFilterEvaluation(NotificationFilterDecision.DISMISS, reason)
    }

}
