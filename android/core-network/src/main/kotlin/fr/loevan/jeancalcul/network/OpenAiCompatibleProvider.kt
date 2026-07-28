package fr.loevan.jeancalcul.network

import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ContentModality
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
import fr.loevan.jeancalcul.domain.ProviderLimits
import fr.loevan.jeancalcul.domain.StreamEvent
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

const val OPENAI_COMPATIBLE_PROVIDER_ID = "openai_compatible"

data class OpenAiCompatibleConfiguration(
    val connection: ProviderConnection,
    val configuredModels: List<ModelDescriptor> = emptyList(),
    val capabilitiesByModel: Map<String, ModelCapabilities> = emptyMap(),
    val defaultCapabilities: ModelCapabilities = DEFAULT_OPENAI_CAPABILITIES,
    val providerId: String = OPENAI_COMPATIBLE_PROVIDER_ID,
    val providerKind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    val fallbackModelIds: List<String> = emptyList(),
    val additionalHeaders: Map<String, String> = emptyMap(),
    val includeUsageCost: Boolean = false,
) {
    init {
        require(connection.kind == providerKind)
        require(providerId.isNotBlank())
        require(configuredModels.map(ModelDescriptor::id).distinct().size == configuredModels.size)
        require(capabilitiesByModel.keys.none(String::isBlank))
        require(fallbackModelIds.none(String::isBlank))
        require(fallbackModelIds.distinct().size == fallbackModelIds.size)
        require(additionalHeaders.all { (name, value) -> name.isNotBlank() && value.isNotBlank() })
    }
}

sealed interface OpenAiConnectionValidation {
    data class Success(
        val models: List<ModelDescriptor>,
        val insecureTransport: Boolean,
    ) : OpenAiConnectionValidation

    data class Failure(
        val error: ProviderError,
    ) : OpenAiConnectionValidation
}

@Singleton
class OpenAiCompatibleProviderFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val secretStore: SecretStore,
    ) {
        fun create(configuration: OpenAiCompatibleConfiguration): OpenAiCompatibleProvider =
            OpenAiCompatibleProvider(client, secretStore, configuration)
    }

/** OpenAI chat-completions adapter for OpenAI and user-configured compatible endpoints. */
class OpenAiCompatibleProvider(
    client: OkHttpClient,
    secretStore: SecretStore,
    private val configuration: OpenAiCompatibleConfiguration,
    private val json: Json = DEFAULT_JSON,
) : ModelProvider {
    override val id: String = configuration.providerId

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

    suspend fun validateConnection(profile: ModelProfile): OpenAiConnectionValidation =
        try {
            validateProfile(profile)
            OpenAiConnectionValidation.Success(
                models = fetchModels(),
                insecureTransport = configuration.connection.usesInsecureTransport,
            )
        } catch (error: ProviderException) {
            OpenAiConnectionValidation.Failure(error.error)
        }

    @Suppress("CyclomaticComplexMethod", "SwallowedException")
    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> =
        flow {
            var sequence = 0L
            emit(StreamEvent.Started(request.requestId, sequence++))
            try {
                validateRequest(request)
                val httpRequest = buildChatRequest(request)
                val call = streamingClient.newCall(httpRequest)
                if (activeCalls.putIfAbsent(request.requestId, call) != null) {
                    throw providerException(
                        ProviderErrorCategory.INVALID_REQUEST,
                        "duplicate_request_id",
                        "Une requete portant cet identifiant est deja active.",
                    )
                }
                val cancellation = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.awaitResponse().use { response ->
                        if (!response.isSuccessful) throw response.toProviderException(id)
                        val body = response.body ?: throw protocolException("empty_response_body")
                        val parser = OpenAiStreamParser(request.requestId, json) { sequence++ }
                        body.source().use { source ->
                            while (!source.exhausted()) {
                                currentCoroutineContext().ensureActive()
                                val line = source.readUtf8Line() ?: break
                                parser.consume(line).forEach { emit(it) }
                            }
                        }
                        parser.finish().forEach { emit(it) }
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
            } catch (timeout: SocketTimeoutException) {
                emit(StreamEvent.Failed(request.requestId, timeoutError(id), sequence))
            } catch (error: IOException) {
                val normalized =
                    if (cancelledRequestIds.remove(request.requestId)) {
                        ProviderError(
                            ProviderErrorCategory.CANCELLED,
                            "request_cancelled",
                            "La requete a ete annulee.",
                        )
                    } else {
                        networkError(id)
                    }
                activeCalls.remove(request.requestId)
                emit(StreamEvent.Failed(request.requestId, normalized, sequence))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun cancel(requestId: String) {
        activeCalls[requestId]?.let { call ->
            cancelledRequestIds += requestId
            call.cancel()
        }
    }

    internal val activeRequestIds: Set<String>
        get() = activeCalls.keys

    @Suppress("ThrowsCount")
    private suspend fun fetchModels(): List<ModelDescriptor> {
        val requestBuilder =
            Request.Builder()
                .url(configuration.endpoint("models"))
                .get()
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .header("User-Agent", USER_AGENT)
        configuration.additionalHeaders.forEach(requestBuilder::header)
        authenticate(requestBuilder)
        return try {
            apiClient.newCall(requestBuilder.build()).awaitResponse().use { response ->
                if (!response.isSuccessful) throw response.toProviderException(id)
                response.decodeModels(json, configuration)
            }
        } catch (timeout: SocketTimeoutException) {
            throw ProviderException(timeoutError(id), timeout)
        } catch (error: IOException) {
            throw ProviderException(networkError(id), error)
        } catch (error: IllegalArgumentException) {
            throw protocolException("invalid_models_response", error)
        } catch (error: IllegalStateException) {
            throw protocolException("invalid_models_response", error)
        }
    }

    private suspend fun buildChatRequest(request: ChatRequest): Request {
        val requestBuilder =
            Request.Builder()
                .url(configuration.endpoint("chat/completions"))
                .post(
                    request.toOpenAiJson(
                        fallbackModelIds = configuration.fallbackModelIds,
                        includeUsageCost = configuration.includeUsageCost,
                    ).toString().toRequestBody(JSON_MEDIA_TYPE),
                )
                .header("Accept", SSE_MEDIA_TYPE)
                .header("User-Agent", USER_AGENT)
        configuration.additionalHeaders.forEach(requestBuilder::header)
        authenticate(requestBuilder)
        return requestBuilder.build()
    }

    private suspend fun authenticate(requestBuilder: Request.Builder) {
        val failure = authenticator.authenticate(configuration.connection, requestBuilder) ?: return
        throw providerException(
            category = ProviderErrorCategory.AUTHENTICATION,
            code = failure.code,
            message = failure.userMessage,
        )
    }

    private fun validateRequest(request: ChatRequest) {
        validateProfile(request.profile)
        if (!configuration.capabilitiesFor(request.profile.modelId).supports(request.capabilityRequirements())) {
            throw providerException(
                ProviderErrorCategory.CAPABILITY_MISMATCH,
                "capability_mismatch",
                "Le modele configure ne prend pas en charge les capacites demandees.",
            )
        }
    }

    private fun validateProfile(profile: ModelProfile) {
        if (profile.providerId != id || profile.connectionId != configuration.connection.id) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "profile_connection_mismatch",
                "Le profil modele ne correspond pas a cette connexion fournisseur.",
            )
        }
        if (!profile.enabled || !configuration.connection.enabled) {
            throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "profile_disabled",
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

private fun Response.toProviderException(providerId: String): ProviderException {
    val retryAfterMillis = header("Retry-After")?.toLongOrNull()?.times(1_000)
    val category = code.toProviderErrorCategory()
    return ProviderException(
        ProviderError(
            category = category,
            code = "${providerId}_http_$code",
            message = category.userMessage(),
            retryAfterMillis = retryAfterMillis,
        ),
    )
}

private fun Int.toProviderErrorCategory(): ProviderErrorCategory =
    when (this) {
        401 -> ProviderErrorCategory.AUTHENTICATION
        402, 403 -> ProviderErrorCategory.PERMISSION_DENIED
        404 -> ProviderErrorCategory.MODEL_NOT_FOUND
        408 -> ProviderErrorCategory.TIMEOUT
        429 -> ProviderErrorCategory.RATE_LIMITED
        in 500..599 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
        else -> ProviderErrorCategory.INVALID_REQUEST
    }

private fun ProviderErrorCategory.userMessage(): String =
    when (this) {
        ProviderErrorCategory.AUTHENTICATION -> "L'authentification du fournisseur a echoue."
        ProviderErrorCategory.PERMISSION_DENIED -> "Le fournisseur a refuse cette requete."
        ProviderErrorCategory.MODEL_NOT_FOUND -> "Le modele ou l'endpoint demande est introuvable."
        ProviderErrorCategory.TIMEOUT -> "Le fournisseur n'a pas repondu dans le delai imparti."
        ProviderErrorCategory.RATE_LIMITED -> "Le fournisseur limite temporairement les requetes."
        ProviderErrorCategory.SERVICE_UNAVAILABLE -> "Le fournisseur est temporairement indisponible."
        else -> "Le fournisseur a refuse la requete."
    }

internal fun providerException(
    category: ProviderErrorCategory,
    code: String,
    message: String,
): ProviderException = ProviderException(ProviderError(category, code, message))

internal fun protocolException(
    code: String,
    cause: Throwable? = null,
): ProviderException =
    ProviderException(
        ProviderError(
            ProviderErrorCategory.PROTOCOL,
            code,
            "Le fournisseur a renvoye une reponse incompatible.",
        ),
        cause,
    )

private fun timeoutError(providerId: String) =
    ProviderError(
        ProviderErrorCategory.TIMEOUT,
        "${providerId}_timeout",
        "Le fournisseur n'a pas repondu dans le delai imparti.",
    )

private fun networkError(providerId: String) =
    ProviderError(
        ProviderErrorCategory.NETWORK,
        "${providerId}_network_error",
        "La connexion au fournisseur a echoue.",
    )

private val DEFAULT_OPENAI_CAPABILITIES =
    ModelCapabilities(
        inputModalities = setOf(ContentModality.TEXT),
        outputModalities = setOf(ContentModality.TEXT),
        supportsStreaming = true,
        supportsCancellation = true,
        supportsToolCalling = true,
        limits = ProviderLimits(maxContextTokens = 128_000, maxOutputTokens = 128_000),
    )

private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val SSE_MEDIA_TYPE = "text/event-stream"
private const val USER_AGENT = "Jean-Calcul-Assistant/0.1"
