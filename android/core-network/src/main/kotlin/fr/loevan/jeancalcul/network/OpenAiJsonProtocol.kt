package fr.loevan.jeancalcul.network

import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

internal fun ChatRequest.toOpenAiJson(
    fallbackModelIds: List<String> = emptyList(),
    includeUsageCost: Boolean = false,
): JsonObject =
    buildJsonObject {
        put("model", profile.modelId)
        if (fallbackModelIds.isNotEmpty()) {
            putJsonArray("models") { fallbackModelIds.forEach { add(JsonPrimitive(it)) } }
        }
        put("stream", true)
        putJsonObject("stream_options") { put("include_usage", true) }
        if (includeUsageCost) putJsonObject("usage") { put("include", true) }
        putJsonArray("messages") { messages.forEach { add(it.toOpenAiJson()) } }
        options.temperature?.let { put("temperature", it) }
        options.maxOutputTokens?.let { put("max_tokens", it) }
        if (options.stopSequences.isNotEmpty()) {
            putJsonArray("stop") { options.stopSequences.forEach { add(JsonPrimitive(it)) } }
        }
        if (tools.isNotEmpty()) {
            putJsonArray("tools") { tools.forEach { add(it.toOpenAiJson()) } }
        }
    }

private fun ChatMessage.toOpenAiJson(): JsonObject =
    buildJsonObject {
        put("role", role.toOpenAiRole())
        toolCallId?.let { put("tool_call_id", it) }
        val textOnly = content.all { it is MessageContent.Text }
        if (textOnly) {
            put("content", content.joinToString("\n") { (it as MessageContent.Text).text })
        } else {
            put("content", buildJsonArray { content.forEach { add(it.toOpenAiJson()) } })
        }
    }

private fun MessageContent.toOpenAiJson(): JsonObject =
    when (this) {
        is MessageContent.Text ->
            buildJsonObject {
                put("type", "text")
                put("text", text)
            }

        is MessageContent.Image ->
            buildJsonObject {
                put("type", "image_url")
                putJsonObject("image_url") { put("url", reference.uri) }
            }

        is MessageContent.Audio ->
            buildJsonObject {
                put("type", "input_audio")
                putJsonObject("input_audio") {
                    put("data", reference.uri)
                    put("format", reference.mimeType.substringAfter('/', reference.mimeType))
                }
            }
    }

private fun ToolDefinition.toOpenAiJson(): JsonObject =
    buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            put("parameters", inputSchema)
        }
    }

internal fun OpenAiCompatibleConfiguration.capabilitiesFor(modelId: String): ModelCapabilities =
    capabilitiesByModel[modelId]
        ?: configuredModels.firstOrNull { it.id == modelId }?.capabilities
        ?: defaultCapabilities

internal fun OpenAiCompatibleConfiguration.endpoint(relativePath: String): HttpUrl {
    val rawBaseUrl = connection.baseUrl.trim().let { if (it.endsWith('/')) it else "$it/" }
    val baseUrl =
        rawBaseUrl.toHttpUrlOrNull()
            ?: throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "invalid_base_url",
                "L'URL de base OpenAI-compatible est invalide.",
            )
    return baseUrl.resolve(relativePath)
        ?: throw providerException(
            ProviderErrorCategory.INVALID_REQUEST,
            "invalid_endpoint_url",
            "L'URL de l'endpoint OpenAI-compatible est invalide.",
        )
}

internal fun Response.decodeModels(
    json: Json,
    configuration: OpenAiCompatibleConfiguration,
): List<ModelDescriptor> {
    val body = body?.string() ?: throw protocolException("empty_models_response")
    val data =
        json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?: throw protocolException("invalid_models_response")
    return data.mapNotNull { element ->
        val modelId = element.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val model = element.jsonObject
        ModelDescriptor(
            id = modelId,
            displayName = model["name"]?.jsonPrimitive?.contentOrNull ?: modelId,
            capabilities = model.toDiscoveredCapabilities(configuration.capabilitiesFor(modelId)),
        )
    }
}

private fun JsonObject.toDiscoveredCapabilities(fallback: ModelCapabilities): ModelCapabilities {
    val architecture = this["architecture"] as? JsonObject
    val inputModalities =
        architecture?.get("input_modalities")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.contentOrNull.toContentModality()
        }?.toSet().orEmpty()
    val outputModalities =
        architecture?.get("output_modalities")?.jsonArray?.mapNotNull {
            it.jsonPrimitive.contentOrNull.toContentModality()
        }?.toSet().orEmpty()
    val parameters = this["supported_parameters"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    val context = this["context_length"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val maxOutput =
        (this["top_provider"] as? JsonObject)
            ?.get("max_completion_tokens")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.toIntOrNull()
    return fallback.copy(
        inputModalities = inputModalities.ifEmpty { fallback.inputModalities },
        outputModalities = outputModalities.ifEmpty { fallback.outputModalities },
        supportsToolCalling = if (parameters.isEmpty()) fallback.supportsToolCalling else "tools" in parameters,
        supportsParallelToolCalls =
            if (parameters.isEmpty()) fallback.supportsParallelToolCalls else "tools" in parameters,
        limits =
            fallback.limits.copy(
                maxContextTokens = context ?: fallback.limits.maxContextTokens,
                maxOutputTokens = maxOutput ?: fallback.limits.maxOutputTokens,
            ),
    )
}

private fun String?.toContentModality() =
    when (this?.lowercase()) {
        "text" -> fr.loevan.jeancalcul.domain.ContentModality.TEXT
        "image" -> fr.loevan.jeancalcul.domain.ContentModality.IMAGE
        "audio" -> fr.loevan.jeancalcul.domain.ContentModality.AUDIO
        else -> null
    }

private fun MessageRole.toOpenAiRole(): String = name.lowercase()
