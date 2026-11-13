package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ProviderConfig(val endpoint: String = "", val model: String = "", val local: Boolean = false) {
    fun validate(): Boolean {
        val url = endpoint.toHttpUrlOrNull() ?: return false
        // Secrets in URLs and plaintext remote credentials are never accepted.
        return url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            url.query == null && url.fragment == null && model.isNotBlank()
    }
    val destination: String get() = if (endpoint.isBlank()) "IA non configurée"
        else "${if (local) "Local déclaré" else "Distant"} · ${endpoint.toHttpUrlOrNull()?.host.orEmpty()}"
}

/** OpenAI chat-completions SSE, also supported by local servers (e.g. llama.cpp). */
class OpenAiProvider(
    private val config: ProviderConfig,
    private val credential: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES).build(),
) : ConversationProvider {
    @Volatile private var call: Call? = null
    @Volatile private var cancelled = false
    override fun cancel() { cancelled = true; call?.cancel() }
    override suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit) {
        if (turn(messages, onText).isNotEmpty()) throw RunFailure(RunError.TOOL_POLICY)
    }
    override suspend fun turn(messages: List<ChatMessage>, onText: suspend (String) -> Unit): List<ToolCall> = withContext(Dispatchers.IO) {
        if (!config.validate()) throw RunFailure(RunError.CONFIGURATION)
        val history = JSONArray()
        messages.forEach { message ->
            val item = JSONObject().put("role", message.role).put("content", message.text)
            if (message.toolCallId.isNotEmpty()) item.put("tool_call_id", message.toolCallId)
            if (message.toolCalls.isNotEmpty()) item.put("tool_calls", JSONArray().apply {
                message.toolCalls.forEach { call -> put(JSONObject().put("id", call.id).put("type", "function")
                    .put("function", JSONObject().put("name", call.name).put("arguments", call.arguments))) }
            })
            history.put(item)
        }
        val tool = JSONObject().put("type", "function").put("function", JSONObject()
            .put("name", "current_time").put("description", "Read the current UTC time. No other device access.")
            .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false)))
        val body = JSONObject().put("model", config.model).put("stream", true).put("messages", history)
            .put("tools", JSONArray().put(tool))
        val builder = Request.Builder().url(config.endpoint.trimEnd('/') + "/chat/completions")
            .header("Accept", "text/event-stream").post(body.toString().toRequestBody("application/json".toMediaType()))
        val key = credential()
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        if (cancelled) throw kotlinx.coroutines.CancellationException()
        val requestCall = client.newCall(builder.build())
        call = requestCall
        if (cancelled) requestCall.cancel()
        try {
            requestCall.execute().use { response ->
                if (!response.isSuccessful) throw RunFailure(when (response.code) {
                    401, 403 -> RunError.AUTHENTICATION
                    429 -> RunError.RATE_LIMIT
                    else -> RunError.PROVIDER
                })
                if (response.header("Content-Type")?.startsWith("text/event-stream") != true) throw RunFailure(RunError.PROTOCOL)
                val source = response.body?.source() ?: throw RunFailure(RunError.PROTOCOL)
                var finished = false
                var done = false
                var content = false
                var toolFinish = false
                val calls = sortedMapOf<Int, ToolCall>()
                val data = StringBuilder()
                suspend fun frame() {
                    if (data.isEmpty()) return
                    val payload = data.toString(); data.clear()
                    if (payload == "[DONE]") { done = true; return }
                    val json = try { JSONObject(payload) } catch (_: Exception) { throw RunFailure(RunError.PROTOCOL) }
                    if (json.has("error")) throw RunFailure(RunError.PROVIDER)
                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return
                    val delta = choice.optJSONObject("delta")
                    if (delta?.has("function_call") == true) throw RunFailure(RunError.TOOL_POLICY)
                    delta?.optJSONArray("tool_calls")?.let { fragments ->
                        for (index in 0 until fragments.length()) {
                            val fragment = fragments.getJSONObject(index)
                            val position = fragment.getInt("index")
                            if (position !in 0..7) throw RunFailure(RunError.PROTOCOL)
                            val old = calls[position] ?: ToolCall("", "", "")
                            val function = fragment.optJSONObject("function")
                            val next = ToolCall(old.id + fragment.optString("id", ""),
                                old.name + function?.optString("name", "").orEmpty(),
                                old.arguments + function?.optString("arguments", "").orEmpty())
                            if (next.arguments.length > 16_384 || next.name.length > 128 || next.id.length > 128) throw RunFailure(RunError.PROTOCOL)
                            calls[position] = next
                        }
                    }
                    val text = delta?.optString("content", "").orEmpty()
                    if (text.isNotEmpty()) { content = true; onText(text) }
                    if (!choice.isNull("finish_reason")) {
                        toolFinish = choice.optString("finish_reason") == "tool_calls"
                        if (choice.optString("finish_reason") !in setOf("stop", "tool_calls")) throw RunFailure(RunError.PROTOCOL)
                        finished = true
                    }
                }
                while (!done) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) frame()
                    else if (line.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).removePrefix(" "))
                        if (data.length > 1_048_576) throw RunFailure(RunError.PROTOCOL)
                    }
                }
                frame()
                if (!finished || !done || (!content && calls.isEmpty())) throw RunFailure(RunError.PROTOCOL)
                if (toolFinish != calls.isNotEmpty() || calls.values.any { it.id.isBlank() || it.name.isBlank() } ||
                    calls.values.map { it.id }.distinct().size != calls.size) throw RunFailure(RunError.PROTOCOL)
                calls.values.toList()
            }
        } catch (_: org.json.JSONException) {
            throw RunFailure(RunError.PROTOCOL)
        } catch (failure: IOException) {
            if (cancelled) throw kotlinx.coroutines.CancellationException()
            throw RunFailure(RunError.NETWORK)
        } finally { call = null }
    }
}
