package fr.loevan.jeancalcul.network

import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.ModelStreamEvent
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.ProviderUsage
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.domain.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class OpenAiStreamParser(
    private val requestId: String,
    private val json: Json,
    private val nextSequence: () -> Long,
) {
    private val toolCalls = linkedMapOf<Int, PendingToolCall>()
    private var finishReason: FinishReason? = null
    private var terminal = false

    fun consume(line: String): List<ModelStreamEvent> =
        try {
            val data = line.takeIf { !terminal && it.startsWith("data:") }?.removePrefix("data:")?.trimStart()
            when {
                data.isNullOrBlank() -> emptyList()
                data == "[DONE]" -> finish()
                else -> consumePayload(parsePayload(data))
            }
        } catch (error: ProviderException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw protocolException("invalid_stream_payload", error)
        } catch (error: IllegalStateException) {
            throw protocolException("invalid_stream_payload", error)
        }

    @Suppress("ThrowsCount")
    private fun parsePayload(data: String): JsonObject {
        val payload =
            try {
                json.parseToJsonElement(data).jsonObject
            } catch (error: IllegalArgumentException) {
                throw protocolException("invalid_stream_json", error)
            } catch (error: IllegalStateException) {
                throw protocolException("invalid_stream_json", error)
            }
        if (payload["error"] != null && payload["error"] != JsonNull) {
            throw protocolException("provider_stream_error")
        }
        return payload
    }

    private fun consumePayload(payload: JsonObject): List<ModelStreamEvent> =
        buildList {
            payload["choices"]?.jsonArray.orEmpty().forEach { addAll(consumeChoice(it.jsonObject)) }
            (payload["usage"] as? JsonObject)?.let { add(it.toUsageEvent()) }
        }

    private fun consumeChoice(choice: JsonObject): List<ModelStreamEvent> =
        buildList {
            val delta = choice["delta"] as? JsonObject
            delta?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let { text ->
                add(StreamEvent.TextDelta(requestId, text, nextSequence()))
            }
            delta?.get("tool_calls")?.jsonArray.orEmpty().forEach { toolCallElement ->
                consumeToolCall(toolCallElement.jsonObject)?.let(::add)
            }
            choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it.toFinishReason() }
        }

    private fun consumeToolCall(toolCall: JsonObject): StreamEvent.ToolCallArgumentsDelta? {
        val index = toolCall["index"]?.jsonPrimitive?.intOrNull ?: toolCalls.size
        val pending = toolCalls.getOrPut(index) { PendingToolCall() }
        toolCall["id"]?.jsonPrimitive?.contentOrNull?.let { pending.callId = it }
        val function = toolCall["function"] as? JsonObject
        function?.get("name")?.jsonPrimitive?.contentOrNull?.let { pending.toolName = it }
        val fragment =
            function?.get("arguments")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)
                ?: return null
        pending.arguments.append(fragment)
        return StreamEvent.ToolCallArgumentsDelta(
            requestId = requestId,
            callId = pending.callId.ifBlank { "$requestId-tool-$index" },
            toolName = pending.toolName.ifBlank { "pending_tool" },
            argumentsDelta = fragment,
            sequence = nextSequence(),
        )
    }

    private fun JsonObject.toUsageEvent() =
        StreamEvent.UsageUpdated(
            requestId = requestId,
            usage =
                ProviderUsage(
                    inputTokens = this["prompt_tokens"]?.jsonPrimitive?.intOrNull,
                    outputTokens = this["completion_tokens"]?.jsonPrimitive?.intOrNull,
                ),
            sequence = nextSequence(),
        )

    fun finish(): List<ModelStreamEvent> {
        val events =
            if (terminal) {
                emptyList()
            } else {
                terminal = true
                buildList {
                    toolCalls.toSortedMap().values.forEach { pending ->
                        add(
                            StreamEvent.ToolCallReady(
                                requestId = requestId,
                                call = pending.toToolCall(json),
                                sequence = nextSequence(),
                            ),
                        )
                    }
                    add(
                        StreamEvent.Completed(
                            requestId = requestId,
                            finishReason = terminalReason(),
                            sequence = nextSequence(),
                        ),
                    )
                }
            }
        return events
    }

    private fun terminalReason(): FinishReason =
        finishReason ?: if (toolCalls.isEmpty()) FinishReason.UNKNOWN else FinishReason.TOOL_CALL
}

private data class PendingToolCall(
    var callId: String = "",
    var toolName: String = "",
    val arguments: StringBuilder = StringBuilder(),
)

private fun PendingToolCall.toToolCall(json: Json): ToolCall {
    val id = callId.takeIf(String::isNotBlank) ?: throw protocolException("missing_tool_call_id")
    val name = toolName.takeIf(String::isNotBlank) ?: throw protocolException("missing_tool_name")
    return ToolCall(id, name, arguments.toJsonObject(json))
}

private fun StringBuilder.toJsonObject(json: Json): JsonObject =
    try {
        json.parseToJsonElement(toString()).jsonObject
    } catch (error: IllegalArgumentException) {
        throw protocolException("invalid_tool_arguments", error)
    } catch (error: IllegalStateException) {
        throw protocolException("invalid_tool_arguments", error)
    }

private fun String.toFinishReason(): FinishReason =
    when (this) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.LENGTH
        "tool_calls", "function_call" -> FinishReason.TOOL_CALL
        "cancelled" -> FinishReason.CANCELLED
        "error" -> FinishReason.ERROR
        else -> FinishReason.UNKNOWN
    }

private fun List<JsonElement>?.orEmpty(): List<JsonElement> = this ?: emptyList()
