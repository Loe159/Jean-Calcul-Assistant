package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.DeterministicVolumeCommandInterpreter
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.toolbridge.PlatformVolume
import fr.loevan.jeancalcul.toolbridge.VolumeController
import fr.loevan.jeancalcul.toolbridge.VolumeToolBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeCommandProcessorTest {
    @Test
    fun `explicit command applies observed volume and returns spoken result`() {
        val controller = FakeVolumeController(current = 4)
        val processor = processorFor(controller)

        val outcome = processor.process("Mets le volume a 30 %")

        assertEquals(3, controller.volume.current)
        assertEquals(
            VoiceCommandOutcome.Completed("Le volume de musique est maintenant a 30 %."),
            outcome,
        )
    }

    @Test
    fun `invalid command does not invoke the volume bridge`() {
        val controller = FakeVolumeController(current = 4)
        val processor = processorFor(controller)

        val outcome = processor.process("Mets le volume a 120 %")

        assertTrue(outcome is VoiceCommandOutcome.Invalid)
        assertEquals(0, controller.writeCount)
    }

    @Test
    fun `cancelling ambiguous command prevents volume write`() {
        val controller = FakeVolumeController(current = 7)
        val processor = processorFor(controller)

        assertTrue(processor.process("Baisse le volume") is VoiceCommandOutcome.ConfirmationRequired)
        processor.cancelPending()

        assertTrue(processor.confirm() is VoiceCommandOutcome.Invalid)
        assertEquals(0, controller.writeCount)
    }

    @Test
    fun `confirmed ambiguous command reads then lowers volume`() {
        val controller = FakeVolumeController(current = 7)
        val processor = processorFor(controller)

        processor.process("Baisse le volume")
        val outcome = processor.confirm()

        assertEquals(6, controller.volume.current)
        assertEquals(
            VoiceCommandOutcome.Completed("Le volume de musique est maintenant a 60 %."),
            outcome,
        )
    }

    private fun processorFor(controller: FakeVolumeController) =
        VolumeCommandProcessor(
            interpreter = DeterministicVolumeCommandInterpreter(actionIdFactory = { "action" }),
            volumeToolBridge = VolumeToolBridge(controller, ToolAuditLogger { }),
        )

    private class FakeVolumeController(current: Int) : VolumeController {
        var volume = PlatformVolume(current = current, maximum = 10)
        var writeCount = 0

        override fun read(stream: VolumeStream): PlatformVolume = volume

        override fun write(
            stream: VolumeStream,
            volume: Int,
        ) {
            writeCount += 1
            this.volume = this.volume.copy(current = volume)
        }
    }
}
