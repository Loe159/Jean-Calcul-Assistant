package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow

/**
 * Platform-neutral speech-recognition contract.
 *
 * Implementations emit partial hypotheses while the user speaks and one structured final result
 * once recognition completes. They do not retain audio after [release].
 */
interface SpeechToTextProvider {
    val events: Flow<SpeechToTextEvent>

    fun startListening()

    fun stopListening()

    fun cancel()

    fun release()
}

sealed interface SpeechToTextEvent {
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
    AUDIO,
    CLIENT,
    NETWORK,
    NO_MATCH,
    TIMEOUT,
    UNKNOWN,
}

/** Platform-neutral speech-synthesis contract. */
interface TextToSpeechProvider {
    val events: Flow<TextToSpeechEvent>

    fun speak(text: String)

    fun stop()

    fun release()
}

sealed interface TextToSpeechEvent {
    data object Ready : TextToSpeechEvent

    data object Started : TextToSpeechEvent

    data object Completed : TextToSpeechEvent

    data object Stopped : TextToSpeechEvent

    data class Error(val message: String) : TextToSpeechEvent
}
