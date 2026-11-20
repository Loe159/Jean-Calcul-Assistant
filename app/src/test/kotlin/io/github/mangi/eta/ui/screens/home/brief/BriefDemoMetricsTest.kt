package io.github.mangi.eta.ui.screens.home.brief

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefDemoMetricsTest {
    @Test
    fun progressIsBoundedAndZeroWithoutATarget() {
        assertEquals(.375f, briefProgress(3, 8), .0001f)
        assertEquals(38, briefPercent(briefProgress(3, 8)))
        assertEquals(0f, briefProgress(-2, 5), 0f)
        assertEquals(1f, briefProgress(7, 5), 0f)
        assertEquals(0f, briefProgress(7, 0), 0f)
        assertEquals(0f, briefProgress(7, -5), 0f)
    }

    @Test
    fun countdownChoosesTheEarliestUnfinishedEventAndSwitchesAtTheStart() {
        val first = event(570, 630)
        val second = event(750, 810)
        val events = listOf(second, event(0, 0), first)

        val before = requireNotNull(briefAgendaCountdown(events, 522))
        assertEquals(first, before.event)
        assertFalse(before.inProgress)
        assertEquals(48, before.minutes)
        assertEquals(1f - 48f / 360f, before.progress, .0001f)
        assertEquals(0f, requireNotNull(briefAgendaCountdown(events, 0)).progress, 0f)

        val atStart = requireNotNull(briefAgendaCountdown(events, 570))
        assertTrue(atStart.inProgress)
        assertEquals(60, atStart.minutes)
        assertEquals(0f, atStart.progress, 0f)

        val running = requireNotNull(briefAgendaCountdown(events, 600))
        assertEquals(30, running.minutes)
        assertEquals(.5f, running.progress, .0001f)
        assertEquals(second, briefAgendaCountdown(events, 630)?.event)
        assertNull(briefAgendaCountdown(events, 810))
    }

    @Test
    fun ribbonClipsIntervalsToTheVisibleWindowAndPlacesTheNowMarker() {
        val before = event(360, 400)
        val crossesStart = event(400, 440)
        val inside = event(480, 510)
        val crossesEnd = event(520, 550)
        val after = event(540, 555)
        val ribbon = briefAgendaRibbon(
            listOf(before, crossesStart, inside, crossesEnd, after, event(460, 460), event(490, 470)),
            now = 450, start = 420, end = 540,
        )

        assertEquals(listOf(crossesStart, inside, crossesEnd), ribbon.segments.map { it.event })
        assertEquals(0f, ribbon.segments[0].start, 0f)
        assertEquals(20f / 120f, ribbon.segments[0].end, .0001f)
        assertEquals(.5f, ribbon.segments[1].start, .0001f)
        assertEquals(.75f, ribbon.segments[1].end, .0001f)
        assertEquals(1f, ribbon.segments[2].end, 0f)
        assertEquals(.25f, requireNotNull(ribbon.now), .0001f)
    }

    @Test
    fun ribbonKeepsEndpointMarkersAndOmitsOutOfWindowMarkers() {
        assertEquals(0f, briefAgendaRibbon(emptyList(), 420).now)
        assertEquals(1f, briefAgendaRibbon(emptyList(), 1260).now)
        assertNull(briefAgendaRibbon(emptyList(), 419).now)
        assertNull(briefAgendaRibbon(emptyList(), 1261).now)
        assertEquals(BriefRibbon(emptyList(), null), briefAgendaRibbon(listOf(event(420, 600)), 420, 420, 420))
    }

    @Test
    fun agendaGapsUseUnionOfOverlappingAppointmentsAndIncludeTheThreshold() {
        val events = listOf(
            event(980, 1000), event(700, 760), event(535, 560), event(480, 600), event(835, 906),
            event(650, 640), event(760, 760), event(750, 775),
        )

        assertEquals(
            listOf(BriefAgendaGap(600, 700)),
            briefAgendaGaps(events),
        )
        assertEquals(
            listOf(BriefAgendaGap(600, 700), BriefAgendaGap(775, 835), BriefAgendaGap(906, 980)),
            briefAgendaGaps(events, minimumMinutes = 60),
        )
        assertEquals(listOf(BriefAgendaGap(540, 615)), briefAgendaGaps(listOf(event(480, 540), event(615, 640))))
        assertTrue(briefAgendaGaps(listOf(event(480, 540), event(540, 600))).isEmpty())
        assertTrue(briefAgendaGaps(emptyList()).isEmpty())
    }

    @Test
    fun agendaFormatsClockAndGapDurationsInFrench() {
        assertEquals("08:42", briefHour(522))
        assertEquals("48 min", briefDuration(48))
        assertEquals("2 h", briefDuration(120))
        assertEquals("1 h 05", briefDuration(65))
        assertEquals("12\u00a0480", briefNumber(12_480))
    }

    private fun event(start: Int, end: Int) = BriefDemoEvent(start, end, "Atelier", "Visio", BriefDemoTone.Blue)
}
