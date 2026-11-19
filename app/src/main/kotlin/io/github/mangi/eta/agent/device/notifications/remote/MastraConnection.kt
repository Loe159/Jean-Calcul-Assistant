package io.github.mangi.eta.agent.device.notifications.remote

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.mangi.eta.data.repository.McpSecretStore
import java.util.Base64

/** Private homelab connection. The device secret is kept only as Android Keystore ciphertext. */
internal data class MastraPairing(val baseUrl: String, val deviceId: String, val secret: String)

internal object MastraConnection {
    private const val PREFS = "notification_mastra"
    private const val SECRET_ID = "jeancalcul-mastra-notifications"
    private val loopbackHosts = setOf("127.0.0.1", "localhost", "10.0.2.2")

    /** `jeancalcul-mastra:v1?url=https%3A%2F%2Fhost&device=jc_…&secret=…` printed by `npm run mobile:device`. */
    fun parsePairing(raw: String): MastraPairing? {
        val text = raw.trim()
        if (!text.startsWith("jeancalcul-mastra:v1?")) return null
        val params = text.removePrefix("jeancalcul-mastra:v1?").split('&').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to java.net.URLDecoder.decode(part.substring(i + 1), "UTF-8")
        }.toMap()
        val url = params["url"]?.trimEnd('/') ?: return null
        val device = params["device"] ?: return null
        val secret = params["secret"] ?: return null
        if (!validBaseUrl(url) || !MastraContract.isOpaque(device)) return null
        val decoded = runCatching { Base64.getUrlDecoder().decode(secret) }.getOrNull() ?: return null
        if (decoded.size < 32) return null
        return MastraPairing(url, device, secret)
    }

    /** HTTPS is mandatory except for loopback and the emulator host alias. */
    fun validBaseUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host ?: return false
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.scheme == "https" || (uri.scheme == "http" && host in loopbackHosts))
    }.getOrDefault(false)

    fun save(context: Context, pairing: MastraPairing) {
        McpSecretStore(context).setBearerToken(SECRET_ID, pairing.secret)
        prefs(context).edit(commit = true) {
            putString("url", pairing.baseUrl); putString("device", pairing.deviceId); putBoolean("enabled", true)
        }
    }

    fun forget(context: Context) {
        McpSecretStore(context).clear(SECRET_ID)
        prefs(context).edit(commit = true) { remove("url"); remove("device"); putBoolean("enabled", false) }
    }

    fun load(context: Context): MastraPairing? {
        val p = prefs(context)
        val url = p.getString("url", null) ?: return null
        val device = p.getString("device", null) ?: return null
        val secret = McpSecretStore(context).bearerToken(SECRET_ID) ?: return null
        return MastraPairing(url, device, secret)
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("enabled", false) && load(context) != null
    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit { putBoolean("enabled", enabled) }

    /** Explicit consent for persistent learning on the homelab (default off). */
    fun learningConsent(context: Context): Boolean = prefs(context).getBoolean("learning_consent", false)
    fun setLearningConsent(context: Context, value: Boolean) = prefs(context).edit { putBoolean("learning_consent", value) }

    fun lastStatus(context: Context): String? = prefs(context).getString("last_status", null)
    fun setLastStatus(context: Context, status: String) = prefs(context).edit { putString("last_status", status.take(160)) }

    fun displayHost(context: Context): String? = prefs(context).getString("url", null)?.toUri()?.host

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
