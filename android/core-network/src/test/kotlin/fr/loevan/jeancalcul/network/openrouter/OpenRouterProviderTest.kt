@file:Suppress("MaxLineLength")

package fr.loevan.jeancalcul.network.openrouter

import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.ContentModality
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenRouterProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `reuses OpenAI streaming with application headers fallbacks tools and exact cost`() =
        runTest {
            server.enqueue(
                sse(
                    """
                    : OPENROUTER PROCESSING

                    data: {"choices":[{"delta":{"content":"Bonjour"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":5,"cost":0.00000125}}

                    data: [DONE]

                    """.trimIndent(),
                ),
            )
            val provider = provider(fallbacks = listOf("google/gemini-fallback"))

            val events = provider.stream(request(tools = true)).toList()

            assertEquals("Bonjour", events.filterIsInstance<StreamEvent.TextDelta>().single().text)
            val usage = events.filterIsInstance<StreamEvent.UsageUpdated>().single().usage
            assertEquals(11, usage.inputTokens)
            assertEquals("0.00000125", usage.cost?.amount)
            assertEquals("USD", usage.cost?.currencyCode)

            val recorded = server.takeRequest()
            assertEquals("/api/v1/chat/completions", recorded.path)
            assertEquals("Bearer test-secret", recorded.getHeader("Authorization"))
            assertEquals("Jean-Calcul tests", recorded.getHeader("X-OpenRouter-Title"))
            assertEquals("https://example.test", recorded.getHeader("HTTP-Referer"))
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).toString()
            assertTrue(body.contains("\"models\":[\"google/gemini-fallback\"]"))
            assertTrue(body.contains("\"usage\":{\"include\":true}"))
            assertTrue(body.contains("\"tools\""))
            assertFalse(body.contains("test-secret"))
        }

    @Test
    fun `discovers arbitrary models and advertised capabilities`() =
        runTest {
            server.enqueue(
                MockResponse().setBody(
                    """{"data":[{"id":"anthropic/claude-test","name":"Claude Test","context_length":200000,""" +
                        """"architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},""" +
                        """"supported_parameters":["tools"],"top_provider":{"max_completion_tokens":8192}}]}""",
                ),
            )

            val model = provider().listModels(profile()).single()

            assertEquals("anthropic/claude-test", model.id)
            assertEquals("Claude Test", model.displayName)
            assertTrue(model.capabilities.supportsToolCalling)
            assertTrue(ContentModality.IMAGE in model.capabilities.inputModalities)
            assertEquals(200_000, model.capabilities.limits.maxContextTokens)
            assertEquals(8_192, model.capabilities.limits.maxOutputTokens)
        }

    @Test
    fun `unavailable model is explicit and does not leak response content`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404).setBody("private prompt test-secret"))

            val failure = provider().stream(request()).toList().filterIsInstance<StreamEvent.Failed>().single()

            assertEquals(ProviderErrorCategory.MODEL_NOT_FOUND, failure.error.category)
            assertEquals("openrouter_http_404", failure.error.code)
            assertFalse(failure.error.message.contains("private prompt"))
            assertFalse(failure.error.message.contains("test-secret"))
        }

    private fun provider(fallbacks: List<String> = emptyList()) =
        OpenRouterProvider(
            OkHttpClient(),
            StaticSecretStore(),
            OpenRouterConfiguration(
                connection =
                    ProviderConnection(
                        CONNECTION_ID,
                        "OpenRouter test",
                        ProviderKind.OPENROUTER,
                        server.url("/api/v1/").toString(),
                        "provider.openrouter",
                    ),
                fallbackModelIds = fallbacks,
                applicationName = "Jean-Calcul tests",
                applicationUrl = "https://example.test",
            ),
        )

    private fun request(tools: Boolean = false) =
        ChatRequest(
            "request-1",
            profile(),
            listOf(ChatMessage("message-1", MessageRole.USER, listOf(MessageContent.Text("Bonjour")))),
            tools = if (tools) listOf(VolumeToolSchemas.definitions[1]) else emptyList(),
        )

    private fun profile() =
        ModelProfile("profile-1", OPENROUTER_PROVIDER_ID, "anthropic/claude-test", "Claude Test", CONNECTION_ID)

    private fun sse(body: String) =
        MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream").setBody(body)

    private companion object {
        const val CONNECTION_ID = "openrouter-connection"
    }
}
