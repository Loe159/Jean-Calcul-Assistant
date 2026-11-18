package io.github.mangi.eta.ui.screens.home

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
    fun entranceRunsOnlyBeforeFirstOpening() {
        assertTrue(shouldAnimateBriefEntrance(false))
        assertFalse(shouldAnimateBriefEntrance(true))
    }

    @Test
    fun entranceIsNotDependentOnReopenCount() {
        repeat(10) { assertFalse(shouldAnimateBriefEntrance(true)) }
    }
}
