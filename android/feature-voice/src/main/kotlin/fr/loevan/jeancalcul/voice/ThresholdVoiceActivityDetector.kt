package fr.loevan.jeancalcul.voice

import fr.loevan.jeancalcul.domain.AudioAmplitudeSource
import fr.loevan.jeancalcul.domain.VoiceActivity
import fr.loevan.jeancalcul.domain.VoiceActivityDetector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ThresholdVoiceActivityDetector(
    private val amplitudeSource: AudioAmplitudeSource,
    private val speechThreshold: Float = 0.16f,
    private val silenceHoldMillis: Long = 650L,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VoiceActivityDetector {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableActivity = MutableStateFlow(VoiceActivity.SILENCE)
    private var collectionJob: Job? = null

    init {
        require(speechThreshold in 0f..1f)
        require(silenceHoldMillis >= 0L)
    }

    override val activity: StateFlow<VoiceActivity> = mutableActivity.asStateFlow()

    override fun start() {
        if (collectionJob != null) return
        collectionJob =
            scope.launch {
                amplitudeSource.amplitude.collectLatest { amplitude ->
                    if (amplitude >= speechThreshold) {
                        mutableActivity.value = VoiceActivity.SPEECH
                    } else if (mutableActivity.value == VoiceActivity.SPEECH) {
                        delay(silenceHoldMillis)
                        mutableActivity.value = VoiceActivity.SILENCE
                    }
                }
            }
    }

    override fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        mutableActivity.value = VoiceActivity.SILENCE
    }

    override fun release() {
        stop()
        scope.cancel()
    }
}
