@file:Suppress("ThrowsCount")

package fr.loevan.jeancalcul.network.ollama

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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class OllamaStreamParser(
    private val requestId: String,
    private val json: Json,
    private val nextSequence: () -> Long,
) {
    private var toolIndex = 0
    private var terminal = false

    fun consume(line: String): List<ModelStreamEvent> {
        if (terminal || line.isBlank()) return emptyList()
        val payload =
            try {
                json.parseToJsonElement(line).jsonObject
            } catch (error: IllegalArgumentException) {
                throw protocolException("ollama_invalid_stream_json", error)
            } catch (error: IllegalStateException) {
                throw protocolException("ollama_invalid_stream_json", error)
            }
        payload["error"]?.jsonPrimitive?.contentOrNull?.let {
            throw ProviderException(
                ProviderError(
                    ProviderErrorCategory.SERVICE_UNAVAILABLE,
                    "ollama_stream_error",
                    "Ollama a interrompu la generation.",
                ),
            )
        }
        return buildList {
            val message = payload["message"] as? JsonObject
            message?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                add(StreamEvent.TextDelta(requestId, it, nextSequence()))
            }
            message?.get("tool_calls")?.jsonArray.orEmpty().forEach { tool ->
                addAll(tool.jsonObject.toToolEvents())
            }
            if (payload["done"]?.jsonPrimitive?.contentOrNull == "true") {
                val input = payload["prompt_eval_count"]?.jsonPrimitive?.intOrNull
                val output = payload["eval_count"]?.jsonPrimitive?.intOrNull
                if (input != null || output != null) {
                    add(StreamEvent.UsageUpdated(requestId, ProviderUsage(input, output), nextSequence()))
                }
                terminal = true
                add(
                    StreamEvent.Completed(
                        requestId,
                        payload["done_reason"]?.jsonPrimitive?.contentOrNull.toFinishReason(),
                        nextSequence(),
                    ),
                )
            }
        }
    }

    fun finish() {
        if (!terminal) throw protocolException("ollama_incomplete_stream")
    }

    private fun JsonObject.toToolEvents(): List<ModelStreamEvent> {
        val function = this["function"] as? JsonObject ?: throw protocolException("ollama_invalid_tool_call")
        val name = function["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val rawArguments =
            when (val arguments = function["arguments"]) {
                is JsonObject -> arguments.toString()
                else -> arguments?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        val parsed =
            try {
                json.parseToJsonElement(rawArguments.ifBlank { "{}" }).jsonObject
            } catch (error: IllegalArgumentException) {
                throw protocolException("ollama_invalid_tool_arguments", error)
            } catch (error: IllegalStateException) {
                throw protocolException("ollama_invalid_tool_arguments", error)
            }
        if (name.isBlank()) throw protocolException("ollama_missing_tool_name")
        val callId = this["id"]?.jsonPrimitive?.contentOrNull ?: "$requestId-tool-${toolIndex++}"
        return listOf(
            StreamEvent.ToolCallArgumentsDelta(requestId, callId, name, rawArguments.ifBlank { "{}" }, nextSequence()),
            StreamEvent.ToolCallReady(requestId, ToolCall(callId, name, parsed), nextSequence()),
        )
    }
}

private fun String?.toFinishReason(): FinishReason =
    when (this) {
        "stop" -> FinishReason.STOP
        "length" -> FinishReason.LENGTH
        else -> FinishReason.UNKNOWN
    }

private fun List<kotlinx.serialization.json.JsonElement>?.orEmpty() = this ?: emptyList()
