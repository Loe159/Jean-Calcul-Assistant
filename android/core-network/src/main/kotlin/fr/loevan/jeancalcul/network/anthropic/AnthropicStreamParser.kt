@file:Suppress("ReturnCount", "ThrowsCount")

package fr.loevan.jeancalcul.network.anthropic

import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.ModelStreamEvent
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.ProviderUsage
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.domain.ToolCall
import fr.loevan.jeancalcul.network.protocolException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class AnthropicStreamParser(
    private val requestId: String,
    private val json: Json,
    private val nextSequence: () -> Long,
) {
    private val tools = mutableMapOf<Int, PendingAnthropicTool>()
    private var finishReason = FinishReason.UNKNOWN
    private var terminal = false

    fun consume(line: String): List<ModelStreamEvent> {
        val data = line.takeIf { !terminal && it.startsWith("data:") }?.removePrefix("data:")?.trimStart()
        if (data.isNullOrBlank()) return emptyList()
        val payload =
            try {
                json.parseToJsonElement(data).jsonObject
            } catch (error: IllegalArgumentException) {
                throw protocolException("anthropic_invalid_stream_json", error)
            } catch (error: IllegalStateException) {
                throw protocolException("anthropic_invalid_stream_json", error)
            }
        return when (payload["type"]?.jsonPrimitive?.contentOrNull) {
            "message_start" -> payload.startUsage()
            "content_block_start" -> consumeBlockStart(payload)
            "content_block_delta" -> consumeBlockDelta(payload)
            "content_block_stop" -> consumeBlockStop(payload)
            "message_delta" -> consumeMessageDelta(payload)
            "message_stop" -> complete()
            "error" -> throw payload.toStreamError()
            "ping", null -> emptyList()
            else -> emptyList()
        }
    }

    private fun JsonObject.startUsage(): List<ModelStreamEvent> {
        val usage = (this["message"] as? JsonObject)?.get("usage") as? JsonObject ?: return emptyList()
        return listOf(
            StreamEvent.UsageUpdated(
                requestId,
                ProviderUsage(inputTokens = usage["input_tokens"]?.jsonPrimitive?.intOrNull),
                nextSequence(),
            ),
        )
    }

    private fun consumeBlockStart(payload: JsonObject): List<ModelStreamEvent> {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return emptyList()
        val block = payload["content_block"] as? JsonObject ?: return emptyList()
        if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
            val initial = (block["input"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.toString().orEmpty()
            tools[index] =
                PendingAnthropicTool(
                    id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    initialArguments = initial,
                )
        }
        return emptyList()
    }

    private fun consumeBlockDelta(payload: JsonObject): List<ModelStreamEvent> {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return emptyList()
        val delta = payload["delta"] as? JsonObject ?: return emptyList()
        return when (delta["type"]?.jsonPrimitive?.contentOrNull) {
            "text_delta" ->
                delta["text"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                    listOf(StreamEvent.TextDelta(requestId, it, nextSequence()))
                }.orEmpty()

            "input_json_delta" -> {
                val fragment = delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val tool = tools[index] ?: return emptyList()
                tool.arguments.append(fragment)
                if (fragment.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        StreamEvent.ToolCallArgumentsDelta(
                            requestId,
                            tool.id,
                            tool.name,
                            fragment,
                            nextSequence(),
                        ),
                    )
                }
            }

            else -> emptyList()
        }
    }

    private fun consumeBlockStop(payload: JsonObject): List<ModelStreamEvent> {
        val index = payload["index"]?.jsonPrimitive?.intOrNull ?: return emptyList()
        val pending = tools.remove(index) ?: return emptyList()
        val rawArguments = pending.arguments.toString().ifBlank { pending.initialArguments.ifBlank { "{}" } }
        val arguments =
            try {
                json.parseToJsonElement(rawArguments).jsonObject
            } catch (error: IllegalArgumentException) {
                throw protocolException("anthropic_invalid_tool_arguments", error)
            } catch (error: IllegalStateException) {
                throw protocolException("anthropic_invalid_tool_arguments", error)
            }
        if (pending.id.isBlank() || pending.name.isBlank()) throw protocolException("anthropic_incomplete_tool_call")
        return listOf(
            StreamEvent.ToolCallReady(
                requestId,
                ToolCall(pending.id, pending.name, arguments),
                nextSequence(),
            ),
        )
    }

    private fun consumeMessageDelta(payload: JsonObject): List<ModelStreamEvent> {
        val stopReason = (payload["delta"] as? JsonObject)?.get("stop_reason")?.jsonPrimitive?.contentOrNull
        finishReason = stopReason.toFinishReason()
        val output = (payload["usage"] as? JsonObject)?.get("output_tokens")?.jsonPrimitive?.intOrNull
        return output?.let {
            listOf(StreamEvent.UsageUpdated(requestId, ProviderUsage(outputTokens = it), nextSequence()))
        }.orEmpty()
    }

    fun complete(): List<ModelStreamEvent> =
        if (terminal) {
            emptyList()
        } else {
            terminal = true
            listOf(StreamEvent.Completed(requestId, finishReason, nextSequence()))
        }

    fun finish() {
        if (!terminal) throw protocolException("anthropic_incomplete_stream")
    }

    private fun JsonObject.toStreamError(): ProviderException {
        val type = (this["error"] as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
        val category =
            when (type) {
                "authentication_error" -> ProviderErrorCategory.AUTHENTICATION
                "permission_error" -> ProviderErrorCategory.PERMISSION_DENIED
                "rate_limit_error" -> ProviderErrorCategory.RATE_LIMITED
                "overloaded_error", "api_error", "timeout_error" -> ProviderErrorCategory.SERVICE_UNAVAILABLE
                else -> ProviderErrorCategory.PROTOCOL
            }
        return ProviderException(
            ProviderError(category, "anthropic_stream_${type ?: "error"}", category.safeMessage()),
        )
    }
}

private data class PendingAnthropicTool(
    val id: String,
    val name: String,
    val initialArguments: String,
    val arguments: StringBuilder = StringBuilder(),
)

private fun String?.toFinishReason(): FinishReason =
    when (this) {
        "end_turn", "stop_sequence" -> FinishReason.STOP
        "max_tokens" -> FinishReason.LENGTH
        "tool_use" -> FinishReason.TOOL_CALL
        "refusal" -> FinishReason.ERROR
        else -> FinishReason.UNKNOWN
    }

private fun ProviderErrorCategory.safeMessage(): String =
    when (this) {
        ProviderErrorCategory.AUTHENTICATION -> "L'authentification Anthropic a echoue."
        ProviderErrorCategory.PERMISSION_DENIED -> "Anthropic a refuse cette requete."
        ProviderErrorCategory.RATE_LIMITED -> "Le quota Anthropic est temporairement atteint."
        ProviderErrorCategory.SERVICE_UNAVAILABLE -> "Anthropic est temporairement surcharge ou indisponible."
        else -> "Anthropic a interrompu le flux avec une erreur incompatible."
    }
