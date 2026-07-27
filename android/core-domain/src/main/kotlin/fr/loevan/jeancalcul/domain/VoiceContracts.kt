package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Stable identifier and capabilities used by future voice-engine selection screens. */
data class VoiceProviderDescriptor(
    val id: String,
    val displayName: String,
    val supportsPartialResults: Boolean,
    val isOnDevice: Boolean,
)

@JvmInline
value class VoiceLocale(val languageTag: String) {
    init {
        require(languageTag.isNotBlank()) { "A voice locale must use a non-blank BCP 47 tag." }
    }
}

data class SpeechToTextRequest(
    val locale: VoiceLocale,
    val partialResults: Boolean = true,
    val completeSilenceMillis: Long = 1_500L,
    val possibleSilenceMillis: Long = 750L,
) {
    init {
        require(completeSilenceMillis > 0L)
        require(possibleSilenceMillis > 0L)
        require(possibleSilenceMillis <= completeSilenceMillis)
    }
}

data class TextToSpeechRequest(
    val text: String,
    val locale: VoiceLocale,
) {
    init {
        require(text.isNotBlank())
    }
}

/**
 * Platform-neutral speech-recognition contract.
 *
 * Implementations emit partial hypotheses while the user speaks and one structured final result.
 * [cancel] must release the active microphone so another interaction can start safely.
 */
interface SpeechToTextProvider {
    val descriptor: VoiceProviderDescriptor
    val events: Flow<SpeechToTextEvent>

    fun isAvailable(): Boolean

    fun startListening(request: SpeechToTextRequest)

    fun stopListening()

    fun cancel()

    fun release()
}

sealed interface SpeechToTextEvent {
    data object Ready : SpeechToTextEvent

    data object SpeechStarted : SpeechToTextEvent

    data class Partial(val text: String) : SpeechToTextEvent

    data object EndOfSpeech : SpeechToTextEvent

    data class Final(val result: SpeechRecognitionResult) : SpeechToTextEvent

    data class Error(val error: SpeechToTextError) : SpeechToTextEvent
}

data class SpeechRecognitionResult(
    val text: String,
    val confidence: Float?,
)

enum class SpeechToTextError {
    UNAVAILABLE,
    PERMISSION_DENIED,
    AUDIO,
    BUSY,
    CLIENT,
    NETWORK,
    LANGUAGE_UNSUPPORTED,
    NO_MATCH,
    TIMEOUT,
    UNKNOWN,
}

/** Platform-neutral speech-synthesis contract. */
interface TextToSpeechProvider {
    val descriptor: VoiceProviderDescriptor
    val events: Flow<TextToSpeechEvent>

    fun isAvailable(): Boolean

    fun speak(request: TextToSpeechRequest)

    fun stop()

    fun release()
}

sealed interface TextToSpeechEvent {
    data object Ready : TextToSpeechEvent

    data object Started : TextToSpeechEvent

    data object Completed : TextToSpeechEvent

    data object Stopped : TextToSpeechEvent

    data class Error(val error: TextToSpeechError) : TextToSpeechEvent
}

enum class TextToSpeechError {
    UNAVAILABLE,
    NOT_READY,
    LANGUAGE_UNSUPPORTED,
    SYNTHESIS_FAILED,
}

/** Normalized real-time microphone level in the inclusive 0..1 range. */
interface AudioAmplitudeSource {
    val amplitude: StateFlow<Float>

    fun start()

    fun stop()

    fun release()
}

enum class VoiceActivity {
    SILENCE,
    SPEECH,
}

/** Converts microphone levels into stable speech/silence states for UI and timeout handling. */
interface VoiceActivityDetector {
    val activity: StateFlow<VoiceActivity>

    fun start()

    fun stop()

    fun release()
}

enum class VoiceAudioUse {
    RECOGNITION,
    SYNTHESIS,
}

enum class VoiceAudioInterruption {
    LOST_TRANSIENT,
    LOST_PERMANENT,
    GAINED,
}

interface VoiceAudioFocusController {
    val interruptions: Flow<VoiceAudioInterruption>

    fun request(audioUse: VoiceAudioUse): Boolean

    fun abandon()

    fun release()
}

enum class VoiceAudioRoute {
    BUILT_IN,
    WIRED,
    BLUETOOTH,
    UNKNOWN,
}

interface VoiceAudioRouteSource {
    val route: StateFlow<VoiceAudioRoute>

    fun start()

    fun stop()

    fun release()
}
