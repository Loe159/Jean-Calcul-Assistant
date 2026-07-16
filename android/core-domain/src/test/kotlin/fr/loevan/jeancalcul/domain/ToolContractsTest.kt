package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolContractsTest {
    @Test
    fun `volume definitions reject additional properties`() {
        val validation =
            VolumeToolSchemas.validate(
                ActionProposal(
                    actionId = "request-1",
                    toolName = VolumeToolSchemas.GET_VOLUME_TOOL_NAME,
                    arguments =
                        JsonObject(
                            mapOf(
                                "stream" to JsonPrimitive("MUSIC"),
                                "unexpected" to JsonPrimitive(true),
                            ),
                        ),
                ),
            )

        assertTrue(validation is VolumeToolValidation.Invalid)
        assertEquals("INVALID_ARGUMENTS", (validation as VolumeToolValidation.Invalid).code)
    }

    @Test
    fun `set volume accepts integer values within the inclusive percentage range`() {
        val validation =
            VolumeToolSchemas.validate(
                ActionProposal(
                    actionId = "request-2",
                    toolName = VolumeToolSchemas.SET_VOLUME_TOOL_NAME,
                    arguments =
                        JsonObject(
                            mapOf(
                                "stream" to JsonPrimitive("ALARM"),
                                "volumePercent" to JsonPrimitive(100),
                            ),
                        ),
                ),
            )

        assertEquals(
            VolumeToolRequest.Set(VolumeStream.ALARM, 100),
            (validation as VolumeToolValidation.Valid).request,
        )
    }

    @Test
    fun `set volume rejects out of range and decimal values`() {
        val invalidValues = listOf(JsonPrimitive(-1), JsonPrimitive(101), JsonPrimitive("30"), JsonPrimitive(30.5))

        invalidValues.forEach { value ->
            val validation =
                VolumeToolSchemas.validate(
                    ActionProposal(
                        actionId = "request-3",
                        toolName = VolumeToolSchemas.SET_VOLUME_TOOL_NAME,
                        arguments = JsonObject(mapOf("stream" to JsonPrimitive("MUSIC"), "volumePercent" to value)),
                    ),
                )

            assertTrue(validation is VolumeToolValidation.Invalid)
            assertEquals("INVALID_VOLUME", (validation as VolumeToolValidation.Invalid).code)
        }
    }

    @Test
    fun `tool result exposes success state`() {
        val result = ToolResult("request-4", "audio.get_volume", output = JsonObject(emptyMap()))

        assertTrue(result.isSuccess)
        assertFalse(ToolResult("request-4", "audio.get_volume", error = ToolError("ERROR", "failure")).isSuccess)
    }
}
