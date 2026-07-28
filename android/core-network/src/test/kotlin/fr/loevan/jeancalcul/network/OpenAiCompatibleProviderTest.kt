package fr.loevan.jeancalcul.network

import app.cash.turbine.test
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.GenerationOptions
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
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import fr.loevan.jeancalcul.security.SecretValue
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

class OpenAiCompatibleProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `streams text usage and parameters through a custom base URL`() =
        runTest {
            server.enqueue(
                sseResponse(
                    """
                    data: {"choices":[{"delta":{"content":"Bon"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{"content":"jour"},"finish_reason":"stop"}],"usage":{"prompt_tokens":4,"completion_tokens":2}}

                    data: [DONE]

                    """.trimIndent(),
                ),
            )
            val provider = provider(basePath = "/custom/v1/")

            val events =
                provider.stream(
                    request(
                        options =
                            GenerationOptions(
                                temperature = 0.25,
                                maxOutputTokens = 128,
                                stopSequences = listOf("FIN"),
                            ),
                    ),
                ).toList()

            assertEquals(listOf("Bon", "jour"), events.filterIsInstance<StreamEvent.TextDelta>().map { it.text })
            assertEquals(4, events.filterIsInstance<StreamEvent.UsageUpdated>().single().usage.inputTokens)
            assertEquals(FinishReason.STOP, events.filterIsInstance<StreamEvent.Completed>().single().finishReason)
            val recorded = server.takeRequest()
            assertEquals("/custom/v1/chat/completions", recorded.path)
            val body = Json.parseToJsonElement(recorded.body.readUtf8()).toString()
            assertTrue(body.contains("\"model\":\"test-model\""))
            assertTrue(body.contains("\"temperature\":0.25"))
            assertTrue(body.contains("\"max_tokens\":128"))
            assertFalse(body.contains("Authorization"))
        }

    @Test
    fun `reassembles streamed tool calls using domain contracts`() =
        runTest {
            server.enqueue(
                sseResponse(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"audio.set_volume","arguments":"{\"stream\":\"MUSIC\","}}]},"finish_reason":null}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"volumePercent\":50}"}}]},"finish_reason":"tool_calls"}]}

                    data: [DONE]

                    """.trimIndent(),
                ),
            )
            val provider = provider()

            val events = provider.stream(request(tools = true)).toList()

            assertEquals(2, events.filterIsInstance<StreamEvent.ToolCallArgumentsDelta>().size)
            val ready = events.filterIsInstance<StreamEvent.ToolCallReady>().single().call
            assertEquals("call-1", ready.callId)
            assertEquals("audio.set_volume", ready.toolName)
            assertEquals("MUSIC", ready.arguments["stream"].toString().trim('"'))
            assertEquals(FinishReason.TOOL_CALL, events.filterIsInstance<StreamEvent.Completed>().single().finishReason)
            val requestBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).toString()
            assertTrue(requestBody.contains("\"additionalProperties\":false"))
            assertTrue(requestBody.contains("\"audio.set_volume\""))
        }

    @Test
    fun `explicit cancellation cancels the active HTTP call`() =
        runTest {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val provider = provider()
            val request = request(requestId = "cancel-me")

            provider.stream(request).test {
                assertTrue(awaitItem() is StreamEvent.Started)
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                assertTrue(request.requestId in provider.activeRequestIds)

                provider.cancel(request.requestId)

                val failure = awaitItem() as StreamEvent.Failed
                assertEquals(ProviderErrorCategory.CANCELLED, failure.error.category)
                awaitComplete()
                assertFalse(request.requestId in provider.activeRequestIds)
            }
        }

    @Test
    fun `normalizes authentication rate limit and server failures without response content`() =
        runTest {
            val cases =
                listOf(
                    401 to ProviderErrorCategory.AUTHENTICATION,
                    429 to ProviderErrorCategory.RATE_LIMITED,
                    503 to ProviderErrorCategory.SERVICE_UNAVAILABLE,
                )
            cases.forEachIndexed { index, (status, expectedCategory) ->
                server.enqueue(
                    MockResponse()
                        .setResponseCode(status)
                        .setHeader("Retry-After", "2")
                        .setBody("private prompt and sk-not-a-real-secret-123456789"),
                )

                val failure =
                    provider().stream(request(requestId = "failure-$index")).toList()
                        .filterIsInstance<StreamEvent.Failed>()
                        .single()

                assertEquals(expectedCategory, failure.error.category)
                assertFalse(failure.error.message.contains("private prompt"))
                assertFalse(failure.error.message.contains("sk-not-a-real-secret"))
                if (status == 429) assertEquals(2_000L, failure.error.retryAfterMillis)
            }
        }

    @Test
    fun `normalizes network read timeout`() =
        runTest {
            server.enqueue(MockResponse().setHeadersDelay(500, TimeUnit.MILLISECONDS).setBody("ignored"))
            val client = OkHttpClient.Builder().readTimeout(100, TimeUnit.MILLISECONDS).build()

            val failure =
                provider(client = client).stream(request()).toList()
                    .filterIsInstance<StreamEvent.Failed>()
                    .single()

            assertEquals(ProviderErrorCategory.TIMEOUT, failure.error.category)
        }

    @Test
    fun `lists remote models and validates the configured connection`() =
        runTest {
            val response = """{"data":[{"id":"gpt-compatible"},{"id":"local-model"}]}"""
            server.enqueue(MockResponse().setResponseCode(200).setBody(response))
            server.enqueue(MockResponse().setResponseCode(200).setBody(response))
            val provider = provider()

            val models = provider.listModels(profile())
            val validation = provider.validateConnection(profile())

            assertEquals(listOf("gpt-compatible", "local-model"), models.map(ModelDescriptor::id))
            assertTrue(validation is OpenAiConnectionValidation.Success)
            assertTrue((validation as OpenAiConnectionValidation.Success).insecureTransport)
            assertEquals("/v1/models", server.takeRequest().path)
            assertEquals("/v1/models", server.takeRequest().path)
        }

    @Test
    fun `uses a configured model catalog without network discovery`() =
        runTest {
            val configured =
                ModelDescriptor(
                    id = "manual-model",
                    displayName = "Manual model",
                    capabilities = ModelCapabilities(supportsToolCalling = false),
                )
            val provider = provider(configuredModels = listOf(configured))

            assertEquals(listOf(configured), provider.listModels(profile(modelId = "manual-model")))
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `missing secure credential fails before sending content`() =
        runTest {
            val provider = provider(secretId = "provider.api-key")

            val failure =
                provider.stream(request()).toList()
                    .filterIsInstance<StreamEvent.Failed>()
                    .single()

            assertEquals(ProviderErrorCategory.AUTHENTICATION, failure.error.category)
            assertEquals("secret_missing", failure.error.code)
            assertEquals(0, server.requestCount)
        }

    private fun provider(
        client: OkHttpClient = OkHttpClient(),
        basePath: String = "/v1/",
        secretId: String? = null,
        configuredModels: List<ModelDescriptor> = emptyList(),
    ): OpenAiCompatibleProvider {
        val connection =
            ProviderConnection(
                id = CONNECTION_ID,
                displayName = "Compatible test server",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = server.url(basePath).toString(),
                secretId = secretId,
            )
        return OpenAiCompatibleProvider(
            client = client,
            secretStore = AlwaysMissingSecretStore,
            configuration =
                OpenAiCompatibleConfiguration(
                    connection = connection,
                    configuredModels = configuredModels,
                ),
        )
    }

    private fun request(
        requestId: String = "request-1",
        options: GenerationOptions = GenerationOptions(),
        tools: Boolean = false,
    ) = ChatRequest(
        requestId = requestId,
        profile = profile(),
        messages =
            listOf(
                ChatMessage(
                    id = "message-$requestId",
                    role = MessageRole.USER,
                    content = listOf(MessageContent.Text("Texte prive a ne jamais journaliser")),
                ),
            ),
        tools = if (tools) listOf(VolumeToolSchemas.definitions[1]) else emptyList(),
        options = options,
    )

    private fun profile(modelId: String = "test-model") =
        ModelProfile(
            id = "profile-1",
            providerId = OPENAI_COMPATIBLE_PROVIDER_ID,
            modelId = modelId,
            displayName = "Test model",
            connectionId = CONNECTION_ID,
        )

    private fun sseResponse(body: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/event-stream")
            .setBody(body)

    private companion object {
        const val CONNECTION_ID = "connection-1"
    }
}

private object AlwaysMissingSecretStore : SecretStore {
    override suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)

    override suspend fun get(id: SecretId): SecretStoreResult<SecretValue?> = SecretStoreResult.Success(null)

    override suspend fun delete(id: SecretId): SecretStoreResult<Boolean> = SecretStoreResult.Success(false)

    override suspend fun reset(): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)
}
