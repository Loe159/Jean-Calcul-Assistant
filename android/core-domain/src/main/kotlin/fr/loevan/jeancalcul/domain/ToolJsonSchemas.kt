package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object ToolJsonSchemas {
    fun strictObject(
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
    ): JsonObject =
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "additionalProperties" to JsonPrimitive(false),
                "properties" to JsonObject(properties),
                "required" to JsonArray(required.map(::JsonPrimitive)),
            ),
        )

    fun string(
        enumValues: List<String>? = null,
        minLength: Int? = null,
        maxLength: Int? = null,
    ): JsonObject =
        JsonObject(
            buildMap {
                put("type", JsonPrimitive("string"))
                enumValues?.let { put("enum", JsonArray(it.map(::JsonPrimitive))) }
                minLength?.let { put("minLength", JsonPrimitive(it)) }
                maxLength?.let { put("maxLength", JsonPrimitive(it)) }
            },
        )

    fun integer(
        minimum: Long? = null,
        maximum: Long? = null,
    ): JsonObject =
        JsonObject(
            buildMap {
                put("type", JsonPrimitive("integer"))
                minimum?.let { put("minimum", JsonPrimitive(it)) }
                maximum?.let { put("maximum", JsonPrimitive(it)) }
            },
        )

    fun boolean(): JsonObject = JsonObject(mapOf("type" to JsonPrimitive("boolean")))
}
