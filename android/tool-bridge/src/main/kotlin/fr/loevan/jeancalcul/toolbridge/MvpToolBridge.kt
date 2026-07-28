@file:Suppress("TooManyFunctions")

package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.MvpToolSchemas
import fr.loevan.jeancalcul.domain.ToolError
import fr.loevan.jeancalcul.feature.tasks.LocalTaskStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

fun interface MediaPlaybackController {
    fun dispatchPlayPause(): Boolean
}

fun interface SettingsPanelLauncher {
    fun open(panel: MvpToolSchemas.SettingsPanel): Boolean
}

interface InstalledAppLauncher {
    fun canLaunch(packageName: String): Boolean

    fun launch(packageName: String): Boolean
}

interface FlashlightController {
    fun primaryCameraId(): String?

    fun setEnabled(
        cameraId: String,
        enabled: Boolean,
    )
}

data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean,
    val isPowerSaveMode: Boolean,
)

fun interface BatteryStatusSource {
    fun read(): BatteryStatus?
}

data class LocalTimeSnapshot(
    val epochMillis: Long,
    val isoLocalDateTime: String,
    val timeZoneId: String,
)

fun interface LocalTimeSource {
    fun read(): LocalTimeSnapshot
}

data class MvpToolDependencies(
    val mediaPlaybackController: MediaPlaybackController,
    val settingsPanelLauncher: SettingsPanelLauncher,
    val installedAppLauncher: InstalledAppLauncher,
    val flashlightController: FlashlightController,
    val batteryStatusSource: BatteryStatusSource,
    val localTimeSource: LocalTimeSource,
    val localTaskStore: LocalTaskStore,
)

internal fun mvpToolRegistrations(
    dependencies: MvpToolDependencies,
    clock: () -> Long,
): List<ToolRegistration> {
    val executors =
        mapOf(
            MvpToolSchemas.MEDIA_PLAY_PAUSE to mediaExecutor(dependencies.mediaPlaybackController),
            MvpToolSchemas.DEVICE_OPEN_SETTINGS to settingsExecutor(dependencies.settingsPanelLauncher),
            MvpToolSchemas.APPS_LAUNCH to appLaunchExecutor(dependencies.installedAppLauncher),
            MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT to flashlightExecutor(dependencies.flashlightController),
            MvpToolSchemas.DEVICE_GET_BATTERY to batteryExecutor(dependencies.batteryStatusSource),
            MvpToolSchemas.DEVICE_GET_LOCAL_TIME to localTimeExecutor(dependencies.localTimeSource),
            MvpToolSchemas.TASKS_CREATE_LOCAL to taskExecutor(dependencies.localTaskStore, clock),
        )
    return MvpToolSchemas.definitions.map { definition ->
        ToolRegistration(definition, requireNotNull(executors[definition.name]))
    }
}

private fun mediaExecutor(controller: MediaPlaybackController) =
    ToolExecutor {
        runTool(
            code = "MEDIA_CONTROL_FAILED",
            message = "The media command could not be dispatched.",
        ) {
            if (!controller.dispatchPlayPause()) return@runTool failure("MEDIA_SESSION_UNAVAILABLE")
            success("dispatched" to JsonPrimitive(true))
        }
    }

private fun settingsExecutor(launcher: SettingsPanelLauncher) =
    ToolExecutor { proposal ->
        val panel =
            proposal.stringArgument("panel")
                ?.let { runCatching { MvpToolSchemas.SettingsPanel.valueOf(it) }.getOrNull() }
                ?: return@ToolExecutor failure("INVALID_SETTINGS_PANEL")
        runTool("SETTINGS_PANEL_UNAVAILABLE", "The requested Android settings panel could not be opened.") {
            if (!launcher.open(panel)) return@runTool failure("SETTINGS_PANEL_UNAVAILABLE")
            success("panel" to JsonPrimitive(panel.name), "opened" to JsonPrimitive(true))
        }
    }

private fun appLaunchExecutor(launcher: InstalledAppLauncher) =
    ToolExecutor { proposal ->
        val packageName = proposal.stringArgument("packageName") ?: return@ToolExecutor failure("INVALID_PACKAGE")
        if (!launcher.canLaunch(packageName)) return@ToolExecutor failure("APP_NOT_INSTALLED")
        runTool("APP_LAUNCH_FAILED", "The installed application could not be launched.") {
            if (!launcher.launch(packageName)) return@runTool failure("APP_LAUNCH_FAILED")
            success("packageName" to JsonPrimitive(packageName), "launched" to JsonPrimitive(true))
        }
    }

private fun flashlightExecutor(controller: FlashlightController) =
    ToolExecutor { proposal ->
        val enabled = proposal.booleanArgument("enabled") ?: return@ToolExecutor failure("INVALID_FLASHLIGHT_STATE")
        val cameraId = controller.primaryCameraId() ?: return@ToolExecutor failure("FLASHLIGHT_UNAVAILABLE")
        runTool("FLASHLIGHT_FAILED", "The flashlight state could not be changed.") {
            controller.setEnabled(cameraId, enabled)
            success("cameraId" to JsonPrimitive(cameraId), "enabled" to JsonPrimitive(enabled))
        }
    }

private fun batteryExecutor(source: BatteryStatusSource) =
    ToolExecutor {
        runTool("BATTERY_READ_FAILED", "The battery status could not be read.") {
            val status = source.read() ?: return@runTool failure("BATTERY_STATUS_UNAVAILABLE")
            success(
                "levelPercent" to JsonPrimitive(status.levelPercent.coerceIn(0, 100)),
                "isCharging" to JsonPrimitive(status.isCharging),
                "isPowerSaveMode" to JsonPrimitive(status.isPowerSaveMode),
            )
        }
    }

private fun localTimeExecutor(source: LocalTimeSource) =
    ToolExecutor {
        runTool("LOCAL_TIME_FAILED", "The local device time could not be read.") {
            val snapshot = source.read()
            success(
                "epochMillis" to JsonPrimitive(snapshot.epochMillis),
                "isoLocalDateTime" to JsonPrimitive(snapshot.isoLocalDateTime),
                "timeZoneId" to JsonPrimitive(snapshot.timeZoneId),
            )
        }
    }

private fun taskExecutor(
    store: LocalTaskStore,
    clock: () -> Long,
) = ToolExecutor { proposal ->
    val title = proposal.stringArgument("title") ?: return@ToolExecutor failure("INVALID_TASK_TITLE")
    val notes = proposal.stringArgument("notes")
    val dueAtEpochMillis = proposal.longArgument("dueAtEpochMillis")
    runTool("TASK_CREATE_FAILED", "The local task could not be persisted.") {
        val task = store.create(proposal.actionId, title, notes, dueAtEpochMillis, clock())
        val output =
            buildMap<String, JsonPrimitive> {
                put("id", JsonPrimitive(task.id))
                put("title", JsonPrimitive(task.title))
                put("createdAtEpochMillis", JsonPrimitive(task.createdAtEpochMillis))
                task.dueAtEpochMillis?.let { put("dueAtEpochMillis", JsonPrimitive(it)) }
            }
        ToolExecutionOutcome.Success(JsonObject(output))
    }
}

private inline fun runTool(
    code: String,
    message: String,
    block: () -> ToolExecutionOutcome,
): ToolExecutionOutcome =
    try {
        block()
    } catch (_: SecurityException) {
        failure("ANDROID_PERMISSION_DENIED")
    } catch (_: RuntimeException) {
        ToolExecutionOutcome.Failure(ToolError(code, message))
    }

private fun ActionProposal.stringArgument(name: String): String? = (arguments[name] as? JsonPrimitive)?.contentOrNull

private fun ActionProposal.booleanArgument(name: String): Boolean? = (arguments[name] as? JsonPrimitive)?.booleanOrNull

private fun ActionProposal.longArgument(name: String): Long? = (arguments[name] as? JsonPrimitive)?.longOrNull

private fun success(vararg fields: Pair<String, JsonPrimitive>) =
    ToolExecutionOutcome.Success(JsonObject(mapOf(*fields)))

private fun failure(code: String) = ToolExecutionOutcome.Failure(ToolError(code, code.lowercase().replace('_', ' ')))
