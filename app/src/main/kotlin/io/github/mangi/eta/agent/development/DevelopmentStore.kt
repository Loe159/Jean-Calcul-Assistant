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
        check(prefs.edit().putBoolean("enabled", false).commit()) { "Could not disconnect GitHub" }
    }
    override fun proposal(): JSONObject? = prefs.getString("proposal", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    override fun saveProposal(proposal: JSONObject) {
        check(prefs.edit().putString("proposal", proposal.toString()).commit()) { "Could not save improvement progress" }
    }
}
