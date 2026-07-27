package fr.loevan.jeancalcul.voice

import android.media.AudioDeviceInfo
import fr.loevan.jeancalcul.domain.AudioAmplitudeSource
import fr.loevan.jeancalcul.domain.VoiceActivity
import fr.loevan.jeancalcul.domain.VoiceAudioRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSignalProcessingTest {
    @Test
    fun `rms levels are normalized and clamped for the UI`() {
        assertEquals(0f, normalizeRms(-20f))
        assertEquals(0.5f, normalizeRms(4f))
        assertEquals(1f, normalizeRms(30f))
    }

    @Test
    fun `bluetooth route wins over other connected microphone routes`() {
        assertEquals(
            VoiceAudioRoute.BLUETOOTH,
            classifyAudioRoute(
                setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_BLUETOOTH_SCO),
            ),
        )
        assertEquals(
            VoiceAudioRoute.WIRED,
            classifyAudioRoute(setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_WIRED_HEADSET)),
        )
        assertEquals(VoiceAudioRoute.BUILT_IN, classifyAudioRoute(setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC)))
    }

    @Test
    fun `voice activity waits for stable silence before leaving speech`() =
        runTest {
            val source = FakeAmplitudeSource()
            val detector =
                ThresholdVoiceActivityDetector(
                    amplitudeSource = source,
                    speechThreshold = 0.2f,
                    silenceHoldMillis = 650L,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            detector.start()
            runCurrent()
            source.emit(0.7f)
            runCurrent()
            assertEquals(VoiceActivity.SPEECH, detector.activity.value)

            source.emit(0.05f)
            runCurrent()
            advanceTimeBy(649L)
            runCurrent()
            assertEquals(VoiceActivity.SPEECH, detector.activity.value)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(VoiceActivity.SILENCE, detector.activity.value)
            detector.release()
        }
}

private class FakeAmplitudeSource : AudioAmplitudeSource {
    private val mutableAmplitude = MutableStateFlow(0f)
    override val amplitude: StateFlow<Float> = mutableAmplitude

    override fun start() = Unit

    override fun stop() = Unit

    override fun release() = Unit

    fun emit(value: Float) {
        mutableAmplitude.value = value
    }
}
