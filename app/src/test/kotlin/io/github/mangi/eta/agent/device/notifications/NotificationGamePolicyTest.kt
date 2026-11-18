package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationGamePolicyTest {
    private val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
        action = NotificationFilterAction.SNOOZE_1H)
    private val base = NotificationFilterEvaluation(NotificationFilterDecision.KEEP, "NLI: publicité non établie")
    private val configured = setOf(GameNotificationKind.REWARD, GameNotificationKind.STREAK,
        GameNotificationKind.EVENT, GameNotificationKind.PUZZLE)

    @Test fun brawlStarsScreenshotsMatchTheFourRemovalCategories() {
        assertEquals(GameNotificationKind.REWARD,
            NotificationGamePolicy.classify("Brawl Stars", "De nouvelles quêtes sont disponibles !"))
        assertEquals(GameNotificationKind.REWARD,
            NotificationGamePolicy.classify("Brawl Stars", "Récupérez votre récompense gratuite avant qu'elle disparaisse !"))
        assertEquals(GameNotificationKind.STREAK,
            NotificationGamePolicy.classify("Brawl Stars", "Vous êtes sur le point de perdre votre série quotidienne de 1 !"))
        assertEquals(GameNotificationKind.EVENT,
            NotificationGamePolicy.classify("Brawl Stars", "Nouvelle carte en mode Survivant. Serez-vous le dernier en jeu ?"))
        assertEquals(GameNotificationKind.PUZZLE,
            NotificationGamePolicy.classify("We're Throwing A Curveball At You", "Think you can solve this puzzle?"))
    }

    @Test fun friendInvitationsAreAlwaysKeptEvenIfNliThinksTheyAreAds() {
        val kind = NotificationGamePolicy.classify("Brawl Stars", "Théo vous invite à jouer une partie")
        assertEquals(GameNotificationKind.INVITATION, kind)
        val incorrectlyRemoved = NotificationFilterEvaluation(NotificationFilterDecision.DISMISS, "NLI publicité")
        val result = NotificationGamePolicy.evaluate(config, incorrectlyRemoved,
            NotificationPersonalization.Interest.UNWANTED, true, kind, configured, 12)
        assertEquals(NotificationFilterDecision.KEEP, result.decision)
    }

    @Test fun invitationsFromUsersAreNotConfusedWithInviteYourFriendsMarketing() {
        assertEquals(GameNotificationKind.INVITATION,
            NotificationGamePolicy.classify("Chess", "Alex invited you to play a game"))
        assertFalse(NotificationGamePolicy.classify("Brawl Stars",
            "Invitez vos amis pour obtenir une récompense gratuite !") == GameNotificationKind.INVITATION)
    }

    @Test fun messagesAndUnknownAlertsCannotBeAutomaticallyDismissed() {
        assertEquals(GameNotificationKind.UNKNOWN, NotificationGamePolicy.classify(
            "Pierre", "Salut, tu joues ce soir ?", Notification.CATEGORY_MESSAGE))
        assertEquals(GameNotificationKind.UNKNOWN, NotificationGamePolicy.classify(
            "Brawl Stars", "Votre compte a été désactivé"))
        assertEquals(NotificationFilterDecision.KEEP, NotificationGamePolicy.evaluate(config, base,
            NotificationPersonalization.Interest.NEUTRAL, true, GameNotificationKind.UNKNOWN,
            configured, 12).decision)
    }

    @Test fun gameRulesRespectGlobalSwitchAndQuietHoursAndExplicitFeedback() {
        val kind = GameNotificationKind.REWARD
        val otherConfig = config.copy(mode = NotificationFilterMode.OFF)
        assertEquals(NotificationFilterDecision.KEEP, NotificationGamePolicy.evaluate(otherConfig, base,
            NotificationPersonalization.Interest.NEUTRAL, true, kind, configured, 12).decision)
        assertEquals(NotificationFilterDecision.KEEP, NotificationGamePolicy.evaluate(
            config.copy(onlyQuietHours = true, quietFrom = 22, quietUntil = 7), base,
            NotificationPersonalization.Interest.NEUTRAL, true, kind, configured, 12).decision)
        assertEquals(NotificationFilterDecision.KEEP, NotificationGamePolicy.evaluate(config, base,
            NotificationPersonalization.Interest.WANTED, true, kind, configured, 12).decision)
        assertEquals(NotificationFilterDecision.KEEP, NotificationGamePolicy.evaluate(config, base,
            NotificationPersonalization.Interest.NEUTRAL, false, kind, configured, 12).decision)
        assertEquals(NotificationFilterDecision.DISMISS, NotificationGamePolicy.evaluate(config, base,
            NotificationPersonalization.Interest.NEUTRAL, true, kind, configured, 12).decision)
    }

    @Test fun everyGameCategoryFollowsTheUserPreferences() {
        configured.forEach { kind ->
            assertEquals(kind.toString(), NotificationFilterDecision.DISMISS,
                NotificationGamePolicy.evaluate(config, base, NotificationPersonalization.Interest.NEUTRAL,
                    true, kind, configured, 14).decision)
        }
        assertEquals(NotificationFilterDecision.KEEP, NotificationGamePolicy.evaluate(config, base,
            NotificationPersonalization.Interest.NEUTRAL, true, GameNotificationKind.EVENT,
            configured - GameNotificationKind.EVENT, 14).decision)
    }

    @Test fun gameTopicDoesNotRelabelOtherAppOrGameDirectMessages() {
        assertEquals("games", NotificationTopics.classify("com.supercell.brawlstars", null,
            null, "Brawl Stars", "Récompenses quotidiennes", null, isGame = true))
        assertEquals("messages", NotificationTopics.classify("com.supercell.brawlstars", null,
            Notification.CATEGORY_MESSAGE, "Pierre", "Salut", null, isGame = true))
        assertEquals("other", NotificationTopics.classify("com.example.mail", null,
            null, "Brawl Stars", "Récompenses quotidiennes", null, isGame = false))
    }

    @Test fun settingsDefaultToTheValidatedChoicesAndCanBeChanged() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("notification_local", Context.MODE_PRIVATE)
        GameNotificationKind.entries.forEach { kind -> prefs.edit().remove("game_filter_${kind.name}").commit() }
        assertEquals(configured, NotificationGameSettings.enabledKinds(context))
        try {
            NotificationGameSettings.setDelete(context, GameNotificationKind.EVENT, false)
            assertEquals(configured - GameNotificationKind.EVENT,
                NotificationGameSettings.enabledKinds(context))
            assertFalse(NotificationGameSettings.enabledKinds(context).contains(GameNotificationKind.INVITATION))
        } finally {
            GameNotificationKind.entries.forEach { kind -> prefs.edit().remove("game_filter_${kind.name}").commit() }
        }
    }

    @Test fun packageIdentityIsNotBasedOnSpoofableNotificationText() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(NotificationGameIdentity.isGame(context, "com.supercell.brawlstars"))
        assertTrue(NotificationGameIdentity.isGame(context, "com.chess"))
        assertFalse(NotificationGameIdentity.isGame(context, "com.example.not_a_game"))
    }
}
