package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.OpenAiSpeechConfig
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechCredentialsUnavailable
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class SpeechErrorCode {
    CONFIGURATION, CREDENTIALS, PERMISSION, AUDIO, NETWORK, TIMEOUT, AUTHENTICATION,
    RATE_LIMITED, SERVER, PROTOCOL, NO_SPEECH, BACKPRESSURE, UNAVAILABLE,
}

internal class SpeechFailure(val code: SpeechErrorCode, val userMessage: String) : IOException(code.name)

internal fun Throwable.speechFailure(): SpeechFailure = when (this) {
    is SpeechFailure -> this
    is SpeechCredentialsUnavailable -> SpeechFailure(SpeechErrorCode.CREDENTIALS, "Could not decrypt speech credentials. Enter and save them again.")
    is SecurityException -> SpeechFailure(SpeechErrorCode.PERMISSION, "Allow Jean-Calcul to use the microphone")
    is kotlinx.coroutines.TimeoutCancellationException -> SpeechFailure(SpeechErrorCode.TIMEOUT, "Speech service timed out. Try again.")
    is IOException -> SpeechFailure(SpeechErrorCode.NETWORK, "Could not connect to the speech service. Check your network.")
    else -> SpeechFailure(SpeechErrorCode.PROTOCOL, "Speech service returned unsupported data")
}

/** Point d’accès OpenAI-compatible résolu pour un appel : racine `/v1`, clé (facultative pour vLLM) et modèle. */
internal data class SpeechEndpoint(val baseUrl: String, val apiKey: String, val model: String) {
    override fun toString(): String = "SpeechEndpoint(baseUrl=$baseUrl, model=$model)"
}

internal fun SpeechSettings.transcriptionEndpoint(secrets: SpeechCredentials): SpeechEndpoint = when (asr) {
    AsrProvider.OPENAI -> SpeechEndpoint(OpenAiSpeechConfig.BASE_URL, secrets.openAi, openAi.transcriptionModel)
    AsrProvider.VLLM -> SpeechEndpoint(vllmBaseUrl(vllm.baseUrl), secrets.vllm, vllm.transcriptionModel)
    AsrProvider.ANDROID -> throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Android recognition does not use a server")
}

internal fun SpeechSettings.speechEndpoint(secrets: SpeechCredentials): SpeechEndpoint = when (tts) {
    TtsProvider.OPENAI -> SpeechEndpoint(OpenAiSpeechConfig.BASE_URL, secrets.openAi, openAi.speechModel)
    TtsProvider.VLLM -> SpeechEndpoint(vllmBaseUrl(vllm.baseUrl), secrets.vllm, vllm.speechModel)
    TtsProvider.ANDROID, TtsProvider.NONE ->
        throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "This speech output does not use a server")
}

internal fun SpeechSettings.speechVoice(): String = when (tts) {
    TtsProvider.OPENAI -> openAi.voice
    TtsProvider.VLLM -> vllm.voice
    TtsProvider.ANDROID, TtsProvider.NONE -> ""
}

/**
 * [hasProviderVllm] : un fournisseur vLLM de conversation est configuré et peut prêter son adresse
 * lorsque le champ vocal est vide.
 */
internal fun validateSpeechSettings(
    settings: SpeechSettings,
    secrets: SpeechCredentials,
    synthesis: Boolean,
    hasProviderVllm: Boolean = false,
) {
    if (!synthesis && !settings.recognitionEnabled) return
    fun required(value: String, name: String) {
        if (value.isBlank()) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Configure first: $name")
    }
    fun vllmServer() {
        if (settings.vllm.baseUrl.isBlank()) {
            if (!hasProviderVllm) required("", "vLLM server address")
        } else {
            vllmBaseUrl(settings.vllm.baseUrl)
        }
    }
    if (synthesis) {
        when (settings.tts) {
            TtsProvider.NONE -> throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Configure spoken answers in settings first")
            TtsProvider.ANDROID -> Unit
            TtsProvider.OPENAI -> {
                required(secrets.openAi, "OpenAI API key")
                required(settings.openAi.speechModel, "Speech model")
                required(settings.openAi.voice, "Voice")
                if (settings.openAi.instructions.length > 500) {
                    throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Reading instructions must be at most 500 characters")
                }
            }
            TtsProvider.VLLM -> {
                vllmServer()
                required(settings.vllm.speechModel, "Speech model")
            }
        }
    } else when (settings.asr) {
        AsrProvider.ANDROID -> Unit
        AsrProvider.OPENAI -> {
            required(secrets.openAi, "OpenAI API key")
            required(settings.openAi.transcriptionModel, "Transcription model")
        }
        AsrProvider.VLLM -> {
            vllmServer()
            required(settings.vllm.transcriptionModel, "Transcription model")
        }
    }
    if (settings.language.isNotBlank() && !LANGUAGE.matches(settings.language)) {
        throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Language must be a code such as fr or fr-FR")
    }
}

/** Serveur vLLM du réseau local : HTTP accepté, mais ni identifiants, ni paramètres, ni fragment dans l’adresse. */
internal fun vllmBaseUrl(value: String): String {
    val url = value.trim().toHttpUrlOrNull()
    if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty() ||
        url.query != null || url.fragment != null
    ) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "The vLLM address must be http(s)://host:port/v1 without credentials or parameters")
    return url.toString().trimEnd('/')
}

/** Langue envoyée aux API de transcription : seul le code ISO-639-1 est accepté par Whisper. */
internal fun SpeechSettings.transcriptionLanguage(): String? =
    language.trim().substringBefore('-').substringBefore('_').lowercase().takeIf { it.length in 2..3 }

private val LANGUAGE = Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*")
