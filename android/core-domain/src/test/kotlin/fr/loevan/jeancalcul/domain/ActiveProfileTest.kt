package fr.loevan.jeancalcul.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveProfileTest {
    private val modelProvider =
        ProviderConnection(
            id = "model-provider",
            displayName = "OpenAI compatible",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.test/v1",
            secretId = "secret.model",
        )

    private val agentBackend =
        ProviderConnection(
            id = "agent-backend",
            displayName = "Codex companion",
            kind = ProviderKind.AGENT_BACKEND,
            baseUrl = "http://127.0.0.1:43120",
            secretId = "secret.agent",
        )

    private val modelProfile =
        ConfiguredModelProfile(
            profile =
                ModelProfile(
                    id = "model-a",
                    providerId = "openai_compatible",
                    modelId = "qwen",
                    displayName = "Model A",
                    connectionId = "model-provider",
                ),
            temperature = 1.0,
            maxOutputTokens = 128,
        )

    private val agentProfile =
        ConfiguredAgentProfile(
            profile =
                AgentProfile(
                    id = "agent-a",
                    backendId = "codex-companion",
                    agentId = "assistant",
                    displayName = "Agent A",
                    connectionId = "agent-backend",
                ),
        )

    /** Genuine pre-JC-01 DataStore payload: both selections active, explicit voice and appearance. */
    private val legacyDualSelectionsJson =
        """
        {
          "providers": [
            {
              "id": "model-provider",
              "displayName": "OpenAI compatible",
              "kind": "OPENAI_COMPATIBLE",
              "baseUrl": "https://api.example.test/v1",
              "secretId": "secret.model",
              "enabled": true
            },
            {
              "id": "agent-backend",
              "displayName": "Codex companion",
              "kind": "AGENT_BACKEND",
              "baseUrl": "http://127.0.0.1:43120",
              "secretId": "secret.agent",
              "enabled": true
            }
          ],
          "modelProfiles": [
            {
              "profile": {
                "id": "model-a",
                "providerId": "openai_compatible",
                "modelId": "qwen",
                "displayName": "Model A",
                "connectionId": "model-provider",
                "enabled": true
              }
            }
          ],
          "agentProfiles": [
            {
              "profile": {
                "id": "agent-a",
                "backendId": "codex-companion",
                "agentId": "assistant",
                "displayName": "Agent A",
                "connectionId": "agent-backend",
                "enabled": true
              }
            }
          ],
          "activeModelProfileId": "model-a",
          "activeAgentProfileId": "agent-a",
          "voice": {
            "languageTag": "fr-CA",
            "inputMode": "TEXT_ONLY",
            "partialResultsEnabled": false,
            "bluetoothEnabled": false
          },
          "appearance": {
            "theme": "DARK",
            "reduceMotion": true,
            "blurEnabled": false,
            "shadersEnabled": false,
            "highContrast": true
          }
        }
        """.trimIndent()

    @Test
    fun `no selection resolves to none and normalization is identity`() {
        val settings = AssistantSettings()

        assertTrue(ActiveProfileResolver.resolve(settings) is ActiveProfile.None)
        assertEquals(settings, ActiveProfileResolver.normalize(settings))
    }

    @Test
    fun `an active model alone resolves to the model profile`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider),
                modelProfiles = listOf(modelProfile),
                activeModelProfileId = "model-a",
            )

        val resolved = ActiveProfileResolver.resolve(settings) as ActiveProfile.Model

        assertEquals(modelProfile, resolved.configured)
        assertEquals(settings, ActiveProfileResolver.normalize(settings))
    }

    @Test
    fun `an active agent alone resolves to the agent profile`() {
        val settings =
            AssistantSettings(
                providers = listOf(agentBackend),
                agentProfiles = listOf(agentProfile),
                activeAgentProfileId = "agent-a",
            )

        val resolved = ActiveProfileResolver.resolve(settings) as ActiveProfile.Agent

        assertEquals(agentProfile, resolved.configured)
        assertEquals(settings, ActiveProfileResolver.normalize(settings))
    }

    @Test
    fun `a legacy dual selection keeps the valid agent and clears the model`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "model-a",
                activeAgentProfileId = "agent-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertEquals("agent-a", normalized.activeAgentProfileId)
        assertNull(normalized.activeModelProfileId)
        assertEquals(modelProfile, normalized.modelProfiles.single())
        assertEquals(agentProfile, normalized.agentProfiles.single())
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.Agent)
    }

    @Test
    fun `a legacy dual selection with an invalid agent keeps the valid model`() {
        val disabledAgent = agentProfile.copy(profile = agentProfile.profile.copy(enabled = false))
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(disabledAgent),
                activeModelProfileId = "model-a",
                activeAgentProfileId = "agent-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeAgentProfileId)
        assertEquals("model-a", normalized.activeModelProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.Model)
    }

    @Test
    fun `no valid selection resolves to none with no hidden fallback`() {
        val offlineProvider = modelProvider.copy(id = "offline-provider", enabled = false)
        val offlineModel =
            modelProfile.copy(
                profile = modelProfile.profile.copy(id = "model-b", connectionId = "offline-provider"),
            )
        val settings =
            AssistantSettings(
                providers = listOf(offlineProvider, agentBackend),
                modelProfiles = listOf(offlineModel),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "model-b",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeModelProfileId)
        assertNull(normalized.activeAgentProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `a deleted connection clears its selection`() {
        val settings =
            AssistantSettings(
                providers = listOf(agentBackend),
                modelProfiles = listOf(modelProfile),
                activeModelProfileId = "model-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeModelProfileId)
        assertEquals(modelProfile, normalized.modelProfiles.single())
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `a disabled connection makes the selection explicitly unavailable`() {
        val disabledProvider = modelProvider.copy(enabled = false)
        val settings =
            AssistantSettings(
                providers = listOf(disabledProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "model-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeModelProfileId)
        assertNull(normalized.activeAgentProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `a model on an agent backend connection is never effective`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles =
                    listOf(
                        modelProfile.copy(
                            profile = modelProfile.profile.copy(connectionId = agentBackend.id),
                        ),
                    ),
                activeModelProfileId = "model-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeModelProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `an agent on a model provider connection is never effective`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                agentProfiles =
                    listOf(
                        agentProfile.copy(
                            profile = agentProfile.profile.copy(connectionId = modelProvider.id),
                        ),
                    ),
                activeAgentProfileId = "agent-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeAgentProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `a disabled profile is never effective even when selected`() {
        val disabledModel = modelProfile.copy(profile = modelProfile.profile.copy(enabled = false))
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider),
                modelProfiles = listOf(disabledModel),
                activeModelProfileId = "model-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeModelProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `normalization preserves voice appearance and unselected profiles`() {
        val voice = VoiceSettings(languageTag = "fr-CA", inputMode = VoiceInputMode.TEXT_ONLY)
        val appearance = AppearanceSettings(theme = AppearanceTheme.DARK)
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "model-a",
                activeAgentProfileId = "agent-a",
                voice = voice,
                appearance = appearance,
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertEquals(voice, normalized.voice)
        assertEquals(appearance, normalized.appearance)
        assertEquals(listOf(modelProvider, agentBackend), normalized.providers)
        assertEquals(listOf(modelProfile), normalized.modelProfiles)
        assertEquals(listOf(agentProfile), normalized.agentProfiles)
    }

    @Test
    fun `normalization is idempotent`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "model-a",
                activeAgentProfileId = "agent-a",
            )

        val once = ActiveProfileResolver.normalize(settings)

        assertEquals(once, ActiveProfileResolver.normalize(once))
    }

    @Test
    fun `resolving a legacy dual selection exposes the configured agent with its options`() {
        val agent =
            agentProfile.copy(
                grantedPermissions = setOf(AgentPermission.TASKS_READ),
                policyMode = AgentPolicyMode.BALANCED,
            )
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agent),
                activeModelProfileId = "model-a",
                activeAgentProfileId = "agent-a",
            )

        val resolved = ActiveProfileResolver.resolve(settings) as ActiveProfile.Agent

        assertEquals(agent, resolved.configured)
        assertEquals("agent-a", resolved.profileId)
    }

    @Test
    fun `a dangling model selection is dropped when a valid agent is selected`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "deleted-model",
                activeAgentProfileId = "agent-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeModelProfileId)
        assertEquals("agent-a", normalized.activeAgentProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.Agent)
    }

    @Test
    fun `a dangling agent selection with no other selection resolves to none`() {
        val settings =
            AssistantSettings(
                providers = listOf(modelProvider, agentBackend),
                modelProfiles = listOf(modelProfile),
                agentProfiles = listOf(agentProfile),
                activeAgentProfileId = "deleted-agent",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertNull(normalized.activeAgentProfileId)
        assertNull(normalized.activeModelProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.None)
    }

    @Test
    fun `a valid agent wins over an invalid model in a legacy dual selection`() {
        val offlineProvider = modelProvider.copy(id = "offline", enabled = false)
        val offlineModel =
            modelProfile.copy(profile = modelProfile.profile.copy(connectionId = "offline"))
        val settings =
            AssistantSettings(
                providers = listOf(offlineProvider, agentBackend),
                modelProfiles = listOf(offlineModel),
                agentProfiles = listOf(agentProfile),
                activeModelProfileId = "model-a",
                activeAgentProfileId = "agent-a",
            )

        val normalized = ActiveProfileResolver.normalize(settings)

        assertEquals("agent-a", normalized.activeAgentProfileId)
        assertNull(normalized.activeModelProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalized) is ActiveProfile.Agent)
    }

    @Test
    fun `legacy dual selection json decodes normalizes and round trips`() {
        val json = Json { ignoreUnknownKeys = true }

        val decoded = json.decodeFromString<AssistantSettings>(legacyDualSelectionsJson)

        assertEquals("model-a", decoded.activeModelProfileId)
        assertEquals("agent-a", decoded.activeAgentProfileId)

        val normalized = ActiveProfileResolver.normalize(decoded)

        assertEquals("agent-a", normalized.activeAgentProfileId)
        assertNull(normalized.activeModelProfileId)
        assertEquals(decoded.voice, normalized.voice)
        assertEquals(decoded.appearance, normalized.appearance)

        val roundTrip = json.decodeFromString<AssistantSettings>(json.encodeToString(normalized))

        assertEquals(normalized, roundTrip)
        assertEquals("agent-a", roundTrip.activeAgentProfileId)
        assertNull(roundTrip.activeModelProfileId)
        assertEquals(decoded.voice, roundTrip.voice)
        assertEquals(decoded.appearance, roundTrip.appearance)

        val invalidAgent =
            decoded.copy(
                agentProfiles = decoded.agentProfiles.map { it.copy(profile = it.profile.copy(enabled = false)) },
            )
        val normalizedInvalid =
            ActiveProfileResolver.normalize(
                json.decodeFromString<AssistantSettings>(json.encodeToString(invalidAgent)),
            )

        assertNull(normalizedInvalid.activeAgentProfileId)
        assertEquals("model-a", normalizedInvalid.activeModelProfileId)
        assertTrue(ActiveProfileResolver.resolve(normalizedInvalid) is ActiveProfile.Model)
        assertEquals(decoded.voice, normalizedInvalid.voice)
        assertEquals(decoded.appearance, normalizedInvalid.appearance)
    }
}
