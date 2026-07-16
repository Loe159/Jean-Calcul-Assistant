package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.SpeechRecognitionResult

internal data class VoiceSessionState(
    val status: VoiceSessionStatus = VoiceSessionStatus.INVOKED,
    val partialTranscript: String = "",
    val finalResult: SpeechRecognitionResult? = null,
    val confirmationPrompt: String? = null,
    val message: String = "Preparation de la session vocale",
)

internal enum class VoiceSessionStatus {
    INVOKED,
    PERMISSION_REQUIRED,
    LISTENING,
    PROCESSING,
    CONFIRMATION_REQUIRED,
    SPEAKING,
    ERROR,
}
