@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "TooManyFunctions",
    "ktlint:standard:function-naming",
)

package fr.loevan.jeancalcul.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.domain.AgentPermission
import fr.loevan.jeancalcul.domain.AgentPolicyMode
import fr.loevan.jeancalcul.domain.AppearanceSettings
import fr.loevan.jeancalcul.domain.AppearanceTheme
import fr.loevan.jeancalcul.domain.ConfiguredAgentProfile
import fr.loevan.jeancalcul.domain.ConfiguredModelProfile
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.VoiceInputMode
import fr.loevan.jeancalcul.domain.VoiceSettings
import fr.loevan.jeancalcul.ui.ContentState
import fr.loevan.jeancalcul.ui.ContentStateMessage
import fr.loevan.jeancalcul.ui.FilterChip
import fr.loevan.jeancalcul.ui.JeanCalculButton
import fr.loevan.jeancalcul.ui.JeanCalculButtonVariant
import fr.loevan.jeancalcul.ui.JeanCalculTextField
import fr.loevan.jeancalcul.ui.JeanCalculToggle
import fr.loevan.jeancalcul.ui.SegmentedControl
import fr.loevan.jeancalcul.ui.SegmentedControlOption
import fr.loevan.jeancalcul.ui.SettingsRow
import fr.loevan.jeancalcul.ui.SettingsSection

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsScreenActions,
    modifier: Modifier = Modifier,
) {
    var sectionId by rememberSaveable { mutableStateOf(SettingsPage.PROVIDERS.name) }
    val section = SettingsPage.valueOf(sectionId)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineMedium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsPage.entries.forEach { page ->
                FilterChip(
                    label = page.label,
                    selected = page == section,
                    onClick = { sectionId = page.name },
                )
            }
        }
        state.errorMessage?.let {
            ContentStateMessage(ContentState.Error, "Configuration invalide", it, onRetry = actions.clearError)
        }
        when (section) {
            SettingsPage.PROVIDERS -> providersPage(state, actions)
            SettingsPage.MODELS -> modelsPage(state, actions)
            SettingsPage.AGENTS -> agentsPage(state, actions)
            SettingsPage.VOICE -> voicePage(state.settings.voice, actions.updateVoice)
            SettingsPage.PERMISSIONS -> permissionsPage(state, actions)
            SettingsPage.APPEARANCE -> appearancePage(state.settings.appearance, actions.updateAppearance)
            SettingsPage.DIAGNOSTIC -> diagnosticPage(state)
        }
    }
}

@Composable
private fun providersPage(
    state: SettingsUiState,
    actions: SettingsScreenActions,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsSection("Fournisseurs") {
        if (state.settings.providers.isEmpty()) {
            Text("Aucun fournisseur configure.")
        }
        state.settings.providers.forEach { provider ->
            SettingsRow(
                title = provider.displayName,
                description = "${provider.kind.label} · ${provider.baseUrl}",
                state = provider.connectionSummary(state.connectionTests[provider.id]),
            ) {
                JeanCalculButton("Modifier", variant = JeanCalculButtonVariant.Ghost) { editingId = provider.id }
            }
        }
        JeanCalculButton("Nouveau fournisseur", variant = JeanCalculButtonVariant.Secondary) { editingId = NEW_ID }
    }
    val provider = state.settings.providers.firstOrNull { it.id == editingId }
    if (editingId != null) {
        key(editingId) {
            providerEditor(
                provider = provider,
                testState = provider?.let { state.connectionTests[it.id] },
                onSave = { draft, key ->
                    actions.saveProvider(draft, key)
                    editingId = null
                },
                onTest = provider?.let { { actions.testConnection(it.id) } },
                onDelete =
                    provider?.let {
                        {
                            actions.deleteProvider(it.id)
                            editingId = null
                        }
                    },
                onCancel = { editingId = null },
            )
        }
    }
}

@Composable
private fun providerEditor(
    provider: ProviderConnection?,
    testState: ConnectionTestUiState?,
    onSave: (ProviderDraft, CharArray?) -> Unit,
    onTest: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(provider?.displayName.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf((provider?.kind ?: ProviderKind.OPENAI_COMPATIBLE).name) }
    var baseUrl by rememberSaveable { mutableStateOf(provider?.baseUrl.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(provider?.enabled ?: true) }
    SettingsSection(if (provider == null) "Nouveau fournisseur" else "Modifier le fournisseur") {
        JeanCalculTextField(name, { name = it }, "Nom")
        SegmentedControl(
            options = ProviderKind.entries.map { SegmentedControlOption(it.name, it.shortLabel) },
            selectedId = kind,
            onSelect = { kind = it },
        )
        JeanCalculTextField(baseUrl, { baseUrl = it }, "URL de base")
        JeanCalculTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label =
                if (provider?.secretId == null) {
                    "Cle API (facultative)"
                } else {
                    "Nouvelle cle API (laisser vide pour conserver)"
                },
            visualTransformation = PasswordVisualTransformation(),
        )
        Text("La cle est stockee dans Android Keystore et ne sera jamais reaffichee.")
        JeanCalculToggle("Fournisseur actif", enabled, { enabled = it })
        testState?.let { Text(it.label, style = MaterialTheme.typography.bodyMedium) }
        actionRow {
            JeanCalculButton("Enregistrer", modifier = Modifier.weight(1f)) {
                val chars = apiKey.takeIf(String::isNotEmpty)?.toCharArray()
                apiKey = ""
                onSave(ProviderDraft(provider?.id, name, ProviderKind.valueOf(kind), baseUrl, enabled), chars)
            }
            onTest?.let { JeanCalculButton("Tester", variant = JeanCalculButtonVariant.Secondary, onClick = it) }
            JeanCalculButton("Annuler", variant = JeanCalculButtonVariant.Ghost, onClick = onCancel)
        }
        onDelete?.let {
            JeanCalculButton("Supprimer", variant = JeanCalculButtonVariant.Destructive, onClick = it)
        }
    }
}

@Composable
private fun modelsPage(
    state: SettingsUiState,
    actions: SettingsScreenActions,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsSection("Profils modeles") {
        if (state.settings.modelProfiles.isEmpty()) Text("Aucun profil modele.")
        state.settings.modelProfiles.forEach { configured ->
            val active = configured.profile.id == state.settings.activeModelProfileId
            SettingsRow(
                title = configured.profile.displayName,
                description = "${configured.profile.modelId} · ${configured.capabilities.modelSummary}",
                state =
                    if (active) {
                        "Profil actif"
                    } else if (configured.profile.enabled) {
                        "Disponible"
                    } else {
                        "Desactive"
                    },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    JeanCalculButton(
                        "Modifier",
                        variant = JeanCalculButtonVariant.Ghost,
                    ) { editingId = configured.profile.id }
                    JeanCalculButton("Activer", variant = JeanCalculButtonVariant.Secondary, enabled = !active) {
                        actions.activateModel(configured.profile.id)
                    }
                }
            }
            actionRow {
                JeanCalculButton("Dupliquer", variant = JeanCalculButtonVariant.Ghost) {
                    actions.duplicateModel(configured.profile.id)
                }
                JeanCalculButton("Supprimer", variant = JeanCalculButtonVariant.Destructive) {
                    actions.deleteModel(configured.profile.id)
                }
            }
        }
        JeanCalculButton("Nouveau profil", variant = JeanCalculButtonVariant.Secondary) { editingId = NEW_ID }
    }
    if (editingId != null) {
        val configured = state.settings.modelProfiles.firstOrNull { it.profile.id == editingId }
        key(editingId) {
            modelEditor(
                configured = configured,
                providers = state.settings.providers.filter { it.kind != ProviderKind.AGENT_BACKEND },
                onSave = {
                    actions.saveModel(it)
                    editingId = null
                },
                onCancel = { editingId = null },
            )
        }
    }
}

@Composable
private fun modelEditor(
    configured: ConfiguredModelProfile?,
    providers: List<ProviderConnection>,
    onSave: (ModelProfileDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(configured?.profile?.displayName.orEmpty()) }
    var providerId by rememberSaveable {
        mutableStateOf(configured?.profile?.connectionId ?: providers.firstOrNull()?.id.orEmpty())
    }
    var modelId by rememberSaveable { mutableStateOf(configured?.profile?.modelId.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(configured?.profile?.enabled ?: true) }
    var streaming by rememberSaveable { mutableStateOf(configured?.capabilities?.supportsStreaming ?: true) }
    var tools by rememberSaveable { mutableStateOf(configured?.capabilities?.supportsToolCalling ?: false) }
    var temperature by rememberSaveable { mutableStateOf(configured?.temperature?.toString().orEmpty()) }
    var maxTokens by rememberSaveable { mutableStateOf(configured?.maxOutputTokens?.toString().orEmpty()) }
    SettingsSection(if (configured == null) "Nouveau profil modele" else "Modifier le profil modele") {
        JeanCalculTextField(name, { name = it }, "Nom du profil")
        Text("Fournisseur")
        chipRow(providers, providerId, ProviderConnection::id, ProviderConnection::displayName) { providerId = it }
        JeanCalculTextField(modelId, { modelId = it }, "Identifiant du modele")
        JeanCalculTextField(temperature, { temperature = it }, "Temperature (0 a 2)")
        JeanCalculTextField(maxTokens, { maxTokens = it }, "Tokens de sortie maximum")
        JeanCalculToggle("Streaming", streaming, { streaming = it })
        JeanCalculToggle("Appels d'outils", tools, { tools = it })
        JeanCalculToggle("Profil actif dans la selection", enabled, { enabled = it })
        actionRow {
            JeanCalculButton("Enregistrer", modifier = Modifier.weight(1f)) {
                onSave(
                    ModelProfileDraft(
                        configured?.profile?.id,
                        name,
                        providerId,
                        modelId,
                        enabled,
                        streaming,
                        tools,
                        temperature,
                        maxTokens,
                    ),
                )
            }
            JeanCalculButton("Annuler", variant = JeanCalculButtonVariant.Ghost, onClick = onCancel)
        }
    }
}

@Composable
private fun agentsPage(
    state: SettingsUiState,
    actions: SettingsScreenActions,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsSection("Profils agents") {
        if (state.settings.agentProfiles.isEmpty()) Text("Aucun profil agent.")
        state.settings.agentProfiles.forEach { configured ->
            val active = configured.profile.id == state.settings.activeAgentProfileId
            SettingsRow(
                title = configured.profile.displayName,
                description = "${configured.profile.backendId} · ${configured.grantedPermissions.size} permission(s)",
                state = if (active) "Profil actif" else configured.policyMode.label,
            ) {
                JeanCalculButton(
                    "Modifier",
                    variant = JeanCalculButtonVariant.Ghost,
                ) { editingId = configured.profile.id }
            }
            actionRow {
                JeanCalculButton("Activer", variant = JeanCalculButtonVariant.Secondary, enabled = !active) {
                    actions.activateAgent(configured.profile.id)
                }
                JeanCalculButton("Dupliquer", variant = JeanCalculButtonVariant.Ghost) {
                    actions.duplicateAgent(configured.profile.id)
                }
                JeanCalculButton("Supprimer", variant = JeanCalculButtonVariant.Destructive) {
                    actions.deleteAgent(configured.profile.id)
                }
            }
        }
        JeanCalculButton("Nouvel agent", variant = JeanCalculButtonVariant.Secondary) { editingId = NEW_ID }
    }
    if (editingId != null) {
        val configured = state.settings.agentProfiles.firstOrNull { it.profile.id == editingId }
        key(editingId) {
            agentEditor(
                configured = configured,
                backends = state.settings.providers.filter { it.kind == ProviderKind.AGENT_BACKEND },
                onSave = {
                    actions.saveAgent(it)
                    editingId = null
                },
                onCancel = { editingId = null },
            )
        }
    }
}

@Composable
private fun agentEditor(
    configured: ConfiguredAgentProfile?,
    backends: List<ProviderConnection>,
    onSave: (AgentProfileDraft) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(configured?.profile?.displayName.orEmpty()) }
    var connectionId by rememberSaveable {
        mutableStateOf(configured?.profile?.connectionId ?: backends.firstOrNull()?.id.orEmpty())
    }
    var backendId by rememberSaveable { mutableStateOf(configured?.profile?.backendId.orEmpty()) }
    var agentId by rememberSaveable { mutableStateOf(configured?.profile?.agentId.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(configured?.profile?.enabled ?: true) }
    var resume by rememberSaveable { mutableStateOf(configured?.capabilities?.supportsSessionResume ?: true) }
    var approvals by rememberSaveable { mutableStateOf(configured?.capabilities?.supportsToolApprovals ?: true) }
    var policy by rememberSaveable { mutableStateOf((configured?.policyMode ?: AgentPolicyMode.STRICT).name) }
    var permissions by remember(configured) { mutableStateOf(configured?.grantedPermissions ?: emptySet()) }
    SettingsSection(if (configured == null) "Nouvel agent" else "Modifier l'agent") {
        JeanCalculTextField(name, { name = it }, "Nom du profil")
        Text("Backend agent")
        chipRow(backends, connectionId, ProviderConnection::id, ProviderConnection::displayName) { connectionId = it }
        JeanCalculTextField(backendId, { backendId = it }, "Type de backend")
        JeanCalculTextField(agentId, { agentId = it }, "Identifiant de l'agent")
        SegmentedControl(
            AgentPolicyMode.entries.map { SegmentedControlOption(it.name, it.shortLabel) },
            policy,
            onSelect = { policy = it },
        )
        JeanCalculToggle("Reprise de session", resume, { resume = it })
        JeanCalculToggle("Approbations d'outils", approvals, { approvals = it })
        JeanCalculToggle("Profil selectionnable", enabled, { enabled = it })
        Text("Permissions accordees")
        AgentPermission.entries.forEach { permission ->
            JeanCalculToggle(permission.id, permission in permissions, { granted ->
                permissions = if (granted) permissions + permission else permissions - permission
            })
        }
        actionRow {
            JeanCalculButton("Enregistrer", modifier = Modifier.weight(1f)) {
                onSave(
                    AgentProfileDraft(
                        configured?.profile?.id,
                        name,
                        connectionId,
                        backendId,
                        agentId,
                        enabled,
                        resume,
                        approvals,
                        permissions,
                        AgentPolicyMode.valueOf(policy),
                    ),
                )
            }
            JeanCalculButton("Annuler", variant = JeanCalculButtonVariant.Ghost, onClick = onCancel)
        }
    }
}

@Composable
private fun voicePage(
    voice: VoiceSettings,
    onUpdate: (VoiceSettings) -> Unit,
) {
    var language by rememberSaveable(voice) { mutableStateOf(voice.languageTag) }
    var mode by rememberSaveable(voice) { mutableStateOf(voice.inputMode.name) }
    var partial by rememberSaveable(voice) { mutableStateOf(voice.partialResultsEnabled) }
    var bluetooth by rememberSaveable(voice) { mutableStateOf(voice.bluetoothEnabled) }
    SettingsSection("Voix") {
        JeanCalculTextField(language, { language = it }, "Langue (BCP 47, ex. fr-FR)")
        SegmentedControl(
            listOf(
                SegmentedControlOption(VoiceInputMode.VOICE_AND_TEXT.name, "Voix + texte"),
                SegmentedControlOption(VoiceInputMode.TEXT_ONLY.name, "Texte seul"),
            ),
            mode,
            onSelect = { mode = it },
        )
        JeanCalculToggle("Resultats partiels", partial, { partial = it })
        JeanCalculToggle("Audio Bluetooth", bluetooth, { bluetooth = it })
        JeanCalculButton("Enregistrer") {
            onUpdate(VoiceSettings(language.trim(), VoiceInputMode.valueOf(mode), partial, bluetooth))
        }
    }
}

@Composable
private fun permissionsPage(
    state: SettingsUiState,
    actions: SettingsScreenActions,
) {
    SettingsSection("Permissions des agents") {
        if (state.settings.agentProfiles.isEmpty()) Text("Creez d'abord un profil agent.")
        state.settings.agentProfiles.forEach { configured ->
            Text(configured.profile.displayName, style = MaterialTheme.typography.titleMedium)
            Text("Politique : ${configured.policyMode.label}")
            AgentPermission.entries.forEach { permission ->
                JeanCalculToggle(
                    label = permission.id,
                    checked = permission in configured.grantedPermissions,
                    onCheckedChange = { actions.setAgentPermission(configured.profile.id, permission, it) },
                    description = permission.permissionDescription,
                )
            }
        }
    }
}

@Composable
private fun appearancePage(
    appearance: AppearanceSettings,
    onUpdate: (AppearanceSettings) -> Unit,
) {
    var theme by rememberSaveable(appearance) { mutableStateOf(appearance.theme.name) }
    var reduceMotion by rememberSaveable(appearance) { mutableStateOf(appearance.reduceMotion) }
    var blur by rememberSaveable(appearance) { mutableStateOf(appearance.blurEnabled) }
    var shaders by rememberSaveable(appearance) { mutableStateOf(appearance.shadersEnabled) }
    var highContrast by rememberSaveable(appearance) { mutableStateOf(appearance.highContrast) }
    SettingsSection("Apparence") {
        SegmentedControl(
            AppearanceTheme.entries.map { SegmentedControlOption(it.name, it.label) },
            theme,
            onSelect = { theme = it },
        )
        JeanCalculToggle("Effets reduits", reduceMotion, { reduceMotion = it })
        JeanCalculToggle("Surfaces translucides", blur, { blur = it })
        JeanCalculToggle("Gradients et shaders", shaders, { shaders = it })
        JeanCalculToggle("Contraste renforce", highContrast, { highContrast = it })
        JeanCalculButton("Appliquer") {
            onUpdate(AppearanceSettings(AppearanceTheme.valueOf(theme), reduceMotion, blur, shaders, highContrast))
        }
    }
}

@Composable
private fun diagnosticPage(state: SettingsUiState) {
    SettingsSection("Diagnostic local") {
        SettingsRow("Fournisseurs", state = state.settings.providers.size.toString())
        SettingsRow("Profils modeles", state = state.settings.modelProfiles.size.toString())
        SettingsRow("Profil modele actif", state = state.activeModelName ?: "Aucun")
        SettingsRow("Profils agents", state = state.settings.agentProfiles.size.toString())
        SettingsRow("Profil agent actif", state = state.activeAgentName ?: "Aucun")
        SettingsRow("Mode de saisie", state = state.settings.voice.inputMode.name)
        state.settings.providers.forEach { provider ->
            SettingsRow(
                title = provider.displayName,
                description = provider.baseUrl,
                state = provider.connectionSummary(state.connectionTests[provider.id]),
            )
        }
        Text("Les secrets sont references par identifiant et restent dans Android Keystore.")
    }
}

@Composable
private fun actionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun <T> chipRow(
    values: List<T>,
    selectedId: String,
    idOf: (T) -> String,
    labelOf: (T) -> String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(labelOf(value), idOf(value) == selectedId, { onSelect(idOf(value)) })
        }
    }
    if (values.isEmpty()) Text("Aucune option compatible n'est configuree.")
}

private val SettingsUiState.activeModelName: String?
    get() = settings.modelProfiles.firstOrNull { it.profile.id == settings.activeModelProfileId }?.profile?.displayName

private val SettingsUiState.activeAgentName: String?
    get() = settings.agentProfiles.firstOrNull { it.profile.id == settings.activeAgentProfileId }?.profile?.displayName

private val fr.loevan.jeancalcul.domain.ModelCapabilities.modelSummary: String
    get() =
        buildList {
            if (supportsStreaming) add("streaming")
            if (supportsToolCalling) add("outils")
            if (inputModalities.size > 1) add("multimodal")
        }.ifEmpty { listOf("texte") }.joinToString(", ")

private fun ProviderConnection.connectionSummary(test: ConnectionTestUiState?): String =
    when (test) {
        null -> if (enabled) "Non teste" else "Desactive"
        is ConnectionTestUiState.Failure -> "Erreur ${test.code} : ${test.message}"
        ConnectionTestUiState.Running -> "Test en cours"
        is ConnectionTestUiState.Success -> listOfNotNull(test.message, test.warning).joinToString(" ")
    }

private val ConnectionTestUiState.label: String
    get() =
        when (this) {
            is ConnectionTestUiState.Failure -> "Erreur $code : $message"
            ConnectionTestUiState.Running -> "Test en cours..."
            is ConnectionTestUiState.Success -> listOfNotNull(message, warning).joinToString(" ")
        }

private val ProviderKind.label: String
    get() = name.lowercase().replace('_', ' ')

private val ProviderKind.shortLabel: String
    get() =
        when (this) {
            ProviderKind.OPENAI_COMPATIBLE -> "OpenAI"
            ProviderKind.ANTHROPIC -> "Anthropic"
            ProviderKind.OPENROUTER -> "OpenRouter"
            ProviderKind.OLLAMA -> "Ollama"
            ProviderKind.AGENT_BACKEND -> "Agent"
        }

private val AgentPolicyMode.label: String
    get() =
        when (this) {
            AgentPolicyMode.STRICT -> "Confirmation stricte"
            AgentPolicyMode.BALANCED -> "Equilibree"
            AgentPolicyMode.EXPLICIT_AUTOMATION -> "Automatisations explicites"
        }

private val AgentPolicyMode.shortLabel: String
    get() =
        when (this) {
            AgentPolicyMode.STRICT -> "Strict"
            AgentPolicyMode.BALANCED -> "Equilibre"
            AgentPolicyMode.EXPLICIT_AUTOMATION -> "Auto. explicite"
        }

private val AgentPermission.permissionDescription: String
    get() =
        when (this) {
            AgentPermission.EMAIL_SEND,
            AgentPermission.CALENDAR_WRITE,
            AgentPermission.MEMORY_WRITE,
            -> "Modification sensible : le Policy Engine conserve la decision finale."

            AgentPermission.NETWORK_PRIVATE -> "Autorise les connexions au reseau local explicitement configurees."
            AgentPermission.DEVICE_BASIC_CONTROL -> "Actions Android enregistrees, validees et auditees uniquement."
            else -> "Acces limite au perimetre declare ; aucune elevation silencieuse."
        }

private val AppearanceTheme.label: String
    get() =
        when (this) {
            AppearanceTheme.SYSTEM -> "Systeme"
            AppearanceTheme.DARK -> "Sombre"
            AppearanceTheme.LIGHT -> "Clair"
        }

private enum class SettingsPage(val label: String) {
    PROVIDERS("Fournisseurs"),
    MODELS("Modeles"),
    AGENTS("Agents"),
    VOICE("Voix"),
    PERMISSIONS("Permissions"),
    APPEARANCE("Apparence"),
    DIAGNOSTIC("Diagnostic"),
}

private const val NEW_ID = "new"
