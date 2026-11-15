package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.DeepSeekAsrConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal suspend fun recognizeDeepSeekSpeech(
    config: DeepSeekAsrConfig,
    key: String,
    wav: ByteArray,
): String = deepSeekAsrTranscript(SpeechHttp.json(deepSeekAsrRequest(config, key, wav)))

internal fun deepSeekAsrRequest(
    config: DeepSeekAsrConfig,
    key: String,
    wav: ByteArray,
): Request {
    if (wav.size > MAX_DEEPSEEK_ASR_BYTES) {
        throw SpeechFailure(SpeechErrorCode.AUDIO, "Recording exceeds the DeepSeek recognition limit of 8 MiB")
    }
    val body = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            "speech.wav",
            wav.toRequestBody("audio/wav".toMediaType()),
        )
        .build()
    return Request.Builder()
        .url(speechBaseUrl(config.baseUrl) + "/v1/tools/asr")
        .header("Authorization", "Bearer ${key.trim()}")
        .post(body)
        .build()
}

internal fun deepSeekAsrTranscript(response: JSONObject): String {
    if (!response.isNull("error")) {
        throw SpeechFailure(SpeechErrorCode.SERVER, "DeepSeek recognition failed. Check permissions and settings.")
    }
    val text = response.opt("text") as? String
        ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "DeepSeek returned no valid recognition result")
    return text.trim()
}

private const val MAX_DEEPSEEK_ASR_BYTES = 8 * 1024 * 1024
