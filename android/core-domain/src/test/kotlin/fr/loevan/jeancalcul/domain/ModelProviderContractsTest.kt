package fr.loevan.jeancalcul.domain

import fr.loevan.jeancalcul.domain.testing.FakeModelProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProviderContractsTest {
    @Test
    fun `request derives vision audio and tool requirements before sending`() {
        val request =
            ChatRequest(
                requestId = "request-1",
                profile = modelProfile(),
                messages =
                    listOf(
                        ChatMessage(
                            id = "message-1",
                            role = MessageRole.USER,
                            content =
                                listOf(
                                    MessageContent.Text("Analyse ceci"),
                                    MessageContent.Image(MediaReference("content://image/1", "image/png")),
                                    MessageContent.Audio(MediaReference("content://audio/1", "audio/ogg")),
                                ),
                        ),
                    ),
                tools =
                    listOf(
                        VolumeToolSchemas.definitions.first(),
                    ),
            )

        val requirements = request.capabilityRequirements()

        assertEquals(
            setOf(ContentModality.TEXT, ContentModality.IMAGE, ContentModality.AUDIO),
            requirements.inputModalities,
        )
        assertTrue(requirements.requiresToolCalling)
        assertFalse(ModelCapabilities().supports(requirements))
    }

    @Test
    fun `fake model streams scripted events and supports explicit cancellation without network`() =
        runTest {
            val provider =
                FakeModelProvider(
                    defaultCapabilities = ModelCapabilities(supportsToolCalling = true),
                )
            val request = textRequest("request-2")
            provider.enqueue(
                listOf(
                    StreamEvent.Started(request.requestId),
                    StreamEvent.TextDelta(request.requestId, "Bonjour"),
                    StreamEvent.Completed(request.requestId, FinishReason.STOP),
                ),
            )

            val events = provider.stream(request).toList()
            provider.cancel(request.requestId)

            assertEquals(request, provider.requests.single())
            assertEquals("Bonjour", (events[1] as StreamEvent.TextDelta).text)
            assertEquals(listOf(request.requestId), provider.cancelledRequestIds)
            assertTrue(provider.capabilities(request.profile).supportsToolCalling)
        }

    @Test
    fun `external failures expose stable retry and fallback semantics`() {
        val transient = ProviderError(ProviderErrorCategory.RATE_LIMITED, "HTTP_429", "Quota reached", 1_000)
        val authentication = ProviderError(ProviderErrorCategory.AUTHENTICATION, "HTTP_401", "Invalid credential")

        assertTrue(transient.retryable)
        assertTrue(transient.fallbackEligible)
        assertFalse(authentication.retryable)
        assertFalse(authentication.fallbackEligible)
    }

    @Test
    fun `provider usage preserves decimal cost without floating point loss`() {
        val usage =
            ProviderUsage(
                inputTokens = 12,
                outputTokens = 4,
                cost = ProviderCost(amount = "0.00000125"),
            )

        assertEquals("0.00000125", usage.cost?.amount)
        assertEquals("USD", usage.cost?.currencyCode)
        assertTrue(usage.cost?.estimated == true)
    }

    private fun textRequest(requestId: String) =
        ChatRequest(
            requestId = requestId,
            profile = modelProfile(),
            messages =
                listOf(
                    ChatMessage(
                        id = "message-$requestId",
                        role = MessageRole.USER,
                        content = listOf(MessageContent.Text("Bonjour")),
                    ),
                ),
        )

    private fun modelProfile() = ModelProfile("model-profile", "fake", "fake-model", "Fake model")
}
