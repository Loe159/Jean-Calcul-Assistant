package io.github.mangi.eta.data.model

import kotlinx.serialization.Serializable

/**
 * Jean-Calcul ne garde que trois moteurs vocaux : la reconnaissance Android locale,
 * l’API audio d’OpenAI et un serveur vLLM (ou autre serveur compatible OpenAI).
 * Les anciennes valeurs ETA (Qwen, Doubao, MiMo…) sont ramenées aux valeurs par défaut
 * au décodage grâce à `coerceInputValues`.
 */
@Serializable
enum class AsrProvider { ANDROID, OPENAI, VLLM }

@Serializable
enum class TtsProvider { NONE, ANDROID, OPENAI, VLLM }

@Serializable
data class OpenAiSpeechConfig(
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val speechModel: String = DEFAULT_SPEECH_MODEL,
    val voice: String = DEFAULT_VOICE,
    /** Consignes de lecture facultatives, transmises aux modèles qui les acceptent. */
    val instructions: String = "",
) {
    companion object {
        const val BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-mini-transcribe"
        const val DEFAULT_SPEECH_MODEL = "gpt-4o-mini-tts"
        const val DEFAULT_VOICE = "alloy"
    }
}

/**
 * Serveur auto-hébergé exposant `/v1/audio/transcriptions` (et éventuellement `/v1/audio/speech`).
 * Adresse vide : on reprend l’adresse et la clé du fournisseur vLLM de conversation.
 */
@Serializable
data class VllmSpeechConfig(
    val baseUrl: String = "",
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val speechModel: String = "",
    val voice: String = "",
) {
    companion object {
        const val DEFAULT_TRANSCRIPTION_MODEL = "openai/whisper-large-v3-turbo"
    }
}

@Serializable
data class SpeechSettings(
    val asr: AsrProvider = AsrProvider.ANDROID,
    val tts: TtsProvider = TtsProvider.ANDROID,
    val autoSpeak: Boolean = false,
    /** Lecture automatique de la réponse finale dans la conversation, distincte de l’assistant flottant. */
    val chatAutoSpeak: Boolean = false,
    /** Code BCP-47 ou ISO-639-1 (`fr`, `fr-FR`) ; vide = langue du téléphone. */
    val language: String = "",
    val openAi: OpenAiSpeechConfig = OpenAiSpeechConfig(),
    val vllm: VllmSpeechConfig = VllmSpeechConfig(),
    /** Silence avant envoi automatique dans l’assistant ; 0 = uniquement sur action de l’utilisateur. */
    val autoSendSilenceMs: Int = DEFAULT_AUTO_SEND_SILENCE_MS,
    /** Correction prudente de la transcription par le modèle de conversation courant. */
    val refineTranscript: Boolean = false,
    /** Ne concerne que la saisie vocale de Jean-Calcul ; la configuration est conservée si désactivée. */
    val recognitionEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_AUTO_SEND_SILENCE_MS = 1_500
        val AUTO_SEND_SILENCE_OPTIONS = listOf(1_000, 1_500, 2_000, 3_000, 0)
    }
}

internal enum class SpeechCredentialField { OPENAI, VLLM }

/** Les clés ne font pas partie de [SpeechSettings] pour ne jamais sortir dans un export ou un journal. */
@Serializable
class SpeechCredentials(
    val openAi: String = "",
    val vllm: String = "",
) {
    internal fun withValue(field: SpeechCredentialField, value: String): SpeechCredentials = SpeechCredentials(
        openAi = if (field == SpeechCredentialField.OPENAI) value else openAi,
        vllm = if (field == SpeechCredentialField.VLLM) value else vllm,
    )

    internal fun withVllm(value: String): SpeechCredentials = SpeechCredentials(openAi = openAi, vllm = value)

    override fun toString(): String = "SpeechCredentials(redacted)"
}
