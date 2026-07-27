package fr.loevan.jeancalcul.toolbridge

import android.media.AudioManager
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolDeviceCapabilities
import fr.loevan.jeancalcul.domain.ToolError
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.domain.VolumeToolRequest
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

data class PlatformVolume(
    val current: Int,
    val maximum: Int,
)

/** Small adapter that makes volume execution testable without an Android device. */
interface VolumeController {
    fun read(stream: VolumeStream): PlatformVolume

    fun write(
        stream: VolumeStream,
        volume: Int,
    )
}

class AudioManagerVolumeController(
    private val audioManager: AudioManager,
) : VolumeController {
    override fun read(stream: VolumeStream): PlatformVolume {
        val androidStream = stream.androidStream()
        return PlatformVolume(
            current = audioManager.getStreamVolume(androidStream),
            maximum = audioManager.getStreamMaxVolume(androidStream),
        )
    }

    override fun write(
        stream: VolumeStream,
        volume: Int,
    ) {
        audioManager.setStreamVolume(stream.androidStream(), volume, 0)
    }

    private fun VolumeStream.androidStream(): Int =
        when (this) {
            VolumeStream.MUSIC -> AudioManager.STREAM_MUSIC
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        }
}

/** Deterministic Android executor used only after the registry has validated a volume request. */
private class VolumeToolExecutor(
    private val volumeController: VolumeController,
) : ToolExecutor {
    override fun execute(proposal: ActionProposal): ToolExecutionOutcome {
        val request =
            proposal.toVolumeRequest()
                ?: return ToolExecutionOutcome.Failure(ToolError("INVALID_ARGUMENTS", "Invalid volume arguments."))
        return try {
            executeRequest(request)
        } catch (_: SecurityException) {
            failure("AUDIO_ACCESS_DENIED", "L'acces au volume Android a ete refuse.")
        } catch (_: IllegalArgumentException) {
            failure("STREAM_UNAVAILABLE", "Le flux audio n'est pas disponible sur cet appareil.")
        } catch (_: RuntimeException) {
            failure("AUDIO_FAILURE", "La lecture ou la modification du volume a echoue.")
        }
    }

    private fun executeRequest(request: VolumeToolRequest): ToolExecutionOutcome {
        val before = volumeController.read(request.stream)
        if (before.maximum <= 0) return streamUnavailable()
        if (request is VolumeToolRequest.Set) {
            val target = request.volumePercent.toPlatformVolume(before.maximum)
            if (before.current != target) volumeController.write(request.stream, target)
        }
        val observed = volumeController.read(request.stream)
        return if (observed.maximum > 0) {
            success(request.stream, observed)
        } else {
            streamUnavailable()
        }
    }

    private fun streamUnavailable() =
        failure("STREAM_UNAVAILABLE", "Le flux audio n'est pas disponible sur cet appareil.")

    private fun success(
        stream: VolumeStream,
        volume: PlatformVolume,
    ): ToolExecutionOutcome =
        ToolExecutionOutcome.Success(
            output =
                JsonObject(
                    mapOf(
                        "stream" to JsonPrimitive(stream.name),
                        "volumePercent" to JsonPrimitive(volume.current.toPercent(volume.maximum)),
                        "platformVolume" to JsonPrimitive(volume.current),
                        "platformMaxVolume" to JsonPrimitive(volume.maximum),
                    ),
                ),
        )

    private fun failure(
        code: String,
        message: String,
    ) = ToolExecutionOutcome.Failure(ToolError(code, message))

    private fun ActionProposal.toVolumeRequest(): VolumeToolRequest? {
        val stream =
            arguments["stream"]
                ?.jsonPrimitive
                ?.content
                ?.let { runCatching { VolumeStream.valueOf(it) }.getOrNull() }
                ?: return null
        return when (toolName) {
            VolumeToolSchemas.GET_VOLUME_TOOL_NAME -> VolumeToolRequest.Get(stream)
            VolumeToolSchemas.SET_VOLUME_TOOL_NAME ->
                VolumeToolRequest.Set(
                    stream = stream,
                    volumePercent = arguments.getValue("volumePercent").jsonPrimitive.int,
                )
            else -> null
        }
    }

    private fun Int.toPlatformVolume(maximum: Int): Int = (maximum * this / 100.0).roundToInt().coerceIn(0, maximum)

    private fun Int.toPercent(maximum: Int): Int = (this * 100.0 / maximum).roundToInt().coerceIn(0, 100)
}

fun createVolumeToolRegistry(
    volumeController: VolumeController,
    auditLogger: ToolAuditLogger? = null,
): ToolRegistry {
    val executor = VolumeToolExecutor(volumeController)
    val registrations = VolumeToolSchemas.definitions.map { ToolRegistration(it, executor) }
    return auditLogger?.let { ToolRegistry(registrations, it) } ?: ToolRegistry(registrations)
}

fun volumeToolAvailabilityContext(isDeviceLocked: Boolean): ToolAvailabilityContext =
    ToolAvailabilityContext(
        deviceCapabilities =
            setOf(
                ToolDeviceCapabilities.VOLUME_READ,
                ToolDeviceCapabilities.VOLUME_WRITE,
            ),
        isDeviceLocked = isDeviceLocked,
    )
