package io.github.mangi.eta.agent.majordome

import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AgentToolSchema
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Delegates a personal-life request to the homelab Majordome (Mastra), which routes it to its specialised agents
 * (Cuisinier for recipes, stock, shopping and meal planning; Secrétaire for the homelab agenda).
 */
internal object MajordomeTool {
    const val NAME = "ask_majordome"
    const val PATH = "/mobile/v1/assistant/messages"
    const val APPROVALS_PATH = "/mobile/v1/assistant/approvals"
    const val MAX_REQUEST_CHARS = 4_000
    private const val CALL_TIMEOUT_MS = 170_000L

    fun schema(): JSONObject = AgentToolSchema.function(
        name = NAME,
        description = "Delegate to the owner's homelab Majordome any request about the owner's household life that it manages: " +
            "cooking (recipe library including favourites and deleting recipes, what to cook with current stock, " +
            "fridge/freezer/cupboard stock and expiry dates, adding or using up products, the shopping list, planning meals " +
            "for a day or a whole week, adding a recipe's or a week's missing ingredients to the shopping list) and the homelab " +
            "agenda. The Majordome reads and writes these real data itself through its own agents; Jean-Calcul has no other " +
            "access to them. Send one complete request in French with every detail the user gave (dates, meals, servings, " +
            "quantities). The Majordome keeps a thread per Jean-Calcul conversation, so follow-up requests can be short. Relay " +
            "its answer faithfully; never claim an action it did not confirm. " +
            "Some writes (deleting a recipe, creating an agenda event) return APPROVAL_REQUIRED: nothing is done yet. Tell the " +
            "user exactly what pending_action will do and ask them to confirm, then end your turn. Only when the user explicitly " +
            "agrees in a later message, call this tool with decision=approve (no request); if they refuse, decision=decline. " +
            "Never approve on your own or in the same turn as the request.",
        parameters = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "request",
                        JSONObject()
                            .put("type", "string")
                            .put("minLength", 1)
                            .put("maxLength", MAX_REQUEST_CHARS)
                            .put("description", "The complete request for the Majordome, in French. Omit when sending a decision."),
                    )
                    .put(
                        "decision",
                        JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray().put("approve").put("decline"))
                            .put(
                                "description",
                                "The user's answer to the pending APPROVAL_REQUIRED action of this conversation. Use only after " +
                                    "the user explicitly answered; omit for a normal request.",
                            ),
                    ),
            ),
    )

    /** Mastra thread ids are bounded to `[A-Za-z0-9_-]{1,64}`; a digest keeps conversation keys private. */
    fun threadId(conversationKey: String): String = "jc-" + MessageDigest.getInstance("SHA-256")
        .digest(conversationKey.toByteArray(Charsets.UTF_8))
        .take(16)
        .joinToString("") { "%02x".format(it) }

    fun requestBody(threadId: String, request: String): String =
        JSONObject().put("threadId", threadId).put("message", request).toString()

    fun decisionBody(threadId: String, approvalId: String, approve: Boolean): String = JSONObject()
        .put("threadId", threadId)
        .put("approvalId", approvalId)
        .put("decision", if (approve) "APPROVE" else "DECLINE")
        .toString()

    /** Turns the Mastra reply (or failure) into the JSON tool result shown to the model. */
    fun result(raw: String): JSONObject {
        val json = JSONObject(raw)
        val status = json.optString("status")
        val text = json.optString("text").trim()
        return when (status) {
            "COMPLETED" -> ok(status).put("answer", text)
            "APPROVAL_REQUIRED" -> ok(status)
                .put("pending_action", json.optJSONObject("approval")?.optString("summary").orEmpty().ifBlank { text })
                .put(
                    "message",
                    "Nothing has been done yet. Ask the user to confirm this exact action and end your turn. Call " +
                        "ask_majordome with decision=approve only after they explicitly agree, or decision=decline if they refuse.",
                )
            else -> error("INVALID_RESPONSE", "The Majordome returned an unknown status")
        }
    }

    /** The pending approval carried by an APPROVAL_REQUIRED reply, if the server sent a usable one. */
    fun pendingApproval(raw: String): Pair<String, String>? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (json.optString("status") != "APPROVAL_REQUIRED") return null
        val approval = json.optJSONObject("approval") ?: return null
        val id = approval.optString("id").takeIf { it.startsWith("apr_") } ?: return null
        return id to approval.optString("summary")
    }

    fun failure(throwable: Throwable): JSONObject = when (throwable) {
        is MastraHttpException -> when {
            throwable.code == "APPROVAL_NOT_FOUND" || throwable.code == "APPROVAL_EXPIRED" ->
                error(throwable.code, "The pending action is no longer available (expired or homelab restarted); nothing was done. " +
                    "Ask the user whether to send the request again")
            throwable.status == 504 || throwable.code == "TIMEOUT" ->
                error("TIMEOUT", "The Majordome took too long to answer; the request may still have been partly applied")
            throwable.status == 401 || throwable.status == 403 ->
                error("NOT_AUTHORIZED", "The homelab refused this device; pair Jean-Calcul again in Settings > Homelab")
            throwable.status == 404 ->
                error("UNSUPPORTED", "The homelab does not expose this Majordome route yet")
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

    fun error(code: String, message: String): JSONObject =
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

/**
 * Approvals the Majordome is waiting for, per Majordome thread. Kept on the phone because Majordome results are
 * sensitive and never reach the saved transcript: the next turn cannot read the approval id. [issuedBy] is the run
 * executor that received it, so a model cannot approve in the turn that triggered the action, only after the user
 * replied. Lost when the app process dies (the homelab then expires the action).
 */
internal object MajordomePendingApprovals {
    data class Pending(val id: String, val summary: String, val issuedBy: Any)

    private val byThread = ConcurrentHashMap<String, Pending>()

    fun put(threadId: String, pending: Pending) { byThread[threadId] = pending }

    fun get(threadId: String): Pending? = byThread[threadId]

    fun remove(threadId: String, pending: Pending) { byThread.remove(threadId, pending) }

    fun clear(threadId: String) { byThread.remove(threadId) }
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
            val decision = args.optString("decision").trim().lowercase()
            when {
                decision.isNotEmpty() && request.isNotEmpty() ->
                    MajordomeTool.error("INVALID_ARGUMENT", "Send either request or decision, not both")
                decision.isNotEmpty() -> decide(decision)
                request.isEmpty() -> MajordomeTool.error("INVALID_ARGUMENT", "request is required")
                request.length > MajordomeTool.MAX_REQUEST_CHARS ->
                    MajordomeTool.error("INVALID_ARGUMENT", "request exceeds ${MajordomeTool.MAX_REQUEST_CHARS} characters")
                else -> {
                    val paired = pairing() ?: return@runCatching MajordomeTool.notPaired()
                    // The homelab declines an unanswered approval when the thread moves on; forget it here too.
                    MajordomePendingApprovals.clear(threadId)
                    reply(transport(paired).execute("POST", MajordomeTool.PATH, MajordomeTool.requestBody(threadId, request)))
                }
            }
        }.getOrElse(MajordomeTool::failure)
        // Household data from the homelab is personal: keep raw results out of persisted transcripts.
        return AgentModelClient.ToolResult(content.toString(), sensitive = true)
    }

    private fun decide(decision: String): JSONObject {
        val approve = when (decision) {
            "approve" -> true
            "decline" -> false
            else -> return MajordomeTool.error("INVALID_ARGUMENT", "decision must be approve or decline")
        }
        val pending = MajordomePendingApprovals.get(threadId)
            ?: return MajordomeTool.error("NO_PENDING_ACTION", "No Majordome action is waiting for approval in this conversation")
        if (approve && pending.issuedBy === this) {
            return MajordomeTool.error(
                "USER_CONFIRMATION_REQUIRED",
                "The user has not answered yet. Ask them to confirm « ${pending.summary} » and end your turn; approve only after their reply.",
            )
        }
        val paired = pairing() ?: return MajordomeTool.notPaired()
        MajordomePendingApprovals.remove(threadId, pending)
        return reply(transport(paired).execute("POST", MajordomeTool.APPROVALS_PATH,
            MajordomeTool.decisionBody(threadId, pending.id, approve)))
    }

    private fun reply(raw: String): JSONObject {
        MajordomeTool.pendingApproval(raw)?.let { (id, summary) ->
            MajordomePendingApprovals.put(threadId, MajordomePendingApprovals.Pending(id, summary, issuedBy = this))
        }
        return MajordomeTool.result(raw)
    }
}
