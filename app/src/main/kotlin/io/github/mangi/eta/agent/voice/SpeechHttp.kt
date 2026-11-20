package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.agent.model.AgentHttpClient
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

internal object SpeechHttp {
    val client = AgentHttpClient.client.newBuilder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    /** La clé est facultative (vLLM sans `--api-key`) : aucun en-tête d’autorisation n’est envoyé si elle est vide. */
    fun authorizedPost(url: String, apiKey: String, body: RequestBody): Request.Builder = Request.Builder()
        .url(url)
        .apply { apiKey.trim().takeIf(String::isNotEmpty)?.let { header("Authorization", "Bearer $it") } }
        .post(body)

    fun jsonRequest(url: String, apiKey: String, body: JSONObject): Request.Builder =
        authorizedPost(url, apiKey, body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

    suspend fun json(request: Request): JSONObject = read(request) { response ->
        val source = response.body.source()
        if (source.request(MAX_JSON_BYTES + 1)) throw SpeechFailure(SpeechErrorCode.PROTOCOL, "Speech service response is too large")
        JSONObject(source.readUtf8())
    }

    /** La lecture de la réponse reste couverte par l’annulation, pas seulement l’établissement de la connexion. */
    suspend fun <T> read(request: Request, consume: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!continuation.isActive) return
                        checkStatus(response.code)
                        consume(response)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    fun checkStatus(status: Int) {
        if (status in 200..299) return
        throw when (status) {
            401, 403 -> SpeechFailure(SpeechErrorCode.AUTHENTICATION, "Speech service authentication failed. Check the API key.")
            404 -> SpeechFailure(SpeechErrorCode.CONFIGURATION, "Speech endpoint not found. Check the server address (…/v1) and that the model supports audio.")
            429 -> SpeechFailure(SpeechErrorCode.RATE_LIMITED, "Speech service rate limit reached. Try again later.")
            else -> SpeechFailure(SpeechErrorCode.SERVER, "Speech service request failed (HTTP $status)")
        }
    }

    const val MAX_JSON_BYTES = 2L * 1024 * 1024
}
