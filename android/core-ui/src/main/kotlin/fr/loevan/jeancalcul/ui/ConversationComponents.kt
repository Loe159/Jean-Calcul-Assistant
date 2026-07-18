@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "MaxLineLength",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AssistantBubbleKind {
    User,
    Assistant,
    System,
    Tool,
    Streaming,
    Interrupted,
    Error,
    Offline,
}

data class AssistantBubbleActions(
    val onStop: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onCopy: (() -> Unit)? = null,
    val onDetails: (() -> Unit)? = null,
)

@Composable
fun AssistantBubble(
    kind: AssistantBubbleKind,
    text: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    actions: AssistantBubbleActions = AssistantBubbleActions(),
) {
    val variant =
        when (kind) {
            AssistantBubbleKind.User -> GlassSurfaceVariant.Selected
            AssistantBubbleKind.Error -> GlassSurfaceVariant.FallbackOpaque
            else -> GlassSurfaceVariant.Panel
        }
    val state = if (kind == AssistantBubbleKind.Error) GlassSurfaceState.Error else GlassSurfaceState.Normal
    GlassSurface(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = bubbleDescription(kind) },
        variant = variant,
        state = state,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = bubbleDescription(kind),
                style = MaterialTheme.typography.labelSmall,
                color = bubbleColor(kind),
            )
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            metadata?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actions.hasAnyAction()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions.onStop?.let { callback -> TextButton(onClick = callback) { Text("Arrêter") } }
                    actions.onRetry?.let { callback -> TextButton(onClick = callback) { Text("Réessayer") } }
                    actions.onCopy?.let { callback -> TextButton(onClick = callback) { Text("Copier") } }
                    actions.onDetails?.let { callback -> TextButton(onClick = callback) { Text("Détails") } }
                }
            }
        }
    }
}

enum class ProviderChipKind {
    Model,
    Agent,
    Local,
    Remote,
}

enum class ProviderChipState {
    Active,
    Unavailable,
    Error,
    NetworkWarning,
}

@Composable
fun ProviderChip(
    label: String,
    kind: ProviderChipKind,
    state: ProviderChipState,
    modifier: Modifier = Modifier,
) {
    val status =
        when (state) {
            ProviderChipState.Active -> "actif"
            ProviderChipState.Unavailable -> "indisponible"
            ProviderChipState.Error -> "erreur"
            ProviderChipState.NetworkWarning -> "avertissement réseau"
        }
    GlassSurface(
        modifier = modifier.semantics { contentDescription = "$label, ${kind.name.lowercase()}, $status" },
        variant = if (state == ProviderChipState.Active) GlassSurfaceVariant.Selected else GlassSurfaceVariant.Panel,
        state = if (state == ProviderChipState.Error) GlassSurfaceState.Error else GlassSurfaceState.Normal,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "$label · $status",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

enum class PrivacyIndicatorState {
    MicrophoneActive,
    MicrophoneInactive,
    LocalProcessing,
    RemoteProcessing,
    DestinationVisible,
    ScreenLocked,
    ContentMasked,
    BiometricRequired,
}

@Composable
fun PrivacyIndicator(
    state: PrivacyIndicatorState,
    modifier: Modifier = Modifier,
    destination: String? = null,
) {
    val label =
        when (state) {
            PrivacyIndicatorState.MicrophoneActive -> "Microphone actif"
            PrivacyIndicatorState.MicrophoneInactive -> "Microphone inactif"
            PrivacyIndicatorState.LocalProcessing -> "Traitement local"
            PrivacyIndicatorState.RemoteProcessing -> "Traitement distant"
            PrivacyIndicatorState.DestinationVisible -> "Destination des données visible"
            PrivacyIndicatorState.ScreenLocked -> "Écran verrouillé"
            PrivacyIndicatorState.ContentMasked -> "Contenu masqué"
            PrivacyIndicatorState.BiometricRequired -> "Biométrie requise"
        }
    val detail = if (state == PrivacyIndicatorState.DestinationVisible && destination != null) "$label : $destination" else label
    Text(
        text = detail,
        modifier = modifier.semantics { contentDescription = detail },
        style = MaterialTheme.typography.labelSmall,
        color = JeanCalculDesign.tokens.semantic.privacy,
    )
}

enum class StatusBadgeState {
    Active,
    Inactive,
    Connected,
    Offline,
    Available,
    Unavailable,
    Success,
    Warning,
    Error,
    Permission,
    RiskR0,
    RiskR1,
    RiskR2,
    RiskR3,
    RiskR4,
    RiskR5,
}

@Composable
fun StatusBadge(
    state: StatusBadgeState,
    modifier: Modifier = Modifier,
    label: String = state.defaultLabel(),
) {
    val color = statusColor(state)
    GlassSurface(
        modifier = modifier.semantics { contentDescription = label },
        variant = GlassSurfaceVariant.Panel,
        state = if (state == StatusBadgeState.Error) GlassSurfaceState.Error else GlassSurfaceState.Normal,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun bubbleColor(kind: AssistantBubbleKind) =
    when (kind) {
        AssistantBubbleKind.Error -> MaterialTheme.colorScheme.error
        AssistantBubbleKind.Offline -> JeanCalculDesign.tokens.semantic.offline
        AssistantBubbleKind.Tool -> JeanCalculDesign.tokens.semantic.privacy
        else -> MaterialTheme.colorScheme.secondary
    }

private fun AssistantBubbleActions.hasAnyAction(): Boolean =
    onStop != null || onRetry != null || onCopy != null || onDetails != null

private fun bubbleDescription(kind: AssistantBubbleKind): String =
    when (kind) {
        AssistantBubbleKind.User -> "Message utilisateur"
        AssistantBubbleKind.Assistant -> "Réponse assistant"
        AssistantBubbleKind.System -> "Message système"
        AssistantBubbleKind.Tool -> "Résultat d’outil"
        AssistantBubbleKind.Streaming -> "Réponse assistant en cours"
        AssistantBubbleKind.Interrupted -> "Réponse interrompue"
        AssistantBubbleKind.Error -> "Erreur récupérable"
        AssistantBubbleKind.Offline -> "Réponse hors connexion"
    }

private fun StatusBadgeState.defaultLabel(): String =
    when (this) {
        StatusBadgeState.Active -> "Actif"
        StatusBadgeState.Inactive -> "Inactif"
        StatusBadgeState.Connected -> "Connecté"
        StatusBadgeState.Offline -> "Hors connexion"
        StatusBadgeState.Available -> "Disponible"
        StatusBadgeState.Unavailable -> "Indisponible"
        StatusBadgeState.Success -> "Réussi"
        StatusBadgeState.Warning -> "Avertissement"
        StatusBadgeState.Error -> "Erreur"
        StatusBadgeState.Permission -> "Permission requise"
        StatusBadgeState.RiskR0 -> "Risque R0"
        StatusBadgeState.RiskR1 -> "Risque R1"
        StatusBadgeState.RiskR2 -> "Risque R2"
        StatusBadgeState.RiskR3 -> "Risque R3"
        StatusBadgeState.RiskR4 -> "Risque R4"
        StatusBadgeState.RiskR5 -> "Risque R5"
    }

@Composable
private fun statusColor(state: StatusBadgeState) =
    when (state) {
        StatusBadgeState.Success,
        StatusBadgeState.Active,
        StatusBadgeState.Connected,
        StatusBadgeState.RiskR0,
        -> JeanCalculDesign.tokens.semantic.success

        StatusBadgeState.Warning,
        StatusBadgeState.Permission,
        StatusBadgeState.RiskR3,
        -> JeanCalculDesign.tokens.semantic.warning

        StatusBadgeState.Error,
        StatusBadgeState.RiskR4,
        StatusBadgeState.RiskR5,
        -> MaterialTheme.colorScheme.error

        StatusBadgeState.Offline,
        StatusBadgeState.Inactive,
        StatusBadgeState.Unavailable,
        -> JeanCalculDesign.tokens.semantic.offline

        StatusBadgeState.Available,
        StatusBadgeState.RiskR1,
        StatusBadgeState.RiskR2,
        -> JeanCalculDesign.tokens.semantic.information
    }
