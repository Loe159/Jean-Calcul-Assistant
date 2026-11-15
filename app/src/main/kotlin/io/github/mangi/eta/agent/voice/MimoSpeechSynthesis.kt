package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.MimoTtsConfig
import java.util.Base64
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal suspend fun synthesizeMimo(
    config: MimoTtsConfig,
    key: String,
    text: String,
    onAudio: (ByteArray) -> Unit,
) {
    SpeechHttp.read(mimoTtsRequest(config, key, text)) { response ->
        val decoder = MimoTtsDecoder(onAudio)
        readSpeechEvents(response) { _, data -> decoder.event(data) }
        decoder.end()
    }
}

internal fun mimoTtsRequest(config: MimoTtsConfig, key: String, text: String): Request {
    val messages = JSONArray()
    if (config.style.isNotBlank()) {
        messages.put(JSONObject().put("role", "user").put("content", config.style.trim()))
    }
    // MiMo 将 assistant 内容作为朗读正文，user 内容只控制风格。
    messages.put(JSONObject().put("role", "assistant").put("content", text))
    return SpeechHttp.jsonRequest(
        speechBaseUrl(config.baseUrl) + "/chat/completions",
        key,
        JSONObject().put("model", "mimo-v2.5-tts").put("messages", messages).put("stream", true)
            .put("audio", JSONObject().put("format", "pcm16").put("voice", config.voice.trim())),
    ).header("Accept", "text/event-stream").build()
}

internal class MimoTtsDecoder(onAudio: (ByteArray) -> Unit) {
    private val audio = SpeechSegmentAudio(onAudio)
    private var completed = false

    fun event(value: String): Boolean {
        if (completed || value == "[DONE]") return false
        val message = JSONObject(value)
        if (!message.isNull("error")) {
            throw SpeechFailure(SpeechErrorCode.SERVER, "MiMo speech synthesis failed. Check service permissions and settings.")
        }
        val choices = message.optionalField<JSONArray>("choices")
            ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo returned no audio data")
        if (choices.length() == 0) return true
        val choice = choices.optJSONObject(0)
            ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo returned invalid audio data")
        val finishReason = choice.optionalField<String>("finish_reason")
        when (finishReason) {
            null, "stop" -> Unit
            "length" -> throw SpeechFailure(SpeechErrorCode.SERVER, "MiMo speech synthesis is incomplete. Try shorter text.")
            "content_filter" -> throw SpeechFailure(SpeechErrorCode.SERVER, "MiMo could not synthesize this text. Adjust it and retry.")
            else -> throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo returned an unknown speech status")
        }
        // 音频标识等元数据不代表音频增量，终止事件也不一定携带 delta。
        val encoded = choice.optionalField<JSONObject>("delta")
            ?.optionalField<JSONObject>("audio")
            ?.optionalField<String>("data")
        if (!encoded.isNullOrEmpty()) {
            val bytes = try {
                Base64.getDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                throw SpeechFailure(SpeechErrorCode.PROTOCOL, "Invalid MiMo audio encoding")
            }
            audio.write(bytes)
        }
        completed = finishReason == "stop"
        return !completed
    }

    fun end() = audio.end(completed)
}

private inline fun <reified T : Any> JSONObject.optionalField(name: String): T? {
    if (isNull(name)) return null
    return opt(name) as? T
        ?: throw SpeechFailure(SpeechErrorCode.PROTOCOL, "MiMo returned invalid audio data")
}
