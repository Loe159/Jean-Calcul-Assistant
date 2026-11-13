package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class OpenAiProviderTest {
    private fun serverTest(block: (MockWebServer, OkHttpClient) -> Unit) {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false); server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .followRedirects(false).build()
            block(server, client)
        }
    }
    private fun frame(delta: String) = "data: $delta\n\n"
    @Test fun actualTlsRequestStreamsAndSendsHistory() = serverTest { server, client -> runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            frame("""{"choices":[{"delta":{"content":"Bon"}}]}""") +
            frame("""{"choices":[{"delta":{"content":"jour"},"finish_reason":"stop"}]}""") + frame("[DONE]")))
        val provider = OpenAiProvider(ProviderConfig(server.url("/v1").toString(), "fixture", true), { "synthetic-test-key" }, client)
        val chunks = mutableListOf<String>(); provider.stream(listOf(ChatMessage("user", "Salut"))) { chunks.add(it) }
        assertEquals(listOf("Bon", "jour"), chunks)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer synthetic-test-key", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("Salut"))
    } }
    @Test fun truncatedStreamAndToolsFailClosed() = serverTest { server, client -> runBlocking {
        for ((body, error) in listOf(
            frame("""{"choices":[{"delta":{"content":"partiel"}}]}""") to RunError.PROTOCOL,
            frame("""{"choices":[{"delta":{"tool_calls":[{"id":"x"}]}}]}""") to RunError.TOOL_POLICY)) {
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
            try {
                OpenAiProvider(ProviderConfig(server.url("/v1").toString(), "fixture"), { "" }, client).stream(emptyList()) { }
                fail("Expected failure")
            } catch (failure: RunFailure) { assertEquals(error, failure.kind) }
        }
    } }
    @Test fun authenticationAndRateLimitsDoNotLeakServerBody() = serverTest { server, client -> runBlocking {
        for ((code, error) in listOf(401 to RunError.AUTHENTICATION, 429 to RunError.RATE_LIMIT, 503 to RunError.PROVIDER)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("sensitive body"))
            try {
                OpenAiProvider(ProviderConfig(server.url("/v1").toString(), "fixture"), { "" }, client).stream(emptyList()) { }
                fail("Expected failure")
            } catch (failure: RunFailure) { assertEquals(error, failure.kind); assertFalse(failure.message.orEmpty().contains("sensitive")) }
        }
    } }
    @Test fun configRejectsPlaintextAndEmbeddedSecrets() {
        assertFalse(ProviderConfig("http://localhost:8080/v1", "model", true).validate())
        assertFalse(ProviderConfig("https://key@host/v1", "model").validate())
        assertFalse(ProviderConfig("https://host/v1?key=secret", "model").validate())
        assertTrue(ProviderConfig("https://host/v1", "model").validate())
    }
}
