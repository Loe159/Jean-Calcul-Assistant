package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.json.JSONObject

/**
 * Synthèse d’un segment via `POST {base}/audio/speech` (OpenAI ou serveur compatible).
 * On demande du PCM 16 bits mono 24 kHz ; un WAV de même format est aussi accepté.
 */
internal suspend fun synthesizeSpeech(
    settings: SpeechSettings,
    credentials: SpeechCredentials,
    text: String,
    onAudio: (ByteArray) -> Unit,
) = withTimeout(90_000) {
    SpeechHttp.read(speechRequest(settings, credentials, text)) { response ->
        val audio = SpeechSegmentAudio(onAudio)
        val source = response.body.source()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            if (count > 0) audio.write(buffer.copyOf(count))
        }
        audio.end(completed = true)
    }
}

internal fun speechRequest(settings: SpeechSettings, credentials: SpeechCredentials, text: String): Request {
    val endpoint = settings.speechEndpoint(credentials)
    val body = JSONObject()
        .put("model", endpoint.model.trim())
        .put("input", text)
        .put("response_format", "pcm")
    settings.speechVoice().trim().takeIf(String::isNotEmpty)?.let { body.put("voice", it) }
    if (settings.tts == TtsProvider.OPENAI) {
        settings.openAi.instructions.trim().takeIf(String::isNotEmpty)?.let { body.put("instructions", it) }
    }
    return SpeechHttp.jsonRequest("${endpoint.baseUrl}/audio/speech", endpoint.apiKey, body).build()
}

/** Borne la taille d’un segment et convertit WAV ou PCM brut en trames PCM jouables. */
internal class SpeechSegmentAudio(
    onAudio: (ByteArray) -> Unit,
    private val limit: Long = 24_000L * 2 * 180,
) {
    private val pcm = SpeechPcmDecoder(onAudio)
    private var bytes = 0L

    fun write(audio: ByteArray) {
        bytes += audio.size
        if (bytes > limit) throw SpeechFailure(SpeechErrorCode.PROTOCOL, "Invalid speech audio format or length")
        pcm.write(audio)
    }

    fun end(completed: Boolean) {
        if (!completed || bytes == 0L) throw SpeechFailure(SpeechErrorCode.PROTOCOL, "Incomplete speech audio. Try again.")
        pcm.end()
    }
}
