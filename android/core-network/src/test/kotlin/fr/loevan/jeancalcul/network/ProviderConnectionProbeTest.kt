package fr.loevan.jeancalcul.network

import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import fr.loevan.jeancalcul.security.SecretValue
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderConnectionProbeTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `successful HTTP probe includes local network warning`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(204))
            val probe = OkHttpProviderConnectionProbe(OkHttpClient(), MissingSecretStore())

            val result = probe.test(connection())

            assertTrue(result is ConnectionProbeResult.Success)
            assertTrue((result as ConnectionProbeResult.Success).insecureTransport)
            assertEquals("/api/tags", server.takeRequest().path)
        }

    @Test
    fun `authentication failure is actionable and normalized`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(401))
            val probe = OkHttpProviderConnectionProbe(OkHttpClient(), MissingSecretStore())

            val result = probe.test(connection())

            assertEquals("authentication_failed", (result as ConnectionProbeResult.Failure).code)
            assertTrue(result.userMessage.contains("cle API"))
        }

    @Test
    fun `missing referenced secret fails before a request is sent`() =
        runTest {
            val probe = OkHttpProviderConnectionProbe(OkHttpClient(), MissingSecretStore())

            val result = probe.test(connection(secretId = "provider.key"))

            assertEquals("secret_missing", (result as ConnectionProbeResult.Failure).code)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `OpenRouter probe uses models route and application authentication`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200))
            val probe = OkHttpProviderConnectionProbe(OkHttpClient(), StaticSecretStore())
            val connection =
                ProviderConnection(
                    id = "openrouter",
                    displayName = "OpenRouter",
                    kind = ProviderKind.OPENROUTER,
                    baseUrl = server.url("/api/v1/").toString(),
                    secretId = "provider.openrouter",
                )

            val result = probe.test(connection)

            assertTrue(result is ConnectionProbeResult.Success)
            val request = server.takeRequest()
            assertEquals("/api/v1/models", request.path)
            assertEquals("Bearer test-secret", request.getHeader("Authorization"))
        }

    private fun connection(secretId: String? = null) =
        ProviderConnection(
            id = "provider",
            displayName = "Local",
            kind = ProviderKind.OLLAMA,
            baseUrl = server.url("/").toString(),
            secretId = secretId,
        )
}

private class MissingSecretStore : SecretStore {
    override suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)

    override suspend fun get(id: SecretId): SecretStoreResult<SecretValue?> = SecretStoreResult.Success(null)

    override suspend fun delete(id: SecretId): SecretStoreResult<Boolean> = SecretStoreResult.Success(false)

    override suspend fun reset(): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)
}
