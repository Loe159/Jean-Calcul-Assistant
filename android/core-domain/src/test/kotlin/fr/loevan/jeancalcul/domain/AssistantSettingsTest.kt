package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSettingsTest {
    @Test
    fun `provider URL rejects embedded credentials and query parameters`() {
        val provider =
            ProviderConnection(
                id = "provider",
                displayName = "Provider",
                kind = ProviderKind.OPENAI_COMPATIBLE,
                baseUrl = "https://user:secret@example.test/v1?token=secret",
            )

        val errors = AssistantSettingsValidator.providerErrors(provider)

        assertEquals(1, errors.size)
    }

    @Test
    fun `model cannot activate when its provider is disabled`() {
        val provider =
            ProviderConnection("provider", "Provider", ProviderKind.OLLAMA, "http://localhost:11434", enabled = false)
        val model =
            ConfiguredModelProfile(
                ModelProfile("model", "ollama", "qwen", "Local", connectionId = provider.id),
            )
        val settings = AssistantSettings(providers = listOf(provider), modelProfiles = listOf(model))

        val errors = AssistantSettingsValidator.modelActivationErrors(model, settings)

        assertTrue(errors.any { it.contains("desactive") })
    }

    @Test
    fun `agent profile requires a distinct agent backend`() {
        val provider = ProviderConnection("provider", "Provider", ProviderKind.ANTHROPIC, "https://api.anthropic.com")
        val agent =
            ConfiguredAgentProfile(
                AgentProfile("agent", "remote", "assistant", "Agent", connectionId = provider.id),
            )
        val settings = AssistantSettings(providers = listOf(provider), agentProfiles = listOf(agent))

        val errors = AssistantSettingsValidator.agentActivationErrors(agent, settings)

        assertTrue(errors.any { it.contains("distinct") })
    }
}
