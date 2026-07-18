@file:Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.ui.AmbientGlow
import fr.loevan.jeancalcul.ui.GlassSurface
import fr.loevan.jeancalcul.ui.GlassSurfaceVariant
import fr.loevan.jeancalcul.ui.GradientOrb
import fr.loevan.jeancalcul.ui.GradientOrbState
import fr.loevan.jeancalcul.ui.JeanCalculButton
import fr.loevan.jeancalcul.ui.JeanCalculButtonVariant
import fr.loevan.jeancalcul.ui.JeanCalculDesign
import fr.loevan.jeancalcul.ui.PrivacyIndicator
import fr.loevan.jeancalcul.ui.PrivacyIndicatorState
import fr.loevan.jeancalcul.ui.StatusBadge
import fr.loevan.jeancalcul.ui.StatusBadgeState

@Composable
internal fun assistantRoleOnboarding(
    status: AssistantRoleStatus,
    microphonePermissionGranted: Boolean,
    onRequestRole: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val spacing = JeanCalculDesign.tokens.spacing
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientGlow(
            modifier = Modifier.fillMaxSize(),
            active = status != AssistantRoleStatus.Unavailable,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.screen, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.gutter),
            ) {
                GradientOrb(
                    state = status.orbState(),
                    modifier = Modifier.size(72.dp),
                    orbSize = 72.dp,
                    progress = 0.34f,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "JEAN CALCUL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "Votre assistant Android",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Une présence calme, prête lorsque vous l’êtes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.section),
                variant = GlassSurfaceVariant.Card,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Rôle assistant", style = MaterialTheme.typography.labelSmall)
                            StatusBadge(status.roleBadge())
                        }
                        Text(
                            text = status.description(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Accès vocal", style = MaterialTheme.typography.labelSmall)
                            StatusBadge(
                                if (microphonePermissionGranted) {
                                    StatusBadgeState.Available
                                } else {
                                    StatusBadgeState.Permission
                                },
                            )
                        }
                        Text(
                            text = microphonePermissionDescription(microphonePermissionGranted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PrivacyIndicator(state = PrivacyIndicatorState.MicrophoneInactive)
                    }

                    if (status == AssistantRoleStatus.Available) {
                        JeanCalculButton(
                            label = "Choisir comme assistant",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRequestRole,
                        )
                    }
                    if (!microphonePermissionGranted) {
                        JeanCalculButton(
                            label = "Autoriser le microphone",
                            modifier = Modifier.fillMaxWidth(),
                            variant = JeanCalculButtonVariant.Secondary,
                            onClick = onRequestMicrophonePermission,
                        )
                    }
                    JeanCalculButton(
                        label = "Ouvrir les paramètres système",
                        modifier = Modifier.fillMaxWidth(),
                        variant = JeanCalculButtonVariant.Ghost,
                        onClick = onOpenSystemSettings,
                    )
                }
            }

            Text(
                text = "Vos permissions restent sous le contrôle d’Android.",
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun microphonePermissionDescription(granted: Boolean): String =
    if (granted) {
        "Le microphone est autorisé et ne s’active que pendant une écoute explicite."
    } else {
        "Le microphone est nécessaire pour la transcription vocale."
    }

private fun AssistantRoleStatus.orbState(): GradientOrbState =
    when (this) {
        AssistantRoleStatus.Active -> GradientOrbState.Completed
        AssistantRoleStatus.Available -> GradientOrbState.Invoked
        AssistantRoleStatus.Unavailable -> GradientOrbState.Offline
    }

private fun AssistantRoleStatus.roleBadge(): StatusBadgeState =
    when (this) {
        AssistantRoleStatus.Active -> StatusBadgeState.Active
        AssistantRoleStatus.Available -> StatusBadgeState.Available
        AssistantRoleStatus.Unavailable -> StatusBadgeState.Unavailable
    }

private fun AssistantRoleStatus.description(): String =
    when (this) {
        AssistantRoleStatus.Active -> "Jean Calcul est déjà l’assistant numérique de cet appareil."
        AssistantRoleStatus.Available -> "Choisissez Jean Calcul pour l’invoquer depuis Android."
        AssistantRoleStatus.Unavailable ->
            "Ce système ne permet pas de demander le rôle automatiquement. Ouvrez les paramètres."
    }
