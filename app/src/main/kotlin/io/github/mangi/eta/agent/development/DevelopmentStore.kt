package io.github.mangi.eta.agent.development

import android.content.Context
import io.github.mangi.eta.data.repository.McpSecretStore
import org.json.JSONObject

/** Reuses the existing Android Keystore credential store; credentials never enter model tools. */
internal interface DevelopmentState {
    val enabled: Boolean
    fun token(): String?
    fun proposal(): JSONObject?
    fun saveProposal(proposal: JSONObject)
    fun setQuestionPending(pending: Boolean)
    fun markRunUsed(runId: String)
    fun markQuestionRun(runId: String)
}
internal class DevelopmentStore(context: Context) : DevelopmentState {
    private val prefs = context.applicationContext.getSharedPreferences("jeancalcul_development", Context.MODE_PRIVATE)
    private val secrets = McpSecretStore(context)
    override val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    override fun token(): String? = secrets.bearerToken("jeancalcul-development")
    fun connect(token: String) {
        require(token.trim().length in 20..512) { "Enter a valid GitHub fine-grained token" }
        secrets.setBearerToken("jeancalcul-development", token)
        check(prefs.edit().putBoolean("enabled", true).commit()) { "Could not enable app improvements" }
    }
    fun disconnect() {
        secrets.clear("jeancalcul-development")
        check(prefs.edit().putBoolean("enabled", false).remove("conversation_id")
            .remove("conversation_key").remove("question_pending").remove("question_run").commit()) { "Could not disconnect GitHub" }
    }
    fun authorizeConversation(id: String, key: String) {
        require(id.isNotBlank() && key.isNotBlank() && enabled)
        check(prefs.edit().putString("conversation_id", id).putString("conversation_key", key)
            .putBoolean("question_pending", false).commit()) { "Could not save improvement conversation" }
    }
    fun mayContinue(id: String): Boolean = enabled && id.isNotBlank() &&
        prefs.getString("conversation_id", null) == id &&
        (prefs.getBoolean("question_pending", false) || proposal()?.let { !it.optBoolean("closed") && !it.optBoolean("merged") } == true)

    fun questionPending(): Boolean = prefs.getBoolean("question_pending", false)
    override fun setQuestionPending(pending: Boolean) {
        check(prefs.edit().putBoolean("question_pending", pending).commit()) { "Could not save question state" }
    }
    override fun markRunUsed(runId: String) {
        prefs.edit().putString("last_development_run", runId).apply()
    }
    override fun markQuestionRun(runId: String) {
        prefs.edit().putString("question_run", runId).apply()
    }
    fun questionRunId(): String? = prefs.getString("question_run", null)
    fun wasRunUsed(runId: String): Boolean = prefs.getString("last_development_run", null) == runId
    fun conversationKey(): String? = prefs.getString("conversation_key", null)
    fun clearConversationIfIdle(id: String) {
        if (prefs.getString("conversation_id", null) != id || questionPending()) return
        if (proposal()?.let { !it.optBoolean("closed") && !it.optBoolean("merged") } == true) return
        prefs.edit().remove("conversation_id").remove("conversation_key").apply()
    }
    fun revokeConversation(id: String) {
        if (prefs.getString("conversation_id", null) == id) {
            check(prefs.edit().remove("conversation_id").remove("conversation_key")
                .remove("question_pending").remove("question_run").commit()) { "Could not revoke improvement conversation" }
        }
    }
    override fun proposal(): JSONObject? = prefs.getString("proposal", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    override fun saveProposal(proposal: JSONObject) {
        check(prefs.edit().putString("proposal", proposal.toString()).commit()) { "Could not save improvement progress" }
    }
}
