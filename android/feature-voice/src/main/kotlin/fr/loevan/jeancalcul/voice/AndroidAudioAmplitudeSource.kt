package fr.loevan.jeancalcul.voice

import fr.loevan.jeancalcul.domain.AudioAmplitudeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidAudioAmplitudeSource : AudioAmplitudeSource {
    private val mutableAmplitude = MutableStateFlow(0f)
    private var active = false

    override val amplitude: StateFlow<Float> = mutableAmplitude.asStateFlow()

    override fun start() {
        active = true
        mutableAmplitude.value = 0f
    }

    override fun stop() {
        active = false
        mutableAmplitude.value = 0f
    }

    override fun release() = stop()

    fun updateRms(rmsDb: Float) {
        if (active) mutableAmplitude.value = normalizeRms(rmsDb)
    }
}

internal fun normalizeRms(rmsDb: Float): Float = ((rmsDb - MIN_RMS_DB) / (MAX_RMS_DB - MIN_RMS_DB)).coerceIn(0f, 1f)

private const val MIN_RMS_DB = -2f
private const val MAX_RMS_DB = 10f
