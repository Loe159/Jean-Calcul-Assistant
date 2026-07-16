package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.DeterministicVolumeCommandInterpreter
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.toolbridge.PlatformVolume
import fr.loevan.jeancalcul.toolbridge.VolumeController
import fr.loevan.jeancalcul.toolbridge.VolumeToolBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceVolumeEndToEndScenarioTest {
    @Test
    fun `transcription applies music volume then displays and speaks observed result`() =
        runTest {
            val speechToText = ScenarioSpeechToTextProvider()
            val textToSpeech = ScenarioTextToSpeechProvider()
            val volumeController = ScenarioVolumeController()
            val controller =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = textToSpeech,
                    voiceCommandProcessor =
                        VolumeCommandProcessor(
                            interpreter = DeterministicVolumeCommandInterpreter { "scenario-action" },
                            volumeToolBridge = VolumeToolBridge(volumeController, ToolAuditLogger { }),
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            controller.startListening()
            speechToText.emit(
                SpeechToTextEvent.Final(
                    SpeechRecognitionResult(text = "Mets le volume a 30 %", confidence = 1f),
                ),
            )
            runCurrent()

            assertEquals(3, volumeController.volume.current)
            assertEquals(VoiceSessionStatus.SPEAKING, controller.state.value.status)
            assertEquals("Le volume de musique est maintenant a 30 %.", controller.state.value.message)
            assertEquals("Le volume de musique est maintenant a 30 %.", textToSpeech.lastSpokenText)
            controller.close()
        }

    private class ScenarioSpeechToTextProvider : SpeechToTextProvider {
        private val mutableEvents = MutableSharedFlow<SpeechToTextEvent>()

        override val events: Flow<SpeechToTextEvent> = mutableEvents.asSharedFlow()

        override fun startListening() = Unit

        override fun stopListening() = Unit

        override fun cancel() = Unit

        override fun release() = Unit

        suspend fun emit(event: SpeechToTextEvent) {
            mutableEvents.emit(event)
        }
    }

    private class ScenarioTextToSpeechProvider : TextToSpeechProvider {
        override val events: Flow<TextToSpeechEvent> = MutableSharedFlow<TextToSpeechEvent>().asSharedFlow()
        var lastSpokenText: String? = null

        override fun speak(text: String) {
            lastSpokenText = text
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class ScenarioVolumeController : VolumeController {
        var volume = PlatformVolume(current = 5, maximum = 10)

        override fun read(stream: VolumeStream): PlatformVolume = volume

        override fun write(
            stream: VolumeStream,
            volume: Int,
        ) {
            this.volume = this.volume.copy(current = volume)
        }
    }
}
