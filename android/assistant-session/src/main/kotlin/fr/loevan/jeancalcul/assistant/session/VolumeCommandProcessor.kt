package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.DeterministicVolumeCommandInterpreter
import fr.loevan.jeancalcul.domain.RelativeVolumeAdjustment
import fr.loevan.jeancalcul.domain.VolumeCommandInterpretation
import fr.loevan.jeancalcul.toolbridge.VolumeToolBridge
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Bridges the phase-0 command interpreter to the already validated volume tool bridge. */
internal class VolumeCommandProcessor(
    private val interpreter: DeterministicVolumeCommandInterpreter,
    private val volumeToolBridge: VolumeToolBridge,
) : VoiceCommandProcessor {
    private var pendingAdjustment: RelativeVolumeAdjustment? = null

    override fun process(transcript: String): VoiceCommandOutcome =
        when (val interpretation = interpreter.interpret(transcript)) {
            is VolumeCommandInterpretation.Ready -> execute(interpretation.proposal)
            is VolumeCommandInterpretation.ConfirmationRequired -> {
                pendingAdjustment = interpretation.adjustment
                VoiceCommandOutcome.ConfirmationRequired(interpretation.prompt)
            }

            is VolumeCommandInterpretation.Invalid -> VoiceCommandOutcome.Invalid(interpretation.message)
        }

    override fun confirm(): VoiceCommandOutcome =
        pendingAdjustment
            ?.also { pendingAdjustment = null }
            ?.let(::confirmAdjustment)
            ?: VoiceCommandOutcome.Invalid("Aucune action n'est en attente de confirmation.")

    override fun cancelPending() {
        pendingAdjustment = null
    }

    private fun confirmAdjustment(adjustment: RelativeVolumeAdjustment): VoiceCommandOutcome =
        volumeToolBridge
            .execute(interpreter.getVolumeProposal(adjustment.stream))
            .output
            ?.get("volumePercent")
            ?.jsonPrimitive
            ?.intOrNull
            ?.let { currentPercent ->
                val targetPercent = (currentPercent + adjustment.deltaPercent).coerceIn(0, 100)
                execute(interpreter.setMusicVolumeProposal(targetPercent))
            }
            ?: VoiceCommandOutcome.Failure("Je n'ai pas pu lire le volume actuel.")

    private fun execute(proposal: fr.loevan.jeancalcul.domain.ActionProposal): VoiceCommandOutcome {
        val result = volumeToolBridge.execute(proposal)
        val observedPercent = result.output?.get("volumePercent")?.jsonPrimitive?.intOrNull
        return if (result.isSuccess && observedPercent != null) {
            VoiceCommandOutcome.Completed("Le volume de musique est maintenant a $observedPercent %.")
        } else {
            VoiceCommandOutcome.Failure(result.error?.message ?: "Je n'ai pas pu modifier le volume.")
        }
    }
}

internal interface VoiceCommandProcessor {
    fun process(transcript: String): VoiceCommandOutcome

    fun confirm(): VoiceCommandOutcome

    fun cancelPending()
}

internal sealed interface VoiceCommandOutcome {
    data class Completed(val response: String) : VoiceCommandOutcome

    data class ConfirmationRequired(val prompt: String) : VoiceCommandOutcome

    data class Invalid(val message: String) : VoiceCommandOutcome

    data class Failure(val message: String) : VoiceCommandOutcome
}
