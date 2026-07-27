package fr.loevan.jeancalcul.voice

import android.content.Context
import fr.loevan.jeancalcul.domain.AudioAmplitudeSource
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.domain.VoiceActivityDetector
import fr.loevan.jeancalcul.domain.VoiceAudioFocusController
import fr.loevan.jeancalcul.domain.VoiceAudioRouteSource

data class VoiceEngineSelection(
    val speechToTextProviderId: String = AndroidSpeechToTextProvider.ID,
    val textToSpeechProviderId: String = AndroidTextToSpeechProvider.ID,
)

/** One replaceable set of voice providers and Android audio signals. */
data class VoicePipeline(
    val speechToTextProvider: SpeechToTextProvider,
    val textToSpeechProvider: TextToSpeechProvider,
    val amplitudeSource: AudioAmplitudeSource,
    val activityDetector: VoiceActivityDetector,
    val audioFocusController: VoiceAudioFocusController,
    val audioRouteSource: VoiceAudioRouteSource,
)

fun interface VoicePipelineFactory {
    fun create(selection: VoiceEngineSelection): VoicePipeline
}

/** Default factory. A settings-backed factory can replace it without changing the session. */
class AndroidVoicePipelineFactory(
    private val context: Context,
) : VoicePipelineFactory {
    override fun create(selection: VoiceEngineSelection): VoicePipeline {
        require(selection.speechToTextProviderId == AndroidSpeechToTextProvider.ID) {
            "Unknown speech-to-text provider: ${selection.speechToTextProviderId}"
        }
        require(selection.textToSpeechProviderId == AndroidTextToSpeechProvider.ID) {
            "Unknown text-to-speech provider: ${selection.textToSpeechProviderId}"
        }

        val amplitudeSource = AndroidAudioAmplitudeSource()
        return VoicePipeline(
            speechToTextProvider = AndroidSpeechToTextProvider(context, amplitudeSource),
            textToSpeechProvider = AndroidTextToSpeechProvider(context),
            amplitudeSource = amplitudeSource,
            activityDetector = ThresholdVoiceActivityDetector(amplitudeSource),
            audioFocusController = AndroidAudioFocusController(context),
            audioRouteSource = AndroidAudioRouteSource(context),
        )
    }
}
