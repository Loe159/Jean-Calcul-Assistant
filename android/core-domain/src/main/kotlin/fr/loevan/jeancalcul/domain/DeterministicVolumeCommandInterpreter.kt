package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.util.UUID

/**
 * Phase-0 interpreter for the small, local-only volume command vocabulary.
 *
 * It intentionally recognizes only explicit absolute requests and the one ambiguous relative
 * request used by the proof of concept. Unknown wording never becomes a tool proposal.
 */
class DeterministicVolumeCommandInterpreter(
    private val actionIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun interpret(transcript: String): VolumeCommandInterpretation =
        transcript.normalizedForMatching().let { normalized ->
            absoluteVolumeExpression.find(normalized)?.let(::interpretAbsoluteVolume)
                ?: normalized
                    .takeIf { it.contains("baisse") && it.contains("volume") }
                    ?.let { confirmationRequired() }
                ?: VolumeCommandInterpretation.Invalid(
                    "Je peux seulement regler le volume de musique avec une valeur entre 0 et 100 %.",
                )
        }

    fun setMusicVolumeProposal(volumePercent: Int): ActionProposal {
        require(volumePercent in 0..100)
        return ActionProposal(
            actionId = actionIdFactory(),
            toolName = VolumeToolSchemas.SET_VOLUME_TOOL_NAME,
            arguments =
                JsonObject(
                    mapOf(
                        "stream" to JsonPrimitive(VolumeStream.MUSIC.name),
                        "volumePercent" to JsonPrimitive(volumePercent),
                    ),
                ),
        )
    }

    fun getVolumeProposal(stream: VolumeStream): ActionProposal =
        ActionProposal(
            actionId = actionIdFactory(),
            toolName = VolumeToolSchemas.GET_VOLUME_TOOL_NAME,
            arguments = JsonObject(mapOf("stream" to JsonPrimitive(stream.name))),
        )

    private fun interpretAbsoluteVolume(match: MatchResult): VolumeCommandInterpretation =
        match.groupValues[1]
            .toIntOrNull()
            ?.takeIf { it in 0..100 }
            ?.let { volumePercent ->
                VolumeCommandInterpretation.Ready(proposal = setMusicVolumeProposal(volumePercent))
            }
            ?: VolumeCommandInterpretation.Invalid("Le volume doit etre compris entre 0 et 100 %.")

    private fun confirmationRequired(): VolumeCommandInterpretation =
        VolumeCommandInterpretation.ConfirmationRequired(
            adjustment = RelativeVolumeAdjustment(stream = VolumeStream.MUSIC, deltaPercent = -10),
            prompt = "Voulez-vous baisser le volume de musique de 10 % ?",
        )

    private fun String.normalizedForMatching(): String =
        Normalizer
            .normalize(lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(WHITESPACE, " ")
            .trim()

    private companion object {
        val absoluteVolumeExpression =
            Regex(
                "\\b(?:mets|mettez|mettre|regle|reglez|regler)\\s+(?:le\\s+)?volume" +
                    "(?:\\s+(?:de\\s+)?musique)?\\s+a\\s+(\\d{1,3})\\s*%?\\b",
            )
        val COMBINING_MARKS = Regex("\\p{M}+")
        val WHITESPACE = Regex("\\s+")
    }
}

sealed interface VolumeCommandInterpretation {
    data class Ready(
        val proposal: ActionProposal,
    ) : VolumeCommandInterpretation

    data class ConfirmationRequired(
        val adjustment: RelativeVolumeAdjustment,
        val prompt: String,
    ) : VolumeCommandInterpretation

    data class Invalid(val message: String) : VolumeCommandInterpretation
}

data class RelativeVolumeAdjustment(
    val stream: VolumeStream,
    val deltaPercent: Int,
) {
    init {
        require(deltaPercent != 0)
    }
}
