package io.github.mangi.eta.agent.majordome

import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentToolSchema
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.json.JSONException
import org.json.JSONObject

/**
 * Delegates a personal-life request to the homelab Majordome (Mastra), which routes it to its specialised agents
 * (Cuisinier for recipes, stock, shopping and meal planning; Secrétaire for the homelab agenda).
 */
internal object MajordomeTool {
    const val NAME = "ask_majordome"
    const val PATH = "/mobile/v1/assistant/messages"
    const val MAX_REQUEST_CHARS = 4_000
    private const val CALL_TIMEOUT_MS = 170_000L

    fun schema(): JSONObject = AgentToolSchema.function(
        name = NAME,
        description = "Delegate to the owner's homelab Majordome any request about the owner's household life that it manages: " +
            "cooking (recipe library, what to cook with current stock, fridge/freezer/cupboard stock and expiry dates, " +
            "adding or using up products, the shopping list, planning meals for a day or a whole week, adding a recipe's or a " +
            "week's missing ingredients to the shopping list) and the homelab agenda. The Majordome reads and writes these data " +
            "itself through its own agents; Jean-Calcul has no other access to them. Send one complete request in French with " +
            "every detail the user gave (dates, meals, servings, quantities). The Majordome keeps a thread per Jean-Calcul " +
            "conversation, so follow-up requests can be short. Relay its answer faithfully; never claim an action it did not " +
            "confirm. On APPROVAL_REQUIRED, tell the user the action was not done.",
        parameters = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "request",
                    JSONObject()
                        .put("type", "string")
                        .put("minLength", 1)
                        .put("maxLength", MAX_REQUEST_CHARS)
                        .put("description", "The complete request for the Majordome, in French."),
                ),
            )
            .put("required", org.json.JSONArray().put("request")),
    )

    /** Mastra thread ids are bounded to `[A-Za-z0-9_-]{1,64}`; a digest keeps conversation keys private. */
    fun threadId(conversationKey: String): String = "jc-" + MessageDigest.getInstance("SHA-256")
        .digest(conversationKey.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it) }

    fun requestBody(threadId: String, request: String): String =
        JSONObject().put("threadId", threadId).put("message", request).toString()

    /** Turns the Mastra reply (or failure) into the JSON tool result shown to the model. */
    fun result(raw: String): JSONObject {
        val json = JSONObject(raw)
        val status = json.optString("status")
        val text = json.optString("text").trim()
        return when (status) {
            "COMPLETED" -> ok(status).put("answer", text)
            "APPROVAL_REQUIRED" -> ok(status)
                .put("answer", text)
                .put("pending_action", json.optJSONObject("approval")?.optString("summary").orEmpty())
                .put("message", "This action needs an approval that is not available from Jean-Calcul; it was not carried out.")
            else -> error("INVALID_RESPONSE", "The Majordome returned an unknown status")
        }
    }

    fun failure(throwable: Throwable): JSONObject = when (throwable) {
        is MastraHttpException -> when {
            throwable.status == 504 || throwable.code == "TIMEOUT" ->
                error("TIMEOUT", "The Majordome took too long to answer; the request may still have been partly applied")
            throwable.status == 401 || throwable.status == 403 ->
                error("NOT_AUTHORIZED", "The homelab refused this device; pair Jean-Calcul again in Settings > Homelab")
            throwable.status == 404 ->
                error("UNSUPPORTED", "The homelab does not expose the Majordome route yet")
            throwable.status == 429 -> error("RATE_LIMITED", "Too many requests to the homelab; try again shortly")
            throwable.status in 500..599 ->
                error(throwable.code ?: "HOMELAB_ERROR", "The Majordome is unavailable (HTTP ${throwable.status})")
            else -> error(throwable.code ?: "HTTP_${throwable.status}", "The Majordome rejected the request")
        }
        is InterruptedIOException ->
            error("TIMEOUT", "The Majordome took too long to answer; the request may still have been partly applied")
        is IOException -> error("OFFLINE", "The homelab is unreachable (Tailscale or network)")
        is JSONException -> error("INVALID_RESPONSE", "The Majordome returned an unreadable answer")
        else -> error("TOOL_ERROR", throwable.message ?: throwable.javaClass.simpleName)
    }

    fun notPaired(): JSONObject =
        error("NOT_PAIRED", "Jean-Calcul is not paired with the homelab, or the homelab connection is disabled in Settings > Homelab")

    private fun ok(status: String) = JSONObject().put("ok", true).put("tool", NAME).put("status", status)

    private fun error(code: String, message: String) =
        JSONObject().put("ok", false).put("tool", NAME).put("code", code).put("message", message)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}

/** Run-scoped executor: one Majordome thread per Jean-Calcul conversation. */
internal class MajordomeToolExecutor(
    conversationKey: String,
    private val pairing: () -> MastraPairing?,
    private val transport: (MastraPairing) -> MastraSignedTransport = { MastraSignedTransport(it, MajordomeTool.http) },
) {
    private val threadId = MajordomeTool.threadId(conversationKey)

    fun available(): Boolean = pairing() != null

    fun execute(call: AgentModelClient.ToolCall): AgentModelClient.ToolResult {
        val content = runCatching {
            val args = JSONObject(call.argumentsJson.ifBlank { "{}" })
            val request = args.optString("request").trim()
            when {
                request.isEmpty() -> JSONObject().put("ok", false).put("tool", MajordomeTool.NAME)
                    .put("code", "INVALID_ARGUMENT").put("message", "request is required")
                request.length > MajordomeTool.MAX_REQUEST_CHARS -> JSONObject().put("ok", false)
                    .put("tool", MajordomeTool.NAME).put("code", "INVALID_ARGUMENT")
                    .put("message", "request exceeds ${MajordomeTool.MAX_REQUEST_CHARS} characters")
                else -> {
                    val paired = pairing() ?: return@runCatching MajordomeTool.notPaired()
                    val raw = transport(paired).execute("POST", MajordomeTool.PATH, MajordomeTool.requestBody(threadId, request))
                    MajordomeTool.result(raw)
                }
            }
        }.getOrElse(MajordomeTool::failure)
        // Household data from the homelab is personal: keep raw results out of persisted transcripts.
        return AgentModelClient.ToolResult(content.toString(), sensitive = true)
    }
}
