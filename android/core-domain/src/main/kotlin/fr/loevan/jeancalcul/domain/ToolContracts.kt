package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A versioned, platform-independent description of an executable local tool. */
data class ToolDefinition(
    val name: String,
    val version: String,
    val description: String,
    val inputSchema: JsonObject,
    val outputSchema: JsonObject,
    val riskLevel: ToolRiskLevel,
    val requiredAndroidPermissions: Set<String> = emptySet(),
    val availability: ToolAvailability = ToolAvailability(),
    val defaultPolicy: ToolDefaultPolicy,
) {
    init {
        require(TOOL_NAME.matches(name))
        require(SEMANTIC_VERSION.matches(version))
        require(description.isNotBlank())
        require(requiredAndroidPermissions.none(String::isBlank))
        require(inputSchema.isStrictObjectSchema())
        require(outputSchema.isStrictObjectSchema())
    }

    fun availabilityIn(context: ToolAvailabilityContext): ToolAvailabilityStatus {
        val missingCapabilities = availability.requiredDeviceCapabilities - context.deviceCapabilities
        val missingPermissions = requiredAndroidPermissions - context.grantedAndroidPermissions
        val unavailableReason =
            when {
                missingCapabilities.isNotEmpty() -> ToolUnavailableReason.DEVICE_CAPABILITY_MISSING
                missingPermissions.isNotEmpty() -> ToolUnavailableReason.PERMISSION_MISSING
                context.isDeviceLocked &&
                    availability.lockScreenConstraint == ToolLockScreenConstraint.UNLOCKED_ONLY ->
                    ToolUnavailableReason.DEVICE_LOCKED
                else -> null
            }
        return if (unavailableReason == null) {
            ToolAvailabilityStatus.Available
        } else {
            ToolAvailabilityStatus.Unavailable(unavailableReason)
        }
    }

    private companion object {
        val TOOL_NAME = Regex("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*")
        val SEMANTIC_VERSION = Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)")
    }
}

enum class ToolRiskLevel {
    R0,
    R1,
    R2,
    R3,
    R4,
    R5,
}

enum class ToolDefaultPolicy {
    ALLOW,
    CONFIRM,
    BIOMETRIC,
    OPEN_SYSTEM_PANEL,
    DENY,
}

enum class ToolLockScreenConstraint {
    AVAILABLE,
    UNLOCKED_ONLY,
}

data class ToolAvailability(
    val requiredDeviceCapabilities: Set<String> = emptySet(),
    val lockScreenConstraint: ToolLockScreenConstraint = ToolLockScreenConstraint.UNLOCKED_ONLY,
) {
    init {
        require(requiredDeviceCapabilities.none(String::isBlank))
    }
}

data class ToolAvailabilityContext(
    val deviceCapabilities: Set<String> = emptySet(),
    val grantedAndroidPermissions: Set<String> = emptySet(),
    val isDeviceLocked: Boolean,
    val isAppForeground: Boolean = true,
) {
    init {
        require(deviceCapabilities.none(String::isBlank))
        require(grantedAndroidPermissions.none(String::isBlank))
    }
}

sealed interface ToolAvailabilityStatus {
    data object Available : ToolAvailabilityStatus

    data class Unavailable(val reason: ToolUnavailableReason) : ToolAvailabilityStatus
}

enum class ToolUnavailableReason {
    DEVICE_CAPABILITY_MISSING,
    PERMISSION_MISSING,
    DEVICE_LOCKED,
}

object ToolDeviceCapabilities {
    const val VOLUME_READ = "device.volume.read"
    const val VOLUME_WRITE = "device.volume.write"
}

/** An untrusted request to execute one declared tool. */
data class ActionProposal(
    val actionId: String,
    val toolName: String,
    val toolVersion: String,
    val arguments: JsonObject,
    val idempotencyKey: String = actionId,
    val expiresAtEpochMillis: Long? = null,
) {
    init {
        require(actionId.isNotBlank())
        require(toolName.isNotBlank())
        require(toolVersion.isNotBlank())
        require(idempotencyKey.isNotBlank())
        require(expiresAtEpochMillis == null || expiresAtEpochMillis >= 0)
    }
}

/** The terminal result of a tool execution. Exactly one of [output] or [error] is populated. */
data class ToolResult(
    val actionId: String,
    val toolName: String,
    val toolVersion: String,
    val output: JsonObject? = null,
    val error: ToolError? = null,
    val replayed: Boolean = false,
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
    REPLAYED,
    RESULT,
    ERROR,
}

data class ToolAuditEvent(
    val actionId: String,
    val toolName: String,
    val toolVersion: String,
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
    const val VERSION = "1.0.0"

    val definitions: List<ToolDefinition> =
        listOf(
            ToolDefinition(
                name = GET_VOLUME_TOOL_NAME,
                version = VERSION,
                description = "Read the current Android volume for one declared audio stream.",
                inputSchema =
                    objectSchema(
                        properties = mapOf("stream" to streamSchema()),
                        required = listOf("stream"),
                    ),
                outputSchema = volumeOutputSchema(),
                riskLevel = ToolRiskLevel.R0,
                availability =
                    ToolAvailability(
                        requiredDeviceCapabilities = setOf(ToolDeviceCapabilities.VOLUME_READ),
                        lockScreenConstraint = ToolLockScreenConstraint.AVAILABLE,
                    ),
                defaultPolicy = ToolDefaultPolicy.ALLOW,
            ),
            ToolDefinition(
                name = SET_VOLUME_TOOL_NAME,
                version = VERSION,
                description = "Set one declared Android audio stream to an absolute percentage.",
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
                riskLevel = ToolRiskLevel.R2,
                availability =
                    ToolAvailability(
                        requiredDeviceCapabilities = setOf(ToolDeviceCapabilities.VOLUME_WRITE),
                        lockScreenConstraint = ToolLockScreenConstraint.UNLOCKED_ONLY,
                    ),
                defaultPolicy = ToolDefaultPolicy.CONFIRM,
            ),
        )

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

private fun JsonObject.isStrictObjectSchema(): Boolean {
    val properties = this["properties"] as? JsonObject
    return this["type"] == JsonPrimitive("object") &&
        this["additionalProperties"] == JsonPrimitive(false) &&
        properties?.values?.all { property ->
            val propertySchema = property as? JsonObject
            propertySchema != null &&
                (
                    propertySchema["type"] != JsonPrimitive("object") ||
                        propertySchema.isStrictObjectSchema()
                )
        } == true
}
