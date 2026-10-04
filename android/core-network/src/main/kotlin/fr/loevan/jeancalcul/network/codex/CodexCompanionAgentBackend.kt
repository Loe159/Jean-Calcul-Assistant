package fr.loevan.jeancalcul.network.codex

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.loevan.jeancalcul.domain.AgentBackend
import fr.loevan.jeancalcul.domain.AgentBackendFactory
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
import fr.loevan.jeancalcul.domain.ContentModality
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.network.ProviderRequestAuthenticator
import fr.loevan.jeancalcul.security.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val BACKEND_ID = "codex-companion"
private const val COMPANION_PROTOCOL_VERSION = "1"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

@Singleton
class CodexCompanionAgentBackendFactory
    @Inject
    constructor(
        private val client: OkHttpClient,
        private val secretStore: SecretStore,
    ) : AgentBackendFactory {
        override suspend fun create(
            connection: ProviderConnection,
            profile: AgentProfile,
        ): AgentBackend {
            require(connection.kind == ProviderKind.AGENT_BACKEND) {
                "Codex companion requires an AGENT_BACKEND connection."
            }
            require(profile.backendId == BACKEND_ID) {
                "Unsupported agent backend: ${profile.backendId}"
            }
            require(profile.connectionId == connection.id) {
                "Agent profile and connection do not match."
            }
            require(connection.enabled && profile.enabled) {
                "Codex companion profile and connection must be enabled."
            }
            require(connection.secretId != null) {
                "Codex companion requires a pairing secret."
            }
            return CodexCompanionAgentBackend(connection, client, secretStore)
        }
    }

@Suppress("TooManyFunctions")
internal class CodexCompanionAgentBackend(
    private val connection: ProviderConnection,
    private val client: OkHttpClient,
    secretStore: SecretStore,
) : AgentBackend {
    override val id: String = BACKEND_ID

    private val json = Json { ignoreUnknownKeys = true }
    private val authenticator = ProviderRequestAuthenticator(secretStore)
    private val activeRuns = ConcurrentHashMap<String, String>()

    override suspend fun capabilities(profile: AgentProfile): AgentCapabilities =
        AgentCapabilities(
            inputModalities = setOf(ContentModality.TEXT),
            outputModalities = setOf(ContentModality.TEXT),
            supportsSessionResume = true,
            supportsCancellation = true,
            supportsToolApprovals = false,
            supportsSkills = false,
            supportsLongRunningJobs = false,
        )

    override suspend fun createSession(profile: AgentProfile): AgentSession {
        val response = executeJson(
            requestBuilder("/v1/sessions")
                .post(ByteArray(0).toRequestBody(null))
                .build(),
            SessionResponse.serializer(),
        )
        return AgentSession(response.sessionId, profile.id, response.resumable)
    }

    override suspend fun resumeSession(
        profile: AgentProfile,
        sessionId: String,
    ): AgentSession {
        val response = executeJson(
            requestBuilder("/v1/sessions/${sessionId.urlPathSegment()}/resume")
                .post(ByteArray(0).toRequestBody(null))
                .build(),
            SessionResponse.serializer(),
        )
        check(response.sessionId == sessionId) { "Companion resumed a different session." }
        return AgentSession(response.sessionId, profile.id, response.resumable)
    }

    override suspend fun sendMessage(
        sessionId: String,
        request: AgentRequest,
    ): AgentRun {
        val text =
            request.messages
                .lastOrNull { it.role == MessageRole.USER }
                ?.content
                ?.filterIsInstance<MessageContent.Text>()
                ?.joinToString("\n") { it.text }
                ?.trim()
                .orEmpty()
        require(text.isNotEmpty()) { "Agent request has no user text." }
        val body = json.encodeToString(
            RunRequest.serializer(),
            RunRequest(request.requestId, text),
        )
        val response = executeJson(
            requestBuilder("/v1/sessions/${sessionId.urlPathSegment()}/runs")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            RunResponse.serializer(),
        )
        activeRuns[sessionId] = response.runId
        return AgentRun(response.runId, sessionId, request.requestId, AgentRunStatus.RUNNING)
    }

    override fun streamEvents(
        sessionId: String,
        afterSequence: Long?,
    ): Flow<AgentStreamEvent> =
        flow {
            val runId =
                activeRuns[sessionId]
                    ?: throw providerException(
                        ProviderErrorCategory.PROTOCOL,
                        "missing_run",
                        "No active Codex run exists for this session.",
                    )
            val after = (afterSequence ?: 0L).coerceAtLeast(0L)
            val request =
                requestBuilder("/v1/sessions/${sessionId.urlPathSegment()}/runs/${runId.urlPathSegment()}/events?after=$after")
                    .get()
                    .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw response.toProviderException()
                    val source = response.body?.source()
                        ?: throw providerException(
                            ProviderErrorCategory.PROTOCOL,
                            "empty_stream",
                            "Codex companion returned an empty event stream.",
                        )
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line()?.trim().orEmpty()
                        if (line.isEmpty()) continue
                        val event = runCatching {
                            json.decodeFromString(CompanionEvent.serializer(), line)
                        }.getOrElse {
                            throw providerException(
                                ProviderErrorCategory.PROTOCOL,
                                "invalid_event",
                                "Codex companion returned an invalid event.",
                                it,
                            )
                        }
                        when (event.type) {
                            "text_delta" ->
                                emit(
                                    StreamEvent.TextDelta(
                                        requestId = event.requestId,
                                        text = event.text.orEmpty(),
                                        sequence = event.sequence,
                                    ),
                                )
                            "completed" -> {
                                emit(
                                    StreamEvent.Completed(
                                        requestId = event.requestId,
                                        finishReason = FinishReason.STOP,
                                        sequence = event.sequence,
                                    ),
                                )
                                activeRuns.remove(sessionId, runId)
                            }
                            "cancelled" -> {
                                emit(
                                    StreamEvent.Completed(
                                        requestId = event.requestId,
                                        finishReason = FinishReason.CANCELLED,
                                        sequence = event.sequence,
                                    ),
                                )
                                activeRuns.remove(sessionId, runId)
                            }
                            "failed" -> {
                                emit(
                                    StreamEvent.Failed(
                                        requestId = event.requestId,
                                        error =
                                            ProviderError(
                                                category = ProviderErrorCategory.SERVICE_UNAVAILABLE,
                                                code = "codex_turn_failed",
                                                message = event.error ?: "Codex turn failed.",
                                            ),
                                        sequence = event.sequence,
                                    ),
                                )
                                activeRuns.remove(sessionId, runId)
                            }
                        }
                    }
                }
            } catch (error: ProviderException) {
                throw error
            } catch (error: IOException) {
                throw providerException(
                    ProviderErrorCategory.NETWORK,
                    "companion_network",
                    "Unable to reach the Codex companion.",
                    error,
                )
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun cancel(
        sessionId: String,
        runId: String,
    ) {
        executeNoContent(
            requestBuilder("/v1/sessions/${sessionId.urlPathSegment()}/runs/${runId.urlPathSegment()}/cancel")
                .post(ByteArray(0).toRequestBody(null))
                .build(),
        )
    }

    override suspend fun listModels(profile: AgentProfile): List<ModelDescriptor> = emptyList()

    override suspend fun listTools(profile: AgentProfile): List<AgentToolDescriptor> = emptyList()

    override suspend fun listSkills(profile: AgentProfile): List<AgentSkillDescriptor> = emptyList()

    override suspend fun approveTool(
        sessionId: String,
        approval: AgentToolApproval,
    ) {
        throw providerException(
            ProviderErrorCategory.CAPABILITY_MISMATCH,
            "tool_approvals_not_available",
            "Codex companion tool approvals are not enabled in this connection phase.",
        )
    }

    override suspend fun getStatus(profile: AgentProfile): AgentBackendStatus =
        try {
            val response = executeJson(
                requestBuilder("/v1/status").get().build(),
                StatusResponse.serializer(),
            )
            when {
                response.protocolVersion != COMPANION_PROTOCOL_VERSION ->
                    AgentBackendStatus(AgentBackendState.DEGRADED, "Version du compagnon Codex incompatible.")
                response.state == "ready" && response.auth == "chatgpt" ->
                    AgentBackendStatus(AgentBackendState.AVAILABLE, "Codex connecté via ChatGPT.")
                else -> AgentBackendStatus(AgentBackendState.DEGRADED, "Compagnon Codex indisponible.")
            }
        } catch (_: ProviderException) {
            AgentBackendStatus(AgentBackendState.OFFLINE, "Compagnon Codex inaccessible.")
        }

    private suspend fun requestBuilder(path: String): Request.Builder {
        val base = connection.baseUrl.trimEnd('/')
        val builder =
            Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
                .header("User-Agent", "Jean-Calcul-Assistant/0.1")
        val authFailure = authenticator.authenticate(connection, builder)
        if (authFailure != null) {
            throw providerException(
                ProviderErrorCategory.AUTHENTICATION,
                authFailure.code,
                authFailure.userMessage,
            )
        }
        return builder
    }

    private suspend fun <T> executeJson(
        request: Request,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw response.toProviderException()
                    val body =
                        response.body?.string()
                            ?: throw providerException(
                                ProviderErrorCategory.PROTOCOL,
                                "empty_response",
                                "Codex companion returned an empty response.",
                            )
                    runCatching { json.decodeFromString(serializer, body) }.getOrElse {
                        throw providerException(
                            ProviderErrorCategory.PROTOCOL,
                            "invalid_response",
                            "Codex companion returned an invalid response.",
                            it,
                        )
                    }
                }
            } catch (error: ProviderException) {
                throw error
            } catch (error: IOException) {
                throw providerException(
                    ProviderErrorCategory.NETWORK,
                    "companion_network",
                    "Unable to reach the Codex companion.",
                    error,
                )
            }
        }

    private suspend fun executeNoContent(request: Request) =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw response.toProviderException()
                }
            } catch (error: ProviderException) {
                throw error
            } catch (error: IOException) {
                throw providerException(
                    ProviderErrorCategory.NETWORK,
                    "companion_network",
                    "Unable to reach the Codex companion.",
                    error,
                )
            }
        }

    private fun okhttp3.Response.toProviderException(): ProviderException {
        val category =
            when (code) {
                401, 403 -> ProviderErrorCategory.AUTHENTICATION
                404 -> ProviderErrorCategory.INVALID_REQUEST
                408 -> ProviderErrorCategory.TIMEOUT
                429 -> ProviderErrorCategory.RATE_LIMITED
                in 500..599 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
                else -> ProviderErrorCategory.PROTOCOL
            }
        return providerException(category, "companion_http_$code", "Codex companion returned HTTP $code.")
    }
}

private fun providerException(
    category: ProviderErrorCategory,
    code: String,
    message: String,
    cause: Throwable? = null,
): ProviderException =
    ProviderException(
        ProviderError(
            category = category,
            code = code,
            message = message,
        ),
        cause,
    )

private fun String.urlPathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

@Serializable
private data class SessionResponse(
    val sessionId: String,
    val resumable: Boolean = true,
)

@Serializable
private data class RunRequest(
    val requestId: String,
    val text: String,
)

@Serializable
private data class RunResponse(
    val runId: String,
    val status: String,
)

@Serializable
private data class StatusResponse(
    val protocolVersion: String,
    val state: String,
    val auth: String,
)

@Serializable
private data class CompanionEvent(
    val sequence: Long,
    val type: String,
    val requestId: String,
    val text: String? = null,
    val error: String? = null,
)

@Module
@InstallIn(SingletonComponent::class)
abstract class CodexCompanionNetworkModule {
    @Binds
    abstract fun bindAgentBackendFactory(implementation: CodexCompanionAgentBackendFactory): AgentBackendFactory
}
