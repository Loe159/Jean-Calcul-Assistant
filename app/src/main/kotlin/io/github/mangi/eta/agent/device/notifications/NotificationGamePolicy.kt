package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import java.util.Locale

/** Strict app identity: a sender merely writing 'Brawl Stars' cannot become a game. */
internal object NotificationGameIdentity {
    private val knownPackages = setOf(
        "com.supercell.brawlstars", // Brawl Stars
        "com.chess",                 // Chess.com
    )

    fun isGame(context: Context, packageName: String): Boolean {
        if (packageName in knownPackages) return true
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).category == ApplicationInfo.CATEGORY_GAME
        }.getOrDefault(false)
    }
}

internal enum class GameNotificationKind { INVITATION, REWARD, STREAK, EVENT, PUZZLE, UNKNOWN }

/** Intentional high-precision patterns: no automatic deletion when the intent is unclear. */
internal object NotificationGamePolicy {
    private val invitation = Regex(
        "(?iu)(?:invited you|invite(?:d|s)? (?:you|to play)|(?:vous|t['’]|te) (?:a |ont )?invit[ée]s? " +
            "(?:à|a|pour) jouer|invit(?:ation|[ée]) (?:de|d['’]|à|a) (?:un |une |votre |ton |ta |l['’])?" +
            "(?:ami|joueur|partie)|friend (?:invite|invitation|request)|a envoy[ée] une invitation|" +
            "join (?:my|their) (?:game|party|match))"
    )
    private val reward = Regex(
        "(?iu)(?:r[ée]compense|qu[êe]te|cadeau (?:du jour|quotidien)|free (?:reward|gift|chest)|" +
            "reward|daily quest|loot box|r[ée]cup[ée]re[zr]? (?:votre |ta |une |des )?)"
    )
    private val streak = Regex(
        "(?iu)(?:s[ée]rie quotidienne|perdre (?:votre|ta) s[ée]rie|streak|daily login|" +
            "(?:revenez|connectez.vous|reviens) (?:aujourd'hui|chaque jour|pour ne pas perdre))"
    )
    private val event = Regex(
        "(?iu)(?:nouve(?:au|aux|lle|lles) (?:mode|saison|carte|mise à jour|[ée]v[ée]nement)|" +
            "new (?:season|event|mode|update|map)|[ée]v[ée]nement (?:disponible|commence|sp[ée]cial)|" +
            "(?:season|saison) (?:start|commence|disponible))"
    )
    private val puzzle = Regex(
        "(?iu)(?:puzzle|chess challenge|daily challenge|d[ée]fi (?:du jour|quotidien)|" +
            "probl[èe]me d['’][ée]checs|can you solve|solve this|curveball|[ée]nigme)"
    )

    fun classify(title: String, content: String, androidCategory: String? = null): GameNotificationKind {
        val text = "$title $content".lowercase(Locale.ROOT)
        if (invitation.containsMatchIn(text)) return GameNotificationKind.INVITATION
        // A direct conversation is never interpreted as disposable game marketing.
        if (androidCategory in setOf(Notification.CATEGORY_MESSAGE, Notification.CATEGORY_CALL,
                Notification.CATEGORY_EMAIL, Notification.CATEGORY_MISSED_CALL)) return GameNotificationKind.UNKNOWN
        return when {
            streak.containsMatchIn(text) -> GameNotificationKind.STREAK
            reward.containsMatchIn(text) -> GameNotificationKind.REWARD
            event.containsMatchIn(text) -> GameNotificationKind.EVENT
            puzzle.containsMatchIn(text) -> GameNotificationKind.PUZZLE
            else -> GameNotificationKind.UNKNOWN
        }
    }

    /** Game-specific decisions never bypass main filter OFF, quiet hours or explicit user feedback. */
    fun evaluate(
        config: NotificationFilterConfig,
        base: NotificationFilterEvaluation,
        preference: NotificationPersonalization.Interest,
        gameApp: Boolean,
        kind: GameNotificationKind,
        enabledKinds: Set<GameNotificationKind>,
        localHour: Int,
    ): NotificationFilterEvaluation {
        if (!gameApp) return base
        if (kind == GameNotificationKind.INVITATION)
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Invitation d'ami à conserver")
        if (preference == NotificationPersonalization.Interest.WANTED)
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Conservée selon vos préférences")
        if (config.mode != NotificationFilterMode.PERSONALIZED ||
            config.onlyQuietHours && !config.inQuietHours(localHour))
            return if (config.mode == NotificationFilterMode.OFF) base
            else NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Filtrage des jeux suspendu")
        if (kind == GameNotificationKind.UNKNOWN || kind !in enabledKinds)
            return NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "Aucune règle de suppression des jeux")
        // Deletion is requested explicitly for these game intents. The listener still validates
        // a current unchanged Android notification, ranking, protection and exact decision.
        return NotificationFilterEvaluation(NotificationFilterDecision.DISMISS, "Jeux : ${kind.name.lowercase(Locale.ROOT)} non souhaité")
    }
}

/** Stores only five fixed preferences, not full notification content. */
internal object NotificationGameSettings {
    private const val PREFS = "notification_local"
    private const val PREFIX = "game_filter_"
    private val removable = setOf(GameNotificationKind.REWARD, GameNotificationKind.STREAK,
        GameNotificationKind.EVENT, GameNotificationKind.PUZZLE)

    fun enabledKinds(context: Context): Set<GameNotificationKind> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return removable.filterTo(mutableSetOf()) { prefs.getBoolean(PREFIX + it.name, true) }
    }

    fun setDelete(context: Context, kind: GameNotificationKind, delete: Boolean) {
        require(kind in removable) { "Les invitations d'amis restent protégées" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PREFIX + kind.name, delete).apply()
    }
}
