package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAuditStage
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeToolBridgeTest {
    @Test
    fun `reads music alarm and notification volume`() {
        val controller = FakeVolumeController()
        val bridge = VolumeToolBridge(controller, ToolAuditLogger { })

        VolumeStream.entries.forEach { stream ->
            val result = bridge.execute(getProposal(stream))

            assertTrue(result.isSuccess)
            assertEquals(
                controller.volumes.getValue(stream).current,
                result.output!!.getValue("platformVolume").jsonPrimitive.content.toInt(),
            )
        }
    }

    @Test
    fun `writes a validated target and returns the observed native value`() {
        val controller = FakeVolumeController()
        val bridge = VolumeToolBridge(controller, ToolAuditLogger { })

        val result = bridge.execute(setProposal(VolumeStream.MUSIC, 30))

        assertTrue(result.isSuccess)
        assertEquals(5, controller.volumes.getValue(VolumeStream.MUSIC).current)
        assertEquals(5, result.output!!.getValue("platformVolume").jsonPrimitive.content.toInt())
        assertEquals(15, result.output!!.getValue("platformMaxVolume").jsonPrimitive.content.toInt())
    }

    @Test
    fun `does not call android volume APIs with invalid percentages`() {
        val controller = FakeVolumeController()
        val events = mutableListOf<ToolAuditEvent>()
        val bridge = VolumeToolBridge(controller, ToolAuditLogger(events::add))

        val result = bridge.execute(setProposal(VolumeStream.ALARM, 101))

        assertFalse(result.isSuccess)
        assertEquals("INVALID_VOLUME", result.error!!.code)
        assertEquals(0, controller.writeCount)
        assertEquals(listOf(ToolAuditStage.REQUESTED, ToolAuditStage.ERROR), events.map(ToolAuditEvent::stage))
    }

    @Test
    fun `same target is idempotent`() {
        val controller =
            FakeVolumeController().apply {
                volumes[VolumeStream.NOTIFICATION] = PlatformVolume(3, 5)
            }
        val bridge = VolumeToolBridge(controller, ToolAuditLogger { })

        val result = bridge.execute(setProposal(VolumeStream.NOTIFICATION, 60))

        assertTrue(result.isSuccess)
        assertEquals(0, controller.writeCount)
    }

    private fun getProposal(stream: VolumeStream) =
        ActionProposal(
            actionId = "get-${stream.name}",
            toolName = VolumeToolSchemas.GET_VOLUME_TOOL_NAME,
            arguments = JsonObject(mapOf("stream" to JsonPrimitive(stream.name))),
        )

    private fun setProposal(
        stream: VolumeStream,
        percent: Int,
    ) = ActionProposal(
        actionId = "set-${stream.name}",
        toolName = VolumeToolSchemas.SET_VOLUME_TOOL_NAME,
        arguments =
            JsonObject(
                mapOf(
                    "stream" to JsonPrimitive(stream.name),
                    "volumePercent" to JsonPrimitive(percent),
                ),
            ),
    )

    private class FakeVolumeController : VolumeController {
        val volumes =
            mutableMapOf(
                VolumeStream.MUSIC to PlatformVolume(4, 15),
                VolumeStream.ALARM to PlatformVolume(3, 7),
                VolumeStream.NOTIFICATION to PlatformVolume(2, 5),
            )
        var writeCount = 0

        override fun read(stream: VolumeStream): PlatformVolume = volumes.getValue(stream)

        override fun write(
            stream: VolumeStream,
            volume: Int,
        ) {
            writeCount += 1
            volumes[stream] = volumes.getValue(stream).copy(current = volume)
        }
    }
}
