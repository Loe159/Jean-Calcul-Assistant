@file:Suppress("MaxLineLength")

package fr.loevan.jeancalcul.network.ollama

import app.cash.turbine.test
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ContentModality
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
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

class OllamaProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `lists installed models and detects tools vision and context`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"models":[{"name":"gemma3","model":"gemma3"}]}"""))
            server.enqueue(
                MockResponse().setBody(
                    """{"capabilities":["completion","vision","tools"],""" +
                        """"model_info":{"gemma3.context_length":131072}}""",
                ),
            )

            val models = provider(configuredModels = emptyList()).listModels(profile())

            assertEquals(listOf("gemma3"), models.map(ModelDescriptor::id))
            assertTrue(models.single().capabilities.supportsToolCalling)
            assertTrue(ContentModality.IMAGE in models.single().capabilities.inputModalities)
            assertEquals(131_072, models.single().capabilities.limits.maxContextTokens)
            assertEquals("/api/tags", server.takeRequest().path)
            assertEquals("/api/show", server.takeRequest().path)
        }

    @Test
    fun `streams local text tools and usage with an explicit HTTP warning`() =
        runTest {
            server.enqueue(
                ndjson(
                    """
                    {"model":"qwen3","message":{"role":"assistant","content":"Bon"},"done":false}
                    {"model":"qwen3","message":{"role":"assistant","content":"jour","tool_calls":[{"function":{"name":"audio.set_volume","arguments":{"stream":"MUSIC","volumePercent":35}}}]},"done":false}
                    {"model":"qwen3","message":{"role":"assistant","content":""},"done":true,"done_reason":"stop","prompt_eval_count":9,"eval_count":3}
                    """.trimIndent(),
                ),
            )
            val provider = provider(toolCalling = true)

            val events = provider.stream(request(tools = true)).toList()

            assertEquals(listOf("Bon", "jour"), events.filterIsInstance<StreamEvent.TextDelta>().map { it.text })
            val tool = events.filterIsInstance<StreamEvent.ToolCallReady>().single().call
            assertEquals("audio.set_volume", tool.toolName)
            assertEquals(35, tool.arguments["volumePercent"].toString().toInt())
            assertEquals(9, events.filterIsInstance<StreamEvent.UsageUpdated>().single().usage.inputTokens)
            assertEquals(FinishReason.STOP, events.filterIsInstance<StreamEvent.Completed>().single().finishReason)
            assertTrue(
                providerConfiguration(toolCalling = true).insecureTransportWarning?.contains("non chiffree") == true,
            )

            val recorded = server.takeRequest()
            assertEquals("/api/chat", recorded.path)
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).toString()
            assertTrue(body.contains("\"tools\""))
            assertFalse(body.contains("test-secret"))
        }

    @Test
    fun `explicit cancellation closes the Ollama stream`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val provider = provider()
            val request = request("cancel-ollama")

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
    fun `server absence returns a recoverable network failure`() =
        runTest {
            val connection =
                ProviderConnection(
                    CONNECTION_ID,
                    "Unavailable Ollama",
                    ProviderKind.OLLAMA,
                    "http://127.0.0.1:1",
                )
            val provider =
                OllamaProvider(
                    OkHttpClient(),
                    StaticSecretStore(null),
                    OllamaConfiguration(
                        connection = connection,
                        timeoutMillis = 100,
                        configuredModels = configuredModels(),
                    ),
                )

            val failure = provider.stream(request()).toList().filterIsInstance<StreamEvent.Failed>().single()

            assertEquals(ProviderErrorCategory.NETWORK, failure.error.category)
            assertTrue(failure.error.retryable)
        }

    @Test
    fun `HTTP can be denied before any local content is sent`() =
        runTest {
            val provider = provider(allowInsecureHttp = false)

            val failure = provider.stream(request()).toList().filterIsInstance<StreamEvent.Failed>().single()

            assertEquals("ollama_insecure_http_requires_opt_in", failure.error.code)
            assertEquals(0, server.requestCount)
        }

    private fun provider(
        toolCalling: Boolean = false,
        allowInsecureHttp: Boolean = true,
        configuredModels: List<ModelDescriptor> = configuredModels(toolCalling),
    ) = OllamaProvider(
        OkHttpClient(),
        StaticSecretStore(null),
        providerConfiguration(toolCalling, allowInsecureHttp, configuredModels),
    )

    private fun providerConfiguration(
        toolCalling: Boolean = false,
        allowInsecureHttp: Boolean = true,
        configuredModels: List<ModelDescriptor> = configuredModels(toolCalling),
    ) = OllamaConfiguration(
        connection =
            ProviderConnection(
                CONNECTION_ID,
                "Ollama test",
                ProviderKind.OLLAMA,
                server.url("/").toString(),
            ),
        allowInsecureHttp = allowInsecureHttp,
        configuredModels = configuredModels,
    )

    private fun configuredModels(toolCalling: Boolean = false) =
        listOf(
            ModelDescriptor(
                "qwen3",
                "Qwen 3",
                ModelCapabilities(supportsToolCalling = toolCalling, supportsParallelToolCalls = toolCalling),
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

    private fun profile() = ModelProfile("profile-1", OLLAMA_PROVIDER_ID, "qwen3", "Qwen 3", CONNECTION_ID)

    private fun ndjson(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "application/x-ndjson").setBody(body)

    private companion object {
        const val CONNECTION_ID = "ollama-connection"
    }
}
