package io.github.mangi.eta.data.repository

import android.content.Context

/** MCP 凭据只以 Android Keystore 密文保存在 App 私有偏好中。 */
internal class McpSecretStore(context: Context) {
    private val store = KeystoreSecretStore(context, "eta_mcp_secrets", "eta_mcp_credentials_v1")

    fun bearerToken(serverId: String): String? = store.read(tokenKey(serverId))

    fun setBearerToken(serverId: String, token: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) clear(serverId) else store.write(tokenKey(serverId), normalized)
    }

    fun clear(serverId: String) = store.clear(tokenKey(serverId))

    private fun tokenKey(serverId: String): String = "bearer_$serverId"
}
