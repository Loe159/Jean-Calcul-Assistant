@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "MaxLineLength",
    "TooManyFunctions",
    "UnusedPrivateMember",
    "ktlint:standard:argument-list-wrapping",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private const val PREVIEW_DARK = 0xFF101415
private const val PREVIEW_LIGHT = 0xFFF7F9FA

@Preview(name = "Fondations · sombre", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun foundationsDarkPreview() = PreviewFrame { FoundationsContent() }

@Preview(name = "Fondations · clair", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_LIGHT)
@Composable
private fun foundationsLightPreview() = PreviewFrame(themeMode = ThemeMode.Light) { FoundationsContent() }

@Preview(name = "Fondations · effets réduits", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun foundationsReducedEffectsPreview() =
    PreviewFrame(effects = VisualEffects(reduceMotion = true)) { FoundationsContent() }

@Preview(name = "Fondations · sans blur", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun foundationsNoBlurPreview() =
    PreviewFrame(effects = VisualEffects(blurEnabled = false)) { FoundationsContent() }

@Preview(name = "Fondations · sans shader", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun foundationsNoShaderPreview() =
    PreviewFrame(effects = VisualEffects(shadersEnabled = false)) { FoundationsContent(noShader = true) }

@Preview(name = "Assistant · sombre", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun assistantDarkPreview() = PreviewFrame { AssistantHomePreviewContent() }

@Preview(name = "Assistant · clair", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_LIGHT)
@Composable
private fun assistantLightPreview() = PreviewFrame(themeMode = ThemeMode.Light) { AssistantHomePreviewContent() }

@Preview(name = "Assistant · effets réduits", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun assistantReducedPreview() =
    PreviewFrame(effects = VisualEffects(reduceMotion = true)) {
        AssistantHomePreviewContent(orbMotionMode = OrbMotionMode.Reduced)
    }

@Preview(name = "Assistant · sans blur", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun assistantNoBlurPreview() =
    PreviewFrame(effects = VisualEffects(blurEnabled = false)) { AssistantHomePreviewContent() }

@Preview(name = "Assistant · sans shader", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun assistantNoShaderPreview() =
    PreviewFrame(effects = VisualEffects(shadersEnabled = false)) {
        AssistantHomePreviewContent(orbMotionMode = OrbMotionMode.NoShader)
    }

@Preview(name = "Conversation · cycle d’action", widthDp = 412, heightDp = 980, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun conversationPreview() = PreviewFrame { ConversationPreviewContent() }

@Preview(name = "Session · sombre", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun listeningSessionDarkPreview() = PreviewFrame { ListeningSessionPreviewContent() }

@Preview(name = "Session · clair", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_LIGHT)
@Composable
private fun listeningSessionLightPreview() =
    PreviewFrame(themeMode = ThemeMode.Light) { ListeningSessionPreviewContent() }

@Preview(name = "Session · effets réduits", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun listeningSessionReducedPreview() =
    PreviewFrame(effects = VisualEffects(reduceMotion = true)) {
        ListeningSessionPreviewContent(orbMotionMode = OrbMotionMode.Reduced)
    }

@Preview(name = "Session · sans blur", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun listeningSessionNoBlurPreview() =
    PreviewFrame(effects = VisualEffects(blurEnabled = false)) { ListeningSessionPreviewContent() }

@Preview(name = "Overlay · sombre", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun compactOverlayDarkPreview() = PreviewFrame { CompactOverlayPreviewContent() }

@Preview(name = "Overlay · clair", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_LIGHT)
@Composable
private fun compactOverlayLightPreview() = PreviewFrame(themeMode = ThemeMode.Light) { CompactOverlayPreviewContent() }

@Preview(name = "Overlay · effets réduits", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun compactOverlayReducedPreview() =
    PreviewFrame(effects = VisualEffects(reduceMotion = true)) { CompactOverlayPreviewContent() }

@Preview(name = "Overlay · sans blur", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun compactOverlayNoBlurPreview() =
    PreviewFrame(effects = VisualEffects(blurEnabled = false)) { CompactOverlayPreviewContent() }

@Preview(name = "Confirmation · simple", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun simpleApprovalPreview() = PreviewFrame { ApprovalPreviewContent(biometric = false) }

@Preview(name = "Confirmation · biométrique", widthDp = 412, heightDp = 892, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun biometricApprovalPreview() = PreviewFrame { ApprovalPreviewContent(biometric = true) }

@Preview(name = "Réglages", widthDp = 412, heightDp = 980, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun settingsPreview() = PreviewFrame { SettingsPreviewContent() }

@Preview(name = "Journal d’audit", widthDp = 412, heightDp = 980, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun auditPreview() = PreviewFrame { AuditPreviewContent() }

@Preview(name = "Diagnostic", widthDp = 412, heightDp = 980, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun diagnosticsPreview() = PreviewFrame { DiagnosticsPreviewContent() }

@Preview(name = "Accessibilité · grande police", widthDp = 412, heightDp = 1040, fontScale = 1.5f, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun largeFontApprovalPreview() = PreviewFrame { ApprovalPreviewContent(biometric = true) }

@Preview(name = "Contenu · français long", widthDp = 412, heightDp = 980, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun longFrenchTextPreview() = PreviewFrame { LongFrenchTextPreviewContent() }

@Preview(name = "État · erreur", widthDp = 412, heightDp = 760, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun errorPreview() = PreviewFrame { ErrorPreviewContent() }

@Preview(name = "État · hors connexion", widthDp = 412, heightDp = 760, showBackground = true, backgroundColor = PREVIEW_DARK)
@Composable
private fun offlinePreview() = PreviewFrame { OfflinePreviewContent() }

@Composable
private fun PreviewFrame(
    themeMode: ThemeMode = ThemeMode.Dark,
    effects: VisualEffects = VisualEffects(),
    content: @Composable () -> Unit,
) {
    jeanCalculTheme(themeMode = themeMode, visualEffects = effects) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize(), content = { content() })
        }
    }
}

@Composable
private fun FoundationsContent(noShader: Boolean = false) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(JeanCalculDesign.tokens.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader(
            eyebrow = "DESIGN SYSTEM",
            title = "Jean Calcul Lumina",
            description = "Ambient Intelligence · Refined Dark Glass",
        )
        Box(modifier = Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.Center) {
            AmbientGlow(Modifier.fillMaxSize(), active = true)
            GradientOrb(
                state = GradientOrbState.Thinking,
                amplitude = 0.32f,
                progress = 0.34f,
                motionMode = if (noShader) OrbMotionMode.NoShader else OrbMotionMode.Static,
                orbSize = 152.dp,
            )
        }
        GlassSurface(variant = GlassSurfaceVariant.Panel) {
            Text("Panneau tonal léger", style = MaterialTheme.typography.bodyLarge)
        }
        GlassSurface(variant = GlassSurfaceVariant.Card) {
            Text("Carte glass avec reflet supérieur", style = MaterialTheme.typography.bodyLarge)
        }
        GlassSurface(variant = GlassSurfaceVariant.Selected) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Surface active", style = MaterialTheme.typography.titleMedium)
                Text("Le violet reste réservé à la sélection et à l’activité de l’assistant.")
            }
        }
        GlassSurface(variant = GlassSurfaceVariant.FallbackOpaque) {
            Text("Fallback tonal lisible sans blur")
        }
        StatusRowPreview()
    }
}

@Composable
private fun StatusRowPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusBadge(StatusBadgeState.Active)
        StatusBadge(StatusBadgeState.RiskR3)
        ProviderChip("Local", ProviderChipKind.Local, ProviderChipState.Active)
    }
}

@Composable
private fun AssistantHomePreviewContent(orbMotionMode: OrbMotionMode = OrbMotionMode.Static) {
    Box(Modifier.fillMaxSize()) {
        AmbientGlow(Modifier.fillMaxSize(), active = true)
        Column(
            modifier = Modifier.fillMaxSize().padding(JeanCalculDesign.tokens.spacing.screen),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("JEAN CALCUL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Lumina", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                CircularIconButton(label = "⋮", onClick = {})
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProviderChip("Prêt · traitement local", ProviderChipKind.Local, ProviderChipState.Active)
                GradientOrb(
                    state = GradientOrbState.Thinking,
                    amplitude = 0.24f,
                    progress = 0.28f,
                    motionMode = orbMotionMode,
                    orbSize = 184.dp,
                )
                Text("Comment puis-je vous aider ?", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    "Une présence calme, locale et transparente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistantInputBar(value = "", onValueChange = {}, onVoice = {}, placeholder = "Parler ou écrire à Jean Calcul")
                FloatingBottomNavigation(
                    items = mainNavigationItems(),
                    selectedId = "assistant",
                    onSelect = {},
                )
            }
        }
    }
}

@Composable
private fun ConversationPreviewContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(JeanCalculDesign.tokens.spacing.screen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader("CONVERSATION", "Action locale encadrée", "Chaque étape reste explicite avant et après l’exécution.")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ProviderChip("Local", ProviderChipKind.Local, ProviderChipState.Active)
            PrivacyIndicator(PrivacyIndicatorState.DestinationVisible, destination = "Cet appareil")
        }
        AssistantBubble(
            kind = AssistantBubbleKind.User,
            text = "Prépare la modification du réglage, mais demande-moi avant de l’appliquer.",
            metadata = "À l’instant",
        )
        AssistantBubble(
            kind = AssistantBubbleKind.Streaming,
            text = "J’ai vérifié la portée et les permissions. Voici une proposition réversible…",
            actions = AssistantBubbleActions(onStop = {}),
        )
        ActionLifecyclePreview()
        ActionCard(data = previewAction(ActionCardState.Proposed), onDetails = {})
        AssistantInputBar(value = "", onValueChange = {}, onSend = {})
    }
}

@Composable
private fun ActionLifecyclePreview() {
    val stages =
        listOf(
            "1" to "Proposition",
            "2" to "Approbation",
            "3" to "Exécution",
            "4" to "Résultat",
        )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stages.forEachIndexed { index, stage ->
            val variant =
                when (index) {
                    1 -> GlassSurfaceVariant.Selected
                    2 -> GlassSurfaceVariant.Interactive
                    else -> GlassSurfaceVariant.Card
                }
            val surfaceState = if (index == 2) GlassSurfaceState.Loading else GlassSurfaceState.Normal
            val stageColor =
                when (index) {
                    1 -> MaterialTheme.colorScheme.tertiary
                    3 -> JeanCalculDesign.tokens.semantic.success
                    else -> MaterialTheme.colorScheme.secondary
                }
            GlassSurface(
                modifier = Modifier.weight(1f),
                variant = variant,
                state = surfaceState,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("● ${stage.first}", style = MaterialTheme.typography.labelSmall, color = stageColor)
                    Text(
                        stage.second,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningSessionPreviewContent(orbMotionMode: OrbMotionMode = OrbMotionMode.Static) {
    Box(Modifier.fillMaxSize()) {
        SimulatedApplicationContext()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.56f)))
        AmbientGlow(Modifier.fillMaxSize(), active = true)
        GlassSurface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            variant = GlassSurfaceVariant.Modal,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GradientOrb(
                    state = GradientOrbState.Listening,
                    amplitude = 0.68f,
                    progress = 0.42f,
                    motionMode = orbMotionMode,
                    orbSize = 148.dp,
                )
                VoiceWave(
                    state = VoiceWaveState.Listening,
                    amplitude = 0.68f,
                    progress = 0.42f,
                    barCount = 7,
                    modifier = Modifier.size(width = 184.dp, height = 48.dp),
                )
                Text("Je vous écoute…", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "« envoie le compte rendu à… »",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrivacyIndicator(PrivacyIndicatorState.MicrophoneActive)
                    PrivacyIndicator(PrivacyIndicatorState.LocalProcessing)
                }
                JeanCalculButton(label = "Interrompre", variant = JeanCalculButtonVariant.Ghost, onClick = {})
            }
        }
    }
}

@Composable
private fun CompactOverlayPreviewContent() {
    Box(Modifier.fillMaxSize()) {
        SimulatedApplicationContext()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.40f)))
        AmbientGlow(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(280.dp),
            active = true,
        )
        GlassSurface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 36.dp),
            variant = GlassSurfaceVariant.Overlay,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularIconButton(label = "↗", onClick = {})
                VoiceWave(
                    state = VoiceWaveState.Listening,
                    amplitude = 0.52f,
                    progress = 0.58f,
                    barCount = 7,
                    modifier = Modifier.width(72.dp).height(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("ÉCOUTE ACTIVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        "…envoie un message à Camille…",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CircularIconButton(label = "×", onClick = {})
            }
        }
    }
}

@Composable
private fun SimulatedApplicationContext() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Application en arrière-plan", style = MaterialTheme.typography.titleLarge)
        GlassSurface(variant = GlassSurfaceVariant.Panel) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projet Lumina", style = MaterialTheme.typography.titleMedium)
                Text("Le contexte reste perceptible, tandis que l’assistant flotte au-dessus.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassSurface(modifier = Modifier.weight(1f).height(132.dp), variant = GlassSurfaceVariant.Panel) {}
            GlassSurface(modifier = Modifier.weight(1f).height(132.dp), variant = GlassSurfaceVariant.Panel) {}
        }
        GlassSurface(modifier = Modifier.fillMaxWidth().height(168.dp), variant = GlassSurfaceVariant.Panel) {}
    }
}

@Composable
private fun ApprovalPreviewContent(biometric: Boolean) {
    Box(Modifier.fillMaxSize()) {
        AmbientGlow(Modifier.fillMaxSize(), active = true)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(JeanCalculDesign.tokens.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            PageHeader(
                eyebrow = "ACTION SENSIBLE",
                title = if (biometric) "Validation Android" else "Confirmation requise",
                description = "Conséquence, risque et justification sont visibles avant la décision.",
            )
            ApprovalSheet(
                state = if (biometric) ApprovalSheetState.Biometric else ApprovalSheetState.SimpleConfirmation,
                action =
                    previewAction(
                        if (biometric) ActionCardState.BiometricRequired else ActionCardState.ConfirmationRequired,
                    ),
                justification =
                    if (biometric) {
                        "Cette modification concerne une donnée personnelle. Android effectue la vérification biométrique sans transmettre le secret à Jean Calcul."
                    } else {
                        "Le réglage sera modifié localement et pourra être rétabli. Aucune donnée ne quitte l’appareil."
                    },
                onApprove = {},
                onReject = {},
            )
        }
    }
}

@Composable
private fun SettingsPreviewContent() {
    PageWithNavigation(selectedId = "settings") {
        PageHeader("RÉGLAGES", "Votre assistant, à votre rythme", "Apparence et confidentialité restent explicites.")
        SettingsSection("Apparence") {
            SettingsRow(
                title = "Effets visuels",
                description = "Profondeur complète avec fallback tonal automatique",
                state = "Système",
            )
            JeanCalculToggle(
                label = "Effets réduits",
                checked = false,
                onCheckedChange = {},
                description = "Supprime les animations et shaders continus",
            )
            SegmentedControl(
                options =
                    listOf(
                        SegmentedControlOption("system", "Système"),
                        SegmentedControlOption("dark", "Sombre"),
                        SegmentedControlOption("light", "Clair"),
                    ),
                selectedId = "system",
                onSelect = {},
            )
        }
        SettingsSection("Confidentialité") {
            SettingsRow(
                title = "Traitement par défaut",
                description = "La destination est affichée avant tout traitement distant",
                state = "Local d’abord",
            ) { StatusBadge(StatusBadgeState.Active) }
            SettingsRow(title = "Microphone", state = "Autorisé") { StatusBadge(StatusBadgeState.Permission) }
        }
    }
}

@Composable
private fun AuditPreviewContent() {
    PageWithNavigation(selectedId = "audit") {
        PageHeader("SÉCURITÉ", "Journal d’audit", "Les paramètres sensibles sont automatiquement expurgés.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(label = "Tout", selected = true, onClick = {})
            FilterChip(label = "À vérifier", selected = false, onClick = {})
            FilterChip(label = "Refus", selected = false, onClick = {})
        }
        ActionCard(previewAction(ActionCardState.Succeeded))
        ActionCard(
            previewAction(ActionCardState.Executing).copy(
                title = "Ouvrir un panneau Android",
                summary = "Action locale en cours, paramètres expurgés.",
            ),
        )
        ActionCard(
            previewAction(ActionCardState.Denied).copy(
                title = "Accès refusé sur écran verrouillé",
                result = "Bloqué par la politique locale",
            ),
        )
    }
}

@Composable
private fun DiagnosticsPreviewContent() {
    PageWithNavigation(selectedId = "settings") {
        PageHeader("ÉTAT LOCAL", "Diagnostic système", "Les capacités essentielles restent lisibles sans dépendre de la couleur.")
        GlassSurface(variant = GlassSurfaceVariant.Selected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Assistant opérationnel", style = MaterialTheme.typography.titleLarge)
                    Text("8 vérifications locales réussies")
                }
                StatusBadge(StatusBadgeState.Success)
            }
        }
        SettingsSection("Services Android") {
            SettingsRow(title = "Rôle assistant", state = "Défini") { StatusBadge(StatusBadgeState.Available) }
            SettingsRow(title = "Microphone et STT", state = "Disponibles") { StatusBadge(StatusBadgeState.Success) }
            SettingsRow(title = "Android Keystore", state = "Protégé") { PrivacyIndicator(PrivacyIndicatorState.LocalProcessing) }
        }
        SettingsSection("Connectivité") {
            SettingsRow(
                title = "Réseau",
                description = "Les actions locales restent disponibles",
                state = "Hors connexion",
            ) { StatusBadge(StatusBadgeState.Offline) }
            SettingsRow(title = "Dernière erreur", state = "Aucune donnée sensible consignée")
        }
    }
}

@Composable
private fun LongFrenchTextPreviewContent() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeader("REFLOW", "Texte français long", "La hiérarchie conserve sa respiration et les décisions importantes restent entièrement lisibles.")
        AssistantBubble(
            kind = AssistantBubbleKind.Assistant,
            text = "Je peux préparer cette modification de configuration, vérifier les conséquences sur les autorisations Android et vous demander une confirmation explicite juste avant l’exécution, sans transmettre le contenu de votre demande à un service distant.",
            metadata = "Traitement local · contenu non transmis",
            actions = AssistantBubbleActions(onCopy = {}, onDetails = {}),
        )
        ApprovalSheet(
            state = ApprovalSheetState.DetailedConfirmation,
            action =
                previewAction(ActionCardState.ConfirmationRequired).copy(
                    title = "Modifier les préférences de confidentialité de cette session",
                    summary = "Cette action change uniquement le comportement de la session actuelle et peut être annulée immédiatement depuis les réglages.",
                ),
            justification = "Votre demande concerne une préférence locale. Jean Calcul explique la conséquence exacte avant de solliciter votre accord.",
            onApprove = {},
            onReject = {},
        )
    }
}

@Composable
private fun ErrorPreviewContent() {
    Box(Modifier.fillMaxSize()) {
        AmbientGlow(Modifier.fillMaxSize(), active = false)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            GradientOrb(
                state = GradientOrbState.Error,
                progress = 0.72f,
                motionMode = OrbMotionMode.Static,
                orbSize = 152.dp,
            )
            ContentStateMessage(
                state = ContentState.Error,
                title = "La demande n’a pas abouti",
                message = "Aucune action n’a été exécutée. Vérifiez la connexion ou réessayez.",
                onRetry = {},
            )
            PrivacyIndicator(PrivacyIndicatorState.MicrophoneInactive)
        }
    }
}

@Composable
private fun OfflinePreviewContent() {
    Box(Modifier.fillMaxSize()) {
        AmbientGlow(Modifier.fillMaxSize(), active = false)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            GradientOrb(
                state = GradientOrbState.Offline,
                progress = 0.18f,
                motionMode = OrbMotionMode.Static,
                orbSize = 152.dp,
            )
            Text("Mode hors connexion", style = MaterialTheme.typography.headlineMedium)
            AssistantBubble(
                kind = AssistantBubbleKind.Offline,
                text = "Le réseau est indisponible. Les actions locales et l’historique sur cet appareil restent accessibles.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge(StatusBadgeState.Offline)
                PrivacyIndicator(PrivacyIndicatorState.LocalProcessing)
            }
        }
    }
}

@Composable
private fun PageWithNavigation(
    selectedId: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        FloatingBottomNavigation(items = mainNavigationItems(), selectedId = selectedId, onSelect = {})
    }
}

@Composable
private fun PageHeader(
    eyebrow: String,
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun mainNavigationItems() =
    listOf(
        NavigationItem("assistant", "Assistant"),
        NavigationItem("audit", "Audit"),
        NavigationItem("settings", "Réglages"),
    )

private fun previewAction(state: ActionCardState) =
    ActionCardData(
        title = "Modifier un réglage local",
        summary = "Une modification réversible est prête à être appliquée.",
        risk = ActionRisk.R2,
        origin = "Demande utilisateur",
        state = state,
        result = if (state == ActionCardState.Succeeded) "Modification appliquée" else null,
    )
