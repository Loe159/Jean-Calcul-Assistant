package fr.loevan.jeancalcul.network.anthropic

import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ContentModality
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderLimits
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.network.protocolException
import fr.loevan.jeancalcul.network.providerException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

internal fun ChatRequest.toAnthropicJson(defaultMaxOutputTokens: Int): JsonObject =
    buildJsonObject {
        put("model", profile.modelId)
        put("stream", true)
        put("max_tokens", options.maxOutputTokens ?: defaultMaxOutputTokens)
        messages.systemText().takeIf(String::isNotBlank)?.let { put("system", it) }
        putJsonArray("messages") {
            messages.filter { it.role != MessageRole.SYSTEM }.forEach { add(it.toAnthropicMessage()) }
        }
        options.temperature?.let { put("temperature", it) }
        if (options.stopSequences.isNotEmpty()) {
            putJsonArray("stop_sequences") { options.stopSequences.forEach { add(JsonPrimitive(it)) } }
        }
        if (tools.isNotEmpty()) putJsonArray("tools") { tools.forEach { add(it.toAnthropicTool()) } }
    }

private fun List<ChatMessage>.systemText(): String =
    filter { it.role == MessageRole.SYSTEM }
        .flatMap(ChatMessage::content)
        .joinToString("\n") { content ->
            (content as? MessageContent.Text)?.text
                ?: throw providerException(
                    ProviderErrorCategory.CAPABILITY_MISMATCH,
                    "anthropic_system_media_unsupported",
                    "Anthropic n'accepte que du texte dans le message systeme.",
                )
        }

private fun ChatMessage.toAnthropicMessage(): JsonObject =
    buildJsonObject {
        put("role", if (role == MessageRole.ASSISTANT) "assistant" else "user")
        put(
            "content",
            if (role == MessageRole.TOOL) {
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", requireNotNull(toolCallId))
                            put("content", buildJsonArray { content.forEach { add(it.toAnthropicContent()) } })
                        },
                    )
                }
            } else {
                buildJsonArray { content.forEach { add(it.toAnthropicContent()) } }
            },
        )
    }

private fun MessageContent.toAnthropicContent(): JsonObject =
    when (this) {
        is MessageContent.Text ->
            buildJsonObject {
                put("type", "text")
                put("text", text)
            }
        is MessageContent.Image -> reference.toAnthropicImage()
        is MessageContent.Audio ->
            throw providerException(
                ProviderErrorCategory.CAPABILITY_MISMATCH,
                "anthropic_audio_unsupported",
                "Le modele Anthropic configure ne prend pas en charge l'audio.",
            )
    }

private fun fr.loevan.jeancalcul.domain.MediaReference.toAnthropicImage(): JsonObject =
    buildJsonObject {
        put("type", "image")
        put(
            "source",
            when {
                uri.startsWith("https://") ->
                    buildJsonObject {
                        put("type", "url")
                        put("url", uri)
                    }

                uri.startsWith("data:") -> {
                    val data = uri.substringAfter("base64,", missingDelimiterValue = "")
                    if (data.isBlank()) {
                        throw providerException(
                            ProviderErrorCategory.INVALID_REQUEST,
                            "anthropic_invalid_image_data",
                            "L'image doit etre une URL HTTPS ou une donnee base64 valide.",
                        )
                    }
                    buildJsonObject {
                        put("type", "base64")
                        put("media_type", mimeType)
                        put("data", data)
                    }
                }

                else ->
                    throw providerException(
                        ProviderErrorCategory.INVALID_REQUEST,
                        "anthropic_image_reference_unsupported",
                        "L'image doit etre une URL HTTPS ou une donnee base64 valide.",
                    )
            },
        )
    }

private fun ToolDefinition.toAnthropicTool(): JsonObject =
    buildJsonObject {
        put("name", name)
        put("description", description)
        put("input_schema", inputSchema)
    }

internal fun AnthropicConfiguration.endpoint(relativePath: String): HttpUrl {
    val rawBaseUrl = connection.baseUrl.trim().let { if (it.endsWith('/')) it else "$it/" }
    val baseUrl =
        rawBaseUrl.toHttpUrlOrNull()
            ?: throw providerException(
                ProviderErrorCategory.INVALID_REQUEST,
                "anthropic_invalid_base_url",
                "L'URL de base Anthropic est invalide.",
            )
    return baseUrl.resolve(relativePath)
        ?: throw providerException(
            ProviderErrorCategory.INVALID_REQUEST,
            "anthropic_invalid_endpoint_url",
            "L'URL de l'endpoint Anthropic est invalide.",
        )
}

internal fun Response.decodeAnthropicModels(
    json: Json,
    configuration: AnthropicConfiguration,
): List<ModelDescriptor> {
    val payload = body?.string() ?: throw protocolException("anthropic_empty_models_response")
    val data =
        json.parseToJsonElement(payload).jsonObject["data"] as? JsonArray
            ?: throw protocolException("anthropic_invalid_models_response")
    return data.mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id = model["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val fallback = configuration.capabilitiesFor(id)
        val capabilities = model["capabilities"] as? JsonObject
        val inputs =
            capabilities?.get("input_modalities")?.jsonArray?.mapNotNull {
                when (it.jsonPrimitive.contentOrNull) {
                    "text" -> ContentModality.TEXT
                    "image" -> ContentModality.IMAGE
                    else -> null
                }
            }?.toSet().orEmpty()
        ModelDescriptor(
            id = id,
            displayName = model["display_name"]?.jsonPrimitive?.contentOrNull ?: id,
            capabilities =
                fallback.copy(
                    inputModalities = inputs.ifEmpty { fallback.inputModalities },
                    supportsToolCalling =
                        capabilities?.get("tool_use")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                            ?: fallback.supportsToolCalling,
                    limits =
                        ProviderLimits(
                            maxContextTokens =
                                model["max_input_tokens"]?.jsonPrimitive?.intOrNull
                                    ?: fallback.limits.maxContextTokens,
                            maxOutputTokens =
                                model["max_tokens"]?.jsonPrimitive?.intOrNull
                                    ?: fallback.limits.maxOutputTokens,
                        ),
                ),
        )
    }
}

internal val DEFAULT_ANTHROPIC_CAPABILITIES =
    ModelCapabilities(
        inputModalities = setOf(ContentModality.TEXT, ContentModality.IMAGE),
        outputModalities = setOf(ContentModality.TEXT),
        supportsToolCalling = true,
        supportsParallelToolCalls = true,
        limits = ProviderLimits(maxContextTokens = 200_000, maxOutputTokens = 64_000),
    )
