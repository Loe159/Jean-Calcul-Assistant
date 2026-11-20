package io.github.mangi.eta.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBriefMotionTest {
    @Test
    fun cardAndWordEntrancesStaggerAtTheMockupIntervals() {
        assertEquals(0L, 0 * BriefMotionTiming.CARD_STAGGER_MS)
        assertEquals(165L, 3 * BriefMotionTiming.CARD_STAGGER_MS)
        assertEquals(600, BriefMotionTiming.CARD_MS)
        assertEquals(500, BriefMotionTiming.DENSITY_MS)
        assertEquals(120, BriefMotionTiming.WORD_START_MS)
        assertEquals(28, BriefMotionTiming.WORD_STAGGER_MS)
        assertEquals(700, BriefMotionTiming.WORD_MS)
    }

    @Test
    fun skyOpacityFollowsSwipeDistanceExactlyInBothDirections() {
        val width = 400f
        val points = (0..8).map { step ->
            val offset = -width * step / 8f
            val progress = io.github.mangi.eta.ui.components.briefPaneMotion(offset, width).progress
            briefSceneAlpha(progress)
        }
        points.forEachIndexed { index, value ->
            assertEquals(index / 8f, value, 0.00001f)
        }
        assertEquals(0f, points.first(), 0f)
        assertEquals(1f, points.last(), 0f)
        assertEquals(points.reversed(), (8 downTo 0).map {
            briefSceneAlpha(io.github.mangi.eta.ui.components.briefPaneMotion(-width * it / 8f, width).progress)
        })
    }

    @Test
    fun skyOpacityClampsInvalidOrOvershootingDragFractions() {
        assertEquals(0f, briefSceneAlpha(-0.3f), 0f)
        assertEquals(0f, briefSceneAlpha(Float.NaN), 0f)
        assertEquals(0f, briefSceneAlpha(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, briefSceneAlpha(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, briefSceneAlpha(1.7f), 0f)
        assertEquals(0.26f, briefSceneAlpha(0.26f), 0f)
    }

    @Test
    fun entranceRunsOncePerCardOnTheFirstOpening() {
        assertTrue(shouldAnimateBriefEntrance(hasClosedAfterOpening = false, alreadySeen = false))
        assertFalse(shouldAnimateBriefEntrance(hasClosedAfterOpening = false, alreadySeen = true))
        assertFalse(shouldAnimateBriefEntrance(hasClosedAfterOpening = true, alreadySeen = false))
        assertFalse(shouldAnimateBriefEntrance(hasClosedAfterOpening = true, alreadySeen = true))
    }

    @Test
    fun verticalScrollMovesTheSkyByThirtyFivePercentOfTheOffset() {
        assertEquals(0f, briefSkyParallax(-10f), 0f)
        assertEquals(0f, briefSkyParallax(0f), 0f)
        assertEquals(70f, briefSkyParallax(200f), .001f)
    }
}
