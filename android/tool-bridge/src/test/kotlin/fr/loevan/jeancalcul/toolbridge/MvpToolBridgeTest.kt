package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.MvpToolSchemas
import fr.loevan.jeancalcul.domain.ToolAndroidPermissions
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAuditStage
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.domain.ToolDeviceCapabilities
import fr.loevan.jeancalcul.feature.tasks.LocalTask
import fr.loevan.jeancalcul.feature.tasks.LocalTaskStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MvpToolBridgeTest {
    private val dependencies = FakeDependencies()

    @Test
    fun `all mvp tools execute through policy registry and emit audit results`() {
        val events = mutableListOf<ToolAuditEvent>()
        val registry = registry(events)
        val proposals =
            listOf(
                proposal("media", MvpToolSchemas.MEDIA_PLAY_PAUSE),
                proposal("settings", MvpToolSchemas.DEVICE_OPEN_SETTINGS, "panel" to "WIFI"),
                proposal("app", MvpToolSchemas.APPS_LAUNCH, "packageName" to "fr.example.installed"),
                proposal("torch", MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT, "enabled" to true),
                proposal("battery", MvpToolSchemas.DEVICE_GET_BATTERY),
                proposal("time", MvpToolSchemas.DEVICE_GET_LOCAL_TIME),
                proposal(
                    "task",
                    MvpToolSchemas.TASKS_CREATE_LOCAL,
                    "title" to "Acheter du lait",
                    "dueAtEpochMillis" to 2_000L,
                ),
            )

        val results = proposals.map { execute(registry, it) }

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, dependencies.mediaDispatchCount)
        assertEquals(listOf(MvpToolSchemas.SettingsPanel.WIFI), dependencies.openedPanels)
        assertEquals(listOf("fr.example.installed"), dependencies.launchedPackages)
        assertEquals(listOf(true), dependencies.flashlightStates)
        assertEquals(1, dependencies.tasks.size)
        assertEquals(7, events.count { it.stage == ToolAuditStage.RESULT })
    }

    @Test
    fun `discovery filters camera permission and lock screen capabilities dynamically`() {
        val registry = registry()
        val unlockedWithoutCamera = availableContext().copy(grantedAndroidPermissions = emptySet())
        val unlockedNames = registry.availableDefinitions(unlockedWithoutCamera).map(ToolDefinition::name)
        val lockedNames =
            registry.availableDefinitions(
                availableContext().copy(isDeviceLocked = true),
            ).map(ToolDefinition::name)

        assertFalse(MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT in unlockedNames)
        assertEquals(
            listOf(MvpToolSchemas.DEVICE_GET_BATTERY, MvpToolSchemas.DEVICE_GET_LOCAL_TIME),
            lockedNames,
        )
    }

    @Test
    fun `application launcher refuses packages that are not installed`() {
        val registry = registry()
        val result =
            execute(
                registry,
                proposal("missing-app", MvpToolSchemas.APPS_LAUNCH, "packageName" to "fr.example.missing"),
            )

        assertEquals("APP_NOT_INSTALLED", result.error?.code)
        assertTrue(dependencies.launchedPackages.isEmpty())
    }

    private fun registry(events: MutableList<ToolAuditEvent> = mutableListOf()) =
        ToolRegistry(
            registrations = mvpToolRegistrations(dependencies.value, clock = { NOW }),
            auditLogger = ToolAuditLogger(events::add),
            clock = { NOW },
        )

    private fun execute(
        registry: ToolRegistry,
        proposal: ActionProposal,
    ) = registry.execute(
        proposal,
        availableContext(),
        policyReceipt(requireNotNull(registry.definitionFor(proposal)), proposal, availableContext(), NOW),
    )

    private fun proposal(
        actionId: String,
        toolName: String,
        vararg arguments: Pair<String, Any>,
    ) = ActionProposal(
        actionId = actionId,
        toolName = toolName,
        toolVersion = MvpToolSchemas.VERSION,
        arguments =
            JsonObject(
                arguments.associate { (name, value) ->
                    name to
                        when (value) {
                            is Boolean -> JsonPrimitive(value)
                            is Long -> JsonPrimitive(value)
                            else -> JsonPrimitive(value.toString())
                        }
                },
            ),
    )

    private fun availableContext() =
        ToolAvailabilityContext(
            deviceCapabilities =
                setOf(
                    ToolDeviceCapabilities.APP_LAUNCHER,
                    ToolDeviceCapabilities.BATTERY_STATUS,
                    ToolDeviceCapabilities.FLASHLIGHT,
                    ToolDeviceCapabilities.LOCAL_TASKS,
                    ToolDeviceCapabilities.LOCAL_TIME,
                    ToolDeviceCapabilities.MEDIA_CONTROL,
                    ToolDeviceCapabilities.SETTINGS_PANEL,
                ),
            grantedAndroidPermissions = setOf(ToolAndroidPermissions.CAMERA),
            isDeviceLocked = false,
        )

    private class FakeDependencies {
        var mediaDispatchCount = 0
        val openedPanels = mutableListOf<MvpToolSchemas.SettingsPanel>()
        val launchedPackages = mutableListOf<String>()
        val flashlightStates = mutableListOf<Boolean>()
        val tasks = mutableListOf<LocalTask>()

        val value =
            MvpToolDependencies(
                mediaPlaybackController =
                    MediaPlaybackController {
                        mediaDispatchCount += 1
                        true
                    },
                settingsPanelLauncher =
                    SettingsPanelLauncher { panel ->
                        openedPanels += panel
                        true
                    },
                installedAppLauncher =
                    object : InstalledAppLauncher {
                        override fun canLaunch(packageName: String) = packageName == "fr.example.installed"

                        override fun launch(packageName: String): Boolean {
                            launchedPackages += packageName
                            return true
                        }
                    },
                flashlightController =
                    object : FlashlightController {
                        override fun primaryCameraId() = "back-camera"

                        override fun setEnabled(
                            cameraId: String,
                            enabled: Boolean,
                        ) {
                            flashlightStates += enabled
                        }
                    },
                batteryStatusSource = BatteryStatusSource { BatteryStatus(73, true, false) },
                localTimeSource = LocalTimeSource { LocalTimeSnapshot(NOW, "1970-01-01T00:00:01Z", "UTC") },
                localTaskStore =
                    object : LocalTaskStore {
                        override fun create(
                            actionId: String,
                            title: String,
                            notes: String?,
                            dueAtEpochMillis: Long?,
                            createdAtEpochMillis: Long,
                        ): LocalTask =
                            LocalTask(actionId, title, notes, dueAtEpochMillis, createdAtEpochMillis).also(tasks::add)

                        override fun list(): List<LocalTask> = tasks.toList()
                    },
            )
    }

    private companion object {
        const val NOW = 1_000L
    }
}
