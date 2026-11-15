package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.MimoAsrConfig
import java.util.Base64
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun recognizeMimoSpeech(config: MimoAsrConfig, key: String, wav: ByteArray): String =
    mimoAsrTranscript(SpeechHttp.json(mimoAsrRequest(config, key, wav)))

internal fun mimoAsrRequest(config: MimoAsrConfig, key: String, wav: ByteArray): Request {
    val encodedBytes = 4 * ((wav.size.toLong() + 2) / 3)
    if (encodedBytes > MAX_MIMO_ASR_BASE64_BYTES) {
        throw SpeechFailure(SpeechErrorCode.AUDIO, "Recording exceeds the MiMo recognition size limit")
    }
    val audio = JSONObject().put("type", "input_audio").put(
        "input_audio", JSONObject().put("data", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav)),
    )
    val body = JSONObject()
        .put("model", "mimo-v2.5-asr")
        .put("stream", false)
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray().put(audio))))
        .put("asr_options", JSONObject().put("language", config.language.apiId))
    return SpeechHttp.jsonRequest(speechBaseUrl(config.baseUrl) + "/chat/completions", key, body).build()
}

internal fun mimoAsrTranscript(response: JSONObject): String {
    if (!response.isNull("error")) {
        throw SpeechFailure(SpeechErrorCode.SERVER, "MiMo speech recognition failed. Check service permissions and settings.")
    }
    val choice = response.optJSONArray("choices")?.optJSONObject(0)
        ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo returned no recognition result")
    when (choice.optString("finish_reason")) {
        "stop" -> Unit
        "length" -> throw SpeechFailure(SpeechErrorCode.SERVER, "MiMo recognition result is incomplete. Try a shorter recording.")
        "content_filter" -> throw SpeechFailure(SpeechErrorCode.SERVER, "MiMo could not process this recording. Try again.")
        else -> throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo did not finish speech recognition normally")
    }
    val text = choice.optJSONObject("message")?.opt("content") as? String
        ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo returned an invalid recognition result")
    return text.trim()
}

private const val MAX_MIMO_ASR_BASE64_BYTES = 10_000_000L
