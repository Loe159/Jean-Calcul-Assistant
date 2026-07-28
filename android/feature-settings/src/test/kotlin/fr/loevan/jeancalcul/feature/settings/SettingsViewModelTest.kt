package fr.loevan.jeancalcul.feature.settings

import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.ConfiguredModelProfile
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import fr.loevan.jeancalcul.network.ConnectionProbeResult
import fr.loevan.jeancalcul.network.ProviderConnectionProbe
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import fr.loevan.jeancalcul.security.SecretValue
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

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
