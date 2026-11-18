package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import android.content.Context
import java.util.Locale

/** High precision rules: a direct conversation always wins over an incidental social keyword. */
internal enum class SocialNotificationKind { DIRECT, MENTION, INVITATION, ACTIVITY, SUGGESTION, POST, UNKNOWN }

internal object NotificationSocialPolicy {
    private val packages = setOf(
        "com.snapchat.android", "com.instagram.android", "com.facebook.katana",
        "com.facebook.orca", "com.linkedin.android", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.strava", "com.discord",
    )
    fun isSocialApp(pkg: String): Boolean = pkg in packages

    private val direct = Regex(
        "(?iu)(vous a envoy[ée] (?:un |une )?(?:message|chat|dm)|" +
            "sent you (?:a |an )?(?:message|chat|dm)|(?:nouveau|new) (?:direct )?(?:message|chat|dm)|" +
            "vous a [ée]crit|(?:vous|t['’]|te) a r[ée]pondu (?:en priv[ée]|[àa] votre message)|" +
            "replied to your (?:message|dm)|message priv[ée]|direct message)"
    )
    private val typing = Regex(
        "(?iu)(est en train d['’][ée]crire|is typing|typing[.]{2,}|en train de saisir)"
    )
    private val suggestion = Regex(
        "(?iu)(spotlight|(?:suggestion|suggested|suggestions?) (?:d['’]amis?|friends?|de comptes?|accounts?)|" +
            "(?:comptes?|accounts?) (?:recommand[ée]s?|suggested)|" +
            "(?:posts?|publications?|vid[ée]os?|reels?|stories|contenus?) (?:recommand[ée]s?|suggested|pour vous)|" +
            "recommended for you|you might (?:like|know)|d[ée]couvr(?:ez|ir) (?:spotlight|ces contenus))"
    )
    private val mention = Regex(
        "(?iu)(vous a (?:mentionn[ée]|identifi[ée]|tagu[ée])|t['’]a (?:mentionn[ée]|identifi[ée]|tagu[ée])|" +
            "mentioned you|tagged you|you were (?:mentioned|tagged)|vous a cit[ée])"
    )
    private val invitation = Regex(
        "(?iu)(vous a (?:envoy[ée] (?:une )?demande d['’]ami|invit[ée] (?:[àa]|dans|sur))|" +
            "(?:demande d['’]ami|demande de connexion|invitation [àa] rejoindre|" +
            "friend request|connection request|invited you to (?:join|connect)|" +
            "wants to (?:add|follow|connect with) you|invitation de groupe))"
    )
    private val activity = Regex(
        "(?iu)(liked your|a aim[ée] votre|a r[ée]agi [àa]|reacted to|a comment[ée]|" +
            "commented on|anniversaire professionnel|work anniversary|" +
            "a (?:commenc[ée] [àa] vous suivre|consult[ée] votre profil)|" +
            "started following you|viewed your profile|nouveau follower|new follower)"
    )
    private val post = Regex(
        "(?iu)(nouvelle publication|new post|a publi[ée]|posted (?:a |an )?|" +
            "nouvelle (?:story|vid[ée]o|activit[ée])|new (?:story|video|activity)|" +
            "vient de publier|publication de|uploaded a video|a partag[ée] (?:une |un )?(?:publication|vid[ée]o))"
    )

    fun classify(pkg: String, title: String, body: String, category: String? = null): SocialNotificationKind {
        if (!isSocialApp(pkg)) return SocialNotificationKind.UNKNOWN
        val text = "$title $body".lowercase(Locale.ROOT)
        if (direct.containsMatchIn(text)) return SocialNotificationKind.DIRECT
        // CATEGORY_MESSAGE alone cannot mean a direct message: Snapchat also uses it for suggestions.
        if (typing.containsMatchIn(text)) return SocialNotificationKind.ACTIVITY
        if (suggestion.containsMatchIn(text)) return SocialNotificationKind.SUGGESTION
        if (mention.containsMatchIn(text)) return SocialNotificationKind.MENTION
        if (invitation.containsMatchIn(text)) return SocialNotificationKind.INVITATION
        if (activity.containsMatchIn(text)) return SocialNotificationKind.ACTIVITY
        if (post.containsMatchIn(text)) return SocialNotificationKind.POST
        return SocialNotificationKind.UNKNOWN
    }

    fun priority(kind: SocialNotificationKind): NotificationPriority? = when (kind) {
        SocialNotificationKind.DIRECT -> NotificationPriority.NOW
        SocialNotificationKind.MENTION, SocialNotificationKind.INVITATION -> NotificationPriority.TODAY
        SocialNotificationKind.ACTIVITY, SocialNotificationKind.SUGGESTION,
        SocialNotificationKind.POST -> NotificationPriority.LOW
        SocialNotificationKind.UNKNOWN -> null
    }

    /** Only six known types can override NLI, never an unrecognized social notification. */
    fun decision(kind: SocialNotificationKind, config: NotificationFilterConfig,
                 interest: NotificationPersonalization.Interest,
                 localHour: Int): NotificationFilterEvaluation? {
        val tier = priority(kind) ?: return null
        if (kind == SocialNotificationKind.DIRECT)
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Message personnel")
        if (interest == NotificationPersonalization.Interest.WANTED)
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Préférence personnelle")
        if (config.mode != NotificationFilterMode.PERSONALIZED ||
            config.onlyQuietHours && !config.inQuietHours(localHour)) return null
        return NotificationFilterEvaluation(NotificationFilterDecision.DISMISS,
            if (tier == NotificationPriority.TODAY) "À consulter ce soir dans le Brief"
            else "Activité sociale indésirable · conservée dans le Brief")
    }
}

/** Immutable user-chosen social priorities. A real message is always NOW. */
internal object NotificationSocialSettings {
    fun preference(kind: SocialNotificationKind): NotificationPriority? =
        NotificationSocialPolicy.priority(kind)
}
