package fr.loevan.jeancalcul.security

import java.io.Closeable

/** Stable, non-sensitive identifier for one provider secret. */
@JvmInline
value class SecretId(
    val value: String,
) {
    init {
        require(VALID_PATTERN.matches(value)) {
            "A secret identifier must contain 1 to 128 letters, digits, dots, dashes, or underscores."
        }
    }

    private companion object {
        val VALID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}

/**
 * A secret kept in a mutable buffer. Call [close] as soon as the provider request has been built.
 * [toString] never reveals the value.
 */
class SecretValue internal constructor(
    value: CharArray,
) : Closeable {
    private val value = value
    private var closed = false

    @Synchronized
    fun <T> useChars(block: (CharArray) -> T): T {
        check(!closed) { "Secret value is already closed." }
        val copy = value.copyOf()
        return try {
            block(copy)
        } finally {
            copy.fill(NULL_CHARACTER)
        }
    }

    @Synchronized
    override fun close() {
        value.fill(NULL_CHARACTER)
        closed = true
    }

    override fun toString(): String = SecretRedactor.REDACTED

    private companion object {
        const val NULL_CHARACTER = '\u0000'
    }
}

/** Public contract used by provider adapters instead of Room, DataStore, or preferences. */
interface SecretStore {
    /** Creates or atomically replaces a secret. The caller remains responsible for clearing [secret]. */
    suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit>

    /** Returns null when no value exists. A returned [SecretValue] must be closed by the caller. */
    suspend fun get(id: SecretId): SecretStoreResult<SecretValue?>

    /** Removes one value without requiring access to the encryption key. */
    suspend fun delete(id: SecretId): SecretStoreResult<Boolean>

    /** Clears encrypted values and key material after a reported invalidation. */
    suspend fun reset(): SecretStoreResult<Unit>
}

sealed interface SecretStoreResult<out T> {
    data class Success<T>(
        val value: T,
    ) : SecretStoreResult<T>

    data class Failure(
        val error: SecretStoreError,
    ) : SecretStoreResult<Nothing>
}

data class SecretStoreError(
    val reason: SecretStoreFailureReason,
    val isRecoverable: Boolean,
    val userMessage: String,
)

enum class SecretStoreFailureReason {
    /** Encrypted data no longer matches the device-only key, commonly after restore or lock changes. */
    INVALIDATED,

    /** The encrypted storage or Android Keystore is temporarily unavailable. */
    STORAGE_UNAVAILABLE,
}
