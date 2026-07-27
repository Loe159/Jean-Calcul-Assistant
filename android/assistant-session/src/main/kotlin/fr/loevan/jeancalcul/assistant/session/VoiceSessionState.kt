package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult

internal data class VoiceSessionState(
    val assistantState: AssistantState = AssistantState.Idle,
    val partialTranscript: String = "",
    val finalResult: SpeechRecognitionResult? = null,
    val confirmationPrompt: String? = null,
    val microphonePermissionRequired: Boolean = false,
    val message: String = "Assistant pret.",
)
