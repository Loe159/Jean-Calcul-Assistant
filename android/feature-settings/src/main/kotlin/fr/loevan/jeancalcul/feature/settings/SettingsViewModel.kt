@file:Suppress("TooManyFunctions")

package fr.loevan.jeancalcul.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.loevan.jeancalcul.domain.AgentCapabilities
import fr.loevan.jeancalcul.domain.AgentPermission
import fr.loevan.jeancalcul.domain.AgentPolicyMode
import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AppearanceSettings
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.AssistantSettingsValidator
import fr.loevan.jeancalcul.domain.ConfiguredAgentProfile
import fr.loevan.jeancalcul.domain.ConfiguredModelProfile
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.VoiceSettings
import fr.loevan.jeancalcul.network.ConnectionProbeResult
import fr.loevan.jeancalcul.network.ProviderConnectionProbe
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val settings: AssistantSettings = AssistantSettings(),
    val connectionTests: Map<String, ConnectionTestUiState> = emptyMap(),
    val errorMessage: String? = null,
)

sealed interface ConnectionTestUiState {
    data object Running : ConnectionTestUiState

    data class Success(
        val message: String,
        val warning: String? = null,
    ) : ConnectionTestUiState

    data class Failure(
        val message: String,
        val code: String,
    ) : ConnectionTestUiState
}

data class ProviderDraft(
    val id: String? = null,
    val displayName: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val enabled: Boolean = true,
)

data class ModelProfileDraft(
    val id: String? = null,
    val displayName: String,
    val providerId: String,
    val modelId: String,
    val enabled: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsToolCalling: Boolean = false,
    val temperature: String = "",
    val maxOutputTokens: String = "",
)

data class AgentProfileDraft(
    val id: String? = null,
    val displayName: String,
    val connectionId: String,
    val backendId: String,
    val agentId: String,
    val enabled: Boolean = true,
    val supportsSessionResume: Boolean = true,
    val supportsToolApprovals: Boolean = true,
    val grantedPermissions: Set<AgentPermission> = emptySet(),
    val policyMode: AgentPolicyMode = AgentPolicyMode.STRICT,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: AssistantSettingsRepository,
        private val secretStore: SecretStore,
        private val connectionProbe: ProviderConnectionProbe,
    ) : ViewModel() {
        private val transient = MutableStateFlow(SettingsTransientState())
        val uiState =
            combine(repository.settings, transient) { settings, local ->
                SettingsUiState(settings, local.connectionTests, local.errorMessage)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun saveProvider(
            draft: ProviderDraft,
            apiKey: CharArray? = null,
        ) {
            val id = draft.id ?: UUID.randomUUID().toString()
            viewModelScope.launch {
                val previous = uiState.value.settings.providers.firstOrNull { it.id == id }
                val suppliedSecret = apiKey?.takeIf(CharArray::isNotEmpty)
                val secretId = previous?.secretId ?: suppliedSecret?.let { "provider.$id.api_key" }
                val connection =
                    ProviderConnection(
                        id = id,
                        displayName = draft.displayName.trim(),
                        kind = draft.kind,
                        baseUrl = draft.baseUrl.trim().trimEnd('/'),
                        secretId = secretId,
                        enabled = draft.enabled,
                    )
                val errors = AssistantSettingsValidator.providerErrors(connection)
                if (errors.isNotEmpty()) {
                    suppliedSecret?.fill(NULL_CHARACTER)
                    showError(errors.joinToString(" "))
                    return@launch
                }
                if (suppliedSecret != null && secretId != null) {
                    val stored =
                        try {
                            secretStore.put(SecretId(secretId), suppliedSecret)
                        } finally {
                            suppliedSecret.fill(NULL_CHARACTER)
                        }
                    if (stored is SecretStoreResult.Failure) {
                        showError(stored.error.userMessage)
                        return@launch
                    }
                }
                repository.update { current ->
                    current.copy(
                        providers = current.providers.replaceBy(id, connection, ProviderConnection::id),
                    ).withoutInvalidSelections()
                }
                clearError()
            }
        }

        fun deleteProvider(id: String) {
            viewModelScope.launch {
                val provider = uiState.value.settings.providers.firstOrNull { it.id == id }
                provider?.secretId?.let { secretStore.delete(SecretId(it)) }
                repository.update { current ->
                    val modelIds =
                        current.modelProfiles.filter { it.profile.connectionId == id }.map { it.profile.id }.toSet()
                    val agentIds =
                        current.agentProfiles.filter { it.profile.connectionId == id }.map { it.profile.id }.toSet()
                    current.copy(
                        providers = current.providers.filterNot { it.id == id },
                        modelProfiles = current.modelProfiles.filterNot { it.profile.id in modelIds },
                        agentProfiles = current.agentProfiles.filterNot { it.profile.id in agentIds },
                        activeModelProfileId = current.activeModelProfileId.takeUnless { it in modelIds },
                        activeAgentProfileId = current.activeAgentProfileId.takeUnless { it in agentIds },
                    ).withoutInvalidSelections()
                }
                transient.update { it.copy(connectionTests = it.connectionTests - id) }
            }
        }

        fun testConnection(id: String) {
            val connection = uiState.value.settings.providers.firstOrNull { it.id == id }
            if (connection == null) {
                showError("Enregistrez le fournisseur avant de tester sa connexion.")
                return
            }
            val errors = AssistantSettingsValidator.providerErrors(connection)
            if (errors.isNotEmpty()) {
                setConnectionTest(id, ConnectionTestUiState.Failure(errors.joinToString(" "), "invalid_configuration"))
                return
            }
            setConnectionTest(id, ConnectionTestUiState.Running)
            viewModelScope.launch {
                val state =
                    when (val result = connectionProbe.test(connection)) {
                        is ConnectionProbeResult.Success ->
                            ConnectionTestUiState.Success(
                                message = result.message,
                                warning =
                                    if (result.insecureTransport) {
                                        "Connexion HTTP non chiffree : reservee au reseau local."
                                    } else {
                                        null
                                    },
                            )

                        is ConnectionProbeResult.Failure ->
                            ConnectionTestUiState.Failure(
                                result.userMessage,
                                result.code,
                            )
                    }
                setConnectionTest(id, state)
            }
        }

        @Suppress("ReturnCount")
        fun saveModel(draft: ModelProfileDraft) {
            val temperature = draft.temperature.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()
            val maxTokens = draft.maxOutputTokens.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
            if (draft.temperature.isNotBlank() && temperature == null) {
                showError("La temperature doit etre un nombre entre 0 et 2.")
                return
            }
            if (draft.maxOutputTokens.isNotBlank() && maxTokens == null) {
                showError("Le nombre maximal de tokens doit etre un entier positif.")
                return
            }
            val configured =
                runCatching {
                    val id = draft.id ?: UUID.randomUUID().toString()
                    ConfiguredModelProfile(
                        profile =
                            ModelProfile(
                                id = id,
                                providerId = providerKindId(draft.providerId),
                                modelId = draft.modelId.trim(),
                                displayName = draft.displayName.trim(),
                                connectionId = draft.providerId,
                                enabled = draft.enabled,
                            ),
                        capabilities =
                            ModelCapabilities(
                                supportsStreaming = draft.supportsStreaming,
                                supportsToolCalling = draft.supportsToolCalling,
                            ),
                        temperature = temperature,
                        maxOutputTokens = maxTokens,
                    )
                }.getOrElse {
                    showError("Renseignez un nom, un fournisseur et un modele valides.")
                    return
                }
            viewModelScope.launch {
                repository.update { current ->
                    current.copy(
                        modelProfiles =
                            current.modelProfiles.replaceBy(
                                configured.profile.id,
                                configured,
                            ) { it.profile.id },
                    ).withoutInvalidSelections()
                }
                clearError()
            }
        }

        fun duplicateModel(id: String) {
            viewModelScope.launch {
                repository.update { current ->
                    val source = current.modelProfiles.firstOrNull { it.profile.id == id } ?: return@update current
                    val copy =
                        source.copy(
                            profile =
                                source.profile.copy(
                                    id = UUID.randomUUID().toString(),
                                    displayName = "${source.profile.displayName} (copie)",
                                    enabled = false,
                                ),
                        )
                    current.copy(modelProfiles = current.modelProfiles + copy)
                }
            }
        }

        fun activateModel(id: String) {
            viewModelScope.launch {
                repository.update { current ->
                    val profile = current.modelProfiles.firstOrNull { it.profile.id == id }
                    val errors =
                        profile?.let { AssistantSettingsValidator.modelActivationErrors(it, current) }
                            ?: listOf("Profil modele introuvable.")
                    if (errors.isNotEmpty()) {
                        showError(errors.joinToString(" "))
                        current
                    } else {
                        clearError()
                        current.copy(activeModelProfileId = id)
                    }
                }
            }
        }

        fun deleteModel(id: String) {
            viewModelScope.launch {
                repository.update { current ->
                    current.copy(
                        modelProfiles = current.modelProfiles.filterNot { it.profile.id == id },
                        activeModelProfileId = current.activeModelProfileId.takeUnless { it == id },
                    )
                }
            }
        }

        fun saveAgent(draft: AgentProfileDraft) {
            val configured =
                runCatching {
                    val id = draft.id ?: UUID.randomUUID().toString()
                    ConfiguredAgentProfile(
                        profile =
                            AgentProfile(
                                id = id,
                                backendId = draft.backendId.trim(),
                                agentId = draft.agentId.trim(),
                                displayName = draft.displayName.trim(),
                                connectionId = draft.connectionId,
                                enabled = draft.enabled,
                            ),
                        capabilities =
                            AgentCapabilities(
                                supportsSessionResume = draft.supportsSessionResume,
                                supportsToolApprovals = draft.supportsToolApprovals,
                            ),
                        grantedPermissions = draft.grantedPermissions,
                        policyMode = draft.policyMode,
                    )
                }.getOrElse {
                    showError("Renseignez un nom, un backend et un identifiant d'agent valides.")
                    return
                }
            viewModelScope.launch {
                repository.update { current ->
                    current.copy(
                        agentProfiles =
                            current.agentProfiles.replaceBy(configured.profile.id, configured) { it.profile.id },
                    ).withoutInvalidSelections()
                }
                clearError()
            }
        }

        fun duplicateAgent(id: String) {
            viewModelScope.launch {
                repository.update { current ->
                    val source = current.agentProfiles.firstOrNull { it.profile.id == id } ?: return@update current
                    val copy =
                        source.copy(
                            profile =
                                source.profile.copy(
                                    id = UUID.randomUUID().toString(),
                                    displayName = "${source.profile.displayName} (copie)",
                                    enabled = false,
                                ),
                        )
                    current.copy(agentProfiles = current.agentProfiles + copy)
                }
            }
        }

        fun activateAgent(id: String) {
            viewModelScope.launch {
                repository.update { current ->
                    val profile = current.agentProfiles.firstOrNull { it.profile.id == id }
                    val errors =
                        profile?.let { AssistantSettingsValidator.agentActivationErrors(it, current) }
                            ?: listOf("Profil agent introuvable.")
                    if (errors.isNotEmpty()) {
                        showError(errors.joinToString(" "))
                        current
                    } else {
                        clearError()
                        current.copy(activeAgentProfileId = id)
                    }
                }
            }
        }

        fun setAgentPermission(
            agentId: String,
            permission: AgentPermission,
            granted: Boolean,
        ) {
            viewModelScope.launch {
                repository.update { current ->
                    current.copy(
                        agentProfiles =
                            current.agentProfiles.map { configured ->
                                if (configured.profile.id != agentId) {
                                    configured
                                } else {
                                    configured.copy(
                                        grantedPermissions =
                                            if (granted) {
                                                configured.grantedPermissions + permission
                                            } else {
                                                configured.grantedPermissions - permission
                                            },
                                    )
                                }
                            },
                    )
                }
            }
        }

        fun deleteAgent(id: String) {
            viewModelScope.launch {
                repository.update { current ->
                    current.copy(
                        agentProfiles = current.agentProfiles.filterNot { it.profile.id == id },
                        activeAgentProfileId = current.activeAgentProfileId.takeUnless { it == id },
                    )
                }
            }
        }

        fun updateVoice(settings: VoiceSettings) {
            viewModelScope.launch { repository.update { it.copy(voice = settings) } }
        }

        fun updateAppearance(settings: AppearanceSettings) {
            viewModelScope.launch { repository.update { it.copy(appearance = settings) } }
        }

        fun clearError() {
            transient.update { it.copy(errorMessage = null) }
        }

        private fun providerKindId(connectionId: String): String =
            uiState.value.settings.providers.firstOrNull { it.id == connectionId }?.kind?.name?.lowercase()
                ?: "unconfigured"

        private fun showError(message: String) {
            transient.update { it.copy(errorMessage = message) }
        }

        private fun setConnectionTest(
            id: String,
            state: ConnectionTestUiState,
        ) {
            transient.update { it.copy(connectionTests = it.connectionTests + (id to state)) }
        }

        private data class SettingsTransientState(
            val connectionTests: Map<String, ConnectionTestUiState> = emptyMap(),
            val errorMessage: String? = null,
        )

        private companion object {
            const val NULL_CHARACTER = '\u0000'
        }
    }

private fun <T> List<T>.replaceBy(
    id: String,
    value: T,
    idOf: (T) -> String,
): List<T> = if (any { idOf(it) == id }) map { if (idOf(it) == id) value else it } else this + value

private fun AssistantSettings.withoutInvalidSelections(): AssistantSettings {
    val validModelId =
        activeModelProfileId?.takeIf { activeId ->
            modelProfiles.firstOrNull { it.profile.id == activeId }
                ?.let { AssistantSettingsValidator.modelActivationErrors(it, this).isEmpty() } == true
        }
    val validAgentId =
        activeAgentProfileId?.takeIf { activeId ->
            agentProfiles.firstOrNull { it.profile.id == activeId }
                ?.let { AssistantSettingsValidator.agentActivationErrors(it, this).isEmpty() } == true
        }
    return copy(activeModelProfileId = validModelId, activeAgentProfileId = validAgentId)
}
