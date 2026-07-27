package fr.loevan.jeancalcul.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import fr.loevan.jeancalcul.domain.VoiceAudioFocusController
import fr.loevan.jeancalcul.domain.VoiceAudioInterruption
import fr.loevan.jeancalcul.domain.VoiceAudioUse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidAudioFocusController(
    context: Context,
) : VoiceAudioFocusController {
    private val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
    private val mutableInterruptions = MutableSharedFlow<VoiceAudioInterruption>(extraBufferCapacity = 4)
    private var activeRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener(::onAudioFocusChanged)

    override val interruptions: Flow<VoiceAudioInterruption> = mutableInterruptions.asSharedFlow()

    override fun request(audioUse: VoiceAudioUse): Boolean {
        abandon()
        val request =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).setOnAudioFocusChangeListener(focusListener)
                .setWillPauseWhenDucked(audioUse == VoiceAudioUse.RECOGNITION)
                .build()
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        activeRequest = request.takeIf { granted }
        return granted
    }

    override fun abandon() {
        activeRequest?.let(audioManager::abandonAudioFocusRequest)
        activeRequest = null
    }

    override fun release() = abandon()

    private fun onAudioFocusChanged(change: Int) {
        val interruption =
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> VoiceAudioInterruption.GAINED
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> VoiceAudioInterruption.LOST_TRANSIENT

                AudioManager.AUDIOFOCUS_LOSS -> VoiceAudioInterruption.LOST_PERMANENT
                else -> return
            }
        mutableInterruptions.tryEmit(interruption)
    }
}
