package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class ProviderKind {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    OPENROUTER,
    OLLAMA,
    AGENT_BACKEND,
}

@Serializable
data class ProviderConnection(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val secretId: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(secretId == null || secretId.isNotBlank())
    }

    val usesInsecureTransport: Boolean
        get() = runCatching { URI(baseUrl.trim()).scheme.equals("http", ignoreCase = true) }.getOrDefault(false)
}

@Serializable
data class ConfiguredModelProfile(
    val profile: ModelProfile,
    val capabilities: ModelCapabilities = ModelCapabilities(),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
) {
    init {
        require(temperature == null || temperature in 0.0..2.0)
        require(maxOutputTokens == null || maxOutputTokens > 0)
    }
}

@Serializable
enum class AgentPermission(val id: String) {
    NOTIFICATIONS_READ("notifications.read"),
    NOTIFICATIONS_DISMISS("notifications.dismiss"),
    CALENDAR_READ("calendar.read"),
    CALENDAR_WRITE("calendar.write"),
    TASKS_READ("tasks.read"),
    TASKS_WRITE("tasks.write"),
    CONTACTS_READ("contacts.read"),
    EMAIL_READ("email.read"),
    EMAIL_SEND("email.send"),
    DEVICE_BASIC_CONTROL("device.basic_control"),
    DEVICE_ACCESSIBILITY("device.accessibility"),
    NETWORK_PUBLIC("network.public"),
    NETWORK_PRIVATE("network.private"),
    FILESYSTEM_WORKSPACE("filesystem.workspace"),
    SHELL_SANDBOXED("shell.sandboxed"),
    MEMORY_READ("memory.read"),
    MEMORY_WRITE("memory.write"),
}

@Serializable
enum class AgentPolicyMode {
    STRICT,
    BALANCED,
    EXPLICIT_AUTOMATION,
}

@Serializable
data class ConfiguredAgentProfile(
    val profile: AgentProfile,
    val capabilities: AgentCapabilities = AgentCapabilities(),
    val grantedPermissions: Set<AgentPermission> = emptySet(),
    val policyMode: AgentPolicyMode = AgentPolicyMode.STRICT,
)

@Serializable
enum class VoiceInputMode {
    VOICE_AND_TEXT,
    TEXT_ONLY,
}

@Serializable
data class VoiceSettings(
    val languageTag: String = "fr-FR",
    val inputMode: VoiceInputMode = VoiceInputMode.VOICE_AND_TEXT,
    val partialResultsEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
) {
    init {
        require(languageTag.isNotBlank())
    }
}

@Serializable
enum class AppearanceTheme {
    SYSTEM,
    DARK,
    LIGHT,
}

@Serializable
data class AppearanceSettings(
    val theme: AppearanceTheme = AppearanceTheme.SYSTEM,
    val reduceMotion: Boolean = false,
    val blurEnabled: Boolean = true,
    val shadersEnabled: Boolean = true,
    val highContrast: Boolean = false,
)

@Serializable
data class AssistantSettings(
    val providers: List<ProviderConnection> = emptyList(),
    val modelProfiles: List<ConfiguredModelProfile> = emptyList(),
    val agentProfiles: List<ConfiguredAgentProfile> = emptyList(),
    val activeModelProfileId: String? = null,
    val activeAgentProfileId: String? = null,
    val voice: VoiceSettings = VoiceSettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
)

interface AssistantSettingsRepository {
    val settings: Flow<AssistantSettings>

    suspend fun update(transform: (AssistantSettings) -> AssistantSettings)
}

object AssistantSettingsValidator {
    fun providerErrors(connection: ProviderConnection): List<String> =
        buildList {
            if (connection.displayName.isBlank()) add("Le nom du fournisseur est obligatoire.")
            val uri = runCatching { URI(connection.baseUrl.trim()) }.getOrNull()
            if (!uri.isValidProviderUri()) {
                add("L'URL doit etre une adresse HTTP(S) absolue, sans identifiants, requete ni fragment.")
            }
        }

    fun modelActivationErrors(
        configured: ConfiguredModelProfile,
        settings: AssistantSettings,
    ): List<String> =
        buildList {
            val connectionId = configured.profile.connectionId
            val connection = settings.providers.firstOrNull { it.id == connectionId }
            if (!configured.profile.enabled) add("Le profil modele est desactive.")
            if (connectionId == null || connection == null) add("Selectionnez un fournisseur existant.")
            if (connection != null) {
                if (!connection.enabled) add("Le fournisseur selectionne est desactive.")
                addAll(providerErrors(connection))
            }
            if (configured.profile.modelId.isBlank()) add("Le modele est obligatoire.")
        }

    fun agentActivationErrors(
        configured: ConfiguredAgentProfile,
        settings: AssistantSettings,
    ): List<String> =
        buildList {
            val connectionId = configured.profile.connectionId
            val connection = settings.providers.firstOrNull { it.id == connectionId }
            if (!configured.profile.enabled) add("Le profil agent est desactive.")
            if (connectionId == null || connection == null) add("Selectionnez un backend agent existant.")
            if (connection != null && connection.kind != ProviderKind.AGENT_BACKEND) {
                add("Le profil agent doit utiliser un backend agent distinct d'un fournisseur modele.")
            }
            if (connection != null) {
                if (!connection.enabled) add("Le backend selectionne est desactive.")
                addAll(providerErrors(connection))
            }
        }
}

private fun URI?.isValidProviderUri(): Boolean {
    if (this == null || !isAbsolute) return false
    val validScheme = scheme.lowercase() in setOf("http", "https")
    val validHost = !host.isNullOrBlank()
    val hasNoEmbeddedData = userInfo == null && query == null && fragment == null
    return validScheme && validHost && hasNoEmbeddedData
}
