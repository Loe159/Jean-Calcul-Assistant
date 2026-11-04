package fr.loevan.jeancalcul.feature.settings

import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AppearanceSettings
import fr.loevan.jeancalcul.domain.AppearanceTheme
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.ConfiguredAgentProfile
import fr.loevan.jeancalcul.domain.ConfiguredModelProfile
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.domain.VoiceSettings
import fr.loevan.jeancalcul.network.ConnectionProbeResult
import fr.loevan.jeancalcul.network.ProviderConnectionProbe
import fr.loevan.jeancalcul.network.codex.CHATGPT_PLAN_AGENT_ID
import fr.loevan.jeancalcul.network.codex.ChatGptPlanAccount
import fr.loevan.jeancalcul.network.codex.ChatGptPlanAuthManager
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import fr.loevan.jeancalcul.security.SecretValue
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

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
            ModelProfile("model-a", "openai_compatible", "qwen", "Model A", connectionId = "model-provider"),
        )

    private val agentProfile =
        ConfiguredAgentProfile(
            AgentProfile("agent-a", "codex-companion", "assistant", "Agent A", connectionId = "agent-backend"),
        )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `saving provider stores secret separately and never in settings`() =
        runTest(dispatcher) {
            val repository = FakeSettingsRepository()
            val secretStore = RecordingSecretStore()
            val viewModel = SettingsViewModel(repository, secretStore, SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.saveProvider(
                ProviderDraft(null, "OpenAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.example.test/v1"),
                "top-secret".toCharArray(),
            )
            advanceUntilIdle()

            val provider = repository.value.providers.single()
            assertNotNull(provider.secretId)
            assertEquals("top-secret", secretStore.lastSecret)
            assertTrue(provider.toString().contains("top-secret").not())
        }

    @Test
    fun `invalid model profile cannot become active`() =
        runTest(dispatcher) {
            val model = ConfiguredModelProfile(ModelProfile("model", "missing", "gpt", "GPT", connectionId = "missing"))
            val repository = FakeSettingsRepository(AssistantSettings(modelProfiles = listOf(model)))
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.activateModel("model")
            advanceUntilIdle()

            assertNull(repository.value.activeModelProfileId)
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("fournisseur"))
        }

    @Test
    fun `editing an active provider to an invalid selection deactivates the profile`() =
        runTest(dispatcher) {
            val provider =
                ProviderConnection("provider", "Local", ProviderKind.OLLAMA, "http://localhost:11434")
            val model =
                ConfiguredModelProfile(
                    ModelProfile("model", "ollama", "qwen", "Local", connectionId = provider.id),
                )
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(provider),
                        modelProfiles = listOf(model),
                        activeModelProfileId = model.profile.id,
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.saveProvider(
                ProviderDraft(provider.id, provider.displayName, provider.kind, provider.baseUrl, enabled = false),
            )
            advanceUntilIdle()

            assertNull(repository.value.activeModelProfileId)
        }

    @Test
    fun `connection failure exposes normalized actionable message`() =
        runTest(dispatcher) {
            val provider = ProviderConnection("provider", "Remote", ProviderKind.ANTHROPIC, "https://api.example.test")
            val repository = FakeSettingsRepository(AssistantSettings(providers = listOf(provider)))
            val probe =
                object : ProviderConnectionProbe {
                    override suspend fun test(connection: ProviderConnection) =
                        ConnectionProbeResult.Failure("authentication_failed", "Remplacez la cle API.", true)
                }
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), probe)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.testConnection(provider.id)
            advanceUntilIdle()

            val result = viewModel.uiState.value.connectionTests[provider.id] as ConnectionTestUiState.Failure
            assertEquals("authentication_failed", result.code)
            assertTrue(result.message.contains("cle API"))
        }

    @Test
    fun `activating a model deactivates a legacy active agent`() =
        runTest(dispatcher) {
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(modelProvider, agentBackend),
                        modelProfiles = listOf(modelProfile),
                        agentProfiles = listOf(agentProfile),
                        activeModelProfileId = "model-a",
                        activeAgentProfileId = "agent-a",
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.activateModel("model-a")
            advanceUntilIdle()

            assertEquals("model-a", repository.value.activeModelProfileId)
            assertNull(repository.value.activeAgentProfileId)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `activating an agent deactivates a legacy active model`() =
        runTest(dispatcher) {
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(modelProvider, agentBackend),
                        modelProfiles = listOf(modelProfile),
                        agentProfiles = listOf(agentProfile),
                        activeModelProfileId = "model-a",
                        activeAgentProfileId = "agent-a",
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.activateAgent("agent-a")
            advanceUntilIdle()

            assertEquals("agent-a", repository.value.activeAgentProfileId)
            assertNull(repository.value.activeModelProfileId)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `failing to activate an invalid model repairs a legacy dual selection`() =
        runTest(dispatcher) {
            val offlineProvider = modelProvider.copy(id = "offline", enabled = false)
            val offlineModel = modelProfile.copy(profile = modelProfile.profile.copy(connectionId = "offline"))
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(offlineProvider, agentBackend),
                        modelProfiles = listOf(offlineModel),
                        agentProfiles = listOf(agentProfile),
                        activeModelProfileId = "model-a",
                        activeAgentProfileId = "agent-a",
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

            viewModel.activateModel("model-a")
            advanceUntilIdle()

            assertNull(repository.value.activeModelProfileId)
            assertEquals("agent-a", repository.value.activeAgentProfileId)
            assertTrue(viewModel.uiState.value.errorMessage!!.contains("desactive"))
        }

    @Test
    fun `deleting a provider clears the selection and preserves voice appearance and other profiles`() =
        runTest(dispatcher) {
            val voice = VoiceSettings(languageTag = "fr-CA")
            val appearance = AppearanceSettings(theme = AppearanceTheme.DARK)
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(modelProvider, agentBackend),
                        modelProfiles = listOf(modelProfile),
                        agentProfiles = listOf(agentProfile),
                        activeModelProfileId = "model-a",
                        voice = voice,
                        appearance = appearance,
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.deleteProvider("model-provider")
            advanceUntilIdle()

            assertNull(repository.value.activeModelProfileId)
            assertEquals(listOf(agentBackend), repository.value.providers)
            assertEquals(listOf(agentProfile), repository.value.agentProfiles)
            assertEquals(voice, repository.value.voice)
            assertEquals(appearance, repository.value.appearance)
        }

    @Test
    fun `deleting an active agent clears its selection without touching the active model`() =
        runTest(dispatcher) {
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(modelProvider, agentBackend),
                        modelProfiles = listOf(modelProfile),
                        agentProfiles = listOf(agentProfile),
                        activeModelProfileId = "model-a",
                        activeAgentProfileId = "agent-a",
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.deleteAgent("agent-a")
            advanceUntilIdle()

            assertNull(repository.value.activeAgentProfileId)
            assertEquals("model-a", repository.value.activeModelProfileId)
        }

    @Test
    fun `saving an unrelated provider normalizes a legacy dual selection`() =
        runTest(dispatcher) {
            val third =
                ProviderConnection(
                    id = "third",
                    displayName = "Third",
                    kind = ProviderKind.ANTHROPIC,
                    baseUrl = "https://api.anthropic.com",
                )
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(modelProvider, agentBackend),
                        modelProfiles = listOf(modelProfile),
                        agentProfiles = listOf(agentProfile),
                        activeModelProfileId = "model-a",
                        activeAgentProfileId = "agent-a",
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.saveProvider(
                ProviderDraft(third.id, third.displayName, third.kind, third.baseUrl),
            )
            advanceUntilIdle()

            assertEquals("agent-a", repository.value.activeAgentProfileId)
            assertNull(repository.value.activeModelProfileId)
        }

    @Test
    fun `connecting the chatgpt plan activates the agent exclusively`() =
        runTest(dispatcher) {
            val authManager = mockk<ChatGptPlanAuthManager>()
            coEvery { authManager.signIn() } returns ChatGptPlanAccount("secret.chatgpt", "user@example.test")
            val repository =
                FakeSettingsRepository(
                    AssistantSettings(
                        providers = listOf(modelProvider),
                        modelProfiles = listOf(modelProfile),
                        activeModelProfileId = "model-a",
                    ),
                )
            val viewModel = SettingsViewModel(repository, RecordingSecretStore(), SuccessfulProbe(), authManager)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.connectChatGptPlan()
            advanceUntilIdle()

            assertEquals(CHATGPT_PLAN_AGENT_ID, repository.value.activeAgentProfileId)
            assertNull(repository.value.activeModelProfileId)
            assertFalse(viewModel.uiState.value.chatGptSignInRunning)
            assertNull(viewModel.uiState.value.errorMessage)
        }
}

private class FakeSettingsRepository(initial: AssistantSettings = AssistantSettings()) : AssistantSettingsRepository {
    private val mutable = MutableStateFlow(initial)
    override val settings = mutable
    val value: AssistantSettings get() = mutable.value

    override suspend fun update(transform: (AssistantSettings) -> AssistantSettings) {
        mutable.update(transform)
    }
}

private class SuccessfulProbe : ProviderConnectionProbe {
    override suspend fun test(connection: ProviderConnection) = ConnectionProbeResult.Success("OK", false)
}

private class RecordingSecretStore : SecretStore {
    var lastSecret: String? = null

    override suspend fun put(
        id: SecretId,
        secret: CharArray,
    ): SecretStoreResult<Unit> {
        lastSecret = secret.concatToString()
        return SecretStoreResult.Success(Unit)
    }

    override suspend fun get(id: SecretId): SecretStoreResult<SecretValue?> = SecretStoreResult.Success(null)

    override suspend fun delete(id: SecretId): SecretStoreResult<Boolean> = SecretStoreResult.Success(true)

    override suspend fun reset(): SecretStoreResult<Unit> = SecretStoreResult.Success(Unit)
}
