package fr.loevan.jeancalcul.domain

/** Strict contracts for the phase-1 Android tools that complement volume control. */
object MvpToolSchemas {
    const val VERSION = "1.0.0"
    const val MEDIA_PLAY_PAUSE = "media.play_pause"
    const val DEVICE_OPEN_SETTINGS = "device.open_settings"
    const val APPS_LAUNCH = "apps.launch"
    const val DEVICE_TOGGLE_FLASHLIGHT = "device.toggle_flashlight"
    const val DEVICE_GET_BATTERY = "device.get_battery"
    const val DEVICE_GET_LOCAL_TIME = "device.get_local_time"
    const val TASKS_CREATE_LOCAL = "tasks.create_local"

    val definitions: List<ToolDefinition> =
        listOf(
            ToolDefinition(
                name = MEDIA_PLAY_PAUSE,
                version = VERSION,
                description = "Dispatch an Android media play or pause command.",
                inputSchema = ToolJsonSchemas.strictObject(),
                outputSchema = booleanResult("dispatched"),
                riskLevel = ToolRiskLevel.R2,
                availability = unlockedCapability(ToolDeviceCapabilities.MEDIA_CONTROL),
                defaultPolicy = ToolDefaultPolicy.CONFIRM,
            ),
            ToolDefinition(
                name = DEVICE_OPEN_SETTINGS,
                version = VERSION,
                description = "Open one declared Android system settings panel without changing a protected setting.",
                inputSchema =
                    ToolJsonSchemas.strictObject(
                        properties = mapOf("panel" to enumString(SettingsPanel.entries.map(SettingsPanel::name))),
                        required = listOf("panel"),
                    ),
                outputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "panel" to enumString(SettingsPanel.entries.map(SettingsPanel::name)),
                                "opened" to ToolJsonSchemas.boolean(),
                            ),
                        required = listOf("panel", "opened"),
                    ),
                riskLevel = ToolRiskLevel.R1,
                availability = unlockedCapability(ToolDeviceCapabilities.SETTINGS_PANEL),
                defaultPolicy = ToolDefaultPolicy.ALLOW,
            ),
            ToolDefinition(
                name = APPS_LAUNCH,
                version = VERSION,
                description = "Launch an application that is installed and visible to Android.",
                inputSchema =
                    ToolJsonSchemas.strictObject(
                        properties = mapOf("packageName" to ToolJsonSchemas.string(minLength = 1, maxLength = 255)),
                        required = listOf("packageName"),
                    ),
                outputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "packageName" to ToolJsonSchemas.string(minLength = 1, maxLength = 255),
                                "launched" to ToolJsonSchemas.boolean(),
                            ),
                        required = listOf("packageName", "launched"),
                    ),
                riskLevel = ToolRiskLevel.R2,
                availability = unlockedCapability(ToolDeviceCapabilities.APP_LAUNCHER),
                defaultPolicy = ToolDefaultPolicy.CONFIRM,
            ),
            ToolDefinition(
                name = DEVICE_TOGGLE_FLASHLIGHT,
                version = VERSION,
                description = "Set the Android camera flashlight to an explicit enabled state.",
                inputSchema =
                    ToolJsonSchemas.strictObject(
                        properties = mapOf("enabled" to ToolJsonSchemas.boolean()),
                        required = listOf("enabled"),
                    ),
                outputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "cameraId" to ToolJsonSchemas.string(minLength = 1),
                                "enabled" to ToolJsonSchemas.boolean(),
                            ),
                        required = listOf("cameraId", "enabled"),
                    ),
                riskLevel = ToolRiskLevel.R2,
                requiredAndroidPermissions = setOf(ToolAndroidPermissions.CAMERA),
                availability = unlockedCapability(ToolDeviceCapabilities.FLASHLIGHT),
                defaultPolicy = ToolDefaultPolicy.CONFIRM,
            ),
            ToolDefinition(
                name = DEVICE_GET_BATTERY,
                version = VERSION,
                description = "Read the current Android battery level and charging state.",
                inputSchema = ToolJsonSchemas.strictObject(),
                outputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "levelPercent" to ToolJsonSchemas.integer(minimum = 0, maximum = 100),
                                "isCharging" to ToolJsonSchemas.boolean(),
                                "isPowerSaveMode" to ToolJsonSchemas.boolean(),
                            ),
                        required = listOf("levelPercent", "isCharging", "isPowerSaveMode"),
                    ),
                riskLevel = ToolRiskLevel.R0,
                availability = lockScreenCapability(ToolDeviceCapabilities.BATTERY_STATUS),
                defaultPolicy = ToolDefaultPolicy.ALLOW,
            ),
            ToolDefinition(
                name = DEVICE_GET_LOCAL_TIME,
                version = VERSION,
                description = "Read the device local time and time zone without network access.",
                inputSchema = ToolJsonSchemas.strictObject(),
                outputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "epochMillis" to ToolJsonSchemas.integer(minimum = 0),
                                "isoLocalDateTime" to ToolJsonSchemas.string(minLength = 1),
                                "timeZoneId" to ToolJsonSchemas.string(minLength = 1),
                            ),
                        required = listOf("epochMillis", "isoLocalDateTime", "timeZoneId"),
                    ),
                riskLevel = ToolRiskLevel.R0,
                availability = lockScreenCapability(ToolDeviceCapabilities.LOCAL_TIME),
                defaultPolicy = ToolDefaultPolicy.ALLOW,
            ),
            ToolDefinition(
                name = TASKS_CREATE_LOCAL,
                version = VERSION,
                description = "Create one task in Jean-Calcul's local-only task storage.",
                inputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "title" to ToolJsonSchemas.string(minLength = 1, maxLength = 200),
                                "notes" to ToolJsonSchemas.string(maxLength = 2_000),
                                "dueAtEpochMillis" to ToolJsonSchemas.integer(minimum = 0),
                            ),
                        required = listOf("title"),
                    ),
                outputSchema =
                    ToolJsonSchemas.strictObject(
                        properties =
                            mapOf(
                                "id" to ToolJsonSchemas.string(minLength = 1),
                                "title" to ToolJsonSchemas.string(minLength = 1, maxLength = 200),
                                "createdAtEpochMillis" to ToolJsonSchemas.integer(minimum = 0),
                                "dueAtEpochMillis" to ToolJsonSchemas.integer(minimum = 0),
                            ),
                        required = listOf("id", "title", "createdAtEpochMillis"),
                    ),
                riskLevel = ToolRiskLevel.R2,
                availability = unlockedCapability(ToolDeviceCapabilities.LOCAL_TASKS),
                defaultPolicy = ToolDefaultPolicy.CONFIRM,
            ),
        )

    enum class SettingsPanel {
        APPLICATION_DETAILS,
        WIFI,
        BLUETOOTH,
        DISPLAY,
        SOUND,
        DATE_TIME,
        ACCESSIBILITY,
    }

    private fun booleanResult(name: String) =
        ToolJsonSchemas.strictObject(
            properties = mapOf(name to ToolJsonSchemas.boolean()),
            required = listOf(name),
        )

    private fun enumString(values: List<String>) = ToolJsonSchemas.string(enumValues = values)

    private fun unlockedCapability(capability: String) =
        ToolAvailability(
            requiredDeviceCapabilities = setOf(capability),
            lockScreenConstraint = ToolLockScreenConstraint.UNLOCKED_ONLY,
        )

    private fun lockScreenCapability(capability: String) =
        ToolAvailability(
            requiredDeviceCapabilities = setOf(capability),
            lockScreenConstraint = ToolLockScreenConstraint.AVAILABLE,
        )
}
