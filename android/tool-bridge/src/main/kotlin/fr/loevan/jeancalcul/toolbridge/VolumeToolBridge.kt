package fr.loevan.jeancalcul.toolbridge

import android.media.AudioManager
import android.util.Log
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAuditStage
import fr.loevan.jeancalcul.domain.ToolError
import fr.loevan.jeancalcul.domain.ToolResult
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.domain.VolumeToolRequest
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import fr.loevan.jeancalcul.domain.VolumeToolValidation
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

/** Executes only the two phase-0 volume tool definitions. */
class VolumeToolBridge(
    private val volumeController: VolumeController,
    private val auditLogger: ToolAuditLogger = LogcatToolAuditLogger,
) {
    fun execute(proposal: ActionProposal): ToolResult {
        audit(proposal, ToolAuditStage.REQUESTED, "Volume tool request received.")
        return when (val validation = VolumeToolSchemas.validate(proposal)) {
            is VolumeToolValidation.Invalid -> failure(proposal, validation.code, validation.message)
            is VolumeToolValidation.Valid -> executeValidated(proposal, validation.request)
        }
    }

    private fun executeValidated(
        proposal: ActionProposal,
        request: VolumeToolRequest,
    ): ToolResult {
        audit(proposal, ToolAuditStage.VALIDATED, "Volume tool request validated.")
        return try {
            executeRequest(proposal, request)
        } catch (_: SecurityException) {
            failure(proposal, "AUDIO_ACCESS_DENIED", "L'acces au volume Android a ete refuse.")
        } catch (_: IllegalArgumentException) {
            failure(proposal, "STREAM_UNAVAILABLE", "Le flux audio n'est pas disponible sur cet appareil.")
        } catch (_: RuntimeException) {
            failure(proposal, "AUDIO_FAILURE", "La lecture ou la modification du volume a echoue.")
        }
    }

    private fun executeRequest(
        proposal: ActionProposal,
        request: VolumeToolRequest,
    ): ToolResult {
        val before = volumeController.read(request.stream)
        if (before.maximum <= 0) return streamUnavailable(proposal)
        if (request is VolumeToolRequest.Set) {
            val target = request.volumePercent.toPlatformVolume(before.maximum)
            if (before.current != target) volumeController.write(request.stream, target)
        }
        val observed = volumeController.read(request.stream)
        return if (observed.maximum > 0) {
            success(proposal, request.stream, observed)
        } else {
            streamUnavailable(proposal)
        }
    }

    private fun streamUnavailable(proposal: ActionProposal) =
        failure(proposal, "STREAM_UNAVAILABLE", "Le flux audio n'est pas disponible sur cet appareil.")

    private fun success(
        proposal: ActionProposal,
        stream: VolumeStream,
        volume: PlatformVolume,
    ): ToolResult {
        val result =
            ToolResult(
                actionId = proposal.actionId,
                toolName = proposal.toolName,
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
        audit(proposal, ToolAuditStage.RESULT, "Volume tool completed.")
        return result
    }

    private fun failure(
        proposal: ActionProposal,
        code: String,
        message: String,
    ): ToolResult {
        val result = ToolResult(proposal.actionId, proposal.toolName, error = ToolError(code, message))
        audit(proposal, ToolAuditStage.ERROR, code)
        return result
    }

    private fun audit(
        proposal: ActionProposal,
        stage: ToolAuditStage,
        message: String,
    ) {
        auditLogger.log(ToolAuditEvent(proposal.actionId, proposal.toolName, stage, message))
    }

    private fun Int.toPlatformVolume(maximum: Int): Int = (maximum * this / 100.0).roundToInt().coerceIn(0, maximum)

    private fun Int.toPercent(maximum: Int): Int = (this * 100.0 / maximum).roundToInt().coerceIn(0, 100)
}

private object LogcatToolAuditLogger : ToolAuditLogger {
    override fun log(event: ToolAuditEvent) {
        Log.i("VolumeToolAudit", "${event.stage}:${event.toolName}:${event.actionId}:${event.message}")
    }
}
