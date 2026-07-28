package fr.loevan.jeancalcul.network.openrouter

import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ModelProvider
import fr.loevan.jeancalcul.domain.ModelStreamEvent
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.network.OpenAiCompatibleConfiguration
import fr.loevan.jeancalcul.network.OpenAiCompatibleProvider
import fr.loevan.jeancalcul.network.OpenAiConnectionValidation
import fr.loevan.jeancalcul.security.SecretStore
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

const val OPENROUTER_PROVIDER_ID = "openrouter"

data class OpenRouterConfiguration(
    val connection: ProviderConnection,
    val fallbackModelIds: List<String> = emptyList(),
    val applicationName: String = "Jean-Calcul Assistant",
    val applicationUrl: String? = null,
    val configuredModels: List<ModelDescriptor> = emptyList(),
    val capabilitiesByModel: Map<String, ModelCapabilities> = emptyMap(),
    val defaultCapabilities: ModelCapabilities = ModelCapabilities(supportsToolCalling = true),
) {
    init {
        require(connection.kind == ProviderKind.OPENROUTER)
        require(connection.secretId != null)
        require(fallbackModelIds.none(String::isBlank))
        require(fallbackModelIds.distinct().size == fallbackModelIds.size)
        require(applicationName.isNotBlank())
        require(applicationUrl == null || applicationUrl.startsWith("https://"))
    }
}

sealed interface OpenRouterConnectionValidation {
    data class Success(
        val models: List<ModelDescriptor>,
    ) : OpenRouterConnectionValidation

    data class Failure(
        val error: ProviderError,
    ) : OpenRouterConnectionValidation
}

@Singleton
class OpenRouterProviderFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val secretStore: SecretStore,
    ) {
        fun create(configuration: OpenRouterConfiguration): OpenRouterProvider =
            OpenRouterProvider(client, secretStore, configuration)
    }

/** OpenRouter specialization of the OpenAI-compatible adapter. */
class OpenRouterProvider(
    client: OkHttpClient,
    secretStore: SecretStore,
    configuration: OpenRouterConfiguration,
) : ModelProvider {
    override val id: String = OPENROUTER_PROVIDER_ID

    private val delegate =
        OpenAiCompatibleProvider(
            client = client,
            secretStore = secretStore,
            configuration =
                OpenAiCompatibleConfiguration(
                    connection = configuration.connection,
                    configuredModels = configuration.configuredModels,
                    capabilitiesByModel = configuration.capabilitiesByModel,
                    defaultCapabilities = configuration.defaultCapabilities,
                    providerId = id,
                    providerKind = ProviderKind.OPENROUTER,
                    fallbackModelIds = configuration.fallbackModelIds,
                    additionalHeaders =
                        buildMap {
                            put("X-OpenRouter-Title", configuration.applicationName)
                            configuration.applicationUrl?.let { put("HTTP-Referer", it) }
                        },
                    includeUsageCost = true,
                ),
        )

    override suspend fun capabilities(profile: ModelProfile): ModelCapabilities = delegate.capabilities(profile)

    override suspend fun listModels(profile: ModelProfile): List<ModelDescriptor> = delegate.listModels(profile)

    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> = delegate.stream(request)

    override suspend fun cancel(requestId: String) = delegate.cancel(requestId)

    suspend fun validateConnection(profile: ModelProfile): OpenRouterConnectionValidation =
        when (val result = delegate.validateConnection(profile)) {
            is OpenAiConnectionValidation.Success -> OpenRouterConnectionValidation.Success(result.models)
            is OpenAiConnectionValidation.Failure -> OpenRouterConnectionValidation.Failure(result.error)
        }
}
