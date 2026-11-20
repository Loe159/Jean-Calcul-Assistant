package io.github.mangi.eta.agent.device.notifications.remote

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.mangi.eta.data.repository.McpSecretStore
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Private homelab connection. The device secret is kept only as Android Keystore ciphertext. */
internal data class MastraPairing(val baseUrl: String, val deviceId: String, val secret: String)

internal class MastraBriefSession(val pairing: MastraPairing, val revision: Long, val cache: String?)

internal object MastraConnection {
    private const val PREFS = "notification_mastra"
    private const val SECRET_ID = "jeancalcul-mastra-notifications"
    private const val BRIEF_CACHE = "brief_summary_v1"
    private val loopbackHosts = setOf("127.0.0.1", "localhost", "10.0.2.2")
    private val _pairingChanges = MutableStateFlow(0L)
    val pairingChanges = _pairingChanges.asStateFlow()

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
        // No path prefix: the signed path must equal the path the server sees.
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.rawPath.isNullOrEmpty() &&
            (uri.scheme == "https" || (uri.scheme == "http" && host in loopbackHosts))
    }.getOrDefault(false)

    @Synchronized
    fun save(context: Context, pairing: MastraPairing) {
        McpSecretStore(context).setBearerToken(SECRET_ID, pairing.secret)
        prefs(context).edit(commit = true) {
            putString("url", pairing.baseUrl); putString("device", pairing.deviceId); putBoolean("enabled", true)
            remove(BRIEF_CACHE)
        }
        _pairingChanges.update { it + 1 }
    }

    @Synchronized
    fun forget(context: Context) {
        McpSecretStore(context).clear(SECRET_ID)
        prefs(context).edit(commit = true) {
            remove("url"); remove("device"); putBoolean("enabled", false); remove(BRIEF_CACHE)
        }
        _pairingChanges.update { it + 1 }
    }

    fun load(context: Context): MastraPairing? {
        val p = prefs(context)
        val url = p.getString("url", null) ?: return null
        val device = p.getString("device", null) ?: return null
        val secret = McpSecretStore(context).bearerToken(SECRET_ID) ?: return null
        return MastraPairing(url, device, secret)
    }

    fun enabled(context: Context): Boolean = loadEnabled(context) != null

    @Synchronized
    private fun loadEnabled(context: Context): MastraPairing? =
        if (prefs(context).getBoolean("enabled", false)) load(context) else null

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean("enabled", enabled)
            if (!enabled) remove(BRIEF_CACHE)
        }
        _pairingChanges.update { it + 1 }
    }

    @Synchronized
    fun briefSession(context: Context): MastraBriefSession? {
        val pairing = loadEnabled(context) ?: return null
        return MastraBriefSession(pairing, pairingChanges.value, prefs(context).getString(BRIEF_CACHE, null))
    }

    @Synchronized
    fun writeBriefCache(context: Context, revision: Long, value: String?): Boolean {
        if (pairingChanges.value != revision) return false
        prefs(context).edit {
            if (value == null) remove(BRIEF_CACHE) else putString(BRIEF_CACHE, value)
        }
        return true
    }

    /** Explicit consent for persistent learning on the homelab (default off). */
    fun learningConsent(context: Context): Boolean = prefs(context).getBoolean("learning_consent", false)
    fun setLearningConsent(context: Context, value: Boolean) = prefs(context).edit { putBoolean("learning_consent", value) }

    fun lastStatus(context: Context): String? = prefs(context).getString("last_status", null)
    fun setLastStatus(context: Context, status: String) = prefs(context).edit { putString("last_status", status.take(160)) }

    fun displayHost(context: Context): String? = prefs(context).getString("url", null)?.toUri()?.host

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
