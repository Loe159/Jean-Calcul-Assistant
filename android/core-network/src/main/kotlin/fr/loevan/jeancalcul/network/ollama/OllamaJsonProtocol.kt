package fr.loevan.jeancalcul.network.ollama

import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ContentModality
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderLimits
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.network.protocolException
import fr.loevan.jeancalcul.network.providerException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

internal fun ChatRequest.toOllamaJson(): JsonObject =
    buildJsonObject {
        put("model", profile.modelId)
        put("stream", true)
        putJsonArray("messages") { messages.forEach { add(it.toOllamaMessage()) } }
        if (tools.isNotEmpty()) putJsonArray("tools") { tools.forEach { add(it.toOllamaTool()) } }
        if (options.temperature != null || options.maxOutputTokens != null || options.stopSequences.isNotEmpty()) {
            putJsonObject("options") {
                options.temperature?.let { put("temperature", it) }
                options.maxOutputTokens?.let { put("num_predict", it) }
                if (options.stopSequences.isNotEmpty()) {
                    putJsonArray("stop") { options.stopSequences.forEach { add(JsonPrimitive(it)) } }
                }
            }
        }
    }

private fun ChatMessage.toOllamaMessage(): JsonObject =
    buildJsonObject {
        put("role", role.toOllamaRole())
        put(
            "content",
            content.filterIsInstance<MessageContent.Text>().joinToString("\n", transform = MessageContent.Text::text),
        )
        val images = content.filterIsInstance<MessageContent.Image>()
        if (images.isNotEmpty()) {
            putJsonArray("images") { images.forEach { add(JsonPrimitive(it.base64Data())) } }
        }
        if (content.any { it is MessageContent.Audio }) {
            throw providerException(
                ProviderErrorCategory.CAPABILITY_MISMATCH,
                "ollama_audio_unsupported",
                "Le modele Ollama configure ne prend pas en charge l'audio.",
            )
        }
        toolCallId?.let { put("tool_call_id", it) }
    }

private fun MessageContent.Image.base64Data(): String {
    val value = reference.uri.substringAfter("base64,", missingDelimiterValue = "")
    if (!reference.uri.startsWith("data:") || value.isBlank()) {
        throw providerException(
            ProviderErrorCategory.INVALID_REQUEST,
            "ollama_image_reference_unsupported",
            "Ollama attend une image encodee en base64.",
        )
    }
    return value
}

private fun MessageRole.toOllamaRole(): String = name.lowercase()

private fun ToolDefinition.toOllamaTool(): JsonObject =
    buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            put("parameters", inputSchema)
        }
    }

internal fun OllamaConfiguration.endpoint(route: String): HttpUrl {
    val rawBase = connection.baseUrl.trim().trimEnd('/')
    val normalized =
        if (rawBase.endsWith("/api")) "$rawBase/" else "$rawBase/api/"
    val base =
        normalized.toHttpUrlOrNull()
            ?: throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "ollama_invalid_base_url",
                "L'URL du serveur Ollama est invalide.",
            )
    return base.resolve(route)
        ?: throw providerException(
            ProviderErrorCategory.INVALID_REQUEST,
            "ollama_invalid_endpoint_url",
            "L'URL de l'endpoint Ollama est invalide.",
        )
}

internal fun Response.decodeOllamaModelNames(json: Json): List<Pair<String, String>> {
    val payload = body?.string() ?: throw protocolException("ollama_empty_models_response")
    val models =
        json.parseToJsonElement(payload).jsonObject["models"] as? JsonArray
            ?: throw protocolException("ollama_invalid_models_response")
    return models.mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id =
            model["model"]?.jsonPrimitive?.contentOrNull
                ?: model["name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
        id to (model["name"]?.jsonPrimitive?.contentOrNull ?: id)
    }
}

internal fun Response.decodeOllamaCapabilities(
    json: Json,
    fallback: ModelCapabilities,
): ModelCapabilities {
    val payload = body?.string() ?: throw protocolException("ollama_empty_model_details")
    val details = json.parseToJsonElement(payload).jsonObject
    val capabilityNames = details["capabilities"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
    val context =
        (details["model_info"] as? JsonObject)
            ?.entries
            ?.firstOrNull { (key, value) -> key.endsWith(".context_length") && value.jsonPrimitive.longOrNull != null }
            ?.value
            ?.jsonPrimitive
            ?.longOrNull
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()
    return fallback.copy(
        inputModalities =
            buildSet {
                add(ContentModality.TEXT)
                if ("vision" in capabilityNames) add(ContentModality.IMAGE)
            },
        supportsToolCalling = "tools" in capabilityNames,
        supportsParallelToolCalls = "tools" in capabilityNames,
        limits = ProviderLimits(maxContextTokens = context, maxOutputTokens = fallback.limits.maxOutputTokens),
    )
}

internal val DEFAULT_OLLAMA_CAPABILITIES =
    ModelCapabilities(
        inputModalities = setOf(ContentModality.TEXT),
        outputModalities = setOf(ContentModality.TEXT),
        supportsToolCalling = false,
        supportsParallelToolCalls = false,
        limits = ProviderLimits(),
    )
