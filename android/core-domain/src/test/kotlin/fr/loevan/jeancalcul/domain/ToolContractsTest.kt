package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolContractsTest {
    @Test
    fun `volume definitions expose versioned security and availability metadata`() {
        val getVolume = VolumeToolSchemas.definitions.first { it.name == VolumeToolSchemas.GET_VOLUME_TOOL_NAME }
        val setVolume = VolumeToolSchemas.definitions.first { it.name == VolumeToolSchemas.SET_VOLUME_TOOL_NAME }

        assertEquals(VolumeToolSchemas.VERSION, getVolume.version)
        assertEquals(ToolRiskLevel.R0, getVolume.riskLevel)
        assertEquals(ToolDefaultPolicy.ALLOW, getVolume.defaultPolicy)
        assertEquals(ToolLockScreenConstraint.AVAILABLE, getVolume.availability.lockScreenConstraint)
        assertEquals(ToolRiskLevel.R2, setVolume.riskLevel)
        assertEquals(ToolDefaultPolicy.CONFIRM, setVolume.defaultPolicy)
        assertEquals(ToolLockScreenConstraint.UNLOCKED_ONLY, setVolume.availability.lockScreenConstraint)
    }

    @Test
    fun `locked device only discovers volume reads`() {
        val lockedContext =
            ToolAvailabilityContext(
                deviceCapabilities =
                    setOf(
                        ToolDeviceCapabilities.VOLUME_READ,
                        ToolDeviceCapabilities.VOLUME_WRITE,
                    ),
                isDeviceLocked = true,
            )

        assertEquals(
            ToolAvailabilityStatus.Available,
            VolumeToolSchemas.definitions.first().availabilityIn(lockedContext),
        )
        assertEquals(
            ToolAvailabilityStatus.Unavailable(ToolUnavailableReason.DEVICE_LOCKED),
            VolumeToolSchemas.definitions.last().availabilityIn(lockedContext),
        )
    }

    @Test
    fun `tool result exposes success state`() {
        val result =
            ToolResult(
                actionId = "request-4",
                toolName = VolumeToolSchemas.GET_VOLUME_TOOL_NAME,
                toolVersion = VolumeToolSchemas.VERSION,
                output = kotlinx.serialization.json.JsonObject(emptyMap()),
            )

        assertTrue(result.isSuccess)
        assertFalse(
            ToolResult(
                actionId = "request-4",
                toolName = VolumeToolSchemas.GET_VOLUME_TOOL_NAME,
                toolVersion = VolumeToolSchemas.VERSION,
                error = ToolError("ERROR", "failure"),
            ).isSuccess,
        )
    }
}
