package io.github.mangi.eta.agent.device.notifications.remote

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class MastraHttpException(val status: Int, val code: String?) : Exception("HTTP $status ${code.orEmpty()}") {
    /** 4xx other than 408/429 will fail again: do not retry the same payload. */
    val permanent: Boolean get() = status in 400..499 && status != 408 && status != 429 && status != 401
}

/** Signed requests to the private homelab API. Never logs bodies, which contain notification text. */
internal class MastraNotificationClient(
    private val pairing: MastraPairing,
    private val http: OkHttpClient = defaultClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun health(): HealthResponse = call("GET", "/mobile/v1/notifications/health", null, HealthResponse.serializer())

    fun analyze(request: AnalyzeRequest): AnalyzeResponse =
        call("POST", "/mobile/v1/notifications/analyze",
            MastraContract.json.encodeToString(AnalyzeRequest.serializer(), request), AnalyzeResponse.serializer())

    fun feedback(request: FeedbackRequest): FeedbackResponse =
        call("POST", "/mobile/v1/notifications/feedback",
            MastraContract.json.encodeToString(FeedbackRequest.serializer(), request), FeedbackResponse.serializer())

    fun actions(): ActionList = call("GET", "/mobile/v1/actions", null, ActionList.serializer())

    fun command(actionId: String, command: ActionCommand): ActionRecord {
        require(Regex("^act_[a-f0-9]{24}$").matches(actionId))
        return call("POST", "/mobile/v1/actions/$actionId",
            MastraContract.json.encodeToString(ActionCommand.serializer(), command), ActionRecord.serializer())
    }

    /** Raw JSON: preferences are displayed, not interpreted, by the phone. */
    fun preferences(): String = callRaw("GET", "/mobile/v1/notifications/preferences", null)

    fun deletePreference(id: String) {
        require(Regex("^pref_[a-z0-9]{8,40}$").matches(id))
        callRaw("DELETE", "/mobile/v1/notifications/preferences/$id", null)
    }

    fun resetLearning(includeUserPreferences: Boolean) {
        callRaw("POST", "/mobile/v1/notifications/learning/reset", "{\"includeUserPreferences\":$includeUserPreferences}")
    }

    private fun <T> call(method: String, path: String, body: String?, serializer: KSerializer<T>): T =
        MastraContract.json.decodeFromString(serializer, callRaw(method, path, body))

    private fun callRaw(method: String, path: String, body: String?): String {
        val payload = body.orEmpty()
        val timestamp = clock().toString()
        val nonce = nonce()
        val request = Request.Builder().url(pairing.baseUrl + path)
            .header("X-JC-Device", pairing.deviceId)
            .header("X-JC-Timestamp", timestamp)
            .header("X-JC-Nonce", nonce)
            .header("X-JC-Signature", sign(pairing.secret, method, path, timestamp, nonce, payload))
            .method(method, if (body == null) null else payload.toRequestBody(JSON_TYPE))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val code = Regex("\"error\"\\s*:\\s*\"([A-Z_]{2,40})\"").find(text)?.groupValues?.get(1)
                throw MastraHttpException(response.code, code)
            }
            return text
        }
    }

    companion object {
        private val JSON_TYPE = "application/json".toMediaType()
        private val random = SecureRandom()
        private val defaultClient: OkHttpClient by lazy {
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
