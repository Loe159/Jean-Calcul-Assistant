package io.github.mangi.eta.agent.device.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFilterPolicyTest {
    private fun scores(top: String = NotificationFilterPolicy.AD, confidence: Double = 0.9995)
        : List<Pair<String, Double>> {
        val others = (1.0 - confidence) / (NotificationNliScores.validatedLabels.size - 1)
        return NotificationNliScores.validatedLabels.map { it to if (it == top) confidence else others }
    }

    private fun candidate(
        app: String = "com.example.store",
        top: String = NotificationFilterPolicy.AD,
        confidence: Double = 0.9995,
        safe: Boolean = true,
        hour: Int = 23,
    ) = NotificationFilterCandidate(app, safe, scores(top, confidence), hour)

    @Test fun disabledNeverFilters() {
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(NotificationFilterConfig(), candidate()))
    }

    @Test fun observationDoesNotChangeNotifications() {
        assertEquals(NotificationFilterDecision.OBSERVE,
            NotificationFilterPolicy.decide(
                NotificationFilterConfig(mode = NotificationFilterMode.OBSERVE), candidate()))
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

    @Test fun personalizedUsesAllEligibleAppsWithoutAnyPackageAllowlist() {
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED,
            filterAds = true,
            filterSpam = true,
        )
        for (pkg in listOf("com.example.shop", "org.other.store", "net.publisher.social")) {
            assertEquals("$pkg should be eligible", NotificationFilterDecision.SNOOZE_1H,
                NotificationFilterPolicy.decide(config, candidate(app = pkg)))
            assertEquals("$pkg should be eligible for spam as well",
                NotificationFilterDecision.SNOOZE_1H,
                NotificationFilterPolicy.decide(config, candidate(app = pkg, top = NotificationFilterPolicy.SPAM)))
        }
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config.copy(filterAds = false, filterSpam = false), candidate()))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(top = "a personal message")))
        // NotificationProtection.safe() performs the strict system/sensitive checks first.
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(app = "com.example.bank", safe = false)))
    }

    @Test fun permanentRemovalOfDominantAdvertisementsInEligibleApps() {
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED,
            action = NotificationFilterAction.DISMISS,
            filterSpam = true,
        )
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(confidence = .399)))
        for (pkg in listOf("com.example.shop", "net.example.promo")) {
            assertEquals(NotificationFilterDecision.DISMISS,
                NotificationFilterPolicy.decide(config, candidate(app = pkg, confidence = .9995)))
        }
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationFilterPolicy.decide(config, candidate(top = NotificationFilterPolicy.SPAM)))
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate(safe = false)))
    }

    @Test fun leboncoinAdvertisingScore448MayBeDismissedWhenClearlyTop() {
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED,
            action = NotificationFilterAction.DISMISS,
        )
        // The label set order follows NotificationNliScores.validatedLabels.
        val relativeScores = listOf(.448, .260, .100, .070, .040, .042, .040)
        assertEquals(1.0, relativeScores.sum(), 0.00001)
        val candidate = NotificationFilterCandidate(
            app = "fr.leboncoin",
            safe = true,
            scores = NotificationNliScores.validatedLabels.zip(relativeScores),
            localHour = 11,
        )
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationFilterPolicy.decide(config, candidate))
        // The app name alone must never override Android safety checks.
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate.copy(safe = false)))
    }

    @Test fun nearlyTied448AdvertisementIsNotDeleted() {
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED,
            action = NotificationFilterAction.DISMISS,
        )
        val scores = listOf(.448, .422, .030, .030, .030, .020, .020)
        assertEquals(1.0, scores.sum(), 0.00001)
        val candidate = NotificationFilterCandidate(
            app = "fr.leboncoin", safe = true,
            scores = NotificationNliScores.validatedLabels.zip(scores),
            localHour = 11,
        )
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config, candidate))
    }

    @Test fun spamNeedsMoreEvidenceThanAdvertisement() {
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED,
            action = NotificationFilterAction.DISMISS, filterSpam = true,
        )
        assertEquals(NotificationFilterDecision.KEEP,
            NotificationFilterPolicy.decide(config,
                candidate(top = NotificationFilterPolicy.SPAM, confidence = .60)))
        assertEquals(NotificationFilterDecision.DISMISS,
            NotificationFilterPolicy.decide(config,
                candidate(top = NotificationFilterPolicy.SPAM, confidence = .75)))
    }

    @Test fun personalizedCanStillSnoozeOneDay() {
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED, action = NotificationFilterAction.SNOOZE_24H)
        assertEquals(NotificationFilterDecision.SNOOZE_24H,
            NotificationFilterPolicy.decide(config, candidate(confidence = .992)))
    }

    @Test fun failClosedOnBadScores() {
        val cfg = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED)
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
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED, onlyQuietHours = true)
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
