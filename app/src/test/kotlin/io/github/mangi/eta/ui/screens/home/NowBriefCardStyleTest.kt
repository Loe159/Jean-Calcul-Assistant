package io.github.mangi.eta.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBriefCardStyleTest {
    @Test fun missingAndUnknownPreferencesUseBalancedGlass() {
        assertEquals(NowBriefCardStyle.GLASS, NowBriefCardStyle.parse(null))
        assertEquals(NowBriefCardStyle.GLASS, NowBriefCardStyle.parse("unknown"))
    }

    @Test fun allVariantsCanBePersistedByNameAndAreDistinct() {
        for (style in NowBriefCardStyle.entries) {
            assertEquals(style, NowBriefCardStyle.parse(style.name))
            assertTrue(style.label.isNotBlank())
            val palette = nowBriefGlassPalette(style, true)
            assertTrue(palette.stops.size >= 2)
            assertTrue(palette.cornerDp in 15..32)
            assertTrue(palette.pressX < 1f && palette.pressY > 1f)
        }
        assertEquals(3, NowBriefCardStyle.entries.size)
    }
}
