package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Transcription d’un enregistrement WAV via `POST {base}/audio/transcriptions`,
 * l’API commune à OpenAI et à vLLM (modèles Whisper, Voxtral…).
 */
internal suspend fun recognizeSpeechFile(
    settings: SpeechSettings,
    credentials: SpeechCredentials,
    wav: ByteArray,
): String = withContext(Dispatchers.IO) {
    withTimeout(120_000) {
        transcriptionText(SpeechHttp.json(transcriptionRequest(settings, credentials, wav)))
    }
}

internal fun transcriptionRequest(settings: SpeechSettings, credentials: SpeechCredentials, wav: ByteArray): Request {
    if (wav.size > MAX_TRANSCRIPTION_BYTES) {
        throw SpeechFailure(SpeechErrorCode.AUDIO, "Recording exceeds the 25 MB transcription limit")
    }
    val endpoint = settings.transcriptionEndpoint(credentials)
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
        .addFormDataPart("model", endpoint.model.trim())
        .addFormDataPart("response_format", "json")
        .apply { settings.transcriptionLanguage()?.let { addFormDataPart("language", it) } }
        .build()
    return SpeechHttp.authorizedPost("${endpoint.baseUrl}/audio/transcriptions", endpoint.apiKey, body).build()
}

internal fun transcriptionText(response: JSONObject): String {
    response.optJSONObject("error")?.let {
        throw SpeechFailure(SpeechErrorCode.SERVER, "Transcription failed. Check the model name and server settings.")
    }
    val text = response.opt("text") as? String
        ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "The server returned no transcription")
    return text.trim()
}

private const val MAX_TRANSCRIPTION_BYTES = 25 * 1024 * 1024
