@file:Suppress(
    "FunctionNaming",
    "MaxLineLength",
    "TooManyFunctions",
    "UnusedPrivateMember",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Fondations sombre", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun foundationsDarkPreview() = PreviewFrame(themeMode = ThemeMode.Dark) { FoundationsContent() }

@Preview(name = "Fondations clair", showBackground = true, backgroundColor = 0xFFF7F9FA)
@Composable
private fun foundationsLightPreview() = PreviewFrame(themeMode = ThemeMode.Light) { FoundationsContent() }

@Preview(name = "Fondations effets réduits", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun foundationsReducedEffectsPreview() =
    PreviewFrame(themeMode = ThemeMode.Dark, effects = VisualEffects(reduceMotion = true)) { FoundationsContent() }

@Preview(name = "Fondations sans blur", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun foundationsNoBlurPreview() =
    PreviewFrame(themeMode = ThemeMode.Dark, effects = VisualEffects(blurEnabled = false, shadersEnabled = false)) {
        FoundationsContent()
    }

@Preview(name = "Accueil assistant", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun assistantHomePreview() = PreviewFrame { AssistantHomePreviewContent() }

@Preview(name = "Conversation streaming", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun conversationPreview() = PreviewFrame { ConversationPreviewContent() }

@Preview(name = "Session transparente écoute", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun listeningSessionPreview() = PreviewFrame { ListeningSessionPreviewContent() }

@Preview(name = "Overlay compact", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun compactOverlayPreview() = PreviewFrame { CompactOverlayPreviewContent() }

@Preview(name = "Confirmation biométrique", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun approvalPreview() = PreviewFrame { ApprovalPreviewContent() }

@Preview(name = "Réglages", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun settingsPreview() = PreviewFrame { SettingsPreviewContent() }

@Preview(name = "Journal audit", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun auditPreview() = PreviewFrame { AuditPreviewContent() }

@Preview(name = "Diagnostic", showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun diagnosticsPreview() = PreviewFrame { DiagnosticsPreviewContent() }

@Preview(name = "Grande police", fontScale = 1.5f, showBackground = true, backgroundColor = 0xFF101415)
@Composable
private fun largeFontApprovalPreview() = PreviewFrame { ApprovalPreviewContent() }

@Composable
private fun PreviewFrame(
    themeMode: ThemeMode = ThemeMode.Dark,
    effects: VisualEffects = VisualEffects(),
    content: @Composable () -> Unit,
) {
    jeanCalculTheme(themeMode = themeMode, visualEffects = effects) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(
                modifier = Modifier.fillMaxSize().padding(JeanCalculDesign.tokens.spacing.screen),
                content = { content() },
            )
        }
    }
}

@Composable
private fun FoundationsContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Jean Calcul Lumina", style = MaterialTheme.typography.headlineMedium)
        GlassSurface(variant = GlassSurfaceVariant.Card) { Text("Carte glass interactive") }
        GlassSurface(variant = GlassSurfaceVariant.FallbackOpaque) { Text("Fallback tonal sans blur") }
        RowPreview()
        PrivacyIndicator(state = PrivacyIndicatorState.LocalProcessing)
    }
}

@Composable
private fun RowPreview() {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusBadge(StatusBadgeState.Active)
        StatusBadge(StatusBadgeState.RiskR4)
        ProviderChip("Profil générique", ProviderChipKind.Local, ProviderChipState.Active)
    }
}

@Composable
private fun AssistantHomePreviewContent() {
    Box(Modifier.fillMaxSize()) {
        AmbientGlow(Modifier.fillMaxSize(), active = true)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProviderChip("Traitement local", ProviderChipKind.Local, ProviderChipState.Active)
            GradientOrb(state = GradientOrbState.Idle)
            AssistantInputBar(value = "", onValueChange = {}, onVoice = {})
        }
    }
}

@Composable
private fun ConversationPreviewContent() {
    val action = previewAction(ActionCardState.Proposed)
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AssistantBubble(kind = AssistantBubbleKind.User, text = "Prépare une action locale réversible.")
        AssistantBubble(
            kind = AssistantBubbleKind.Streaming,
            text = "Je vérifie les conséquences avant de vous proposer une action…",
            actions = AssistantBubbleActions(onStop = {}),
        )
        ActionCard(data = action)
        AssistantInputBar(value = "", onValueChange = {}, onSend = {})
    }
}

@Composable
private fun ListeningSessionPreviewContent() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GradientOrb(state = GradientOrbState.Listening, amplitude = 0.62f, progress = 0.4f)
        VoiceWave(
            state = VoiceWaveState.Listening,
            amplitude = 0.62f,
            modifier = Modifier.size(width = 160.dp, height = 36.dp),
        )
        Text("Je vous écoute…", style = MaterialTheme.typography.headlineMedium)
        PrivacyIndicator(state = PrivacyIndicatorState.MicrophoneActive)
        JeanCalculButton(label = "Interrompre", variant = JeanCalculButtonVariant.Ghost, onClick = {})
    }
}

@Composable
private fun CompactOverlayPreviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(180.dp))
        GlassSurface(variant = GlassSurfaceVariant.Overlay) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                CircularIconButton(label = "Ouvrir", onClick = {})
                VoiceWave(
                    state = VoiceWaveState.Listening,
                    amplitude = 0.48f,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).height(28.dp),
                )
                CircularIconButton(label = "Fermer", onClick = {})
            }
        }
    }
}

@Composable
private fun ApprovalPreviewContent() {
    ApprovalSheet(
        state = ApprovalSheetState.Biometric,
        action = previewAction(ActionCardState.BiometricRequired),
        justification = "Cette action peut modifier une donnée personnelle. Android demande une confirmation explicite.",
        onApprove = {},
        onReject = {},
    )
}

@Composable
private fun SettingsPreviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection("Apparence") {
            SettingsRow(
                title = "Effets visuels",
                description = "Réduire les mouvements et les effets coûteux",
                state = "Système",
            )
            JeanCalculToggle(label = "Effets réduits", checked = false, onCheckedChange = {})
            SegmentedControl(
                options =
                    listOf(
                        SegmentedControlOption("system", "Système"),
                        SegmentedControlOption("reduced", "Réduits"),
                    ),
                selectedId = "system",
                onSelect = {},
            )
        }
        SettingsSection("Confidentialité") {
            SettingsRow(
                title = "Destination",
                description = "Toujours visible avant un traitement distant",
                state = "Local",
            )
        }
    }
}

@Composable
private fun AuditPreviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Journal d’audit", style = MaterialTheme.typography.headlineMedium)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(label = "Tout", selected = true, onClick = {})
            FilterChip(label = "Risques", selected = false, onClick = {})
        }
        ActionCard(previewAction(ActionCardState.Succeeded))
        ActionCard(previewAction(ActionCardState.Denied))
    }
}

@Composable
private fun DiagnosticsPreviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnostic local", style = MaterialTheme.typography.headlineMedium)
        SettingsSection("État système") {
            SettingsRow(title = "Microphone", state = "Disponible") { StatusBadge(StatusBadgeState.Available) }
            SettingsRow(title = "Réseau", state = "Hors connexion") { StatusBadge(StatusBadgeState.Offline) }
            SettingsRow(
                title = "Contenu sensible",
                state = "Masqué",
            ) { PrivacyIndicator(PrivacyIndicatorState.ContentMasked) }
        }
    }
}

private fun previewAction(state: ActionCardState) =
    ActionCardData(
        title = "Modifier un réglage local",
        summary = "Une modification réversible est prête à être appliquée.",
        risk = ActionRisk.R2,
        origin = "Demande utilisateur",
        state = state,
        result = if (state == ActionCardState.Succeeded) "Modification appliquée" else null,
    )
