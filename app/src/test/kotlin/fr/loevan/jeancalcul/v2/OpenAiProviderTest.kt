package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
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
            frame("""{"choices":[{"delta":{"tool_calls":[{"id":"x"}]}}]}""") to RunError.PROTOCOL)) {
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
    @Test fun toolFragmentsAreAssembledAndResultIsSentBack() = serverTest { server, client -> runBlocking {
        val first = org.json.JSONObject().put("choices", org.json.JSONArray().put(org.json.JSONObject().put("delta",
            org.json.JSONObject().put("tool_calls", org.json.JSONArray().put(org.json.JSONObject().put("index", 0)
                .put("id", "call1").put("function", org.json.JSONObject().put("name", "current_").put("arguments", "{")))))))
        val second = """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"time","arguments":"}"}}]},"finish_reason":"tool_calls"}]}"""
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(frame(first.toString()) + frame(second) + frame("[DONE]")))
        val provider = OpenAiProvider(ProviderConfig(server.url("/v1").toString(), "fixture"), { "" }, client)
        val calls = provider.turn(listOf(ChatMessage("user", "Heure"))) { }
        assertEquals(listOf(ToolCall("call1", "current_time", "{}")), calls)
        assertTrue(server.takeRequest().body.readUtf8().contains("current_time"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
            frame("""{"choices":[{"delta":{"content":"Fin"},"finish_reason":"stop"}]}""") + frame("[DONE]")))
        provider.turn(listOf(ChatMessage("assistant", "", calls), ChatMessage("tool", "UTC synthétique", toolCallId = "call1"))) { }
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
        assertEquals("call1", body.getJSONObject(0).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("call1", body.getJSONObject(1).getString("tool_call_id"))
    } }
    @Test fun cancellationClosesAnActualWaitingSocket() = serverTest { server, client -> runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val provider = OpenAiProvider(ProviderConfig(server.url("/v1").toString(), "fixture"), { "" }, client)
        val stopped = kotlinx.coroutines.CompletableDeferred<Unit>()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try { provider.turn(listOf(ChatMessage("user", "Attente synthétique"))) { } }
            catch (_: kotlinx.coroutines.CancellationException) { stopped.complete(Unit) }
        }
        assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
        provider.cancel()
        kotlinx.coroutines.withTimeout(5_000) { stopped.await(); job.join() }
    } }
    @Test fun configRejectsPlaintextAndEmbeddedSecrets() {
        assertFalse(ProviderConfig("http://localhost:8080/v1", "model", true).validate())
        assertFalse(ProviderConfig("https://key@host/v1", "model").validate())
        assertFalse(ProviderConfig("https://host/v1?key=secret", "model").validate())
        assertTrue(ProviderConfig("https://host/v1", "model").validate())
    }
}
