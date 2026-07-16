package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicVolumeCommandInterpreterTest {
    @Test
    fun `explicit absolute volume command creates a music proposal`() {
        val interpretation =
            DeterministicVolumeCommandInterpreter(actionIdFactory = { "action-1" })
                .interpret("Mets le volume a 30 %")

        assertTrue(interpretation is VolumeCommandInterpretation.Ready)
        interpretation as VolumeCommandInterpretation.Ready
        assertEquals("action-1", interpretation.proposal.actionId)
        assertEquals(VolumeToolSchemas.SET_VOLUME_TOOL_NAME, interpretation.proposal.toolName)
        assertEquals("MUSIC", interpretation.proposal.arguments.getValue("stream").jsonPrimitive.content)
        assertEquals("30", interpretation.proposal.arguments.getValue("volumePercent").jsonPrimitive.content)
    }

    @Test
    fun `relative decrease requires confirmation before creating a set proposal`() {
        val interpretation = DeterministicVolumeCommandInterpreter().interpret("Baisse le volume")

        assertTrue(interpretation is VolumeCommandInterpretation.ConfirmationRequired)
        interpretation as VolumeCommandInterpretation.ConfirmationRequired
        assertEquals(VolumeStream.MUSIC, interpretation.adjustment.stream)
        assertEquals(-10, interpretation.adjustment.deltaPercent)
    }

    @Test
    fun `invalid percentage is rejected`() {
        val interpretation = DeterministicVolumeCommandInterpreter().interpret("Mets le volume a 120 %")

        assertTrue(interpretation is VolumeCommandInterpretation.Invalid)
    }

    @Test
    fun `unrecognized command is rejected`() {
        val interpretation = DeterministicVolumeCommandInterpreter().interpret("Envoie un message")

        assertTrue(interpretation is VolumeCommandInterpretation.Invalid)
    }
}
