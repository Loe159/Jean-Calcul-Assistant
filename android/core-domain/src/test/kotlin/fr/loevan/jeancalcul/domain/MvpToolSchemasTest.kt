package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MvpToolSchemasTest {
    @Test
    fun `all seven mvp tools have strict unique versioned schemas`() {
        assertEquals(7, MvpToolSchemas.definitions.size)
        assertEquals(7, MvpToolSchemas.definitions.map(ToolDefinition::name).toSet().size)
        MvpToolSchemas.definitions.forEach { definition ->
            assertEquals(MvpToolSchemas.VERSION, definition.version)
            assertEquals(JsonPrimitive(false), definition.inputSchema["additionalProperties"])
            assertEquals(JsonPrimitive(false), definition.outputSchema["additionalProperties"])
        }
    }

    @Test
    fun `only safe read tools remain available on the lock screen`() {
        val context =
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
                isDeviceLocked = true,
            )

        val available =
            MvpToolSchemas.definitions
                .filter { it.availabilityIn(context) == ToolAvailabilityStatus.Available }
                .map(ToolDefinition::name)

        assertEquals(
            setOf(MvpToolSchemas.DEVICE_GET_BATTERY, MvpToolSchemas.DEVICE_GET_LOCAL_TIME),
            available.toSet(),
        )
    }

    @Test
    fun `flashlight declares camera permission and reversible tools require confirmation`() {
        val flashlight = MvpToolSchemas.definitions.first { it.name == MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT }
        assertEquals(setOf(ToolAndroidPermissions.CAMERA), flashlight.requiredAndroidPermissions)

        val reversible =
            MvpToolSchemas.definitions.filter { it.riskLevel == ToolRiskLevel.R2 }
        assertTrue(reversible.isNotEmpty())
        assertTrue(reversible.all { it.defaultPolicy == ToolDefaultPolicy.CONFIRM })
    }
}
