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
class VoiceVolumeEndToEndInstrumentedTest {
    @Test
    fun volume_command_scenario_is_reproducible() =
        runTest {
            val speechToText = InstrumentedSpeechToTextProvider()
            val textToSpeech = InstrumentedTextToSpeechProvider()
            val volumeController = InstrumentedVolumeController()
            val session =
                VoiceSessionController(
                    speechToTextProvider = speechToText,
                    textToSpeechProvider = textToSpeech,
                    voiceCommandProcessor =
                        VolumeCommandProcessor(
                            DeterministicVolumeCommandInterpreter { "instrumented-action" },
                            VolumeToolBridge(volumeController, ToolAuditLogger { }),
                        ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()

            session.startListening()
            speechToText.emit(
                SpeechToTextEvent.Final(
                    SpeechRecognitionResult("Mets le volume a 30 %", confidence = 1f),
                ),
            )
            runCurrent()

            assertEquals(3, volumeController.volume.current)
            assertEquals("Le volume de musique est maintenant a 30 %.", textToSpeech.lastSpokenText)
            session.close()
        }

    private class InstrumentedSpeechToTextProvider : SpeechToTextProvider {
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

    private class InstrumentedTextToSpeechProvider : TextToSpeechProvider {
        override val events: Flow<TextToSpeechEvent> = MutableSharedFlow<TextToSpeechEvent>().asSharedFlow()
        var lastSpokenText: String? = null

        override fun speak(text: String) {
            lastSpokenText = text
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class InstrumentedVolumeController : VolumeController {
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
