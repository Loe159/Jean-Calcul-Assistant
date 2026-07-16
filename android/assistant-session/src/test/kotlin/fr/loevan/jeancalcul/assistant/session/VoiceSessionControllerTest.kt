package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionControllerTest {
    @Test
    fun `partial and final recognition results update the session state`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val textToSpeech = FakeTextToSpeechProvider()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = textToSpeech,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            speechToText.emit(SpeechToTextEvent.Partial("Mets le volume"))
            runCurrent()

            assertEquals(VoiceSessionStatus.LISTENING, controller.state.value.status)
            assertEquals("Mets le volume", controller.state.value.partialTranscript)

            speechToText.emit(
                SpeechToTextEvent.Final(
                    SpeechRecognitionResult(text = "Mets le volume a 30", confidence = 0.9f),
                ),
            )
            runCurrent()

            assertEquals(VoiceSessionStatus.INVOKED, controller.state.value.status)
            assertEquals("Mets le volume a 30", controller.state.value.finalResult?.text)
            controller.close()
        }

    @Test
    fun `listening timeout cancels the microphone`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            advanceTimeBy(15_000L)
            runCurrent()

            assertTrue(speechToText.cancelled)
            assertEquals(VoiceSessionStatus.ERROR, controller.state.value.status)
            controller.close()
        }

    @Test
    fun `test response can be interrupted and releases providers on close`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val textToSpeech = FakeTextToSpeechProvider()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = textToSpeech,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.speakTestResponse()

            assertEquals("La reponse vocale de test fonctionne.", textToSpeech.spokenText)
            assertEquals(VoiceSessionStatus.SPEAKING, controller.state.value.status)

            controller.cancelActiveWork()
            controller.close()

            assertTrue(speechToText.cancelled)
            assertTrue(speechToText.released)
            assertTrue(textToSpeech.stopped)
            assertTrue(textToSpeech.released)
        }

    @Test
    fun `text fallback produces a final structured result`() =
        runTest {
            val controller =
                VoiceSessionController(
                    speechToTextProvider = FakeSpeechToTextProvider(),
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.updateTextFallback("Bonjour Jean")
            controller.submitTextFallback()

            assertEquals("Bonjour Jean", controller.state.value.finalResult?.text)
            controller.close()
        }
}

private class FakeSpeechToTextProvider : SpeechToTextProvider {
    private val mutableEvents = MutableSharedFlow<SpeechToTextEvent>()

    override val events: Flow<SpeechToTextEvent> = mutableEvents.asSharedFlow()
    var cancelled = false
    var released = false

    override fun startListening() = Unit

    override fun stopListening() = Unit

    override fun cancel() {
        cancelled = true
    }

    override fun release() {
        released = true
    }

    suspend fun emit(event: SpeechToTextEvent) {
        mutableEvents.emit(event)
    }
}

private class FakeTextToSpeechProvider : TextToSpeechProvider {
    private val mutableEvents = MutableSharedFlow<TextToSpeechEvent>()

    override val events: Flow<TextToSpeechEvent> = mutableEvents.asSharedFlow()
    var spokenText: String? = null
    var stopped = false
    var released = false

    override fun speak(text: String) {
        spokenText = text
    }

    override fun stop() {
        stopped = true
    }

    override fun release() {
        released = true
    }
}
