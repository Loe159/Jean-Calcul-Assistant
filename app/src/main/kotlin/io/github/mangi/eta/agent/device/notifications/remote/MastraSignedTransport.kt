package io.github.mangi.eta.agent.device.notifications.remote

import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class MastraHttpException(val status: Int, val code: String?) : Exception("HTTP $status ${code.orEmpty()}") {
    /** 4xx other than 408/429 will fail again: do not retry the same payload. */
    val permanent: Boolean get() = status in 400..499 && status != 408 && status != 429 && status != 401
}

/** Signs the exact encoded path and UTF-8 body sent to the private homelab API. */
internal class MastraSignedTransport(
    private val pairing: MastraPairing,
    http: OkHttpClient = notificationHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val http = if (!http.followRedirects && !http.followSslRedirects) http else
        http.newBuilder().followRedirects(false).followSslRedirects(false).build()

    fun execute(method: String, path: String, body: String? = null): String =
        http.newCall(signedRequest(method, path, body)).execute().use { readResponse(it) }

    suspend fun enqueue(
        method: String,
        path: String,
        body: String? = null,
        maxBodyBytes: Long? = null,
        timeoutMillis: Long? = null,
    ): String {
        val call = http.newCall(signedRequest(method, path, body))
        if (timeoutMillis != null) call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    try {
                        continuation.resume(response.use { readResponse(it, maxBodyBytes) })
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private fun signedRequest(method: String, path: String, body: String?): Request {
        require(method in methods)
        require(path.startsWith('/') && '?' !in path && '#' !in path)
        val timestamp = clock().toString()
        val nonce = nonce()
        val payload = body.orEmpty()
        val requestBody = when {
            body != null -> payload.toRequestBody(JSON_TYPE)
            method == "POST" || method == "PUT" || method == "PATCH" -> payload.toRequestBody()
            else -> null
        }
        val request = Request.Builder().url(pairing.baseUrl + path)
            .header("X-JC-Device", pairing.deviceId)
            .header("X-JC-Timestamp", timestamp)
            .header("X-JC-Nonce", nonce)
            .header("X-JC-Signature", sign(pairing.secret, method, path, timestamp, nonce, payload))
            .method(method, requestBody)
            .build()
        require(request.url.encodedPath == path && request.url.query == null)
        return request
    }

    private fun readResponse(response: Response, maxBodyBytes: Long? = null): String {
        if (maxBodyBytes != null && response.body.source().request(maxBodyBytes + 1)) {
            throw IOException("Mastra response is too large")
        }
        val text = response.body.string()
        if (!response.isSuccessful) {
            val code = Regex("\"error\"\\s*:\\s*\"([A-Z_]{2,40})\"").find(text)?.groupValues?.get(1)
            throw MastraHttpException(response.code, code)
        }
        return text
    }

    companion object {
        private val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE")
        private val JSON_TYPE = "application/json".toMediaType()
        private val random = SecureRandom()
        val notificationHttp: OkHttpClient by lazy {
            OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        }

        fun canonical(method: String, path: String, timestamp: String, nonce: String, body: String): String =
            listOf("JC1", method.uppercase(), path, timestamp, nonce, sha256(body)).joinToString("\n")

        fun sign(secret: String, method: String, path: String, timestamp: String, nonce: String, body: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(Base64.getUrlDecoder().decode(secret), "HmacSHA256"))
            return mac.doFinal(canonical(method, path, timestamp, nonce, body).toByteArray(Charsets.UTF_8)).hex()
        }

        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).hex()

        fun nonce(): String = ByteArray(16).also(random::nextBytes).hex()

        /** Opaque identifiers for events and batches. */
        fun opaqueId(prefix: String): String =
            prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(15).also(random::nextBytes))

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
    }
}
