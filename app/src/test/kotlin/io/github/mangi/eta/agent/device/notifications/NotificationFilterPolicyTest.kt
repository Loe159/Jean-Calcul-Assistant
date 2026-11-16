package io.github.mangi.eta.agent.device.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterPolicyTest {
    private val pkg = "com.example.store"
    private fun scores(top: String = NotificationFilterPolicy.AD, confidence: Double = 0.9995)
        : List<Pair<String, Double>> {
        val others = (1.0 - confidence) / (NotificationNliScores.validatedLabels.size - 1)
        return NotificationNliScores.validatedLabels.map { it to if (it == top) confidence else others }
    }
    private fun candidate(top: String = NotificationFilterPolicy.AD, confidence: Double = 0.9995,
                          safe: Boolean = true, hour: Int = 23) =
        NotificationFilterCandidate(pkg, safe, scores(top, confidence), hour)

    @Test fun disabledNeverFilters() {
        val config = NotificationFilterConfig()
        assertEquals(NotificationFilterDecision.KEEP, NotificationFilterPolicy.decide(config, candidate()))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config.copy(mode = NotificationFilterMode.OFF,
                appRules = mapOf(pkg to NotificationAppRule.FILTER)), candidate()))
    }

    @Test fun observationDoesNotChangeNotifications() {
        assertEquals(NotificationFilterDecision.OBSERVE,
            NotificationFilterPolicy.decide(NotificationFilterConfig(mode = NotificationFilterMode.OBSERVE),
                candidate()))
    }

    @Test fun cautiousOnlySnoozesExtremelyConfidentAds() {
        val config = NotificationFilterConfig(mode = NotificationFilterMode.CAUTIOUS)
        assertEquals(NotificationFilterDecision.SNOOZE_1H,
            NotificationFilterPolicy.decide(config, candidate()))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(confidence = .994)))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(top = NotificationFilterPolicy.SPAM)))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(safe = false)))
    }

    @Test fun personalizedRequiresExplicitAppPermissionAndLabelPermission() {
        val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED, filterSpam = true)
        assertEquals(NotificationFilterDecision.KEEP, NotificationFilterPolicy.decide(config, candidate()))
        val allowed = config.copy(appRules = mapOf(pkg to NotificationAppRule.FILTER))
        assertEquals(NotificationFilterDecision.SNOOZE_1H,
            NotificationFilterPolicy.decide(allowed, candidate()))
        assertEquals(NotificationFilterDecision.SNOOZE_1H,
            NotificationFilterPolicy.decide(allowed, candidate(NotificationFilterPolicy.SPAM)))
        assertEquals(NotificationFilterDecision.KEEP, NotificationFilterPolicy.decide(
            allowed.copy(filterAds = false, filterSpam = false), candidate()))
        assertEquals(NotificationFilterDecision.KEEP, NotificationFilterPolicy.decide(
            allowed.copy(appRules = mapOf(pkg to NotificationAppRule.KEEP)), candidate()))
        assertEquals(NotificationFilterDecision.KEEP, NotificationFilterPolicy.decide(
            allowed, candidate(top = "a personal message")))
    }

    @Test fun personalizedCanSnoozeOneDayOrDismissOnlyWithVeryHighThreshold() {
        val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
            appRules = mapOf(pkg to NotificationAppRule.FILTER),
            action = NotificationFilterAction.SNOOZE_24H)
        assertEquals(NotificationFilterDecision.SNOOZE_24H,
            NotificationFilterPolicy.decide(config, candidate(confidence = .992)))
        assertEquals(NotificationFilterDecision.KEEP, NotificationFilterPolicy.decide(
            config.copy(action = NotificationFilterAction.DISMISS), candidate(confidence = .998)))
        assertEquals(NotificationFilterDecision.DISMISS, NotificationFilterPolicy.decide(
            config.copy(action = NotificationFilterAction.DISMISS), candidate(confidence = .9995)))
    }

    @Test fun failClosedOnBadScores() {
        val cfg = NotificationFilterConfig(mode = NotificationFilterMode.CAUTIOUS)
        val base = candidate()
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(cfg, base.copy(scores = listOf(NotificationFilterPolicy.AD to 1.0))))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(cfg, base.copy(scores = scores().mapIndexed { i, it ->
                if (i == 0) it.first to Double.NaN else it
            })))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(cfg, base.copy(scores = scores().map { it.first to .9 })))
    }

    @Test fun quietHoursAndDefaultProtection() {
        val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
            onlyQuietHours = true, appRules = mapOf(pkg to NotificationAppRule.FILTER))
        assertTrue(config.inQuietHours(23))
        assertTrue(config.inQuietHours(0))
        assertFalse(config.inQuietHours(11))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(hour = 11)))
        assertEquals(NotificationFilterDecision.SNOOZE_1H,
            NotificationFilterPolicy.decide(config, candidate(hour = 23)))
        assertFalse(config.copy(quietFrom = 9, quietUntil = 18).inQuietHours(23))
    }
}
