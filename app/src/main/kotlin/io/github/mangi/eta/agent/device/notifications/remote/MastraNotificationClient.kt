package io.github.mangi.eta.agent.device.notifications.remote

import java.io.IOException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import okhttp3.OkHttpClient

/** Signed requests for notifications and the Now Brief summary. */
internal class MastraNotificationClient(
    pairing: MastraPairing,
    http: OkHttpClient = MastraSignedTransport.notificationHttp,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val transport = MastraSignedTransport(pairing, http, clock)

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

    suspend fun briefSummary(request: MastraBriefSummaryRequest): MastraBriefSummaryResponse {
        val body = MastraBriefSummaryContract.encodeRequest(request)
        return withTimeoutOrNull(BRIEF_TIMEOUT_MS) {
            MastraBriefSummaryContract.decodeResponse(
                transport.enqueue("POST", MastraBriefSummaryContract.PATH, body, MAX_BRIEF_BODY_BYTES),
            )
        } ?: throw IOException("Mastra brief summary timed out")
    }

    private fun <T> call(method: String, path: String, body: String?, serializer: KSerializer<T>): T =
        MastraContract.json.decodeFromString(serializer, callRaw(method, path, body))

    private fun callRaw(method: String, path: String, body: String?): String =
        transport.execute(method, path, body)

    companion object {
        private const val BRIEF_TIMEOUT_MS = 4_000L
        private const val MAX_BRIEF_BODY_BYTES = 16 * 1024L

        fun canonical(method: String, path: String, timestamp: String, nonce: String, body: String) =
            MastraSignedTransport.canonical(method, path, timestamp, nonce, body)

        fun sign(secret: String, method: String, path: String, timestamp: String, nonce: String, body: String) =
            MastraSignedTransport.sign(secret, method, path, timestamp, nonce, body)

        fun sha256(value: String) = MastraSignedTransport.sha256(value)
        fun opaqueId(prefix: String) = MastraSignedTransport.opaqueId(prefix)
    }
}
