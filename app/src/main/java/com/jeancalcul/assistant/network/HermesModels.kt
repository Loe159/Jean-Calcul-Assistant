package com.jeancalcul.assistant.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HermesHealth(
    val status: String = "unknown",
    val name: String? = null,
    val version: String? = null
) {
    val isOk: Boolean
        get() = status.equals("ok", ignoreCase = true)
}

@Serializable
data class MobileRequest(
    val requestId: String,
    val input: UserInput,
    val client: ClientInfo,
    val capabilities: List<ToolCapability> = emptyList()
)

@Serializable
data class UserInput(
    val mode: String = "text",
    val text: String,
    val locale: String = "fr-FR"
)

@Serializable
data class ClientInfo(
    val platform: String = "android",
    val appVersion: String = "0.1.0"
)

@Serializable
data class ToolCapability(
    val name: String,
    val risk: String = "low"
)

@Serializable
data class HermesResponse(
    val requestId: String? = null,
    val message: String? = null,
    val actions: List<HermesAction> = emptyList(),
    val raw: JsonObject? = null
) {
    val displayText: String
        get() = message ?: raw?.toString() ?: "Réponse vide"
}

@Serializable
data class HermesAction(
    val actionId: String,
    val tool: String,
    val arguments: JsonElement? = null
)
