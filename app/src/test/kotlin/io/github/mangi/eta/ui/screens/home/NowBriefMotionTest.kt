package io.github.mangi.eta.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBriefMotionTest {
    @Test
    fun initialContentDoesNotRemainHiddenForNearlyASecond() {
        val maxDelay = BriefMotionTiming.CARD_MAX_DELAY_MS + BriefMotionTiming.CARD_MS
        assertTrue(maxDelay <= 300)
        assertTrue(BriefMotionTiming.TITLE_MS <= 250)
        assertTrue(BriefMotionTiming.COUNTER_MS <= 350)
        assertTrue(BriefMotionTiming.BAR_MS <= 300)
    }

    @Test
    fun haloOpacityFollowsSwipeDistanceExactlyInBothDirections() {
        val width = 400f
        val points = (0..8).map { step ->
            val offset = -width * step / 8f
            val progress = io.github.mangi.eta.ui.components.briefPaneMotion(offset, width).progress
            briefHaloAlpha(progress)
        }
        points.forEachIndexed { index, value ->
            assertEquals(index / 8f, value, 0.00001f)
        }
        assertEquals(0f, points.first(), 0f)
        assertEquals(1f, points.last(), 0f)
        assertEquals(points.reversed(), (8 downTo 0).map {
            briefHaloAlpha(io.github.mangi.eta.ui.components.briefPaneMotion(-width * it / 8f, width).progress)
        })
    }

    @Test
    fun haloOpacityClampsInvalidOrOvershootingDragFractions() {
        assertEquals(0f, briefHaloAlpha(-0.3f), 0f)
        assertEquals(0f, briefHaloAlpha(Float.NaN), 0f)
        assertEquals(0f, briefHaloAlpha(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, briefHaloAlpha(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, briefHaloAlpha(1.7f), 0f)
        assertEquals(0.26f, briefHaloAlpha(0.26f), 0f)
    }

    @Test
    fun entranceRunsOnlyBeforeFirstOpening() {
        assertTrue(shouldAnimateBriefEntrance(false))
        assertFalse(shouldAnimateBriefEntrance(true))
    }

    @Test
    fun entranceIsNotDependentOnReopenCount() {
        repeat(10) { assertFalse(shouldAnimateBriefEntrance(true)) }
    }
}
