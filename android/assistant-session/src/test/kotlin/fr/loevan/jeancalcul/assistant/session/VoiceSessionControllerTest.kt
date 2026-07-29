package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.domain.AudioAmplitudeSource
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.SpeechToTextRequest
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.domain.TextToSpeechRequest
import fr.loevan.jeancalcul.domain.VoiceActivity
import fr.loevan.jeancalcul.domain.VoiceActivityDetector
import fr.loevan.jeancalcul.domain.VoiceAudioFocusController
import fr.loevan.jeancalcul.domain.VoiceAudioInterruption
import fr.loevan.jeancalcul.domain.VoiceAudioRoute
import fr.loevan.jeancalcul.domain.VoiceAudioRouteSource
import fr.loevan.jeancalcul.domain.VoiceAudioUse
import fr.loevan.jeancalcul.domain.VoiceProviderDescriptor
import fr.loevan.jeancalcul.observability.PerformanceTrace
import fr.loevan.jeancalcul.observability.PerformanceTraceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

            assertEquals(AssistantState.Listening, controller.state.value.assistantState)
            assertEquals("Mets le volume", controller.state.value.partialTranscript)

            speechToText.emit(
                SpeechToTextEvent.Final(
                    SpeechRecognitionResult(text = "Mets le volume a 30", confidence = 0.9f),
                ),
            )
            runCurrent()

            assertTrue(controller.state.value.assistantState is AssistantState.Speaking)
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
            assertTrue(controller.state.value.assistantState is AssistantState.Error)
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
            assertTrue(controller.state.value.assistantState is AssistantState.Speaking)

            controller.cancelActiveWork()
            assertTrue(controller.state.value.assistantState is AssistantState.Cancelled)
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
            assertTrue(controller.state.value.assistantState is AssistantState.Speaking)
            controller.close()
        }

    @Test
    fun `interrupting active work returns to invoked and stops providers`() =
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
            controller.interruptActiveWork()

            assertEquals(AssistantState.Invoked, controller.assistantState.value)
            assertTrue(speechToText.cancelled)
            assertTrue(textToSpeech.stopped)
            controller.close()
        }

    @Test
    fun `speech callbacks emit the required performance milestones`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val performanceTrace = RecordingPerformanceTrace()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    performanceTrace = performanceTrace,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            speechToText.emit(SpeechToTextEvent.Ready)
            speechToText.emit(SpeechToTextEvent.SpeechStarted)
            speechToText.emit(SpeechToTextEvent.Partial("Mets le volume"))
            speechToText.emit(
                SpeechToTextEvent.Final(
                    SpeechRecognitionResult(text = "Mets le volume a 30", confidence = 0.9f),
                ),
            )
            runCurrent()

            assertEquals(
                listOf(
                    PerformanceTraceEvent.MICROPHONE_READY,
                    PerformanceTraceEvent.SPEECH_STARTED,
                    PerformanceTraceEvent.FIRST_TRANSCRIPTION,
                    PerformanceTraceEvent.FINAL_RESULT,
                ),
                performanceTrace.events,
            )
            controller.close()
        }

    @Test
    fun `microphone amplitude and bluetooth route are exposed to the UI state`() =
        runTest {
            val amplitude = FakeAmplitudeSource()
            val route = FakeAudioRouteSource()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = FakeSpeechToTextProvider(),
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    amplitudeSource = amplitude,
                    audioRouteSource = route,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            amplitude.emit(0.72f)
            route.emit(VoiceAudioRoute.BLUETOOTH)
            runCurrent()

            assertEquals(0.72f, controller.state.value.microphoneAmplitude)
            assertEquals(VoiceAudioRoute.BLUETOOTH, controller.state.value.audioRoute)
            controller.close()
        }

    @Test
    fun `recognition acquires audio focus only after the microphone is ready`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val audioFocus = FakeAudioFocusController()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    audioFocusController = audioFocus,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            assertTrue(audioFocus.requests.isEmpty())

            speechToText.emit(SpeechToTextEvent.Ready)
            runCurrent()

            assertEquals(listOf(VoiceAudioUse.RECOGNITION), audioFocus.requests)
            controller.close()
        }

    @Test
    fun `transient audio interruption resumes listening after focus returns`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val audioFocus = FakeAudioFocusController()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    audioFocusController = audioFocus,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()
            controller.startListening()
            speechToText.emit(SpeechToTextEvent.Ready)
            runCurrent()

            audioFocus.emit(VoiceAudioInterruption.LOST_TRANSIENT)
            runCurrent()
            assertEquals(AssistantState.Invoked, controller.assistantState.value)
            assertTrue(speechToText.cancelled)

            audioFocus.emit(VoiceAudioInterruption.GAINED)
            runCurrent()
            assertEquals(AssistantState.Listening, controller.assistantState.value)

            speechToText.emit(SpeechToTextEvent.Ready)
            runCurrent()
            assertEquals(listOf(VoiceAudioUse.RECOGNITION, VoiceAudioUse.RECOGNITION), audioFocus.requests)
            controller.close()
        }

    @Test
    fun `configured locale is forwarded to recognition`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    initialLocaleTag = "en-GB",
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()

            assertEquals("en-GB", speechToText.lastRequest?.locale?.languageTag)
            controller.close()
        }

    @Test
    fun `text fallback remains usable when recognition is unavailable`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider().apply { available = false }
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            assertTrue(controller.assistantState.value is AssistantState.Error)
            assertTrue(!controller.state.value.voiceInputAvailable)

            controller.updateTextFallback("Bonjour en texte")
            controller.submitTextFallback()
            assertEquals("Bonjour en texte", controller.state.value.finalResult?.text)
            assertTrue(controller.assistantState.value is AssistantState.Speaking)
            controller.close()
        }

    @Test
    fun `recognition error stops every active audio resource`() =
        runTest {
            val speechToText = FakeSpeechToTextProvider()
            val amplitude = FakeAmplitudeSource()
            val activity = FakeActivityDetector()
            val route = FakeAudioRouteSource()
            val audioFocus = FakeAudioFocusController()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = FakeTextToSpeechProvider(),
                    amplitudeSource = amplitude,
                    activityDetector = activity,
                    audioFocusController = audioFocus,
                    audioRouteSource = route,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()
            controller.startListening()

            speechToText.emit(SpeechToTextEvent.Error(fr.loevan.jeancalcul.domain.SpeechToTextError.AUDIO))
            runCurrent()

            assertTrue(speechToText.cancelled)
            assertTrue(amplitude.stopped)
            assertTrue(activity.stopped)
            assertTrue(route.stopped)
            assertTrue(audioFocus.abandoned)
            controller.close()
        }
}

private class FakeSpeechToTextProvider : SpeechToTextProvider {
    private val mutableEvents = MutableSharedFlow<SpeechToTextEvent>()

    override val events: Flow<SpeechToTextEvent> = mutableEvents.asSharedFlow()
    override val descriptor = VoiceProviderDescriptor("fake.stt", "Fake STT", true, true)
    var cancelled = false
    var released = false
    var available = true
    var lastRequest: SpeechToTextRequest? = null

    override fun isAvailable() = available

    override fun startListening(request: SpeechToTextRequest) {
        lastRequest = request
    }

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
    override val descriptor = VoiceProviderDescriptor("fake.tts", "Fake TTS", false, true)
    var spokenText: String? = null
    var stopped = false
    var released = false

    override fun isAvailable() = true

    override fun speak(request: TextToSpeechRequest) {
        spokenText = request.text
    }

    override fun stop() {
        stopped = true
    }

    override fun release() {
        released = true
    }
}

internal class RecordingPerformanceTrace : PerformanceTrace {
    val events = mutableListOf<PerformanceTraceEvent>()

    override fun startInvocation() = Unit

    override fun mark(event: PerformanceTraceEvent) {
        events += event
    }

    override fun captureMemory(checkpoint: String) = Unit

    override fun finishInvocation(reason: String) = Unit
}

private class FakeAmplitudeSource : AudioAmplitudeSource {
    private val mutableAmplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = mutableAmplitude
    var stopped = false

    override fun start() {
        stopped = false
    }

    override fun stop() {
        stopped = true
        mutableAmplitude.value = 0f
    }

    override fun release() = Unit

    fun emit(value: Float) {
        mutableAmplitude.value = value
    }
}

private class FakeActivityDetector : VoiceActivityDetector {
    override val activity: StateFlow<VoiceActivity> = MutableStateFlow(VoiceActivity.SILENCE)
    var stopped = false

    override fun start() {
        stopped = false
    }

    override fun stop() {
        stopped = true
    }

    override fun release() = Unit
}

private class FakeAudioFocusController : VoiceAudioFocusController {
    private val mutableInterruptions = MutableSharedFlow<VoiceAudioInterruption>()
    override val interruptions: Flow<VoiceAudioInterruption> = mutableInterruptions.asSharedFlow()
    val requests = mutableListOf<VoiceAudioUse>()
    var abandoned = false

    override fun request(audioUse: VoiceAudioUse): Boolean {
        requests += audioUse
        abandoned = false
        return true
    }

    override fun abandon() {
        abandoned = true
    }

    override fun release() = Unit

    suspend fun emit(interruption: VoiceAudioInterruption) {
        mutableInterruptions.emit(interruption)
    }
}

private class FakeAudioRouteSource : VoiceAudioRouteSource {
    private val mutableRoute = MutableStateFlow(VoiceAudioRoute.UNKNOWN)
    override val route: StateFlow<VoiceAudioRoute> = mutableRoute
    var stopped = false

    override fun start() {
        stopped = false
    }

    override fun stop() {
        stopped = true
    }

    override fun release() = Unit

    fun emit(value: VoiceAudioRoute) {
        mutableRoute.value = value
    }
}
