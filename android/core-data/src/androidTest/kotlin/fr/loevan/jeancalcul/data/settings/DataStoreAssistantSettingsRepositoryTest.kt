package fr.loevan.jeancalcul.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreAssistantSettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = DataStoreAssistantSettingsRepository(context)

    @Before
    fun clearBefore() = runTest { repository.update { AssistantSettings() } }

    @After
    fun clearAfter() = runTest { repository.update { AssistantSettings() } }

    @Test
    fun settingsSurviveRepositoryRecreation() =
        runTest {
            val provider =
                ProviderConnection(
                    id = "ollama",
                    displayName = "Ollama local",
                    kind = ProviderKind.OLLAMA,
                    baseUrl = "http://192.168.1.10:11434",
                )
            repository.update { it.copy(providers = listOf(provider)) }

            val restored = DataStoreAssistantSettingsRepository(context).settings.first()

            assertEquals(provider, restored.providers.single())
        }

    @Test
    fun legacyDualSelectionsAreNormalizedOnDecodeAndRoundTrip() =
        runTest {
            context.assistantSettingsDataStore.edit { preferences ->
                preferences[LegacySettingsJson.KEY] = LegacySettingsJson.DUAL_SELECTIONS
            }

            val decoded = repository.settings.first()

            assertEquals("agent-a", decoded.activeAgentProfileId)
            assertNull(decoded.activeModelProfileId)
            assertEquals(2, decoded.providers.size)
            assertEquals(1, decoded.modelProfiles.size)
            assertEquals(1, decoded.agentProfiles.size)

            repository.update { it }

            val roundTrip = DataStoreAssistantSettingsRepository(context).settings.first()

            assertEquals(decoded, roundTrip)
            assertEquals("agent-a", roundTrip.activeAgentProfileId)
            assertNull(roundTrip.activeModelProfileId)
        }
}

private object LegacySettingsJson {
    val KEY = stringPreferencesKey("settings_json_v1")

    val DUAL_SELECTIONS =
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
          "activeAgentProfileId": "agent-a"
        }
        """
}
