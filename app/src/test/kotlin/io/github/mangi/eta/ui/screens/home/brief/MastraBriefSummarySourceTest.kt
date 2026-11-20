package io.github.mangi.eta.ui.screens.home.brief

import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefCounts
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefMoment
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSegment
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSession
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSummaryContract
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSummaryRequest
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefSummaryResponse
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.briefSummaryFixture
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MastraBriefSummarySourceTest {
    private val instant = Instant.parse("2026-11-20T08:10:00Z")
    private val input = BriefSummaryInput(
        instant.atZone(ZoneId.of("Europe/Paris")), "fr-FR", MastraBriefCounts(2, 5, 4),
    )

    @Test
    fun `scope expires at the server deadline and resets on context changes`() {
        val expiry = instant.plusSeconds(30)
        val validity = RemoteBriefValidity(input.scope, expiry)
        assertTrue(validity.accepts(input, expiry.minusNanos(1)))
        assertFalse(validity.accepts(input, expiry))
        assertFalse(validity.accepts(input.copy(now = input.now.plusHours(3)), instant))
        assertFalse(validity.accepts(input.copy(now = input.now.plusDays(1)), instant))
        assertFalse(validity.accepts(input.copy(now = input.now.withZoneSameInstant(ZoneId.of("Europe/Berlin"))), instant))
        assertFalse(validity.accepts(input.copy(locale = "en-GB"), instant))
        assertFalse(validity.accepts(input.copy(counts = null), instant))
    }

    @Test
    fun `scope tolerates small changes in later tiers with a fixed baseline`() {
        fun accepts(now: Int, today: Int, low: Int) =
            input.scope.accepts(input.copy(counts = MastraBriefCounts(now, today, low)).scope)
        assertTrue(accepts(2, 7, 3))
        assertFalse(accepts(3, 5, 4))
        assertFalse(accepts(2, 8, 4))
        assertFalse(accepts(2, 5, 1))
        val oneLeft = input.copy(counts = MastraBriefCounts(2, 5, 1))
        assertFalse(oneLeft.scope.accepts(oneLeft.copy(counts = MastraBriefCounts(2, 5, 0)).scope))
    }

    @Test
    fun `request uses the actual clock and all counters even when the card is hidden`() = runBlocking<Unit> {
        val lab = TestLab(instant.plusSeconds(17))
        val remote = lab.source.summarize(input.copy(showNotificationCount = false))

        assertEquals(MastraBriefSummaryRequest(
            deviceTime = "2026-11-20T09:10:17+01:00",
            timeZone = "Europe/Paris",
            locale = "fr-FR",
            moment = MastraBriefMoment.MORNING,
            notificationCounts = MastraBriefCounts(2, 5, 4),
        ), lab.requests.single())
        assertEquals("Tu as 2 notifications prioritaires et Dentiste à 9 h 30.", remote.text)
        assertEquals(listOf("2 notifications prioritaires", "Dentiste à 9 h 30"),
            remote.parts.filter { it.highlighted }.map { it.text })
    }

    @Test
    fun `stored summary is reused up to expiry and then refreshed`() = runBlocking<Unit> {
        val lab = TestLab(instant)
        val first = lab.source.summarize(input)
        val expiry = requireNotNull(first.remote).until
        val smallChange = input.copy(counts = MastraBriefCounts(2, 7, 4))

        lab.clock.value = expiry.minusMillis(1)
        lab.network = false
        assertEquals(first, lab.newSource().summarize(smallChange))
        assertEquals(1, lab.requests.size)
        lab.clock.value = expiry
        lab.network = true
        assertNotNull(lab.newSource().summarize(smallChange).remote)
        assertEquals(listOf(input.counts, smallChange.counts), lab.requests.map { it.notificationCounts })
    }

    @Test
    fun `material differences invalidate the stored summary immediately`() = runBlocking<Unit> {
        val lab = TestLab(instant)
        lab.source.summarize(input)
        val changed = input.copy(counts = MastraBriefCounts(3, 5, 4))
        assertNotNull(lab.source.summarize(changed).remote)
        lab.clock.value = instant.plusSeconds(3 * 3600)
        assertNotNull(lab.source.summarize(changed.copy(now = changed.now.plusHours(3))).remote)

        assertEquals(3, lab.requests.size)
        assertEquals(MastraBriefMoment.DAY, lab.requests.last().moment)
    }

    @Test
    fun `unpaired offline or unavailable counts quietly use local wording`() = runBlocking<Unit> {
        val lab = TestLab(instant)
        lab.pairing = null
        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        assertEquals(0, lab.cacheReads)
        lab.pairing = paired
        lab.network = false
        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        lab.network = true
        val missing = input.copy(counts = null)
        assertEquals(localBriefSummary(missing), lab.source.summarize(missing))
        assertTrue(lab.requests.isEmpty())
    }

    @Test
    fun `corrupt cache and network errors fall back without creating a refresh deadline`() = runBlocking<Unit> {
        val lab = TestLab(instant)
        lab.cache = "{broken"
        lab.fetch = { throw IOException("test network failure") }

        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        assertEquals(1, lab.requests.size)
        assertNull(lab.cache)
    }

    @Test
    fun `invalid expired or disconnected responses are not stored or displayed`() = runBlocking<Unit> {
        val lab = TestLab(instant)
        lab.fetch = { lab.response().copy(segments = listOf(MastraBriefSegment("", true))) }
        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        lab.fetch = {
            lab.response().copy(generatedAt = instant.minusSeconds(60).toString(), validUntil = instant.toString())
        }
        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        lab.fetch = {
            lab.pairing = null
            lab.response()
        }
        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        assertNull(lab.cache)
        lab.pairing = paired
        lab.fetch = {
            lab.pairing = paired
            lab.response()
        }
        assertEquals(localBriefSummary(input), lab.source.summarize(input))
        assertNull(lab.cache)
    }

    @Test
    fun `cancellation of a visible brief request is propagated`() = runBlocking<Unit> {
        val lab = TestLab(instant)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        lab.fetch = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val job = launch { lab.source.summarize(input) }
        started.await()
        job.cancelAndJoin()

        assertTrue(cancelled.isCompleted)
        assertNull(lab.cache)
    }

    private class TestLab(now: Instant) {
        private var revision = 0L
        var pairing: MastraPairing? = paired
            set(value) {
                revision++
                cache = null
                field = value
            }
        var cache: String? = null
        var cacheReads = 0
        var network = true
        val clock = TestClock(now)
        val requests = mutableListOf<MastraBriefSummaryRequest>()
        var fetch: suspend () -> MastraBriefSummaryResponse = { response() }
        val source = newSource()

        fun response() = MastraBriefSummaryContract.decodeResponse(briefSummaryFixture("response")).copy(
            generatedAt = clock.value.toString(),
            validUntil = clock.value.plusSeconds(20 * 60).toString(),
        )

        fun newSource() = MastraBriefSummarySource(
            loadSession = { pairing?.let { cacheReads++; MastraBriefSession(it, revision, cache) } },
            writeCache = { expected, value -> (revision == expected).also { if (it) cache = value } },
            hasNetwork = { network },
            fetch = { _, request -> requests.add(request); fetch() },
            clock = clock,
        )
    }

    private class TestClock(var value: Instant) : Clock() {
        override fun instant() = value
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(value, zone)
    }

    private companion object {
        val paired = MastraPairing("https://homelab.example", "jc_fixture", "synthetic")
    }
}
