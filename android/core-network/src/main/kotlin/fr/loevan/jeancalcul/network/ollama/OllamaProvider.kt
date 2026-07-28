package fr.loevan.jeancalcul.network.ollama

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

const val OLLAMA_PROVIDER_ID = "ollama"

enum class OllamaCertificatePolicy {
    SYSTEM_TRUST_ONLY,
}

data class OllamaConfiguration(
    val connection: ProviderConnection,
    val timeoutMillis: Long = 30_000,
    val allowInsecureHttp: Boolean = true,
    val certificatePolicy: OllamaCertificatePolicy = OllamaCertificatePolicy.SYSTEM_TRUST_ONLY,
    val configuredModels: List<ModelDescriptor> = emptyList(),
    val capabilitiesByModel: Map<String, ModelCapabilities> = emptyMap(),
    val defaultCapabilities: ModelCapabilities = DEFAULT_OLLAMA_CAPABILITIES,
) {
    init {
        require(connection.kind == ProviderKind.OLLAMA)
        require(timeoutMillis > 0)
        require(configuredModels.map(ModelDescriptor::id).distinct().size == configuredModels.size)
    }

    val insecureTransportWarning: String?
        get() =
            if (connection.usesInsecureTransport) {
                "Connexion HTTP locale non chiffree : utilisez uniquement un reseau prive de confiance."
            } else {
                null
            }

    fun configuredCapabilities(modelId: String): ModelCapabilities? =
        capabilitiesByModel[modelId] ?: configuredModels.firstOrNull { it.id == modelId }?.capabilities
}

sealed interface OllamaConnectionValidation {
    data class Success(
        val models: List<ModelDescriptor>,
        val insecureTransportWarning: String?,
    ) : OllamaConnectionValidation

    data class Failure(val error: ProviderError) : OllamaConnectionValidation
}

@Singleton
class OllamaProviderFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val secretStore: SecretStore,
    ) {
        fun create(configuration: OllamaConfiguration): OllamaProvider =
            OllamaProvider(client, secretStore, configuration)
    }

@Suppress("TooManyFunctions")
class OllamaProvider(
    client: OkHttpClient,
    secretStore: SecretStore,
    private val configuration: OllamaConfiguration,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : ModelProvider {
    override val id: String = OLLAMA_PROVIDER_ID

    private val apiClient =
        client.newBuilder()
            .connectTimeout(configuration.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(configuration.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    private val streamingClient = apiClient.newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).build()
    private val authenticator = ProviderRequestAuthenticator(secretStore)
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val cancelledRequestIds = ConcurrentHashMap.newKeySet<String>()

    override suspend fun capabilities(profile: ModelProfile): ModelCapabilities {
        validateProfile(profile)
        validateTransportPolicy()
        return configuration.configuredCapabilities(profile.modelId) ?: fetchCapabilities(profile.modelId)
    }

    override suspend fun listModels(profile: ModelProfile): List<ModelDescriptor> {
        validateProfile(profile)
        validateTransportPolicy()
        if (configuration.configuredModels.isNotEmpty()) return configuration.configuredModels
        return fetchModels()
    }

    suspend fun validateConnection(profile: ModelProfile): OllamaConnectionValidation =
        try {
            validateProfile(profile)
            validateTransportPolicy()
            OllamaConnectionValidation.Success(fetchModels(), configuration.insecureTransportWarning)
        } catch (error: ProviderException) {
            OllamaConnectionValidation.Failure(error.error)
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "SwallowedException")
    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> =
        flow {
            var sequence = 0L
            emit(StreamEvent.Started(request.requestId, sequence++))
            try {
                validateProfile(request.profile)
                validateTransportPolicy()
                val capabilities =
                    configuration.configuredCapabilities(request.profile.modelId)
                        ?: fetchCapabilities(request.profile.modelId)
                if (!capabilities.supports(request.capabilityRequirements())) {
                    throw providerException(
                        ProviderErrorCategory.CAPABILITY_MISMATCH,
                        "ollama_capability_mismatch",
                        "Le modele Ollama configure ne prend pas en charge les capacites demandees.",
                    )
                }
                val call = streamingClient.newCall(buildChatRequest(request))
                if (activeCalls.putIfAbsent(request.requestId, call) != null) {
                    throw providerException(
                        ProviderErrorCategory.INVALID_REQUEST,
                        "ollama_duplicate_request_id",
                        "Une requete portant cet identifiant est deja active.",
                    )
                }
                val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.awaitResponse().use { response ->
                        if (!response.isSuccessful) throw response.toOllamaException()
                        val body = response.body ?: throw protocolException("ollama_empty_response_body")
                        val parser = OllamaStreamParser(request.requestId, json) { sequence++ }
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
                            "ollama_request_cancelled",
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

    private suspend fun fetchModels(): List<ModelDescriptor> {
        val names =
            execute(Request.Builder().url(configuration.endpoint("tags")).get()).use {
                it.decodeOllamaModelNames(json)
            }
        return names.map { (id, displayName) ->
            ModelDescriptor(id, displayName, configuration.configuredCapabilities(id) ?: fetchCapabilities(id))
        }
    }

    private suspend fun fetchCapabilities(modelId: String): ModelCapabilities {
        val request =
            Request.Builder()
                .url(configuration.endpoint("show"))
                .post(buildJsonObject { put("model", modelId) }.toString().toRequestBody(JSON_MEDIA_TYPE))
        return execute(request).use { it.decodeOllamaCapabilities(json, configuration.defaultCapabilities) }
    }

    @Suppress("ThrowsCount")
    private suspend fun execute(builder: Request.Builder): Response {
        builder.header("Accept", JSON_MEDIA_TYPE.toString()).header("User-Agent", USER_AGENT)
        authenticate(builder)
        return try {
            apiClient.newCall(builder.build()).awaitResponse().also { response ->
                if (!response.isSuccessful) {
                    val exception = response.toOllamaException()
                    response.close()
                    throw exception
                }
            }
        } catch (error: SocketTimeoutException) {
            throw ProviderException(transportError(ProviderErrorCategory.TIMEOUT), error)
        } catch (error: IOException) {
            throw ProviderException(transportError(ProviderErrorCategory.NETWORK), error)
        }
    }

    private suspend fun buildChatRequest(request: ChatRequest): Request {
        val builder =
            Request.Builder()
                .url(configuration.endpoint("chat"))
                .post(request.toOllamaJson().toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", NDJSON_MEDIA_TYPE)
                .header("User-Agent", USER_AGENT)
        authenticate(builder)
        return builder.build()
    }

    private suspend fun authenticate(builder: Request.Builder) {
        val failure = authenticator.authenticate(configuration.connection, builder) ?: return
        throw providerException(ProviderErrorCategory.AUTHENTICATION, failure.code, failure.userMessage)
    }

    private fun validateTransportPolicy() {
        if (configuration.connection.usesInsecureTransport && !configuration.allowInsecureHttp) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "ollama_insecure_http_requires_opt_in",
                "La connexion HTTP Ollama doit etre explicitement autorisee pour ce reseau local.",
            )
        }
    }

    private fun validateProfile(profile: ModelProfile) {
        if (profile.providerId != id || profile.connectionId != configuration.connection.id) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "ollama_profile_connection_mismatch",
                "Le profil modele ne correspond pas a cette connexion Ollama.",
            )
        }
        if (!profile.enabled || !configuration.connection.enabled) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "ollama_profile_disabled",
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

private fun Response.toOllamaException(): ProviderException {
    val category =
        when (code) {
            401 -> ProviderErrorCategory.AUTHENTICATION
            403 -> ProviderErrorCategory.PERMISSION_DENIED
            404 -> ProviderErrorCategory.MODEL_NOT_FOUND
            408 -> ProviderErrorCategory.TIMEOUT
            429 -> ProviderErrorCategory.RATE_LIMITED
            in 500..599 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
            else -> ProviderErrorCategory.INVALID_REQUEST
        }
    return ProviderException(
        ProviderError(
            category,
            "ollama_http_$code",
            when (category) {
                ProviderErrorCategory.MODEL_NOT_FOUND -> "Le modele Ollama demande n'est pas installe."
                ProviderErrorCategory.RATE_LIMITED -> "Ollama limite temporairement les requetes."
                ProviderErrorCategory.SERVICE_UNAVAILABLE -> "Le serveur Ollama est temporairement indisponible."
                else -> "Le serveur Ollama a refuse la requete."
            },
        ),
    )
}

private fun transportError(category: ProviderErrorCategory) =
    ProviderError(
        category,
        if (category == ProviderErrorCategory.TIMEOUT) "ollama_timeout" else "ollama_network_unavailable",
        if (category == ProviderErrorCategory.TIMEOUT) {
            "Le serveur Ollama n'a pas repondu dans le delai configure."
        } else {
            "Le serveur Ollama est introuvable sur le reseau configure."
        },
    )

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val NDJSON_MEDIA_TYPE = "application/x-ndjson"
private const val USER_AGENT = "Jean-Calcul-Assistant/0.1"
