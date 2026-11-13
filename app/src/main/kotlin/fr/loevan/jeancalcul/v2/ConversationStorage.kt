package fr.loevan.jeancalcul.v2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private, atomic history. No credentials, provider bodies or tool arguments in the journal. */
class FileSessionStore(context: Context) : SessionStore {
    private val file = AtomicFile(File(context.filesDir, "conversation.json"))
    @Synchronized override fun load(): Session {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return Session()
        try {
            val json = JSONObject(String(file.readFully(), Charsets.UTF_8))
            val messages = json.getJSONArray("messages")
            val events = json.getJSONArray("events")
            return Session(json.getString("id"), (0 until messages.length()).map {
                messages.getJSONObject(it).let { m ->
                    val calls = m.optJSONArray("toolCalls") ?: JSONArray()
                    ChatMessage(m.getString("role"), m.getString("text"), (0 until calls.length()).map { index ->
                        calls.getJSONObject(index).let { call -> ToolCall(call.getString("id"), call.getString("name"), call.getString("arguments")) }
                    }, m.optString("toolCallId", ""))
                }
            }, RunStatus.valueOf(json.getString("status")),
                json.optString("error").takeIf { it.isNotBlank() }?.let { RunError.valueOf(it) },
                json.getString("runId"), (0 until events.length()).map {
                    events.getJSONObject(it).let { e -> RunEvent(e.getString("run"), e.getString("kind"), e.getLong("time")) }
                })
        } catch (_: Exception) { throw RunFailure(RunError.STORAGE) }
    }
    @Synchronized override fun save(session: Session) {
        val messages = JSONArray(); val events = JSONArray()
        session.messages.forEach { message ->
            val calls = JSONArray()
            message.toolCalls.forEach { calls.put(JSONObject().put("id", it.id).put("name", it.name).put("arguments", it.arguments)) }
            messages.put(JSONObject().put("role", message.role).put("text", message.text)
                .put("toolCalls", calls).put("toolCallId", message.toolCallId))
        }
        session.events.forEach { events.put(JSONObject().put("run", it.runId).put("kind", it.kind).put("time", it.time)) }
        val bytes = JSONObject().put("id", session.id).put("messages", messages)
            .put("status", session.status.name).put("error", session.error?.name.orEmpty())
            .put("runId", session.runId).put("events", events).toString().toByteArray()
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
}

class ProviderSettings(context: Context) {
    private val preferences = context.getSharedPreferences("provider", Context.MODE_PRIVATE)
    fun config() = ProviderConfig(preferences.getString("endpoint", "").orEmpty(),
        preferences.getString("model", "").orEmpty(), preferences.getBoolean("local", false))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("provider-credential-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("provider-credential-v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun credential(): String {
        val encrypted = preferences.getString("credential", null) ?: return ""
        try {
            val bytes = android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) { throw RunFailure(RunError.AUTHENTICATION) }
    }
    /** Blank means remove the previous key; secrets are never read back into the UI. */
    fun save(config: ProviderConfig, credential: String) {
        if (!config.validate()) throw RunFailure(RunError.CONFIGURATION)
        val edit = preferences.edit().putString("endpoint", config.endpoint.trimEnd('/'))
            .putString("model", config.model).putBoolean("local", config.local)
        if (credential.isBlank()) edit.remove("credential") else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val bytes = cipher.iv + cipher.doFinal(credential.toByteArray())
            edit.putString("credential", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        }
        if (!edit.commit()) throw RunFailure(RunError.STORAGE)
    }
}
