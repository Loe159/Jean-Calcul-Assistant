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
    override suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        if (!config.validate()) throw RunFailure(RunError.CONFIGURATION)
        val history = JSONArray()
        messages.forEach { history.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject().put("model", config.model).put("stream", true).put("messages", history)
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
                val data = StringBuilder()
                suspend fun frame() {
                    if (data.isEmpty()) return
                    val payload = data.toString(); data.clear()
                    if (payload == "[DONE]") { done = true; return }
                    val json = try { JSONObject(payload) } catch (_: Exception) { throw RunFailure(RunError.PROTOCOL) }
                    if (json.has("error")) throw RunFailure(RunError.PROVIDER)
                    val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return
                    val delta = choice.optJSONObject("delta")
                    if (delta?.has("tool_calls") == true || delta?.has("function_call") == true) {
                        // No tool schema is advertised; unsolicited calls still pass the deny policy.
                        check(!AndroidToolPolicy.permits("unregistered"))
                        throw RunFailure(RunError.TOOL_POLICY)
                    }
                    val text = delta?.optString("content", "").orEmpty()
                    if (text.isNotEmpty()) { content = true; onText(text) }
                    if (!choice.isNull("finish_reason")) {
                        if (choice.optString("finish_reason") != "stop") throw RunFailure(RunError.PROTOCOL)
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
                if (!finished || !done || !content) throw RunFailure(RunError.PROTOCOL)
            }
        } catch (failure: IOException) {
            if (cancelled) throw kotlinx.coroutines.CancellationException()
            throw RunFailure(RunError.NETWORK)
        } finally { call = null }
    }
}
