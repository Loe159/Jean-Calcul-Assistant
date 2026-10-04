package fr.loevan.jeancalcul.network.codex

import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import fr.loevan.jeancalcul.security.SecretValue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodexCompanionAgentBackendTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `session creation authenticates with pairing token and parses response`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"sessionId":"thread-1","resumable":true}"""),
            )
            val backend = backend()
            val session = backend.createSession(profile)

            assertEquals("thread-1", session.id)
            assertTrue(session.resumable)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/sessions", request.path)
            assertEquals("Bearer pairing-secret", request.getHeader("Authorization"))
        }

    @Test
    fun `run streams text and completion in order`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(202)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"runId":"turn-1","status":"running"}"""),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/x-ndjson")
                    .setBody(
                        """
                        {"sequence":1,"type":"text_delta","requestId":"req-1","text":"Bon"}
                        {"sequence":2,"type":"text_delta","requestId":"req-1","text":"jour"}
                        {"sequence":3,"type":"completed","requestId":"req-1"}
                        """.trimIndent() + "\n",
                    ),
            )
            val backend = backend()
            val request =
                AgentRequest(
                    requestId = "req-1",
                    messages =
                        listOf(
                            ChatMessage(
                                id = "message-1",
                                role = MessageRole.USER,
                                content = listOf(MessageContent.Text("Bonjour")),
                            ),
                        ),
                )

            val run = backend.sendMessage("thread-1", request)
            val events = backend.streamEvents("thread-1").toList()

            assertEquals("turn-1", run.id)
            assertEquals(3, events.size)
            assertEquals("Bon", (events[0] as StreamEvent.TextDelta).text)
            assertEquals("jour", (events[1] as StreamEvent.TextDelta).text)
            assertTrue(events[2] is StreamEvent.Completed)
            assertEquals("/v1/sessions/thread-1/runs", server.takeRequest().path)
            assertEquals("/v1/sessions/thread-1/runs/turn-1/events?after=0", server.takeRequest().path)
        }

    @Test
    fun `status only reports available for chatgpt companion`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"protocolVersion":"1","state":"ready","auth":"chatgpt"}"""),
            )

            val status = backend().getStatus(profile)

            assertEquals(fr.loevan.jeancalcul.domain.AgentBackendState.AVAILABLE, status.state)
        }

    private fun backend() =
        CodexCompanionAgentBackend(
            connection =
                ProviderConnection(
                    id = "connection",
                    displayName = "Codex",
                    kind = ProviderKind.AGENT_BACKEND,
                    baseUrl = server.url("/").toString().trimEnd('/'),
                    secretId = "pairing.secret",
                ),
            client = OkHttpClient(),
            secretStore = InMemorySecretStore("pairing-secret"),
        )

    private val profile =
        AgentProfile(
            id = "profile",
            backendId = "codex-companion",
            agentId = "codex",
            displayName = "Codex",
            connectionId = "connection",
        )
}

private class InMemorySecretStore(secret: String) : SecretStore {
    private var value: CharArray? = secret.toCharArray()

    override suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit> {
        value?.fill('\u0000')
        value = secret.copyOf()
        return SecretStoreResult.Success(Unit)
    }

    override suspend fun get(id: SecretId): SecretStoreResult<SecretValue?> =
        SecretStoreResult.Success(value?.let(SecretValue::copyOf))

    override suspend fun delete(id: SecretId): SecretStoreResult<Boolean> {
        val existed = value != null
        value?.fill('\u0000')
        value = null
        return SecretStoreResult.Success(existed)
    }

    override suspend fun reset(): SecretStoreResult<Unit> {
        value?.fill('\u0000')
        value = null
        return SecretStoreResult.Success(Unit)
    }
}
