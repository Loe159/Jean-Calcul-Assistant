package io.github.mangi.eta.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class NowBriefMotionTest {
    @Test
    fun ambientCycleRotatesForEveryOpen() {
        assertEquals(listOf(0, 1, 2, 0, 1, 2),
            (1..6).map(::briefAmbienceIndex))
    }

    @Test
    fun ambienceLabelsRemainStable() {
        assertEquals("Dégradé", briefAmbienceName(0))
        assertEquals("Halo", briefAmbienceName(1))
        assertEquals("Particules", briefAmbienceName(2))
        assertEquals("Dégradé", briefAmbienceName(3))
    }

    @Test
    fun initialSessionIsSafe() {
        assertEquals(0, briefAmbienceIndex(0))
        assertEquals(0, briefAmbienceIndex(-2))
    }
}
