@file:Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.assistant.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.ui.AmbientGlow
import fr.loevan.jeancalcul.ui.GlassSurface
import fr.loevan.jeancalcul.ui.GlassSurfaceVariant
import fr.loevan.jeancalcul.ui.GradientOrb
import fr.loevan.jeancalcul.ui.GradientOrbState
import fr.loevan.jeancalcul.ui.JeanCalculButton
import fr.loevan.jeancalcul.ui.JeanCalculButtonVariant
import fr.loevan.jeancalcul.ui.JeanCalculTextField
import fr.loevan.jeancalcul.ui.PrivacyIndicator
import fr.loevan.jeancalcul.ui.PrivacyIndicatorState
import fr.loevan.jeancalcul.ui.StatusBadge
import fr.loevan.jeancalcul.ui.StatusBadgeState
import fr.loevan.jeancalcul.ui.VoiceWave
import fr.loevan.jeancalcul.ui.VoiceWaveState
import fr.loevan.jeancalcul.ui.jeanCalculTheme

/**
 * Transparent assistant-session composition. Its glass treatment is tonal and remains readable
 * without any system backdrop blur.
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
                    .background(
                        Brush.verticalGradient(
                            listOf(TransparentSessionScrimTop, TransparentSessionScrimBottom),
                        ),
                    ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AmbientGlow(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(360.dp),
                active = voiceState.status in ActiveGlowStatuses,
            )
            GlassSurface(
                modifier =
                    Modifier
                        .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 32.dp)
                        .fillMaxWidth()
                        .widthIn(max = 430.dp)
                        .heightIn(min = 400.dp, max = 640.dp),
                variant = GlassSurfaceVariant.Modal,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
    GradientOrb(
        state = voiceState.orbState(visualState),
        amplitude = voiceState.visualAmplitude(),
        progress = 0.42f,
        orbSize = 112.dp,
    )
    VoiceWave(
        state = voiceState.waveState(),
        amplitude = voiceState.visualAmplitude(),
        progress = 0.42f,
        modifier = Modifier.size(width = 184.dp, height = 40.dp),
    )
    Text(
        text = voiceState.title(visualState),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Text(
        text = voiceState.message,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrivacyIndicator(
            state =
                if (voiceState.status == VoiceSessionStatus.LISTENING) {
                    PrivacyIndicatorState.MicrophoneActive
                } else {
                    PrivacyIndicatorState.MicrophoneInactive
                },
        )
        StatusBadge(voiceState.statusBadge())
    }
    PrivacyIndicator(
        state = voiceState.processingIndicator(),
        destination = "Services vocaux Android",
    )
    if (voiceState.partialTranscript.isNotBlank()) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassSurfaceVariant.Selected,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TRANSCRIPTION", style = MaterialTheme.typography.labelSmall)
                Text(text = voiceState.partialTranscript, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    voiceState.finalResult?.let { result ->
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            variant = GlassSurfaceVariant.Card,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("RÉSULTAT", style = MaterialTheme.typography.labelSmall)
                Text(text = result.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun voiceSessionTextFallback(
    voiceState: VoiceSessionState,
    actions: VoiceSessionActions,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), variant = GlassSurfaceVariant.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            JeanCalculTextField(
                value = voiceState.partialTranscript,
                onValueChange = actions::textChanged,
                label = "Saisie texte de secours",
            )
            JeanCalculButton(
                label = "Utiliser le texte",
                modifier = Modifier.fillMaxWidth(),
                variant = JeanCalculButtonVariant.Secondary,
                onClick = actions::submitText,
            )
        }
    }
}

@Composable
private fun voiceSessionControls(
    voiceState: VoiceSessionState,
    actions: VoiceSessionActions,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (voiceState.status == VoiceSessionStatus.CONFIRMATION_REQUIRED) {
            JeanCalculButton(
                label = "Confirmer l’action",
                modifier = Modifier.fillMaxWidth(),
                onClick = actions::confirmVoiceCommand,
            )
        } else if (voiceState.status == VoiceSessionStatus.PERMISSION_REQUIRED) {
            JeanCalculButton(
                label = "Autoriser le microphone",
                modifier = Modifier.fillMaxWidth(),
                onClick = actions::requestMicrophonePermission,
            )
        } else {
            JeanCalculButton(
                label = if (voiceState.status == VoiceSessionStatus.LISTENING) "Écoute en cours" else "Écouter",
                modifier = Modifier.fillMaxWidth(),
                enabled = voiceState.status != VoiceSessionStatus.LISTENING,
                onClick = actions::startListening,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JeanCalculButton(
                label = "Tester la voix",
                modifier = Modifier.weight(1f),
                variant = JeanCalculButtonVariant.Ghost,
                onClick = actions::speakTestResponse,
            )
            JeanCalculButton(
                label = "Interrompre",
                modifier = Modifier.weight(1f),
                variant = JeanCalculButtonVariant.Secondary,
                onClick = actions::cancelVoice,
            )
        }
    }
}

private fun VoiceSessionState.orbState(visualState: AssistantSessionVisualState): GradientOrbState =
    if (visualState == AssistantSessionVisualState.ERROR) {
        GradientOrbState.Error
    } else {
        when (status) {
            VoiceSessionStatus.INVOKED -> GradientOrbState.Invoked
            VoiceSessionStatus.PERMISSION_REQUIRED -> GradientOrbState.Offline
            VoiceSessionStatus.LISTENING -> GradientOrbState.Listening
            VoiceSessionStatus.PROCESSING -> GradientOrbState.Transcribing
            VoiceSessionStatus.CONFIRMATION_REQUIRED -> GradientOrbState.WaitingApproval
            VoiceSessionStatus.SPEAKING -> GradientOrbState.Speaking
            VoiceSessionStatus.ERROR -> GradientOrbState.Error
        }
    }

private fun VoiceSessionState.waveState(): VoiceWaveState =
    when (status) {
        VoiceSessionStatus.INVOKED -> VoiceWaveState.Waiting
        VoiceSessionStatus.PERMISSION_REQUIRED,
        VoiceSessionStatus.ERROR,
        -> VoiceWaveState.MicrophoneUnavailable

        VoiceSessionStatus.LISTENING -> VoiceWaveState.Listening
        VoiceSessionStatus.PROCESSING -> VoiceWaveState.Static
        VoiceSessionStatus.CONFIRMATION_REQUIRED -> VoiceWaveState.Silence
        VoiceSessionStatus.SPEAKING -> VoiceWaveState.Speaking
    }

private fun VoiceSessionState.visualAmplitude(): Float =
    when (status) {
        VoiceSessionStatus.LISTENING -> 0.64f
        VoiceSessionStatus.SPEAKING -> 0.54f
        VoiceSessionStatus.PROCESSING -> 0.34f
        else -> 0.16f
    }

private fun VoiceSessionState.statusBadge(): StatusBadgeState =
    when (status) {
        VoiceSessionStatus.LISTENING,
        VoiceSessionStatus.PROCESSING,
        VoiceSessionStatus.SPEAKING,
        -> StatusBadgeState.Active

        VoiceSessionStatus.CONFIRMATION_REQUIRED -> StatusBadgeState.Warning
        VoiceSessionStatus.PERMISSION_REQUIRED -> StatusBadgeState.Permission
        VoiceSessionStatus.ERROR -> StatusBadgeState.Error
        VoiceSessionStatus.INVOKED -> StatusBadgeState.Available
    }

private fun VoiceSessionState.processingIndicator(): PrivacyIndicatorState =
    when (status) {
        VoiceSessionStatus.PROCESSING,
        VoiceSessionStatus.CONFIRMATION_REQUIRED,
        -> PrivacyIndicatorState.LocalProcessing

        else -> PrivacyIndicatorState.DestinationVisible
    }

private fun VoiceSessionState.title(visualState: AssistantSessionVisualState): String =
    when (status) {
        VoiceSessionStatus.INVOKED ->
            if (visualState == AssistantSessionVisualState.ERROR) {
                "Assistant indisponible"
            } else {
                "Assistant invoqué"
            }

        VoiceSessionStatus.PERMISSION_REQUIRED -> "Microphone requis"
        VoiceSessionStatus.LISTENING -> "Je vous écoute…"
        VoiceSessionStatus.PROCESSING -> "Transcription en cours"
        VoiceSessionStatus.CONFIRMATION_REQUIRED -> "Confirmation requise"
        VoiceSessionStatus.SPEAKING -> "Réponse vocale"
        VoiceSessionStatus.ERROR -> "Assistant indisponible"
    }

private val ActiveGlowStatuses =
    setOf(
        VoiceSessionStatus.LISTENING,
        VoiceSessionStatus.PROCESSING,
        VoiceSessionStatus.CONFIRMATION_REQUIRED,
        VoiceSessionStatus.SPEAKING,
    )

private val TransparentSessionScrimTop = Color(0x66050A0E)
private val TransparentSessionScrimBottom = Color(0xD90B0F10)
