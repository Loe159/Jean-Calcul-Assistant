package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun `recognition request carries locale and validated silence windows`() {
        val request =
            SpeechToTextRequest(
                locale = VoiceLocale("fr-FR"),
                completeSilenceMillis = 1_500L,
                possibleSilenceMillis = 750L,
            )

        assertEquals("fr-FR", request.locale.languageTag)
        assertEquals(1_500L, request.completeSilenceMillis)
    }

    @Test
    fun `recognition request rejects an inverted silence window`() {
        assertThrows(IllegalArgumentException::class.java) {
            SpeechToTextRequest(
                locale = VoiceLocale("fr-FR"),
                completeSilenceMillis = 500L,
                possibleSilenceMillis = 750L,
            )
        }
    }
}
