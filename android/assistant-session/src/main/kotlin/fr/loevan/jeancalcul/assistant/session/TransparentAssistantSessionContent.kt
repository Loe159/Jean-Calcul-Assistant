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
import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.ui.ActionCard
import fr.loevan.jeancalcul.ui.ActionCardData
import fr.loevan.jeancalcul.ui.ActionRisk
import fr.loevan.jeancalcul.ui.AmbientGlow
import fr.loevan.jeancalcul.ui.GlassSurface
import fr.loevan.jeancalcul.ui.GlassSurfaceVariant
import fr.loevan.jeancalcul.ui.GradientOrb
import fr.loevan.jeancalcul.ui.JeanCalculButton
import fr.loevan.jeancalcul.ui.JeanCalculButtonVariant
import fr.loevan.jeancalcul.ui.JeanCalculTextField
import fr.loevan.jeancalcul.ui.PrivacyIndicator
import fr.loevan.jeancalcul.ui.PrivacyIndicatorState
import fr.loevan.jeancalcul.ui.StatusBadge
import fr.loevan.jeancalcul.ui.VoiceWave
import fr.loevan.jeancalcul.ui.jeanCalculTheme

/** Transparent assistant-session composition driven by the shared assistant lifecycle. */
@Composable
internal fun transparentAssistantSessionContent(
    voiceState: VoiceSessionState,
    actions: VoiceSessionActions,
) {
    val presentation = voiceState.presentation()
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
                active = presentation.activeGlow,
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
                    voiceSessionStatusContent(voiceState, presentation)
                    voiceSessionTextFallback(voiceState, actions)
                    voiceSessionControls(voiceState, actions)
                }
            }
        }
    }
}

@Composable
private fun voiceSessionStatusContent(
    voiceState: VoiceSessionState,
    presentation: AssistantStatePresentation,
) {
    GradientOrb(
        state = presentation.orbState,
        amplitude = presentation.visualAmplitude(),
        progress = 0.42f,
        orbSize = 112.dp,
    )
    VoiceWave(
        state = presentation.waveState,
        amplitude = presentation.visualAmplitude(),
        progress = 0.42f,
        modifier = Modifier.size(width = 184.dp, height = 40.dp),
    )
    Text(
        text = presentation.title,
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
        PrivacyIndicator(state = presentation.microphoneState)
        StatusBadge(presentation.badgeState)
    }
    PrivacyIndicator(
        state = presentation.processingState,
        destination =
            if (presentation.processingState == PrivacyIndicatorState.DestinationVisible) {
                "Profil selectionne"
            } else {
                null
            },
    )
    actionCard(presentation)
    transcriptCards(voiceState)
}

@Composable
private fun actionCard(presentation: AssistantStatePresentation) {
    val actionState = presentation.actionState ?: return
    val summary = presentation.actionSummary ?: return
    ActionCard(
        data =
            ActionCardData(
                title = "Action Android",
                summary = summary,
                risk = ActionRisk.R2,
                origin = "Assistant local",
                state = actionState,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun transcriptCards(voiceState: VoiceSessionState) {
    if (voiceState.partialTranscript.isNotBlank()) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), variant = GlassSurfaceVariant.Selected) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("TRANSCRIPTION", style = MaterialTheme.typography.labelSmall)
                Text(text = voiceState.partialTranscript, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    voiceState.finalResult?.let { result ->
        GlassSurface(modifier = Modifier.fillMaxWidth(), variant = GlassSurfaceVariant.Card) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("RESULTAT", style = MaterialTheme.typography.labelSmall)
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
                enabled = voiceState.assistantState == AssistantState.Invoked,
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
    val current = voiceState.assistantState
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            current is AssistantState.WaitingApproval ->
                JeanCalculButton(
                    label = "Confirmer l'action",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions::confirmVoiceCommand,
                )

            voiceState.microphonePermissionRequired ->
                JeanCalculButton(
                    label = "Autoriser le microphone",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions::requestMicrophonePermission,
                )

            else ->
                JeanCalculButton(
                    label = if (current == AssistantState.Listening) "Ecoute en cours" else "Ecouter",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !current.isInterruptible(),
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
                enabled = !current.isInterruptible(),
                onClick = actions::speakTestResponse,
            )
            JeanCalculButton(
                label = "Interrompre",
                modifier = Modifier.weight(1f),
                variant = JeanCalculButtonVariant.Secondary,
                enabled = current.isInterruptible(),
                onClick = actions::interruptVoice,
            )
        }
    }
}

private fun AssistantStatePresentation.visualAmplitude(): Float =
    when (waveState) {
        fr.loevan.jeancalcul.ui.VoiceWaveState.Listening -> 0.64f
        fr.loevan.jeancalcul.ui.VoiceWaveState.Speaking -> 0.54f
        fr.loevan.jeancalcul.ui.VoiceWaveState.Static -> 0.34f
        else -> 0.16f
    }

private val TransparentSessionScrimTop = Color(0x66050A0E)
private val TransparentSessionScrimBottom = Color(0xD90B0F10)
