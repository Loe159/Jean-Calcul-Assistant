package io.github.mangi.eta.ui.screens.home.brief

import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefCounts
import io.github.mangi.eta.data.car.BriefCarState
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefModelTest {
    @Test
    fun localMomentAndGreetingChangeAtTheMockupBoundaries() {
        val cases = listOf(
            "00:00" to BriefMoment.NIGHT,
            "04:59" to BriefMoment.NIGHT,
            "05:00" to BriefMoment.MORNING,
            "11:59" to BriefMoment.MORNING,
            "12:00" to BriefMoment.DAY,
            "17:59" to BriefMoment.DAY,
            "18:00" to BriefMoment.EVENING,
            "21:59" to BriefMoment.EVENING,
            "22:00" to BriefMoment.NIGHT,
            "23:59" to BriefMoment.NIGHT,
        )
        cases.forEach { (time, expected) -> assertEquals(time, expected, briefMoment(LocalTime.parse(time))) }
        assertEquals(
            listOf("Bonjour", "Bon après-midi", "Bonsoir", "Bonne nuit"),
            BriefMoment.entries.map { it.greeting },
        )
    }

    @Test
    fun feedUsesContextualOrderAndKeepsWeatherAndHomeAtTheEnd() {
        assertFalse(BriefCardId.entries.any { it.key == "music" })
        val expectedStarts = listOf(
            listOf(BriefCardId.AGENDA, BriefCardId.CAR, BriefCardId.TASKS),
            listOf(BriefCardId.AGENDA, BriefCardId.SUGGESTIONS, BriefCardId.NOTIFICATIONS),
            listOf(BriefCardId.NOTIFICATIONS, BriefCardId.SUGGESTIONS, BriefCardId.AGENDA),
            listOf(BriefCardId.AGENDA, BriefCardId.HEALTH, BriefCardId.SUGGESTIONS),
        )
        BriefMoment.entries.forEachIndexed { index, moment ->
            val order = briefFeedOrder(moment)
            assertEquals(expectedStarts[index], order.take(3))
            assertEquals(11, order.size)
            assertEquals(BriefCardId.entries.toSet(), order.toSet())
            assertEquals(listOf(BriefCardId.WEATHER, BriefCardId.HOME), order.takeLast(2))
        }
    }

    @Test
    fun feedExcludesEveryHiddenCardWithoutMovingItsNeighbors() {
        val hidden = setOf(BriefCardId.NOTIFICATIONS, BriefCardId.CAR, BriefCardId.WEATHER)
        BriefMoment.entries.forEach { moment ->
            val visible = briefFeedOrder(moment, hidden)
            assertEquals(briefFeedOrder(moment).filterNot(hidden::contains), visible)
            assertEquals(BriefCardId.HOME, visible.last())
            assertTrue(briefFeedOrder(moment, BriefCardId.entries.toSet()).isEmpty())
        }
    }

    @Test
    fun carKeepsItsKeyAndMovesOutOfPreviewsWithASession() = runBlocking {
        val demo = DemoCarSource()
        val ready = demo.load() as BriefCarState.Ready
        assertEquals("Peugeot 208", ready.data.label)
        assertEquals("car", BriefCardId.CAR.key)
        assertTrue(BriefCardId.CAR.isPreview(demo.settings.value.hasSession))
        assertFalse(BriefCardId.CAR.isPreview(hasCarSession = true))
        assertFalse(BriefCardId.NOTIFICATIONS.isPreview(hasCarSession = false))
        assertTrue(BriefCardId.AGENDA.isPreview(hasCarSession = true))
    }

    @Test
    fun localSummaryHighlightsExactCountsAndHandlesHiddenHistory() {
        val now = LocalDateTime.parse("2026-11-20T18:00").atZone(ZoneId.of("Europe/Paris"))
        fun highlights(count: Int?, show: Boolean = true): List<String> =
            localBriefSummary(BriefSummaryInput(now, "fr-FR", count?.let { MastraBriefCounts(it, 0, 0) }, show))
                .parts.filter { it.highlighted }.map { it.text }

        assertEquals(listOf("1 notification urgente.", "la journée de demain."), highlights(1))
        assertEquals("2 notifications urgentes.", highlights(2).first())
        assertEquals("Aucune notification urgente.", highlights(0).first())
        assertEquals(listOf("la journée de demain."), highlights(null))
        assertEquals(listOf("la journée de demain."), highlights(2, show = false))
    }

    @Test
    fun skyUsesTheMockupPaletteAndThemeMixAtScenarioTimes() {
        val dawn = briefSkyPalette(LocalTime.of(7, 42), dark = true)
        assertEquals(BriefSkyPalette(0x222D6C, 0x904F56, 0x80613D, 4f, 2f), dawn)
        val day = briefSkyPalette(LocalTime.of(12, 10), dark = false)
        assertEquals(BriefSkyPalette(0xC5DBF7, 0xDDEFFD, 0xF5FBFF, 0f, 4f), day)
        val dusk = briefSkyPalette(LocalTime.of(19, 12), dark = true)
        assertEquals(14f, dusk.stars, 0f)
        assertEquals(2f, dusk.clouds, 0f)
        assertEquals(34f, briefSkyPalette(LocalTime.of(23, 42), dark = true).stars, 0f)
        val lightNight = briefSkyPalette(LocalTime.of(23, 42), dark = false)
        assertEquals(0f, lightNight.stars, 0f)
        assertEquals(13f, lightNight.moonRadius, 0f)
    }

    @Test
    fun paletteInterpolatesContinuouslyThroughDawnAndAcrossMidnight() {
        fun at(time: String) = briefSkyPalette(LocalTime.parse(time), dark = true)
        val start = at("04:30")
        val middle = at("05:30")
        val end = at("06:30")
        assertEquals(19f, middle.stars, 0.0001f)
        assertEquals(1f, middle.clouds, 0.0001f)
        assertTrue(middle.top in (start.top + 1) until end.top)
        assertEquals(start, at("04:29:59"))
        assertEquals(end, at("06:30:01"))
        assertEquals(at("23:59:59"), at("00:00"))
    }

    @Test
    fun sunFollowsAnArcAndFadesAtTheHorizon() {
        val rise = requireNotNull(briefSunPosition(LocalTime.of(4, 30)))
        val morning = requireNotNull(briefSunPosition(LocalTime.of(7, 42)))
        val noon = requireNotNull(briefSunPosition(LocalTime.NOON))
        val set = requireNotNull(briefSunPosition(LocalTime.of(19, 30)))
        assertEquals(0f, rise.opacity, 0f)
        assertEquals(0f, set.opacity, 0f)
        assertEquals(1f, noon.opacity, 0f)
        assertTrue(rise.x > morning.x && morning.x > noon.x && noon.x > set.x)
        assertTrue(noon.y < morning.y && morning.y < rise.y)
        assertNull(briefSunPosition(LocalTime.MIDNIGHT))
    }

    @Test
    fun moonStaysOnItsArcWhenTheDateRollsOver() {
        val before = requireNotNull(briefMoonPosition(LocalTime.of(23, 59)))
        val after = requireNotNull(briefMoonPosition(LocalTime.MIDNIGHT))
        assertTrue(before.x > after.x)
        assertEquals(before.x, after.x, .002f)
        assertEquals(before.y, after.y, .002f)
        assertEquals(1f, after.opacity, 0f)
        assertNotNull(briefMoonPosition(LocalTime.of(19, 12)))
        assertNull(briefMoonPosition(LocalTime.NOON))
    }
}
