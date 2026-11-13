package fr.loevan.jeancalcul.v2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** One atomic AES-GCM envelope: refresh-token rotation never leaves mixed credential records. */
internal class ChatGptSecureStore(context: Context) : ChatGptStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "chatgpt-accounts.aes"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("chatgpt-accounts-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("chatgpt-accounts-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun load(): ChatGptRegistration {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ChatGptRegistration()
        try {
            val value = file.readFully()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, value.copyOfRange(0, 12)))
            return ChatGptRegistration.parse(String(cipher.doFinal(value.copyOfRange(12, value.size)), Charsets.UTF_8))
        } catch (_: Exception) { throw RunFailure(RunError.STORAGE) }
    }
    @Synchronized override fun save(value: ChatGptRegistration) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.iv + cipher.doFinal(value.json().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw RunFailure(RunError.STORAGE) }
    }
}
