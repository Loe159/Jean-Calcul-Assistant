package fr.loevan.jeancalcul.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class EncryptedSecret(
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
)

internal interface SecretCipher {
    fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedSecret

    fun decrypt(
        encryptedSecret: EncryptedSecret,
        associatedData: ByteArray,
    ): ByteArray

    fun deleteKey()
}

internal interface EncryptedSecretStorage {
    fun read(id: SecretId): EncryptedSecret?

    fun write(
        id: SecretId,
        encryptedSecret: EncryptedSecret,
    )

    fun remove(id: SecretId): Boolean

    fun clear()
}

internal class SecretMaterialInvalidatedException : Exception()

internal class SecretStorageException : Exception()

internal class EncryptedSecretStore(
    private val cipher: SecretCipher,
    private val storage: EncryptedSecretStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {
    private val mutex = Mutex()

    override suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit> =
        execute {
            val plaintext = secret.encodeUtf8()
            val associatedData = id.value.toByteArray(StandardCharsets.UTF_8)
            try {
                storage.write(id, cipher.encrypt(plaintext, associatedData))
            } finally {
                plaintext.fill(0)
                associatedData.fill(0)
            }
        }

    override suspend fun get(id: SecretId): SecretStoreResult<SecretValue?> =
        execute {
            val encryptedSecret = storage.read(id) ?: return@execute null
            val associatedData = id.value.toByteArray(StandardCharsets.UTF_8)
            try {
                val plaintext = cipher.decrypt(encryptedSecret, associatedData)
                try {
                    SecretValue(plaintext.decodeUtf8())
                } finally {
                    plaintext.fill(0)
                }
            } finally {
                associatedData.fill(0)
            }
        }

    override suspend fun delete(id: SecretId): SecretStoreResult<Boolean> = execute { storage.remove(id) }

    override suspend fun reset(): SecretStoreResult<Unit> =
        execute {
            storage.clear()
            cipher.deleteKey()
        }

    private suspend fun <T> execute(block: () -> T): SecretStoreResult<T> =
        withContext(dispatcher) {
            mutex.withLock {
                try {
                    SecretStoreResult.Success(block())
                } catch (_: SecretMaterialInvalidatedException) {
                    SecretStoreResult.Failure(INVALIDATED_ERROR)
                } catch (_: SecretStorageException) {
                    SecretStoreResult.Failure(STORAGE_ERROR)
                }
            }
        }

    private companion object {
        val INVALIDATED_ERROR =
            SecretStoreError(
                reason = SecretStoreFailureReason.INVALIDATED,
                isRecoverable = true,
                userMessage = "Saved credentials can no longer be decrypted. Remove and enter them again.",
            )
        val STORAGE_ERROR =
            SecretStoreError(
                reason = SecretStoreFailureReason.STORAGE_UNAVAILABLE,
                isRecoverable = true,
                userMessage = "Secure credential storage is temporarily unavailable.",
            )
    }
}

private fun CharArray.encodeUtf8(): ByteArray =
    try {
        val encoder = StandardCharsets.UTF_8.newEncoder()
        encoder.onMalformedInput(CodingErrorAction.REPORT)
        encoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(this))
        try {
            encoded.copyRemainingBytes()
        } finally {
            encoded.clearBackingArray()
        }
    } catch (_: CharacterCodingException) {
        throw SecretStorageException()
    }

private fun ByteArray.decodeUtf8(): CharArray =
    try {
        val decoder = StandardCharsets.UTF_8.newDecoder()
        decoder.onMalformedInput(CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        val decoded = decoder.decode(ByteBuffer.wrap(this))
        try {
            decoded.copyRemainingChars()
        } finally {
            decoded.clearBackingArray()
        }
    } catch (_: CharacterCodingException) {
        throw SecretMaterialInvalidatedException()
    }

private fun ByteBuffer.copyRemainingBytes(): ByteArray = ByteArray(remaining()).also { destination -> get(destination) }

private fun CharBuffer.copyRemainingChars(): CharArray = CharArray(remaining()).also { destination -> get(destination) }

private fun ByteBuffer.clearBackingArray() {
    if (hasArray()) array().fill(0)
}

private fun CharBuffer.clearBackingArray() {
    if (hasArray()) array().fill(NULL_CHARACTER)
}

private const val NULL_CHARACTER = '\u0000'
