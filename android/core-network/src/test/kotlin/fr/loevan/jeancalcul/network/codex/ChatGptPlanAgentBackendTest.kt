package fr.loevan.jeancalcul.network.codex

import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MvpToolSchemas
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.StreamEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class ChatGptPlanAgentBackendTest {
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
    fun `SIWC request namespaces tools and maps wire name back to Android tool`() =
        runTest {
            val definition =
                MvpToolSchemas.definitions.first {
                    it.name == MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT
                }
            val backend = backend()
            val session = backend.createSession(profile)
            backend.sendMessage(
                session.id,
                AgentRequest(
                    requestId = "request-1",
                    messages =
                        listOf(
                            ChatMessage(
                                id = "message-1",
                                role = MessageRole.USER,
                                content = listOf(MessageContent.Text("Allume la lampe torche")),
                            ),
                        ),
                    availableTools = listOf(definition),
                ),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """
                        data: {"type":"response.output_item.added","sequence_number":1,"item":{"type":"function_call","id":"item-1","call_id":"call-1","name":"device_toggle_flashlight_0","namespace":"android","arguments":""}}

                        data: {"type":"response.function_call_arguments.done","sequence_number":2,"item_id":"item-1","arguments":"{\"enabled\":true}"}

                        """.trimIndent(),
                    ),
            )

            val event =
                backend.streamEvents(session.id)
                    .first { it is StreamEvent.ToolCallReady } as StreamEvent.ToolCallReady

            assertEquals(MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT, event.call.toolName)
            assertEquals(true, event.call.arguments["enabled"]?.jsonPrimitive?.content?.toBoolean())

            val request = server.takeRequest()
            val requestJson = Json.parseToJsonElement(requireNotNull(request.body.readUtf8())).jsonObject
            val namespace = requestJson["tools"]!!.jsonArray.single().jsonObject
            assertEquals("namespace", namespace["type"]?.jsonPrimitive?.content)
            assertEquals("android", namespace["name"]?.jsonPrimitive?.content)
            val wireTool = namespace["tools"]!!.jsonArray.single().jsonObject
            assertEquals("function", wireTool["type"]?.jsonPrimitive?.content)
            assertEquals("device_toggle_flashlight_0", wireTool["name"]?.jsonPrimitive?.content)
            assertFalse(wireTool["name"]!!.jsonPrimitive.content.contains('.'))
        }

    private fun backend() =
        ChatGptPlanAgentBackend(
            connection =
                ProviderConnection(
                    id = CHATGPT_PLAN_PROVIDER_ID,
                    displayName = "ChatGPT",
                    kind = ProviderKind.AGENT_BACKEND,
                    baseUrl = CHATGPT_PLAN_API_BASE_URL,
                    secretId = CHATGPT_PLAN_SECRET_ID,
                ),
            profile = profile,
            client = OkHttpClient(),
            tokenProvider = ChatGptPlanTokenProvider { "oauth-token" },
            apiBaseUrl = server.url("/v1").toString().trimEnd('/'),
        )

    private val profile =
        AgentProfile(
            id = CHATGPT_PLAN_AGENT_ID,
            backendId = CHATGPT_PLAN_BACKEND_ID,
            agentId = CHATGPT_PLAN_DEFAULT_MODEL,
            displayName = "ChatGPT",
            connectionId = CHATGPT_PLAN_PROVIDER_ID,
        )
}
