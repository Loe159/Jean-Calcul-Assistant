package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.VoiceActivity
import fr.loevan.jeancalcul.domain.VoiceAudioRoute

internal data class VoiceSessionState(
    val assistantState: AssistantState = AssistantState.Idle,
    val partialTranscript: String = "",
    val finalResult: SpeechRecognitionResult? = null,
    val confirmationPrompt: String? = null,
    val microphonePermissionRequired: Boolean = false,
    val voiceInputAvailable: Boolean = true,
    val microphoneAmplitude: Float = 0f,
    val voiceActivity: VoiceActivity = VoiceActivity.SILENCE,
    val audioRoute: VoiceAudioRoute = VoiceAudioRoute.UNKNOWN,
    val localeTag: String = "fr-FR",
    val message: String = "Assistant pret.",
)
