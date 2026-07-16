package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceContractsTest {
    @Test
    fun `final recognition event preserves its structured result`() {
        val result = SpeechRecognitionResult(text = "Mets le volume a 30", confidence = 0.84f)

        val event = SpeechToTextEvent.Final(result)

        assertEquals("Mets le volume a 30", event.result.text)
        assertEquals(0.84f, event.result.confidence)
    }

    @Test
    fun `recognition result accepts an unknown confidence`() {
        val result = SpeechRecognitionResult(text = "Bonjour", confidence = null)

        assertNull(result.confidence)
    }
}
