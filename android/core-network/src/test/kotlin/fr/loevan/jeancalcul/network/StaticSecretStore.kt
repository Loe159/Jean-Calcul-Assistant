package fr.loevan.jeancalcul.network

import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import fr.loevan.jeancalcul.security.SecretValue

internal class StaticSecretStore(
    private val value: String? = "test-secret",
) : SecretStore {
    override suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)

    override suspend fun get(id: SecretId): SecretStoreResult<SecretValue?> =
        SecretStoreResult.Success(value?.let { SecretValue.copyOf(it.toCharArray()) })

    override suspend fun delete(id: SecretId): SecretStoreResult<Boolean> = SecretStoreResult.Success(value != null)

    override suspend fun reset(): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)
}
