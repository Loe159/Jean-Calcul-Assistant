package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectionTest {
    private val policy = ProfileSelectionPolicy("primary", listOf("vision-fallback", "text-fallback"))

    @Test
    fun `selection skips a primary that does not meet detected capabilities`() {
        val result =
            ProviderSelector.selectModel(
                policy = policy,
                candidates =
                    listOf(
                        modelCandidate("primary", ModelCapabilities()),
                        modelCandidate(
                            "vision-fallback",
                            ModelCapabilities(inputModalities = setOf(ContentModality.TEXT, ContentModality.IMAGE)),
                        ),
                    ),
                requirements =
                    ModelCapabilityRequirements(
                        inputModalities = setOf(ContentModality.TEXT, ContentModality.IMAGE),
                    ),
            )

        result as ProfileSelectionResult.Selected
        assertEquals("vision-fallback", result.value.profile.id)
        assertTrue(result.isFallback)
    }

    @Test
    fun `transient error selects the next compatible fallback`() {
        val result =
            ProviderSelector.fallbackModel(
                policy = policy,
                currentProfileId = "primary",
                error = ProviderError(ProviderErrorCategory.TIMEOUT, "TIMEOUT", "Provider timed out"),
                candidates =
                    listOf(
                        modelCandidate("vision-fallback", ModelCapabilities(), available = false),
                        modelCandidate("text-fallback", ModelCapabilities()),
                    ),
                requirements = ModelCapabilityRequirements(),
            )

        result as ProfileSelectionResult.Selected
        assertEquals("text-fallback", result.value.profile.id)
        assertTrue(result.isFallback)
    }

    @Test
    fun `authentication failure never leaks to another configured profile`() {
        val result =
            ProviderSelector.fallbackModel(
                policy = policy,
                currentProfileId = "primary",
                error = ProviderError(ProviderErrorCategory.AUTHENTICATION, "HTTP_401", "Invalid credential"),
                candidates = listOf(modelCandidate("vision-fallback", ModelCapabilities())),
                requirements = ModelCapabilityRequirements(),
            )

        result as ProfileSelectionResult.Unavailable
        assertEquals(SelectionRejectionReason.FALLBACK_NOT_ALLOWED, result.rejections.single().reason)
    }

    @Test
    fun `agent selector accepts only agent candidates and requirements`() {
        val result =
            ProviderSelector.selectAgent(
                policy = ProfileSelectionPolicy("agent-primary"),
                candidates =
                    listOf(
                        AgentCandidate(
                            profile = AgentProfile("agent-primary", "backend", "agent", "Agent"),
                            capabilities = AgentCapabilities(supportsSkills = true),
                        ),
                    ),
                requirements = AgentCapabilityRequirements(requiresSkills = true),
            )

        assertTrue(result is ProfileSelectionResult.Selected)
    }

    private fun modelCandidate(
        id: String,
        capabilities: ModelCapabilities,
        available: Boolean = true,
    ) = ModelCandidate(
        profile = ModelProfile(id, "provider-$id", "model-$id", id),
        capabilities = capabilities,
        available = available,
    )
}
