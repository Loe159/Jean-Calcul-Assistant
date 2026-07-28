@file:Suppress("MaxLineLength")

package fr.loevan.jeancalcul.network.anthropic

import app.cash.turbine.test
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import fr.loevan.jeancalcul.network.StaticSecretStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AnthropicProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `streams native text usage and lossless tool arguments`() =
        runTest {
            server.enqueue(
                sse(
                    """
                    event: message_start
                    data: {"type":"message_start","message":{"usage":{"input_tokens":8}}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Bonjour"}}

                    event: content_block_start
                    data: {"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu-1","name":"audio.set_volume","input":{}}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"stream\":\"MUSIC\","}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"volumePercent\":42}"}}

                    event: content_block_stop
                    data: {"type":"content_block_stop","index":1}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":12}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """.trimIndent(),
                ),
            )

            val events = provider().stream(request(tools = true)).toList()

            assertEquals("Bonjour", events.filterIsInstance<StreamEvent.TextDelta>().single().text)
            assertEquals(
                listOf(8, null),
                events.filterIsInstance<StreamEvent.UsageUpdated>().map { it.usage.inputTokens },
            )
            assertEquals(12, events.filterIsInstance<StreamEvent.UsageUpdated>().last().usage.outputTokens)
            val call = events.filterIsInstance<StreamEvent.ToolCallReady>().single().call
            assertEquals("toolu-1", call.callId)
            assertEquals("audio.set_volume", call.toolName)
            assertEquals("MUSIC", call.arguments["stream"].toString().trim('"'))
            assertEquals(42, call.arguments["volumePercent"].toString().toInt())
            assertEquals(FinishReason.TOOL_CALL, events.filterIsInstance<StreamEvent.Completed>().single().finishReason)

            val recorded = server.takeRequest()
            assertEquals("/v1/messages", recorded.path)
            assertEquals("test-secret", recorded.getHeader("x-api-key"))
            assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).toString()
            assertTrue(body.contains("\"input_schema\""))
            assertTrue(body.contains("\"max_tokens\":4096"))
            assertFalse(body.contains("test-secret"))
        }

    @Test
    fun `normalizes authentication quota and overload without response leakage`() =
        runTest {
            listOf(
                401 to ProviderErrorCategory.AUTHENTICATION,
                429 to ProviderErrorCategory.RATE_LIMITED,
                529 to ProviderErrorCategory.SERVICE_UNAVAILABLE,
            ).forEachIndexed { index, (status, category) ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("private prompt test-secret"))

                val failure =
                    provider().stream(request("failure-$index")).toList()
                        .filterIsInstance<StreamEvent.Failed>()
                        .single()

                assertEquals(category, failure.error.category)
                assertFalse(failure.error.message.contains("private prompt"))
                assertFalse(failure.error.message.contains("test-secret"))
            }
        }

    @Test
    fun `cancellation closes the native messages call`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val provider = provider()
            val request = request("cancel-anthropic")

            provider.stream(request).test {
                assertTrue(awaitItem() is StreamEvent.Started)
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                assertTrue(request.requestId in provider.activeRequestIds)
                provider.cancel(request.requestId)
                assertEquals(ProviderErrorCategory.CANCELLED, (awaitItem() as StreamEvent.Failed).error.category)
                awaitComplete()
                assertFalse(request.requestId in provider.activeRequestIds)
            }
        }

    @Test
    fun `lists models with provider capabilities`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[{"id":"claude-test","display_name":"Claude Test","max_input_tokens":200000,""" +
                        """"max_tokens":8192,"capabilities":{"input_modalities":["text","image"],"tool_use":true}}]}""",
                ),
            )

            val model = provider().listModels(profile()).single()

            assertEquals("claude-test", model.id)
            assertEquals("Claude Test", model.displayName)
            assertTrue(model.capabilities.supportsToolCalling)
            assertEquals(200_000, model.capabilities.limits.maxContextTokens)
        }

    private fun provider(): AnthropicProvider =
        AnthropicProvider(
            OkHttpClient(),
            StaticSecretStore(),
            AnthropicConfiguration(
                ProviderConnection(
                    id = CONNECTION_ID,
                    displayName = "Anthropic test",
                    kind = ProviderKind.ANTHROPIC,
                    baseUrl = server.url("/v1/").toString(),
                    secretId = "provider.anthropic",
                ),
            ),
        )

    private fun request(
        requestId: String = "request-1",
        tools: Boolean = false,
    ) = ChatRequest(
        requestId,
        profile(),
        listOf(ChatMessage("message-$requestId", MessageRole.USER, listOf(MessageContent.Text("Bonjour")))),
        tools = if (tools) listOf(VolumeToolSchemas.definitions[1]) else emptyList(),
    )

    private fun profile() = ModelProfile("profile-1", ANTHROPIC_PROVIDER_ID, "claude-test", "Claude", CONNECTION_ID)

    private fun sse(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(body)

    private companion object {
        const val CONNECTION_ID = "anthropic-connection"
    }
}
