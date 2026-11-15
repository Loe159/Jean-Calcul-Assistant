package io.github.mangi.eta.product

import android.content.Context
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.model.*
import io.github.mangi.eta.data.provider.BuiltinProviders
import io.github.mangi.eta.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JeanCalculProviderRepositoryTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        EtaDatabase.closeForTests(); context.deleteDatabase("eta.db")
        SettingsDataStore.init(context); ProviderRepository.init(context)
        runBlocking { SettingsDataStore.setSelection(null, null); SettingsDataStore.setOfficialModelCatalogRevision(0) }
    }
    private fun local(id: String = "local", key: String = "") = OpenAiCompatibleProviderSetting(
        id = id, name = "vLLM", baseUrl = "http://192.0.2.10:8000/v1", apiKey = key,
        models = listOf(Model(id = "$id-model", modelId = "served-model", displayName = "Local model", contextWindowOverride = 8192, toolCall = true)))
    @Test fun defaultsHaveOnlyTheApprovedConnectionsAndNoInventedServerModels() = runBlocking {
        ProviderRepository.ensureBuiltInsMerged()
        assertEquals(setOf(BuiltinProviders.OPENAI_ID, BuiltinProviders.VLLM_ID), ProviderRepository.allProviders().map { it.id }.toSet())
        assertTrue(ProviderRepository.allProviders().all { it.models.isEmpty() })
    }
    @Test fun localProfileAndModelRoundTripAndReachTheRuntimeWithoutAKey() = runBlocking {
        val source = local().copy(customHeaders = listOf(CustomHeader("X-Fixture", "value")))
        ProviderRepository.addProvider(source)
        RuntimeConfigRepository.setSelectedProviderId(source.id)
        RuntimeConfigRepository.setSelectedModelId(source.models.single().id)
        val restored = requireNotNull(ProviderRepository.providerById(source.id))
        assertEquals(source.models, restored.models)
        assertEquals(source.customHeaders, restored.customHeaders)
        val config = requireNotNull(RuntimeConfigRepository.currentRuntimeConfig())
        assertEquals("", config.apiKey); assertEquals("served-model", config.model)
        assertEquals(8192, config.contextWindow); assertEquals(source.baseUrl, config.baseUrl)
    }
    @Test fun selectionRemembersEachLocalServersModelAndUsesUpdatedCredentials() = runBlocking {
        val first = local("first"); val second = local("second")
        ProviderRepository.addProvider(first); ProviderRepository.addProvider(second)
        RuntimeConfigRepository.setSelectedProviderId(first.id)
        RuntimeConfigRepository.setSelectedModelId(first.models.single().id)
        RuntimeConfigRepository.setSelectedProviderId(second.id)
        RuntimeConfigRepository.setSelectedModelId(second.models.single().id)
        RuntimeConfigRepository.setSelectedProviderId(first.id)
        ProviderRepository.updateProvider(first.copy(apiKey = "synthetic-local-key"))
        val config = requireNotNull(RuntimeConfigRepository.currentRuntimeConfig())
        assertEquals(first.models.single().modelId, config.model)
        assertEquals("synthetic-local-key", config.apiKey)
    }
    @Test fun oauthCopiesNeverCopyASecretOrAModelIdentity() = runBlocking {
        val oauth = local("chatgpt").copy(authMode = ProviderAuthModes.OPENAI_CHATGPT, apiKey = "must-not-be-persisted")
        ProviderRepository.addProvider(oauth)
        val restored = requireNotNull(ProviderRepository.providerById(oauth.id))
        assertEquals("", restored.apiKey)
        val copy = requireNotNull(ProviderRepository.copyProvider(oauth.id))
        assertEquals(ProviderAuthModes.OPENAI_CHATGPT, copy.authMode)
        assertEquals("", copy.apiKey)
        assertNotEquals(restored.id, copy.id); assertNotEquals(restored.models.single().id, copy.models.single().id)
    }
    @Test fun excludedProvidersCannotEnterStorageAndDeletingLocalRepairsSelection() = runBlocking {
        val unsupported = AnthropicProviderSetting(id="excluded", name="excluded", baseUrl="https://example.invalid")
        assertTrue(runCatching { ProviderRepository.addProvider(unsupported) }.isFailure)
        assertNull(ProviderRepository.providerById("excluded"))
        val provider = local(); ProviderRepository.addProvider(provider)
        RuntimeConfigRepository.setSelectedProviderId(provider.id)
        ProviderRepository.deleteProvider(provider.id)
        assertNull(ProviderRepository.providerById(provider.id))
        assertNull(RuntimeConfigRepository.currentRuntimeConfig())
    }
}
