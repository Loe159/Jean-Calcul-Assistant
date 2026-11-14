package io.github.mangi.eta.data.provider

import io.github.mangi.eta.data.model.AnthropicProviderSetting
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSourceTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinProvidersTest {
    @Test
    fun builtInsKeepOnlyChatGptAndLocalMetadataWithoutInventingModels() {
        val providers = BuiltinProviders.PROVIDERS.associateBy { it.id }
        assertEquals(setOf(BuiltinProviders.OPENAI_ID, BuiltinProviders.VLLM_ID), providers.keys)
        assertTrue(providers.values.all { it is OpenAiCompatibleProviderSetting && it.models.isEmpty() })
        val chatgpt = providers.getValue(BuiltinProviders.OPENAI_ID) as OpenAiCompatibleProviderSetting
        assertEquals(io.github.mangi.eta.data.model.ProviderAuthModes.OPENAI_CHATGPT, chatgpt.authMode)
        assertEquals(OpenAiEndpointMode.RESPONSES, chatgpt.endpointMode)
        val local = providers.getValue(BuiltinProviders.VLLM_ID) as OpenAiCompatibleProviderSetting
        assertEquals(ProviderSourceTypes.CUSTOM, local.sourceType)
        assertEquals(OpenAiEndpointMode.CHAT_COMPLETIONS, local.endpointMode)
        assertEquals("", local.apiKey)
    }

    @Test
    fun xaiBrandingUsesExactOfficialHosts() {
        for (host in listOf("api.x.ai", "cli-chat-proxy.grok.com")) {
            assertEquals(ProviderSourceTypes.XAI, ProviderSourceRegistry.resolve(
                providerId = "custom", baseUrl = "https://$host/v1", providerType = "openai_compatible",
            ))
        }
        assertEquals(ProviderSourceTypes.CUSTOM, ProviderSourceRegistry.resolve(
            providerId = "custom", baseUrl = "https://api.x.ai.example.invalid/v1", providerType = "openai_compatible",
        ))
    }
}
