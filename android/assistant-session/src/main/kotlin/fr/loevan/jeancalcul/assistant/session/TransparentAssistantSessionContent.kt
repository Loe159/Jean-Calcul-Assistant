package fr.loevan.jeancalcul.assistant.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.ui.jeanCalculTheme

/**
 * Translucent fallback is always present, so the session remains legible when system blur is absent.
 */
@Composable
internal fun transparentAssistantSessionContent(
    visualState: AssistantSessionVisualState,
    voiceState: VoiceSessionState,
    actions: VoiceSessionActions,
) {
    jeanCalculTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(TransparentSessionScrim),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .size(width = 320.dp, height = 384.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    voiceSessionStatusContent(visualState = visualState, voiceState = voiceState)
                    voiceSessionTextFallback(voiceState = voiceState, actions = actions)
                    voiceSessionControls(voiceState = voiceState, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun voiceSessionStatusContent(
    visualState: AssistantSessionVisualState,
    voiceState: VoiceSessionState,
) {
    Text(
        text = voiceState.title(visualState),
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = voiceState.message,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (voiceState.partialTranscript.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = voiceState.partialTranscript,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    voiceState.finalResult?.let { result ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Resultat final : ${result.text}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun voiceSessionTextFallback(
    voiceState: VoiceSessionState,
    actions: VoiceSessionActions,
) {
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = voiceState.partialTranscript,
        onValueChange = actions::textChanged,
        label = { Text("Saisie texte de secours") },
        singleLine = true,
    )
    Button(
        modifier = Modifier.padding(top = 8.dp),
        onClick = actions::submitText,
    ) {
        Text("Utiliser le texte")
    }
}

@Composable
private fun voiceSessionControls(
    voiceState: VoiceSessionState,
    actions: VoiceSessionActions,
) {
    Spacer(Modifier.height(8.dp))
    if (voiceState.status == VoiceSessionStatus.PERMISSION_REQUIRED) {
        Button(onClick = actions::requestMicrophonePermission) {
            Text("Autoriser le microphone")
        }
    } else {
        Button(onClick = actions::startListening) {
            Text("Ecouter")
        }
    }
    Button(
        modifier = Modifier.padding(top = 8.dp),
        onClick = actions::speakTestResponse,
    ) {
        Text("Lire la reponse de test")
    }
    if (voiceState.status == VoiceSessionStatus.CONFIRMATION_REQUIRED) {
        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = actions::confirmVoiceCommand,
        ) {
            Text("Confirmer")
        }
    }
    Button(
        modifier = Modifier.padding(top = 8.dp),
        onClick = actions::cancelVoice,
    ) {
        Text("Interrompre")
    }
}

private fun VoiceSessionState.title(visualState: AssistantSessionVisualState): String =
    when (status) {
        VoiceSessionStatus.INVOKED ->
            if (visualState == AssistantSessionVisualState.ERROR) {
                "Assistant indisponible"
            } else {
                "Assistant invoque"
            }

        VoiceSessionStatus.PERMISSION_REQUIRED -> "Microphone requis"
        VoiceSessionStatus.LISTENING -> "A l'ecoute"
        VoiceSessionStatus.PROCESSING -> "Transcription en cours"
        VoiceSessionStatus.CONFIRMATION_REQUIRED -> "Confirmation requise"
        VoiceSessionStatus.SPEAKING -> "Reponse vocale"
        VoiceSessionStatus.ERROR -> "Assistant indisponible"
    }

private val TransparentSessionScrim = Color(0x33050B16)
