package fr.loevan.jeancalcul.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.security.SecretStore
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ConnectionProbeResult {
    data class Success(
        val message: String,
        val insecureTransport: Boolean,
    ) : ConnectionProbeResult

    data class Failure(
        val code: String,
        val userMessage: String,
        val recoverable: Boolean,
    ) : ConnectionProbeResult
}

interface ProviderConnectionProbe {
    suspend fun test(connection: ProviderConnection): ConnectionProbeResult
}

@Singleton
class OkHttpProviderConnectionProbe
    @Inject
    constructor(
        private val client: OkHttpClient,
        secretStore: SecretStore,
    ) : ProviderConnectionProbe {
        private val authenticator = ProviderRequestAuthenticator(secretStore)

        @Suppress("ReturnCount")
        override suspend fun test(connection: ProviderConnection): ConnectionProbeResult {
            val url =
                connection.probeUrl()
                    ?: return ConnectionProbeResult.Failure(
                        code = "invalid_url",
                        userMessage = "L'URL du fournisseur est invalide. Verifiez le protocole et le nom d'hote.",
                        recoverable = true,
                    )
            val requestBuilder =
                Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "Jean-Calcul-Assistant/0.1")
            val authenticationFailure = authenticator.authenticate(connection, requestBuilder)
            if (authenticationFailure != null) {
                return ConnectionProbeResult.Failure(
                    code = authenticationFailure.code,
                    userMessage = authenticationFailure.userMessage,
                    recoverable = authenticationFailure.recoverable,
                )
            }

            return try {
                client.newCall(requestBuilder.build()).await().use { response -> response.toProbeResult(connection) }
            } catch (_: IOException) {
                ConnectionProbeResult.Failure(
                    code = "network_unreachable",
                    userMessage = "Connexion impossible. Verifiez l'URL, le reseau et la disponibilite du serveur.",
                    recoverable = true,
                )
            }
        }

        private fun Response.toProbeResult(connection: ProviderConnection): ConnectionProbeResult =
            when (code) {
                in 200..399 ->
                    ConnectionProbeResult.Success(
                        message = "Serveur joignable (HTTP $code).",
                        insecureTransport = connection.usesInsecureTransport,
                    )

                401, 403 ->
                    ConnectionProbeResult.Failure(
                        code = "authentication_failed",
                        userMessage = "Authentification refusee. Remplacez la cle API puis reessayez.",
                        recoverable = true,
                    )

                404 ->
                    ConnectionProbeResult.Failure(
                        code = "endpoint_not_found",
                        userMessage = "Serveur joignable, mais cette route est introuvable. Verifiez l'URL de base.",
                        recoverable = true,
                    )

                429 ->
                    ConnectionProbeResult.Failure(
                        code = "rate_limited",
                        userMessage = "Le fournisseur limite temporairement les requetes. Reessayez plus tard.",
                        recoverable = true,
                    )

                in 500..599 ->
                    ConnectionProbeResult.Failure(
                        code = "provider_unavailable",
                        userMessage = "Le fournisseur est temporairement indisponible (HTTP $code).",
                        recoverable = true,
                    )

                else ->
                    ConnectionProbeResult.Failure(
                        code = "unexpected_status",
                        userMessage = "Le fournisseur a renvoye une reponse inattendue (HTTP $code).",
                        recoverable = true,
                    )
            }
    }

private fun ProviderConnection.probeUrl(): okhttp3.HttpUrl? {
    val rawBase = baseUrl.trim().trimEnd('/')
    val route =
        when (kind) {
            ProviderKind.OPENAI_COMPATIBLE,
            ProviderKind.ANTHROPIC,
            ProviderKind.OPENROUTER,
            -> "models"

            ProviderKind.OLLAMA -> if (rawBase.endsWith("/api")) "tags" else "api/tags"
            ProviderKind.AGENT_BACKEND -> null
        }
    val normalized = "$rawBase/".toHttpUrlOrNull() ?: return null
    return route?.let(normalized::resolve) ?: normalized
}

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    error: IOException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            },
        )
    }

@Module
@InstallIn(SingletonComponent::class)
object NetworkSettingsModule {
    @Provides
    @Singleton
    fun provideSettingsOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideProviderConnectionProbe(implementation: OkHttpProviderConnectionProbe): ProviderConnectionProbe =
        implementation
}
