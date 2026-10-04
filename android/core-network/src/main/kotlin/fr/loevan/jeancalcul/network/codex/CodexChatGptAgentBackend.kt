package fr.loevan.jeancalcul.network.codex

import android.annotation.SuppressLint
import fr.loevan.jeancalcul.domain.AgentBackend
import fr.loevan.jeancalcul.domain.AgentBackendState
import fr.loevan.jeancalcul.domain.AgentBackendStatus
import fr.loevan.jeancalcul.domain.AgentCapabilities
import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.AgentRun
import fr.loevan.jeancalcul.domain.AgentRunStatus
import fr.loevan.jeancalcul.domain.AgentSession
import fr.loevan.jeancalcul.domain.AgentSkillDescriptor
import fr.loevan.jeancalcul.domain.AgentStreamEvent
import fr.loevan.jeancalcul.domain.AgentToolApproval
import fr.loevan.jeancalcul.domain.AgentToolDescriptor
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.AssistantSettingsValidator
import fr.loevan.jeancalcul.domain.ContentModality
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.ProviderUsage
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CompanionHttpClientFactory
    @Inject
    constructor(
        private val baseClient: OkHttpClient,
    ) {
        private val companionBaseClient: OkHttpClient =
            baseClient
                .newBuilder()
                .readTimeout(COMPANION_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(COMPANION_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        private val pinnedClients = ConcurrentHashMap<String, OkHttpClient>()

        @SuppressLint("CustomX509TrustManager", "BadHostnameVerifier")
        fun clientFor(connection: ProviderConnection): OkHttpClient {
            val pin = connection.tlsCertificateSha256 ?: return companionBaseClient
            return pinnedClients.getOrPut(pin) {
                val trustManager = PinnedTrustManager(pin)
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustManager), SecureRandom())
                }
                companionBaseClient
                    .newBuilder()
                    .sslSocketFactory(sslContext.socketFactory, trustManager)
                    .hostnameVerifier(PinnedHostnameVerifier(pin))
                    .build()
            }
        }

        private companion object {
            const val COMPANION_READ_TIMEOUT_SECONDS = 35L
            const val COMPANION_CALL_TIMEOUT_SECONDS = 45L
        }
    }

private class PinnedTrustManager(
    private val expectedPin: String,
) : X509TrustManager {
    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        throw CertificateException("Client certificates are not supported.")
    }

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Missing companion certificate.")
        if (!leaf.matchesPin(expectedPin)) throw CertificateException("Companion certificate pin mismatch.")
        leaf.checkValidity()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private class PinnedHostnameVerifier(
    private val expectedPin: String,
) : HostnameVerifier {
    override fun verify(
        hostname: String?,
        session: SSLSession?,
    ): Boolean =
        runCatching {
            val leaf = session?.peerCertificates?.firstOrNull() as? X509Certificate ?: return false
            leaf.matchesPin(expectedPin)
        }.getOrDefault(false)
}

private fun X509Certificate.matchesPin(expectedPin: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
    val actual = "sha256/${Base64.getEncoder().encodeToString(digest)}"
    return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), expectedPin.toByteArray(Charsets.US_ASCII))
}

@Singleton
@Suppress("TooManyFunctions")
class CodexChatGptAgentBackend
    @Inject
    constructor(
        private val settingsRepository: AssistantSettingsRepository,
        private val secretStore: SecretStore,
        private val clientFactory: CompanionHttpClientFactory,
    ) : AgentBackend {
        override val id: String = ID

        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val sessions = ConcurrentHashMap<String, ProviderConnection>()

        override suspend fun capabilities(profile: AgentProfile): AgentCapabilities {
            requireCompatibleProfile(profile)
            return AgentCapabilities(
                inputModalities = setOf(ContentModality.TEXT),
                outputModalities = setOf(ContentModality.TEXT),
                supportsSessionResume = true,
                supportsCancellation = true,
                supportsToolApprovals = false,
                supportsSkills = false,
                supportsLongRunningJobs = false,
            )
        }

        override suspend fun createSession(profile: AgentProfile): AgentSession {
            val connection = connectionFor(profile)
            val response =
                callJson<CompanionSessionResponse>(
                    connection,
                    requestBuilder(connection, listOf("v1", "sessions"))
                        .post(
                            json.encodeToString(CreateSessionRequest(profile.agentId))
                                .toRequestBody(JSON_MEDIA_TYPE),
                        ),
                )
            sessions[response.id] = connection
            return AgentSession(response.id, profile.id, response.resumable)
        }

        override suspend fun resumeSession(
            profile: AgentProfile,
            sessionId: String,
        ): AgentSession {
            val connection = connectionFor(profile)
            val response =
                callJson<CompanionSessionResponse>(
                    connection,
                    requestBuilder(connection, listOf("v1", "sessions", sessionId, "resume"))
                        .post(
                            json.encodeToString(CreateSessionRequest(profile.agentId))
                                .toRequestBody(JSON_MEDIA_TYPE),
                        ),
                )
            sessions[response.id] = connection
            return AgentSession(response.id, profile.id, response.resumable)
        }

        override suspend fun sendMessage(
            sessionId: String,
            request: AgentRequest,
        ): AgentRun {
            val connection = sessionConnection(sessionId)
            val payload =
                CompanionMessageRequest(
                    requestId = request.requestId,
                    messages =
                        request.messages.mapNotNull { message ->
                            val text =
                                message.content
                                    .filterIsInstance<MessageContent.Text>()
                                    .joinToString("\n") { it.text }
                                    .trim()
                            text.takeIf(String::isNotBlank)?.let {
                                CompanionMessage(message.role.name, it)
                            }
                        },
                )
            val run =
                callJson<CompanionRunResponse>(
                    connection,
                    requestBuilder(connection, listOf("v1", "sessions", sessionId, "messages"))
                        .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)),
                )
            return AgentRun(
                id = run.id,
                sessionId = run.sessionId,
                requestId = run.requestId,
                status = run.status.toAgentRunStatus(),
            )
        }

        override fun streamEvents(
            sessionId: String,
            afterSequence: Long?,
        ): Flow<AgentStreamEvent> =
            flow {
                val connection = sessionConnection(sessionId)
                var cursor = afterSequence ?: 0L
                while (currentCoroutineContext().isActive) {
                    val response =
                        callJson<CompanionEventsResponse>(
                            connection,
                            requestBuilder(
                                connection,
                                listOf("v1", "sessions", sessionId, "events"),
                                query = mapOf("after" to cursor.toString()),
                            ).get(),
                        )
                    for (event in response.events.sortedBy(CompanionEvent::sequence)) {
                        if (event.sequence <= cursor) continue
                        cursor = event.sequence
                        val normalized = event.toDomainEvent()
                        emit(normalized)
                        if (normalized is StreamEvent.Completed || normalized is StreamEvent.Failed) {
                            return@flow
                        }
                    }
                }
            }

        override suspend fun cancel(
            sessionId: String,
            runId: String,
        ) {
            val connection = sessionConnection(sessionId)
            callJson<CancelResponse>(
                connection,
                requestBuilder(connection, listOf("v1", "sessions", sessionId, "cancel"))
                    .post(json.encodeToString(CancelRequest(runId)).toRequestBody(JSON_MEDIA_TYPE)),
            )
        }

        override suspend fun listModels(profile: AgentProfile): List<ModelDescriptor> {
            val connection = connectionFor(profile)
            val response =
                callJson<ModelsResponse>(
                    connection,
                    requestBuilder(connection, listOf("v1", "models")).get(),
                )
            return response.models.map {
                ModelDescriptor(
                    id = it.id,
                    displayName = it.displayName,
                    capabilities =
                        ModelCapabilities(
                            supportsStreaming = true,
                            supportsCancellation = true,
                            supportsToolCalling = false,
                        ),
                )
            }
        }

        override suspend fun listTools(profile: AgentProfile): List<AgentToolDescriptor> {
            requireCompatibleProfile(profile)
            return emptyList()
        }

        override suspend fun listSkills(profile: AgentProfile): List<AgentSkillDescriptor> {
            requireCompatibleProfile(profile)
            return emptyList()
        }

        override suspend fun approveTool(
            sessionId: String,
            approval: AgentToolApproval,
        ) {
            throw providerException(
                ProviderErrorCategory.CAPABILITY_MISMATCH,
                "tool_approval_not_supported",
                "Les appels d'outils Codex Android seront raccordes dans l'etape outils dediee.",
            )
        }

        override suspend fun getStatus(profile: AgentProfile): AgentBackendStatus =
            try {
                val connection = connectionFor(profile)
                val response =
                    callJson<StatusResponse>(
                        connection,
                        requestBuilder(connection, listOf("v1", "status")).get(),
                    )
                AgentBackendStatus(
                    state =
                        when (response.state) {
                            "AVAILABLE" -> AgentBackendState.AVAILABLE
                            "DEGRADED" -> AgentBackendState.DEGRADED
                            else -> AgentBackendState.OFFLINE
                        },
                    message = response.message,
                )
            } catch (error: ProviderException) {
                AgentBackendStatus(AgentBackendState.OFFLINE, error.error.message)
            }

        private suspend fun connectionFor(profile: AgentProfile): ProviderConnection {
            requireCompatibleProfile(profile)
            val connectionId =
                profile.connectionId
                    ?: throw providerException(
                        ProviderErrorCategory.INVALID_REQUEST,
                        "missing_connection",
                        "Le profil Codex n'a aucun compagnon configure.",
                    )
            val settings = settingsRepository.settings.first()
            val connection =
                settings.providers.firstOrNull { it.id == connectionId }
                    ?: throw providerException(
                        ProviderErrorCategory.INVALID_REQUEST,
                        "unknown_connection",
                        "Le compagnon configure est introuvable.",
                    )
            if (connection.kind != ProviderKind.AGENT_BACKEND || !connection.enabled) {
                throw providerException(
                    ProviderErrorCategory.INVALID_REQUEST,
                    "invalid_connection",
                    "Le compagnon agent est desactive ou invalide.",
                )
            }
            val configurationErrors = AssistantSettingsValidator.providerErrors(connection)
            if (configurationErrors.isNotEmpty()) {
                throw providerException(
                    ProviderErrorCategory.INVALID_REQUEST,
                    "invalid_connection_security",
                    configurationErrors.joinToString(" "),
                )
            }
            return connection
        }

        private fun requireCompatibleProfile(profile: AgentProfile) {
            if (profile.backendId != id) {
                throw providerException(
                    ProviderErrorCategory.INVALID_REQUEST,
                    "backend_mismatch",
                    "Ce profil ne cible pas le backend ChatGPT du compagnon.",
                )
            }
        }

        private fun sessionConnection(sessionId: String): ProviderConnection =
            sessions[sessionId]
                ?: throw providerException(
                    ProviderErrorCategory.PROTOCOL,
                    "session_context_missing",
                    "La session compagnon doit etre reprise avant utilisation.",
                )

        private suspend inline fun <reified T> callJson(
            connection: ProviderConnection,
            builder: Request.Builder,
        ): T {
            val request = authorizedRequest(connection, builder)
            val response =
                try {
                    clientFactory.clientFor(connection).newCall(request).await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IOException) {
                    throw providerException(
                        ProviderErrorCategory.NETWORK,
                        "companion_unreachable",
                        "Le compagnon ChatGPT est injoignable.",
                        failure,
                    )
                } catch (failure: Exception) {
                    throw providerException(
                        ProviderErrorCategory.NETWORK,
                        "companion_tls_failure",
                        "La connexion securisee au compagnon a echoue. Verifiez l'empreinte TLS.",
                        failure,
                    )
                }
            response.use {
                if (!it.isSuccessful) throw it.toProviderException()
                val body = it.body?.string().orEmpty()
                return try {
                    json.decodeFromString(body)
                } catch (failure: Exception) {
                    throw providerException(
                        ProviderErrorCategory.PROTOCOL,
                        "invalid_companion_response",
                        "Le compagnon a renvoye une reponse invalide.",
                        failure,
                    )
                }
            }
        }

        private suspend fun authorizedRequest(
            connection: ProviderConnection,
            builder: Request.Builder,
        ): Request {
            val secretId =
                connection.secretId
                    ?: throw providerException(
                        ProviderErrorCategory.AUTHENTICATION,
                        "pairing_required",
                        "Le compagnon doit etre appaire dans les parametres.",
                    )
            return when (val result = secretStore.get(SecretId(secretId))) {
                is SecretStoreResult.Failure ->
                    throw providerException(
                        ProviderErrorCategory.AUTHENTICATION,
                        "pairing_secret_unavailable",
                        result.error.userMessage,
                    )

                is SecretStoreResult.Success -> {
                    val secret =
                        result.value
                            ?: throw providerException(
                                ProviderErrorCategory.AUTHENTICATION,
                                "pairing_secret_missing",
                                "Le jeton d'appairage n'est plus disponible.",
                            )
                    secret.use { value ->
                        value.useChars { chars ->
                            builder
                                .header("Authorization", "Bearer ${chars.concatToString()}")
                                .header("Accept", "application/json")
                                .build()
                        }
                    }
                }
            }
        }

        private fun requestBuilder(
            connection: ProviderConnection,
            pathSegments: List<String>,
            query: Map<String, String> = emptyMap(),
        ): Request.Builder {
            val urlBuilder = connection.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            pathSegments.forEach(urlBuilder::addPathSegment)
            query.forEach(urlBuilder::addQueryParameter)
            return Request.Builder().url(urlBuilder.build())
        }

        private fun Response.toProviderException(): ProviderException {
            val category =
                when (code) {
                    401 -> ProviderErrorCategory.AUTHENTICATION
                    403 -> ProviderErrorCategory.PERMISSION_DENIED
                    404 -> ProviderErrorCategory.PROTOCOL
                    408 -> ProviderErrorCategory.TIMEOUT
                    409 -> ProviderErrorCategory.INVALID_REQUEST
                    429 -> ProviderErrorCategory.RATE_LIMITED
                    in 500..599 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
                    else -> ProviderErrorCategory.UNKNOWN
                }
            val message =
                when (code) {
                    401 -> "Appairage refuse ou connexion ChatGPT requise sur le compagnon."
                    429 -> "La limite du forfait ChatGPT est temporairement atteinte."
                    in 500..599 -> "Le compagnon ChatGPT est temporairement indisponible."
                    else -> "Le compagnon a refuse la requete (HTTP $code)."
                }
            return providerException(category, "companion_http_$code", message)
        }

        companion object {
            const val ID = "chatgpt-plan"
            private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }

private suspend fun Call.await(): Response =
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

private fun providerException(
    category: ProviderErrorCategory,
    code: String,
    message: String,
    cause: Throwable? = null,
): ProviderException = ProviderException(ProviderError(category, code, message), cause)

@Serializable
private data class CreateSessionRequest(
    val model: String,
)

@Serializable
private data class CompanionSessionResponse(
    val id: String,
    val model: String,
    val resumable: Boolean,
)

@Serializable
private data class CompanionMessage(
    val role: String,
    val text: String,
)

@Serializable
private data class CompanionMessageRequest(
    @SerialName("request_id")
    val requestId: String,
    val messages: List<CompanionMessage>,
)

@Serializable
private data class CompanionRunResponse(
    val id: String,
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("request_id")
    val requestId: String,
    val status: String,
)

private fun String.toAgentRunStatus(): AgentRunStatus =
    runCatching { AgentRunStatus.valueOf(this) }.getOrDefault(AgentRunStatus.RUNNING)

@Serializable
private data class CompanionEventsResponse(
    val events: List<CompanionEvent>,
)

@Serializable
private data class CompanionEvent(
    val sequence: Long,
    val type: String,
    @SerialName("request_id")
    val requestId: String,
    val text: String? = null,
    val code: String? = null,
    val message: String? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
    @SerialName("input_tokens")
    val inputTokens: Int? = null,
    @SerialName("output_tokens")
    val outputTokens: Int? = null,
) {
    fun toDomainEvent(): AgentStreamEvent =
        when (type) {
            "started" -> StreamEvent.Started(requestId, sequence)
            "text_delta" -> StreamEvent.TextDelta(requestId, text.orEmpty(), sequence)
            "usage" ->
                StreamEvent.UsageUpdated(
                    requestId,
                    ProviderUsage(inputTokens = inputTokens, outputTokens = outputTokens),
                    sequence,
                )
            "completed" ->
                StreamEvent.Completed(
                    requestId,
                    runCatching { FinishReason.valueOf(finishReason ?: "UNKNOWN") }
                        .getOrDefault(FinishReason.UNKNOWN),
                    sequence,
                )
            "failed" ->
                StreamEvent.Failed(
                    requestId,
                    ProviderError(
                        category = code.toProviderErrorCategory(),
                        code = code ?: "companion_error",
                        message = message ?: "Erreur compagnon.",
                    ),
                    sequence,
                )
            else ->
                StreamEvent.Failed(
                    requestId,
                    ProviderError(
                        ProviderErrorCategory.PROTOCOL,
                        "unknown_companion_event",
                        "Evenement compagnon inconnu.",
                    ),
                    sequence,
                )
        }
}

private fun String?.toProviderErrorCategory(): ProviderErrorCategory =
    when (this) {
        "chatgpt_authentication_failed", "pairing_required" -> ProviderErrorCategory.AUTHENTICATION
        "subscription_sharing_usage_limit_exceeded", "rate_limited" -> ProviderErrorCategory.RATE_LIMITED
        "model_not_found", "invalid_model" -> ProviderErrorCategory.MODEL_NOT_FOUND
        "stream_interrupted" -> ProviderErrorCategory.NETWORK
        else -> ProviderErrorCategory.UNKNOWN
    }

@Serializable
private data class CancelRequest(
    @SerialName("run_id")
    val runId: String,
)

@Serializable
private data class CancelResponse(
    val cancelled: Boolean,
)

@Serializable
private data class ModelsResponse(
    val models: List<CompanionModel>,
)

@Serializable
private data class CompanionModel(
    val id: String,
    @SerialName("display_name")
    val displayName: String,
)

@Serializable
private data class StatusResponse(
    val state: String,
    val authenticated: Boolean,
    val message: String? = null,
)
