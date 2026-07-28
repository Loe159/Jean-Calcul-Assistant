package fr.loevan.jeancalcul.network.anthropic

import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ModelProvider
import fr.loevan.jeancalcul.domain.ModelStreamEvent
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.network.ProviderRequestAuthenticator
import fr.loevan.jeancalcul.network.protocolException
import fr.loevan.jeancalcul.network.providerException
import fr.loevan.jeancalcul.security.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val ANTHROPIC_PROVIDER_ID = "anthropic"

data class AnthropicConfiguration(
    val connection: ProviderConnection,
    val configuredModels: List<ModelDescriptor> = emptyList(),
    val capabilitiesByModel: Map<String, ModelCapabilities> = emptyMap(),
    val defaultCapabilities: ModelCapabilities = DEFAULT_ANTHROPIC_CAPABILITIES,
    val defaultMaxOutputTokens: Int = 4_096,
) {
    init {
        require(connection.kind == ProviderKind.ANTHROPIC)
        require(connection.secretId != null)
        require(defaultMaxOutputTokens > 0)
        require(configuredModels.map(ModelDescriptor::id).distinct().size == configuredModels.size)
    }

    fun capabilitiesFor(modelId: String): ModelCapabilities =
        capabilitiesByModel[modelId]
            ?: configuredModels.firstOrNull { it.id == modelId }?.capabilities
            ?: defaultCapabilities
}

sealed interface AnthropicConnectionValidation {
    data class Success(val models: List<ModelDescriptor>) : AnthropicConnectionValidation

    data class Failure(val error: ProviderError) : AnthropicConnectionValidation
}

@Singleton
class AnthropicProviderFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val secretStore: SecretStore,
    ) {
        fun create(configuration: AnthropicConfiguration): AnthropicProvider =
            AnthropicProvider(client, secretStore, configuration)
    }

class AnthropicProvider(
    client: OkHttpClient,
    secretStore: SecretStore,
    private val configuration: AnthropicConfiguration,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ModelProvider {
    override val id: String = ANTHROPIC_PROVIDER_ID

    private val apiClient = client
    private val streamingClient = client.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
    private val authenticator = ProviderRequestAuthenticator(secretStore)
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val cancelledRequestIds = ConcurrentHashMap.newKeySet<String>()

    override suspend fun capabilities(profile: ModelProfile): ModelCapabilities {
        validateProfile(profile)
        return configuration.capabilitiesFor(profile.modelId)
    }

    override suspend fun listModels(profile: ModelProfile): List<ModelDescriptor> {
        validateProfile(profile)
        if (configuration.configuredModels.isNotEmpty()) return configuration.configuredModels
        return fetchModels()
    }

    suspend fun validateConnection(profile: ModelProfile): AnthropicConnectionValidation =
        try {
            validateProfile(profile)
            AnthropicConnectionValidation.Success(fetchModels())
        } catch (error: ProviderException) {
            AnthropicConnectionValidation.Failure(error.error)
        }

    @Suppress("CyclomaticComplexMethod", "SwallowedException")
    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> =
        flow {
            var sequence = 0L
            emit(StreamEvent.Started(request.requestId, sequence++))
            try {
                validateRequest(request)
                val call = streamingClient.newCall(buildMessagesRequest(request))
                if (activeCalls.putIfAbsent(request.requestId, call) != null) {
                    throw providerException(
                        ProviderErrorCategory.INVALID_REQUEST,
                        "anthropic_duplicate_request_id",
                        "Une requete portant cet identifiant est deja active.",
                    )
                }
                val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.awaitResponse().use { response ->
                        if (!response.isSuccessful) throw response.toAnthropicException()
                        val body = response.body ?: throw protocolException("anthropic_empty_response_body")
                        val parser = AnthropicStreamParser(request.requestId, json) { sequence++ }
                        body.source().use { source ->
                            while (!source.exhausted()) {
                                currentCoroutineContext().ensureActive()
                                parser.consume(source.readUtf8Line() ?: break).forEach { emit(it) }
                            }
                        }
                        parser.finish()
                    }
                } finally {
                    cancellation.dispose()
                    activeCalls.remove(request.requestId, call)
                }
            } catch (cancelled: CancellationException) {
                cancelledRequestIds.remove(request.requestId)
                throw cancelled
            } catch (error: ProviderException) {
                emit(StreamEvent.Failed(request.requestId, error.error, sequence))
            } catch (error: SocketTimeoutException) {
                emit(StreamEvent.Failed(request.requestId, transportError(ProviderErrorCategory.TIMEOUT), sequence))
            } catch (error: IOException) {
                val failure =
                    if (cancelledRequestIds.remove(request.requestId)) {
                        ProviderError(
                            ProviderErrorCategory.CANCELLED,
                            "anthropic_request_cancelled",
                            "La requete a ete annulee.",
                        )
                    } else {
                        transportError(ProviderErrorCategory.NETWORK)
                    }
                activeCalls.remove(request.requestId)
                emit(StreamEvent.Failed(request.requestId, failure, sequence))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun cancel(requestId: String) {
        activeCalls[requestId]?.let {
            cancelledRequestIds += requestId
            it.cancel()
        }
    }

    internal val activeRequestIds: Set<String>
        get() = activeCalls.keys

    @Suppress("ThrowsCount")
    private suspend fun fetchModels(): List<ModelDescriptor> {
        val builder =
            Request.Builder()
                .url(configuration.endpoint("models"))
                .get()
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .header("User-Agent", USER_AGENT)
        authenticate(builder)
        return try {
            apiClient.newCall(builder.build()).awaitResponse().use { response ->
                if (!response.isSuccessful) throw response.toAnthropicException()
                response.decodeAnthropicModels(json, configuration)
            }
        } catch (error: SocketTimeoutException) {
            throw ProviderException(transportError(ProviderErrorCategory.TIMEOUT), error)
        } catch (error: IOException) {
            throw ProviderException(transportError(ProviderErrorCategory.NETWORK), error)
        } catch (error: IllegalArgumentException) {
            throw protocolException("anthropic_invalid_models_response", error)
        } catch (error: IllegalStateException) {
            throw protocolException("anthropic_invalid_models_response", error)
        }
    }

    private suspend fun buildMessagesRequest(request: ChatRequest): Request {
        val builder =
            Request.Builder()
                .url(configuration.endpoint("messages"))
                .post(
                    request.toAnthropicJson(
                        configuration.defaultMaxOutputTokens,
                    ).toString().toRequestBody(JSON_MEDIA_TYPE),
                )
                .header("Accept", SSE_MEDIA_TYPE)
                .header("User-Agent", USER_AGENT)
        authenticate(builder)
        return builder.build()
    }

    private suspend fun authenticate(builder: Request.Builder) {
        val failure = authenticator.authenticate(configuration.connection, builder) ?: return
        throw providerException(ProviderErrorCategory.AUTHENTICATION, failure.code, failure.userMessage)
    }

    private fun validateRequest(request: ChatRequest) {
        validateProfile(request.profile)
        if (!configuration.capabilitiesFor(request.profile.modelId).supports(request.capabilityRequirements())) {
            throw providerException(
                ProviderErrorCategory.CAPABILITY_MISMATCH,
                "anthropic_capability_mismatch",
                "Le modele Anthropic configure ne prend pas en charge les capacites demandees.",
            )
        }
    }

    private fun validateProfile(profile: ModelProfile) {
        if (profile.providerId != id || profile.connectionId != configuration.connection.id) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "anthropic_profile_connection_mismatch",
                "Le profil modele ne correspond pas a cette connexion Anthropic.",
            )
        }
        if (!profile.enabled || !configuration.connection.enabled) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "anthropic_profile_disabled",
                "Le profil modele ou sa connexion est desactive.",
            )
        }
    }
}

private suspend fun Call.awaitResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    error: IOException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            },
        )
    }

private fun Response.toAnthropicException(): ProviderException {
    val category =
        when (code) {
            401 -> ProviderErrorCategory.AUTHENTICATION
            402, 403 -> ProviderErrorCategory.PERMISSION_DENIED
            404 -> ProviderErrorCategory.MODEL_NOT_FOUND
            408, 504 -> ProviderErrorCategory.TIMEOUT
            429 -> ProviderErrorCategory.RATE_LIMITED
            529 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
            in 500..599 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
            else -> ProviderErrorCategory.INVALID_REQUEST
        }
    return ProviderException(
        ProviderError(
            category,
            "anthropic_http_$code",
            category.safeMessage(),
            header("Retry-After")?.toLongOrNull()?.times(1_000),
        ),
    )
}

private fun transportError(category: ProviderErrorCategory) =
    ProviderError(
        category,
        if (category == ProviderErrorCategory.TIMEOUT) "anthropic_timeout" else "anthropic_network_error",
        if (category == ProviderErrorCategory.TIMEOUT) {
            "Anthropic n'a pas repondu dans le delai imparti."
        } else {
            "La connexion a Anthropic a echoue."
        },
    )

private fun ProviderErrorCategory.safeMessage(): String =
    when (this) {
        ProviderErrorCategory.AUTHENTICATION -> "L'authentification Anthropic a echoue."
        ProviderErrorCategory.PERMISSION_DENIED -> "Le compte Anthropic ne permet pas cette requete."
        ProviderErrorCategory.MODEL_NOT_FOUND -> "Le modele Anthropic demande est introuvable."
        ProviderErrorCategory.TIMEOUT -> "Anthropic n'a pas repondu dans le delai imparti."
        ProviderErrorCategory.RATE_LIMITED -> "Le quota Anthropic est temporairement atteint."
        ProviderErrorCategory.SERVICE_UNAVAILABLE -> "Anthropic est temporairement surcharge ou indisponible."
        else -> "Anthropic a refuse la requete."
    }

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val SSE_MEDIA_TYPE = "text/event-stream"
private const val USER_AGENT = "Jean-Calcul-Assistant/0.1"
