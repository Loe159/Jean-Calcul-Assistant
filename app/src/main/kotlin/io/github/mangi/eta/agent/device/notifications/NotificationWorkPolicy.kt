package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import java.util.Locale

/** Local rules for the seven explicitly validated work, GitHub and AI cases. */
internal enum class WorkNotificationKind {
    ACTION_REQUIRED, CI_FAILED, COLLEAGUE_MESSAGE,
    AGENT_COMPLETED, BUILD_SUCCEEDED, BOT_COMMENT, BOT_ACTIVITY, UNKNOWN,
}

internal object NotificationWorkPolicy {
    private val developerPackages = setOf(
        "com.github.android", "com.openai.chatgpt", "com.microsoft.teams",
        "com.Slack", "com.slack", "com.microsoft.office.outlook", "com.google.android.gm",
    )
    private val emailPackages = setOf("com.microsoft.office.outlook", "com.google.android.gm")
    private val sourceHint = Regex("(?iu)(github|gitlab|codex|chatgpt|pull.request|\\bPR\\s*[#!]?[0-9]+|pipeline|workflow|CI/CD)")
    private val botMarker = Regex("(?iu)(\\[bot\\]|github.actions|dependabot|renovate|automated bot|bot automatique)")
    private val actionRequired = Regex(
        "(?iu)(review requested|requested (?:your|a) review|changes requested|" +
            "approval (?:required|needed)|approve (?:this|the)|please review|" +
            "requires? (?:your )?(?:action|attention|approval)|action required|" +
            "assigned to you|waiting for (?:your|approval)|merge conflict|cannot merge|" +
            "intervention n[ée]cessaire|action (?:requise|n[ée]cessaire)|" +
            "validation (?:requise|demand[ée]e)|revue demand[ée]e|" +
            "conflit de fusion|modifications demand[ée]es|bloqu[ée]e? par)"
    )
    private val failed = Regex(
        "(?iu)(build (?:has )?failed|build failure|checks? failed|failed checks?|" +
            "pipeline (?:has )?failed|workflow (?:has )?failed|deployment failed|" +
            "failed to deploy|tests? failed|test failure|CI failed|" +
            "[ée]chec (?:du|de la|des) (?:build|compilation|tests?|d[ée]ploiement|pipeline)|" +
            "compilation [ée]chou[ée]e|d[ée]ploiement [ée]chou[ée]|tests? en [ée]chec)"
    )
    private val success = Regex(
        "(?iu)(build (?:succeeded|successful|passed)|build completed successfully|" +
            "checks? passed|all checks passed|workflow (?:succeeded|passed)|" +
            "pipeline (?:succeeded|passed)|deployment (?:succeeded|completed)|" +
            "deploy(?:ment)? successful|successfully deployed|" +
            "compilation r[ée]ussie|tests? r[ée]ussis?|d[ée]ploiement termin[ée]|" +
            "build termin[ée] avec succ[èe]s)"
    )
    private val agentCompleted = Regex(
        "(?iu)(task (?:is )?(?:completed|finished|ready)|agent (?:has )?(?:completed|finished)|" +
            "codex (?:has )?(?:completed|finished)|chatgpt (?:has )?(?:completed|finished)|" +
            "your (?:task|result|answer) is ready|" +
            "t[âa]che (?:termin[ée]e|achev[ée]e)|r[ée]sultat (?:disponible|pr[êe]t)|" +
            "agent a termin[ée]|analyse termin[ée]e)"
    )
    private val comment = Regex(
        "(?iu)(commented on|added a comment|new comment|left a comment|" +
            "a ajout[ée] un commentaire|nouveau commentaire|a comment[ée]|" +
            "commentaire automatique|automatic comment|bot comment)"
    )
    private val directColleague = Regex(
        "(?iu)(peux.tu|pourrais.tu|pouvez.vous|pourriez.vous|" +
            "tu peux|aurais.tu|j['’]ai une question|j['’]aurais une question|" +
            "merci de (?:me |nous )?(?:r[ée]pondre|confirmer|v[ée]rifier|faire|envoyer)|" +
            "can you (?:please )?|could you (?:please )?|please (?:reply|confirm|check|send)|" +
            "let me know|j['’]attends ton retour|j['’]attends votre retour)"
    )
    private val bulkMail = Regex(
        "(?iu)(unsubscribe|se d[ée]sabonner|newsletter|no.reply|noreply|" +
            "offre sp[ée]ciale|promotion|webinaire|vente flash|code promo|annonce sponsoris[ée]e)"
    )

    fun isDeveloperSource(pkg: String): Boolean = pkg in developerPackages ||
        pkg.contains("github", ignoreCase = true) || pkg.contains("gitlab", ignoreCase = true)

    fun classify(pkg: String, title: String, body: String,
                 androidCategory: String? = null): WorkNotificationKind {
        val text = "$title $body".lowercase(Locale.ROOT)
        // Outlook and Gmail do not always fill Android's category field.
        val isEmail = pkg in emailPackages && androidCategory in setOf(Notification.CATEGORY_EMAIL, null)
        val devContext = sourceHint.containsMatchIn(text) ||
            pkg.contains("github", ignoreCase = true) || pkg.contains("gitlab", ignoreCase = true)
        val isBot = botMarker.containsMatchIn(text)

        if ((devContext || isBot) && actionRequired.containsMatchIn(text))
            return WorkNotificationKind.ACTION_REQUIRED
        if ((devContext || Regex("(?iu)\\bCI\\b").containsMatchIn(text)) &&
            failed.containsMatchIn(text)) return WorkNotificationKind.CI_FAILED
        if (pkg == "com.openai.chatgpt" && agentCompleted.containsMatchIn(text) ||
            devContext && agentCompleted.containsMatchIn(text)) return WorkNotificationKind.AGENT_COMPLETED
        if (devContext && success.containsMatchIn(text)) return WorkNotificationKind.BUILD_SUCCEEDED
        if (isBot && devContext && comment.containsMatchIn(text)) return WorkNotificationKind.BOT_COMMENT
        if (isBot && devContext) return WorkNotificationKind.BOT_ACTIVITY

        // Sender identity alone isn't enough: a company mailing list may use a human name.
        // Only an addressed personal request, and never a marketing email, is immediate.
        if (isEmail && !bulkMail.containsMatchIn(text) &&
            directColleague.containsMatchIn(body)) return WorkNotificationKind.COLLEAGUE_MESSAGE
        // A direct, person-to-person message in a work chat deserves immediate attention.
        if (pkg in setOf("com.microsoft.teams", "com.Slack", "com.slack") &&
            androidCategory == Notification.CATEGORY_MESSAGE &&
            !bulkMail.containsMatchIn(text)) return WorkNotificationKind.COLLEAGUE_MESSAGE
        return WorkNotificationKind.UNKNOWN
    }

    fun priority(kind: WorkNotificationKind): NotificationPriority? = when (kind) {
        WorkNotificationKind.ACTION_REQUIRED, WorkNotificationKind.CI_FAILED,
        WorkNotificationKind.COLLEAGUE_MESSAGE -> NotificationPriority.NOW
        WorkNotificationKind.AGENT_COMPLETED, WorkNotificationKind.BUILD_SUCCEEDED -> NotificationPriority.TODAY
        WorkNotificationKind.BOT_COMMENT, WorkNotificationKind.BOT_ACTIVITY -> NotificationPriority.LOW
        WorkNotificationKind.UNKNOWN -> null
    }
}
