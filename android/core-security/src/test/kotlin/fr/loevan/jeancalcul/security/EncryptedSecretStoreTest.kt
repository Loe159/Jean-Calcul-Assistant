package fr.loevan.jeancalcul.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class EncryptedSecretStoreTest {
    private val cipher = FakeSecretCipher()
    private val storage = FakeEncryptedSecretStorage()
    private val store = EncryptedSecretStore(cipher, storage, Dispatchers.Unconfined)
    private val secretId = SecretId("provider.openai.primary")

    @Test
    fun `creates reads replaces and deletes an encrypted secret`() =
        runBlocking {
            val original = "sk-original".toCharArray()
            val replacement = "sk-replacement".toCharArray()

            assertSuccess(store.put(secretId, original))
            assertArrayEquals(original, readSecret())

            assertSuccess(store.put(secretId, replacement))
            assertArrayEquals(replacement, readSecret())
            assertEquals(1, storage.values.size)

            assertTrue(assertSuccess(store.delete(secretId)))
            assertFalse(assertSuccess(store.delete(secretId)))
            assertNull(assertSuccess(store.get(secretId)))
        }

    @Test
    fun `never persists the plaintext value`() =
        runBlocking {
            val secret = "very-sensitive-provider-key".toCharArray()

            assertSuccess(store.put(secretId, secret))

            val persisted = storage.values.getValue(secretId).ciphertext
            assertFalse(String(persisted, StandardCharsets.UTF_8).contains(String(secret)))
            assertFalse(persisted.contentEquals(String(secret).toByteArray(StandardCharsets.UTF_8)))
        }

    @Test
    fun `returns a recoverable failure when key material is invalidated`() =
        runBlocking {
            assertSuccess(store.put(secretId, "sk-before-restore".toCharArray()))
            cipher.invalidated = true

            val failure = store.get(secretId) as SecretStoreResult.Failure

            assertEquals(SecretStoreFailureReason.INVALIDATED, failure.error.reason)
            assertTrue(failure.error.isRecoverable)
            assertFalse(failure.error.userMessage.contains("sk-before-restore"))

            assertSuccess(store.reset())
            assertSuccess(store.put(secretId, "sk-after-recovery".toCharArray()))
            assertArrayEquals("sk-after-recovery".toCharArray(), readSecret())
        }

    @Test
    fun `secret values redact toString and reject use after close`() =
        runBlocking {
            assertSuccess(store.put(secretId, "sk-memory".toCharArray()))
            val value = requireNotNull(assertSuccess(store.get(secretId)))

            assertEquals(SecretRedactor.REDACTED, value.toString())
            value.close()
            assertThrows(IllegalStateException::class.java) {
                value.useChars { chars -> chars.concatToString() }
            }
            Unit
        }

    private suspend fun readSecret(): CharArray {
        val value = requireNotNull(assertSuccess(store.get(secretId)))
        return value.use { secretValue -> secretValue.useChars(CharArray::copyOf) }
    }

    private fun <T> assertSuccess(result: SecretStoreResult<T>): T =
        when (result) {
            is SecretStoreResult.Success -> result.value
            is SecretStoreResult.Failure -> error("Expected success but got ${result.error.reason}")
        }

    private class FakeSecretCipher : SecretCipher {
        var invalidated = false

        override fun encrypt(
            plaintext: ByteArray,
            associatedData: ByteArray,
        ): EncryptedSecret {
            if (invalidated) throw SecretMaterialInvalidatedException()
            return EncryptedSecret(
                initializationVector = byteArrayOf(1, 2, 3),
                ciphertext = plaintext.mapIndexed { index, byte -> byte xor mask(associatedData, index) }.toByteArray(),
            )
        }

        override fun decrypt(
            encryptedSecret: EncryptedSecret,
            associatedData: ByteArray,
        ): ByteArray {
            if (invalidated) throw SecretMaterialInvalidatedException()
            return encryptedSecret.ciphertext
                .mapIndexed { index, byte -> byte xor mask(associatedData, index) }
                .toByteArray()
        }

        override fun deleteKey() {
            invalidated = false
        }

        private fun mask(
            associatedData: ByteArray,
            index: Int,
        ): Byte = (associatedData[index % associatedData.size].toInt() xor 0x5A).toByte()

        private infix fun Byte.xor(other: Byte): Byte = (toInt() xor other.toInt()).toByte()
    }

    private class FakeEncryptedSecretStorage : EncryptedSecretStorage {
        val values = mutableMapOf<SecretId, EncryptedSecret>()

        override fun read(id: SecretId): EncryptedSecret? = values[id]

        override fun write(
            id: SecretId,
            encryptedSecret: EncryptedSecret,
        ) {
            values[id] = encryptedSecret
        }

        override fun remove(id: SecretId): Boolean = values.remove(id) != null

        override fun clear() = values.clear()
    }
}
