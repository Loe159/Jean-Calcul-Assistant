package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A versioned, platform-independent description of an executable local tool. */
data class ToolDefinition(
    val name: String,
    val version: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject,
)

/** An untrusted request to execute one declared tool. */
data class ActionProposal(
    val actionId: String,
    val toolName: String,
    val arguments: JsonObject,
)

/** The terminal result of a tool execution. Exactly one of [output] or [error] is populated. */
data class ToolResult(
    val actionId: String,
    val toolName: String,
    val output: JsonObject? = null,
    val error: ToolError? = null,
) {
    init {
        require((output == null) != (error == null))
    }

    val isSuccess: Boolean
        get() = error == null
}

data class ToolError(
    val code: String,
    val message: String,
)

enum class ToolAuditStage {
    REQUESTED,
    VALIDATED,
    RESULT,
    ERROR,
}

data class ToolAuditEvent(
    val actionId: String,
    val toolName: String,
    val stage: ToolAuditStage,
    val message: String,
)

/** Receives audit events without coupling domain contracts to a storage implementation. */
fun interface ToolAuditLogger {
    fun log(event: ToolAuditEvent)
}

enum class VolumeStream {
    MUSIC,
    ALARM,
    NOTIFICATION,
}

sealed interface VolumeToolRequest {
    val stream: VolumeStream

    data class Get(override val stream: VolumeStream) : VolumeToolRequest

    data class Set(
        override val stream: VolumeStream,
        val volumePercent: Int,
    ) : VolumeToolRequest
}

/** Definitions and strict JSON validation for the phase-0 volume tools. */
object VolumeToolSchemas {
    const val GET_VOLUME_TOOL_NAME = "audio.get_volume"
    const val SET_VOLUME_TOOL_NAME = "audio.set_volume"
    private const val VERSION = "1.0.0"

    val definitions: List<ToolDefinition> =
        listOf(
            ToolDefinition(
                name = GET_VOLUME_TOOL_NAME,
                version = VERSION,
                inputSchema =
                    objectSchema(
                        properties = mapOf("stream" to streamSchema()),
                        required = listOf("stream"),
                    ),
                outputSchema = volumeOutputSchema(),
            ),
            ToolDefinition(
                name = SET_VOLUME_TOOL_NAME,
                version = VERSION,
                inputSchema =
                    objectSchema(
                        properties =
                            mapOf(
                                "stream" to streamSchema(),
                                "volumePercent" to
                                    JsonObject(
                                        mapOf(
                                            "type" to JsonPrimitive("integer"),
                                            "minimum" to JsonPrimitive(0),
                                            "maximum" to JsonPrimitive(100),
                                        ),
                                    ),
                            ),
                        required = listOf("stream", "volumePercent"),
                    ),
                outputSchema = volumeOutputSchema(),
            ),
        )

    fun validate(proposal: ActionProposal): VolumeToolValidation =
        when (proposal.toolName) {
            GET_VOLUME_TOOL_NAME -> validateGet(proposal.arguments)
            SET_VOLUME_TOOL_NAME -> validateSet(proposal.arguments)
            else -> VolumeToolValidation.Invalid("UNKNOWN_TOOL", "Outil de volume inconnu.")
        }

    private fun validateGet(arguments: JsonObject): VolumeToolValidation {
        if (arguments.keys != setOf("stream")) return unexpectedArguments()
        return parseStream(arguments["stream"])
            ?.let { VolumeToolValidation.Valid(VolumeToolRequest.Get(it)) }
            ?: VolumeToolValidation.Invalid("INVALID_STREAM", "Le flux audio est invalide.")
    }

    private fun validateSet(arguments: JsonObject): VolumeToolValidation =
        if (arguments.keys != setOf("stream", "volumePercent")) {
            unexpectedArguments()
        } else {
            val stream = parseStream(arguments["stream"])
            val percent = arguments["volumePercent"] as? JsonPrimitive
            val value = percent?.takeUnless(JsonPrimitive::isString)?.content?.toIntOrNull()
            when {
                stream == null -> VolumeToolValidation.Invalid("INVALID_STREAM", "Le flux audio est invalide.")
                value == null || value !in 0..100 ->
                    VolumeToolValidation.Invalid(
                        "INVALID_VOLUME",
                        "Le volume doit etre un entier entre 0 et 100.",
                    )
                else -> VolumeToolValidation.Valid(VolumeToolRequest.Set(stream, value))
            }
        }

    private fun parseStream(value: Any?): VolumeStream? =
        (value as? JsonPrimitive)
            ?.takeIf(JsonPrimitive::isString)
            ?.let { runCatching { VolumeStream.valueOf(it.content) }.getOrNull() }

    private fun unexpectedArguments() =
        VolumeToolValidation.Invalid("INVALID_ARGUMENTS", "Les proprietes de la demande sont invalides.")

    private fun streamSchema() =
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("string"),
                "enum" to JsonArray(VolumeStream.entries.map { JsonPrimitive(it.name) }),
            ),
        )

    private fun volumeOutputSchema() =
        objectSchema(
            properties =
                mapOf(
                    "stream" to streamSchema(),
                    "volumePercent" to JsonObject(mapOf("type" to JsonPrimitive("integer"))),
                    "platformVolume" to JsonObject(mapOf("type" to JsonPrimitive("integer"))),
                    "platformMaxVolume" to JsonObject(mapOf("type" to JsonPrimitive("integer"))),
                ),
            required = listOf("stream", "volumePercent", "platformVolume", "platformMaxVolume"),
        )

    private fun objectSchema(
        properties: Map<String, JsonObject>,
        required: List<String>,
    ): JsonObject =
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "additionalProperties" to JsonPrimitive(false),
                "properties" to JsonObject(properties),
                "required" to JsonArray(required.map(::JsonPrimitive)),
            ),
        )
}

sealed interface VolumeToolValidation {
    data class Valid(val request: VolumeToolRequest) : VolumeToolValidation

    data class Invalid(
        val code: String,
        val message: String,
    ) : VolumeToolValidation
}
