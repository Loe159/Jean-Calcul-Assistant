package io.github.mangi.eta.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBriefMotionTest {
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
