package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Official ChatGPT-plan route: public Responses API, store=false, HTTP/SSE only. */
internal class ChatGptProvider(private val accounts: ChatGptAccounts, private val accountId: String) : ConversationProvider {
    @Volatile private var cancelled = false
    @Volatile private var call: Call? = null
    override fun cancel() { cancelled = true; call?.cancel() }
    override suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit) {
        if (turn(messages, onText).isNotEmpty()) throw RunFailure(RunError.TOOL_POLICY)
    }
    override suspend fun turn(messages: List<ChatMessage>, onText: suspend (String) -> Unit): List<ToolCall> = withContext(Dispatchers.IO) {
        val account = accounts.credentials(accountId)
        val token = account.tokens ?: authFailed()
        val owner = "$accountId:${account.model}"
        val input = input(messages, owner)
        val tool = JSONObject().put("type", "function").put("name", "current_time")
            .put("description", "Read the current UTC time. No other device access.")
            .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()).put("additionalProperties", false))
        val body = JSONObject().put("model", account.model).put("input", input)
            .put("store", false).put("stream", true).put("tools", JSONArray().put(tool))
            .put("include", JSONArray().put("reasoning.encrypted_content"))
        if (cancelled) throw CancellationException()
        val request = Request.Builder().url("${accounts.oauth.endpoints.api}/responses")
            .header("Authorization", "Bearer ${token.access}").header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val current = accounts.oauth.http.newBuilder().readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(5, java.util.concurrent.TimeUnit.MINUTES).build().newCall(request); call = current
        if (cancelled) current.cancel()
        try {
            current.execute().use { response ->
                if (!response.isSuccessful) throw RunFailure(when (response.code) {
                    401 -> RunError.CHATGPT_AUTH
                    403 -> RunError.CHATGPT_PERMISSION
                    429 -> RunError.RATE_LIMIT
                    else -> RunError.PROVIDER
                })
                if (response.header("Content-Type")?.startsWith("text/event-stream") != true) throw RunFailure(RunError.PROTOCOL)
                val source = response.body?.source() ?: throw RunFailure(RunError.PROTOCOL)
                val data = StringBuilder()
                var completed: JSONObject? = null
                var emitted = false
                val streamedText = StringBuilder()
                suspend fun frame() {
                    if (data.isEmpty()) return
                    val json = JSONObject(data.toString()); data.clear()
                    when (json.optString("type")) {
                        "response.output_text.delta" -> {
                            val delta = json.getString("delta")
                            if (delta.isNotEmpty()) {
                                streamedText.append(delta)
                                if (streamedText.length > 2_097_152) throw RunFailure(RunError.PROTOCOL)
                                emitted = true; onText(delta)
                            }
                        }
                        "response.completed" -> {
                            val result = json.getJSONObject("response")
                            if (result.optString("status") != "completed") throw RunFailure(RunError.PROTOCOL)
                            completed = result
                        }
                        "error", "response.failed" -> {
                            val code = json.optJSONObject("response")?.optJSONObject("error")?.optString("code")
                                ?: json.optString("code")
                            throw RunFailure(when (code) {
                                "subscription_sharing_usage_limit_exceeded", "rate_limit_exceeded" -> RunError.RATE_LIMIT
                                "invalid_api_key", "invalid_token", "authentication_error" -> RunError.CHATGPT_AUTH
                                else -> RunError.PROVIDER
                            })
                        }
                        "response.incomplete" -> throw RunFailure(RunError.PROTOCOL)
                        "response.refusal.done" -> throw RunFailure(RunError.PROVIDER)
                    }
                }
                while (completed == null) {
                    val line = source.readUtf8LineStrict(1_048_576)
                    if (line.isEmpty()) frame()
                    else if (line.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).removePrefix(" "))
                        if (data.length > 1_048_576) throw RunFailure(RunError.PROTOCOL)
                    }
                }
                val output = completed.getJSONArray("output")
                val results = mutableListOf<ToolCall>()
                var finalText = ""
                for (index in 0 until output.length()) {
                    val item = output.getJSONObject(index)
                    when (item.getString("type")) {
                        "function_call" -> {
                            val id = item.getString("call_id"); val name = item.getString("name"); val args = item.getString("arguments")
                            if (id.isBlank() || id.length > 128 || name.isBlank() || name.length > 128 || args.length > 16_384 || results.size >= 8) throw RunFailure(RunError.PROTOCOL)
                            results += ToolCall(id, name, args)
                        }
                        "message" -> {
                            val contents = item.getJSONArray("content")
                            for (i in 0 until contents.length()) {
                                val part = contents.getJSONObject(i)
                                if (part.optString("type") == "output_text") finalText += part.getString("text")
                                if (part.optString("type") == "refusal") throw RunFailure(RunError.PROVIDER)
                            }
                        }
                        "reasoning" -> Unit
                        else -> throw RunFailure(RunError.PROTOCOL)
                    }
                }
                if (results.map { it.id }.distinct().size != results.size) throw RunFailure(RunError.PROTOCOL)
                if (emitted && streamedText.toString() != finalText) throw RunFailure(RunError.PROTOCOL)
                if (!emitted && finalText.isNotEmpty()) onText(finalText)
                if (finalText.isEmpty() && results.isEmpty()) throw RunFailure(RunError.PROTOCOL)
                // Preserve reasoning and function-call items for stateless continuation after tools.
                if (results.isNotEmpty()) results[0] = results[0].copy(continuation = output.toString(), continuationOwner = owner)
                results
            }
        } catch (_: org.json.JSONException) {
            throw RunFailure(RunError.PROTOCOL)
        } catch (_: java.io.EOFException) {
            if (cancelled) throw CancellationException()
            throw RunFailure(RunError.PROTOCOL)
        } catch (_: IOException) {
            if (cancelled) throw CancellationException()
            throw RunFailure(RunError.NETWORK)
        } finally { call = null }
    }
    companion object {
        internal fun input(messages: List<ChatMessage>, owner: String = ""): JSONArray {
            val input = JSONArray()
            messages.forEach { message ->
                if (message.role == "tool") {
                    input.put(JSONObject().put("type", "function_call_output").put("call_id", message.toolCallId).put("output", message.text))
                } else if (message.toolCalls.firstOrNull()?.let { it.continuation.isNotBlank() && it.continuationOwner == owner } == true) {
                    val output = JSONArray(message.toolCalls.first().continuation)
                    for (index in 0 until output.length()) input.put(output.getJSONObject(index))
                } else {
                    if (message.text.isNotEmpty()) input.put(JSONObject().put("role", message.role).put("content", message.text))
                    message.toolCalls.forEach { call -> input.put(JSONObject().put("type", "function_call")
                        .put("call_id", call.id).put("name", call.name).put("arguments", call.arguments)) }
                }
            }
            return input
        }
    }
}
