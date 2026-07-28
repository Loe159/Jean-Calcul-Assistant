package fr.loevan.jeancalcul.network

import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import okhttp3.Request

internal data class ProviderAuthenticationFailure(
    val code: String,
    val userMessage: String,
    val recoverable: Boolean,
)

/** Adds provider credentials without exposing them outside the request-building boundary. */
internal class ProviderRequestAuthenticator(
    private val secretStore: SecretStore,
) {
    suspend fun authenticate(
        connection: ProviderConnection,
        requestBuilder: Request.Builder,
    ): ProviderAuthenticationFailure? {
        val secretId = connection.secretId
        return if (secretId == null) {
            null
        } else {
            authenticate(SecretId(secretId), connection.kind, requestBuilder)
        }
    }

    private suspend fun authenticate(
        secretId: SecretId,
        providerKind: ProviderKind,
        requestBuilder: Request.Builder,
    ): ProviderAuthenticationFailure? =
        when (val result = secretStore.get(secretId)) {
            is SecretStoreResult.Failure ->
                ProviderAuthenticationFailure(
                    code = "secret_unavailable",
                    userMessage = result.error.userMessage,
                    recoverable = result.error.isRecoverable,
                )

            is SecretStoreResult.Success -> {
                val secret = result.value
                if (secret == null) {
                    ProviderAuthenticationFailure(
                        code = "secret_missing",
                        userMessage = "La cle API n'est plus disponible. Saisissez-la de nouveau.",
                        recoverable = true,
                    )
                } else {
                    secret.use { value ->
                        value.useChars { chars ->
                            when (providerKind) {
                                ProviderKind.ANTHROPIC -> {
                                    requestBuilder.header("x-api-key", chars.concatToString())
                                    requestBuilder.header("anthropic-version", "2023-06-01")
                                }

                                else -> requestBuilder.header("Authorization", "Bearer ${chars.concatToString()}")
                            }
                        }
                    }
                    null
                }
            }
        }
}
