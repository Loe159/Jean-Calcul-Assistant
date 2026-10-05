@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MaxLineLength",
    "NestedBlockDepth",
    "ReturnCount",
    "ThrowsCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package fr.loevan.jeancalcul.network.codex

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
import fr.loevan.jeancalcul.domain.AgentToolResultSink
import fr.loevan.jeancalcul.domain.ContentModality
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.domain.ToolCall
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.domain.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val RESPONSE_JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

internal class ChatGptPlanAgentBackend(
    private val connection: ProviderConnection,
    private val profile: AgentProfile,
    private val client: OkHttpClient,
    private val tokenProvider: ChatGptPlanTokenProvider,
    private val apiBaseUrl: String = CHATGPT_PLAN_API_BASE_URL,
) : AgentBackend, AgentToolResultSink {
    override val id: String = CHATGPT_PLAN_BACKEND_ID

    private val json = Json { ignoreUnknownKeys = true }
    private val runs = ConcurrentHashMap<String, RunState>()
    private val activeCalls = ConcurrentHashMap<String, Call>()

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

    override suspend fun createSession(profile: AgentProfile): AgentSession =
        AgentSession(UUID.randomUUID().toString(), profile.id, resumable = true)

    override suspend fun resumeSession(
        profile: AgentProfile,
        sessionId: String,
    ): AgentSession = AgentSession(sessionId, profile.id, resumable = true)

    override suspend fun sendMessage(
        sessionId: String,
        request: AgentRequest,
    ): AgentRun {
        val runId = UUID.randomUUID().toString()
        val wireToolNames =
            request.availableTools.mapIndexed { index, definition ->
                responseToolWireName(definition.name, index) to definition.name
            }.toMap()
        runs[runId] =
            RunState(
                sessionId = sessionId,
                request = request,
                wireToolNames = wireToolNames,
            )
        return AgentRun(runId, sessionId, request.requestId, AgentRunStatus.RUNNING)
    }

    override fun streamEvents(
        sessionId: String,
        afterSequence: Long?,
    ): Flow<AgentStreamEvent> =
        flow {
            val state =
                runs.values.firstOrNull { it.sessionId == sessionId && !it.finished }
                    ?: throw providerException(
                        ProviderErrorCategory.PROTOCOL,
                        "missing_run",
                        "Aucun tour ChatGPT actif pour cette session.",
                    )
            val sequence = AtomicLong((afterSequence ?: 0L).coerceAtLeast(0L))
            val historyItems = mutableListOf<JsonElement>()
            try {
                repeat(MAX_TOOL_ROUNDS) { round ->
                    if (state.cancelled) {
                        emit(
                            StreamEvent.Completed(
                                state.request.requestId,
                                FinishReason.CANCELLED,
                                sequence.incrementAndGet(),
                            ),
                        )
                        state.finished = true
                        return@flow
                    }
                    val roundResult =
                        executeResponseRound(
                            state = state,
                            additionalInput = historyItems,
                            sequence = sequence,
                            emitEvent = { emit(it) },
                        )
                    if (roundResult.failed != null) {
                        emit(
                            StreamEvent.Failed(
                                state.request.requestId,
                                roundResult.failed,
                                sequence.incrementAndGet(),
                            ),
                        )
                        state.finished = true
                        return@flow
                    }
                    if (roundResult.functionCalls.isEmpty()) {
                        emit(
                            StreamEvent.Completed(
                                state.request.requestId,
                                FinishReason.STOP,
                                sequence.incrementAndGet(),
                            ),
                        )
                        state.finished = true
                        return@flow
                    }

                    historyItems += roundResult.outputItems
                    for (functionCall in roundResult.functionCalls) {
                        val result =
                            state.toolResults.remove(functionCall.callId)
                                ?: run {
                                    emit(
                                        StreamEvent.Failed(
                                            state.request.requestId,
                                            ProviderError(
                                                ProviderErrorCategory.PROTOCOL,
                                                "tool_result_missing",
                                                "Le résultat de l'outil Android n'a pas été renvoyé.",
                                            ),
                                            sequence.incrementAndGet(),
                                        ),
                                    )
                                    state.finished = true
                                    return@flow
                                }
                        historyItems += functionCallOutput(functionCall, result)
                    }
                    if (round == MAX_TOOL_ROUNDS - 1) {
                        emit(
                            StreamEvent.Failed(
                                state.request.requestId,
                                ProviderError(
                                    ProviderErrorCategory.PROTOCOL,
                                    "tool_round_limit",
                                    "Le modèle a demandé trop d'outils successifs.",
                                ),
                                sequence.incrementAndGet(),
                            ),
                        )
                        state.finished = true
                        return@flow
                    }
                }
            } finally {
                activeCalls.remove(state.request.requestId)?.cancel()
                if (state.finished || state.cancelled) runs.entries.removeIf { it.value === state }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun executeResponseRound(
        state: RunState,
        additionalInput: List<JsonElement>,
        sequence: AtomicLong,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ): RoundResult {
        val accessToken =
            try {
                tokenProvider.accessToken(
                    connection.secretId
                        ?: throw providerException(
                            ProviderErrorCategory.AUTHENTICATION,
                            "oauth_missing",
                            "Connectez ChatGPT dans les réglages.",
                        ),
                )
            } catch (error: ProviderException) {
                throw error
            } catch (error: Exception) {
                throw providerException(
                    ProviderErrorCategory.AUTHENTICATION,
                    "oauth_failed",
                    error.message ?: "Impossible d'utiliser la connexion ChatGPT.",
                    error,
                )
            }

        val body =
            buildJsonObject {
                put("model", profile.agentId)
                put("store", false)
                put("stream", true)
                put("instructions", AGENT_INSTRUCTIONS)
                put(
                    "input",
                    buildJsonArray {
                        state.request.messages.forEach { message ->
                            message.toResponseInput()?.let(::add)
                        }
                        additionalInput.forEach(::add)
                    },
                )
                if (state.request.availableTools.isNotEmpty()) {
                    put(
                        "tools",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "namespace")
                                    put("name", ANDROID_TOOL_NAMESPACE)
                                    put(
                                        "description",
                                        "Actions locales disponibles sur le téléphone Android de l'utilisateur.",
                                    )
                                    put(
                                        "tools",
                                        buildJsonArray {
                                            state.request.availableTools.forEachIndexed { index, definition ->
                                                add(
                                                    definition.toResponseTool(
                                                        responseToolWireName(definition.name, index),
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
        val request =
            Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/responses")
                .post(body.toString().toRequestBody(RESPONSE_JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Jean-Calcul-Assistant/0.1")
                .build()
        val call = client.newCall(request)
        activeCalls[state.request.requestId] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw response.toProviderException()
                val source =
                    response.body?.source()
                        ?: throw providerException(
                            ProviderErrorCategory.PROTOCOL,
                            "empty_response",
                            "OpenAI a renvoyé une réponse vide.",
                        )
                val functionCalls = LinkedHashMap<String, PendingFunctionCall>()
                var completedOutput = JsonArray(emptyList())
                var failed: ProviderError? = null
                while (!source.exhausted()) {
                    if (state.cancelled) {
                        call.cancel()
                        break
                    }
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val event =
                        runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
                    if (event == null) {
                        failed =
                            ProviderError(
                                ProviderErrorCategory.PROTOCOL,
                                "invalid_sse",
                                "Flux OpenAI invalide.",
                            )
                        break
                    }
                    val type = event["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    when (type) {
                        "response.output_text.delta" -> {
                            val delta = event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (delta.isNotEmpty()) {
                                emitEvent(
                                    StreamEvent.TextDelta(
                                        state.request.requestId,
                                        delta,
                                        event.sequence(sequence),
                                    ),
                                )
                            }
                        }

                        "response.output_item.added" -> {
                            val item = event["item"] as? JsonObject ?: continue
                            if (item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
                                val itemId = item["id"]?.jsonPrimitive?.contentOrNull ?: continue
                                val callId = item["call_id"]?.jsonPrimitive?.contentOrNull ?: continue
                                val wireName = item["name"]?.jsonPrimitive?.contentOrNull ?: continue
                                val toolName = state.wireToolNames[wireName] ?: wireName
                                functionCalls[itemId] =
                                    PendingFunctionCall(
                                        itemId = itemId,
                                        callId = callId,
                                        wireName = wireName,
                                        toolName = toolName,
                                        namespace = item["namespace"]?.jsonPrimitive?.contentOrNull,
                                        arguments = item["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                    )
                            }
                        }

                        "response.function_call_arguments.delta" -> {
                            val itemId = event["item_id"]?.jsonPrimitive?.contentOrNull ?: continue
                            val pending = functionCalls[itemId] ?: continue
                            pending.arguments += event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        }

                        "response.function_call_arguments.done" -> {
                            val itemId = event["item_id"]?.jsonPrimitive?.contentOrNull ?: continue
                            val pending = functionCalls[itemId] ?: continue
                            event["arguments"]?.jsonPrimitive?.contentOrNull?.let { pending.arguments = it }
                            emitToolCallIfReady(state, pending, sequence, emitEvent)
                        }

                        "response.completed" -> {
                            val responseObject = event["response"] as? JsonObject
                            completedOutput = responseObject?.get("output") as? JsonArray ?: JsonArray(emptyList())
                            completedOutput.functionCalls().forEach { completed ->
                                val pending =
                                    functionCalls.getOrPut(completed.itemId) { completed }
                                pending.arguments = completed.arguments
                                emitToolCallIfReady(state, pending, sequence, emitEvent)
                            }
                        }

                        "response.failed" -> {
                            failed = event.responseFailure()
                        }

                        "error" -> {
                            failed = event.errorFailure()
                        }
                    }
                }
                return RoundResult(
                    outputItems = completedOutput.toList(),
                    functionCalls =
                        completedOutput.functionCalls().ifEmpty {
                            functionCalls.values.filter { it.emitted }
                        },
                    failed = failed,
                )
            }
        } catch (error: ProviderException) {
            throw error
        } catch (error: IOException) {
            if (state.cancelled) {
                return RoundResult(emptyList(), emptyList(), null)
            }
            throw providerException(
                ProviderErrorCategory.NETWORK,
                "openai_network",
                "Impossible de joindre OpenAI.",
                error,
            )
        } finally {
            activeCalls.remove(state.request.requestId, call)
        }
    }

    private suspend fun emitToolCallIfReady(
        state: RunState,
        pending: PendingFunctionCall,
        sequence: AtomicLong,
        emitEvent: suspend (AgentStreamEvent) -> Unit,
    ) {
        if (pending.emitted) return
        val arguments =
            runCatching { json.parseToJsonElement(pending.arguments).jsonObject }
                .getOrElse {
                    throw providerException(
                        ProviderErrorCategory.PROTOCOL,
                        "invalid_tool_arguments",
                        "OpenAI a renvoyé des arguments d'outil invalides.",
                        it,
                    )
                }
        pending.emitted = true
        emitEvent(
            StreamEvent.ToolCallReady(
                state.request.requestId,
                ToolCall(pending.callId, pending.toolName, arguments),
                sequence.incrementAndGet(),
            ),
        )
    }

    override suspend fun submitToolResult(
        sessionId: String,
        runId: String,
        callId: String,
        result: ToolResult,
    ) {
        val state =
            runs[runId]
                ?: throw providerException(
                    ProviderErrorCategory.PROTOCOL,
                    "missing_run",
                    "Le tour ChatGPT n'existe plus.",
                )
        require(state.sessionId == sessionId) { "Agent session mismatch." }
        state.toolResults[callId] = result
    }

    override suspend fun cancel(
        sessionId: String,
        runId: String,
    ) {
        val state = runs[runId] ?: return
        if (state.sessionId != sessionId) return
        state.cancelled = true
        activeCalls.remove(state.request.requestId)?.cancel()
    }

    override suspend fun listModels(profile: AgentProfile): List<ModelDescriptor> {
        val token =
            tokenProvider.accessToken(
                connection.secretId
                    ?: throw providerException(
                        ProviderErrorCategory.AUTHENTICATION,
                        "oauth_missing",
                        "Connectez ChatGPT dans les réglages.",
                    ),
            )
        val request =
            Request.Builder()
                .url("${apiBaseUrl.trimEnd('/')}/models")
                .get()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toProviderException()
                val root =
                    runCatching { json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject }
                        .getOrElse {
                            throw providerException(
                                ProviderErrorCategory.PROTOCOL,
                                "invalid_models",
                                "OpenAI a renvoyé une liste de modèles invalide.",
                                it,
                            )
                        }
                val models =
                    (root["models"] as? JsonArray)?.mapNotNull { item ->
                        val objectItem = item as? JsonObject ?: return@mapNotNull null
                        if (objectItem["visibility"]?.jsonPrimitive?.contentOrNull?.let { it != "list" } == true) {
                            return@mapNotNull null
                        }
                        val slug = objectItem["slug"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val display = objectItem["display_name"]?.jsonPrimitive?.contentOrNull ?: slug
                        ModelDescriptor(slug, display, DIRECT_MODEL_CAPABILITIES)
                    } ?: (root["data"] as? JsonArray).orEmpty().mapNotNull { item ->
                        val objectItem = item as? JsonObject ?: return@mapNotNull null
                        val modelId = objectItem["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        ModelDescriptor(modelId, modelId, DIRECT_MODEL_CAPABILITIES)
                    }
                return models
            }
        } catch (error: IOException) {
            throw providerException(
                ProviderErrorCategory.NETWORK,
                "openai_network",
                "Impossible de joindre OpenAI.",
                error,
            )
        }
    }

    override suspend fun listTools(profile: AgentProfile): List<AgentToolDescriptor> = emptyList()

    override suspend fun listSkills(profile: AgentProfile): List<AgentSkillDescriptor> = emptyList()

    override suspend fun approveTool(
        sessionId: String,
        approval: AgentToolApproval,
    ) {
        throw providerException(
            ProviderErrorCategory.CAPABILITY_MISMATCH,
            "tool_approvals_not_available",
            "Les approbations restent gérées localement par Jean Calcul.",
        )
    }

    override suspend fun getStatus(profile: AgentProfile): AgentBackendStatus {
        val secretId =
            connection.secretId
                ?: return AgentBackendStatus(AgentBackendState.OFFLINE, "ChatGPT n'est pas connecté.")
        return try {
            tokenProvider.accessToken(secretId)
            AgentBackendStatus(AgentBackendState.AVAILABLE, "ChatGPT connecté directement.")
        } catch (error: Exception) {
            AgentBackendStatus(AgentBackendState.DEGRADED, error.message ?: "Connexion ChatGPT indisponible.")
        }
    }

    private fun ToolDefinition.toResponseTool(wireName: String): JsonObject =
        buildJsonObject {
            put("type", "function")
            put("name", wireName)
            put("description", description)
            put("parameters", inputSchema)
            put("strict", true)
        }

    private fun responseToolWireName(
        toolName: String,
        index: Int,
    ): String {
        val normalized =
            toolName
                .replace(Regex("[^A-Za-z0-9_-]"), "_")
                .trim('_')
                .take(MAX_TOOL_NAME_LENGTH - TOOL_NAME_SUFFIX_RESERVE)
                .ifBlank { "android_tool" }
        return "${normalized}_${index}"
    }

    private fun fr.loevan.jeancalcul.domain.ChatMessage.toResponseInput(): JsonObject? {
        val text = content.filterIsInstance<MessageContent.Text>().joinToString("\n") { it.text }.trim()
        if (text.isEmpty()) return null
        val role =
            when (role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> return null
                MessageRole.TOOL -> return null
            }
        return buildJsonObject {
            put("role", role)
            put("content", text)
        }
    }

    private fun JsonObject.sequence(fallback: AtomicLong): Long {
        val provided = this["sequence_number"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        if (provided != null) {
            fallback.updateAndGet { current -> maxOf(current, provided) }
            return provided
        }
        return fallback.incrementAndGet()
    }

    private fun JsonArray.functionCalls(): List<PendingFunctionCall> =
        mapNotNull { item ->
            val objectItem = item as? JsonObject ?: return@mapNotNull null
            if (objectItem["type"]?.jsonPrimitive?.contentOrNull != "function_call") return@mapNotNull null
            val wireName = objectItem["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            PendingFunctionCall(
                itemId = objectItem["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                callId = objectItem["call_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                wireName = wireName,
                toolName = wireName,
                namespace = objectItem["namespace"]?.jsonPrimitive?.contentOrNull,
                arguments = objectItem["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }

    private fun functionCallOutput(
        call: PendingFunctionCall,
        result: ToolResult,
    ): JsonObject =
        buildJsonObject {
            put("type", "function_call_output")
            put("call_id", call.callId)
            put("name", call.wireName)
            call.namespace?.let { put("namespace", it) }
            put(
                "output",
                result.output?.toString()
                    ?: buildJsonObject {
                        put(
                            "error",
                            buildJsonObject {
                                put("code", result.error?.code ?: "TOOL_FAILED")
                                put("message", result.error?.message ?: "L'outil Android a échoué.")
                            },
                        )
                    }.toString(),
            )
        }

    private fun JsonObject.responseFailure(): ProviderError {
        val response = this["response"] as? JsonObject
        val error = response?.get("error") as? JsonObject
        return ProviderError(
            ProviderErrorCategory.SERVICE_UNAVAILABLE,
            error?.get("code")?.jsonPrimitive?.contentOrNull ?: "response_failed",
            error?.get("message")?.jsonPrimitive?.contentOrNull ?: "La réponse OpenAI a échoué.",
        )
    }

    private fun JsonObject.errorFailure(): ProviderError {
        val error = this["error"] as? JsonObject
        return ProviderError(
            ProviderErrorCategory.SERVICE_UNAVAILABLE,
            error?.get("code")?.jsonPrimitive?.contentOrNull ?: "openai_error",
            error?.get("message")?.jsonPrimitive?.contentOrNull ?: "OpenAI a signalé une erreur.",
        )
    }

    private fun okhttp3.Response.toProviderException(): ProviderException {
        val httpCode = code
        val rawBody = body?.string().orEmpty()
        val apiError =
            runCatching {
                val root = json.parseToJsonElement(rawBody).jsonObject
                root["error"] as? JsonObject
            }.getOrNull()
        val apiCode = apiError?.get("code")?.jsonPrimitive?.contentOrNull
        val apiMessage =
            apiError?.get("message")?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.take(MAX_ERROR_MESSAGE_LENGTH)
        val category =
            when (httpCode) {
                400 -> ProviderErrorCategory.INVALID_REQUEST
                401, 403 -> ProviderErrorCategory.AUTHENTICATION
                404 -> ProviderErrorCategory.MODEL_NOT_FOUND
                408 -> ProviderErrorCategory.TIMEOUT
                429 -> ProviderErrorCategory.RATE_LIMITED
                in 500..599 -> ProviderErrorCategory.SERVICE_UNAVAILABLE
                else -> ProviderErrorCategory.PROTOCOL
            }
        val message =
            apiMessage?.takeIf(String::isNotBlank)
                ?: when (category) {
                    ProviderErrorCategory.AUTHENTICATION -> "La connexion ChatGPT doit être renouvelée."
                    ProviderErrorCategory.RATE_LIMITED -> "Le quota ChatGPT limite temporairement cette requête."
                    ProviderErrorCategory.MODEL_NOT_FOUND -> "Le modèle ChatGPT configuré n'est pas disponible."
                    ProviderErrorCategory.SERVICE_UNAVAILABLE -> "OpenAI est temporairement indisponible."
                    else -> "OpenAI a renvoyé HTTP $httpCode."
                }
        return providerException(
            category,
            apiCode?.takeIf(String::isNotBlank) ?: "openai_http_$httpCode",
            message,
        )
    }

    private data class RunState(
        val sessionId: String,
        val request: AgentRequest,
        val wireToolNames: Map<String, String>,
        val toolResults: ConcurrentHashMap<String, ToolResult> = ConcurrentHashMap(),
        @Volatile var cancelled: Boolean = false,
        @Volatile var finished: Boolean = false,
    )

    private data class PendingFunctionCall(
        val itemId: String,
        val callId: String,
        val wireName: String,
        var toolName: String,
        val namespace: String?,
        var arguments: String,
        var emitted: Boolean = false,
    )

    private data class RoundResult(
        val outputItems: List<JsonElement>,
        val functionCalls: List<PendingFunctionCall>,
        val failed: ProviderError?,
    )

    private companion object {
        const val MAX_TOOL_ROUNDS = 8
        const val MAX_TOOL_NAME_LENGTH = 128
        const val TOOL_NAME_SUFFIX_RESERVE = 12
        const val MAX_ERROR_MESSAGE_LENGTH = 500
        const val ANDROID_TOOL_NAMESPACE = "android"
        const val AGENT_INSTRUCTIONS =
            "Vous êtes Jean Calcul, l'assistant Android de l'utilisateur. " +
                "Répondez dans la langue de l'utilisateur. Pour agir sur le téléphone, utilisez uniquement " +
                "les outils déclarés. N'inventez jamais le résultat d'un outil et n'affirmez pas qu'une action " +
                "a réussi avant d'avoir reçu son résultat."

        val DIRECT_MODEL_CAPABILITIES =
            ModelCapabilities(
                inputModalities = setOf(ContentModality.TEXT),
                outputModalities = setOf(ContentModality.TEXT),
                supportsStreaming = true,
                supportsCancellation = true,
                supportsToolCalling = true,
                supportsParallelToolCalls = true,
            )
    }
}

private fun providerException(
    category: ProviderErrorCategory,
    code: String,
    message: String,
    cause: Throwable? = null,
): ProviderException = ProviderException(ProviderError(category, code, message), cause)
