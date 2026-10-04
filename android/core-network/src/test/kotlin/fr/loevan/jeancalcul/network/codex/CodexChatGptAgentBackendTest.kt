package fr.loevan.jeancalcul.network.codex

import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.network.StaticSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CodexChatGptAgentBackendTest {
    private lateinit var server: MockWebServer
    private lateinit var connection: ProviderConnection
    private lateinit var profile: AgentProfile
    private lateinit var backend: CodexChatGptAgentBackend

    @Before
    fun setUp() {
        val heldCertificate =
            HeldCertificate
                .Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .build()
        val serverCertificates =
            HandshakeCertificates
                .Builder()
                .heldCertificate(heldCertificate)
                .build()
        val clientCertificates =
            HandshakeCertificates
                .Builder()
                .addTrustedCertificate(heldCertificate.certificate)
                .build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        connection =
            ProviderConnection(
                id = "companion",
                displayName = "Companion",
                kind = ProviderKind.AGENT_BACKEND,
                baseUrl = server.url("/").toString().trimEnd('/'),
                secretId = "provider.companion.pairing_token",
            )
        profile =
            AgentProfile(
                id = "agent",
                backendId = CodexChatGptAgentBackend.ID,
                agentId = "gpt-test",
                displayName = "ChatGPT",
                connectionId = connection.id,
            )
        backend =
            CodexChatGptAgentBackend(
                settingsRepository =
                    StaticSettingsRepository(
                        AssistantSettings(providers = listOf(connection)),
                    ),
                secretStore = StaticSecretStore("pairing-secret"),
                clientFactory =
                    CompanionHttpClientFactory(
                        OkHttpClient
                            .Builder()
                            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                            .build(),
                    ),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `session send and event stream use pairing token and normalized contract`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"id":"session-1","model":"gpt-test","resumable":true}"""),
            )
            val session = backend.createSession(profile)
            assertEquals("session-1", session.id)
            assertEquals("Bearer pairing-secret", server.takeRequest().getHeader("Authorization"))

            server.enqueue(
                MockResponse()
                    .setResponseCode(202)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"id":"run-1","session_id":"session-1","request_id":"request-1","status":"RUNNING"}""",
                    ),
            )
            val run =
                backend.sendMessage(
                    session.id,
                    AgentRequest(
                        requestId = "request-1",
                        messages =
                            listOf(
                                ChatMessage(
                                    id = "message-1",
                                    role = MessageRole.USER,
                                    content = listOf(MessageContent.Text("Bonjour")),
                                ),
                            ),
                    ),
                )
            assertEquals("run-1", run.id)
            val sent = server.takeRequest()
            assertEquals("/v1/sessions/session-1/messages", sent.path)
            assertEquals("Bearer pairing-secret", sent.getHeader("Authorization"))
            assertTrue(sent.body.readUtf8().contains(""Bonjour""))

            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "events": [
                            {"sequence":101,"type":"text_delta","request_id":"request-1","text":"Bon"},
                            {"sequence":102,"type":"text_delta","request_id":"request-1","text":"jour"},
                            {"sequence":103,"type":"completed","request_id":"request-1","finish_reason":"STOP"}
                          ]
                        }
                        """.trimIndent(),
                    ),
            )
            val events = backend.streamEvents(session.id, 100).toList()
            assertEquals(3, events.size)
            assertEquals("Bon", (events[0] as StreamEvent.TextDelta).text)
            assertEquals("jour", (events[1] as StreamEvent.TextDelta).text)
            assertTrue(events[2] is StreamEvent.Completed)
            assertEquals("/v1/sessions/session-1/events?after=100", server.takeRequest().path)
        }
}

private class StaticSettingsRepository(
    initial: AssistantSettings,
) : AssistantSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AssistantSettings> = state

    override suspend fun update(transform: (AssistantSettings) -> AssistantSettings) {
        state.value = transform(state.value)
    }
}
