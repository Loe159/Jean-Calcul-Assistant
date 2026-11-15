package io.github.mangi.eta.ui.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCardImportMessagesTest {
    @Test
    fun damagedPriorityV3ExplainsThatV2WasNotUsed() {
        val message = checkNotNull(characterCardImportMessage("CARD_V3_INVALID"))
        assertTrue(message.contains("V3"))
        assertTrue(message.contains("No V2 fallback was used"))
    }

    @Test
    fun unrecognizedErrorCannotExposeFileOrExceptionText() {
        assertNull(characterCardImportMessage("Characters卡原文：private example"))
        assertNull(characterCardImportMessage(null))
        assertNotNull(characterCardImportMessage("CARD_METADATA_MISSING"))
        assertNotNull(characterCardImportMessage("CARD_TOO_LARGE"))
    }
}
