package com.jeancalcul.assistant.ui

import com.jeancalcul.assistant.data.HermesSettings
import com.jeancalcul.assistant.data.SettingsRepository
import com.jeancalcul.assistant.network.HermesApi
import com.jeancalcul.assistant.network.HermesHealth
import com.jeancalcul.assistant.network.HermesResponse
import com.jeancalcul.assistant.network.MobileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test fun `charge les reglages et synchronise les champs de brouillon`() = runTest {
        val settings = HermesSettings("http://10.0.2.2:8765", "secret")
        val viewModel = MainViewModel(FakeSettingsStore(settings), FakeHermesClient())

        assertEquals(settings, viewModel.uiState.value.settings)
        assertEquals("http://10.0.2.2:8765", viewModel.uiState.value.baseUrlDraft)
        assertEquals("secret", viewModel.uiState.value.tokenDraft)
    }

    @Test fun `enregistre les reglages nettoyes et affiche une confirmation`() = runTest {
        val store = FakeSettingsStore()
        val viewModel = MainViewModel(store, FakeHermesClient())

        viewModel.updateBaseUrl("  http://localhost:8765/// ")
        viewModel.updateToken(" token ")
        viewModel.saveSettings()

        assertEquals(HermesSettings("http://localhost:8765", "token"), store.savedSettings)
        assertEquals("Configuration enregistrée.", viewModel.uiState.value.status)
    }

    @Test fun `teste la connexion Hermes avec succes`() = runTest {
        val viewModel = MainViewModel(FakeSettingsStore(), FakeHermesClient(health = HermesHealth("ok", "Hermes", "1.2.3")))

        viewModel.testConnection()

        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals("Hermes OK: Hermes (1.2.3)", viewModel.uiState.value.status)
    }

    @Test fun `teste la connexion Hermes et remonte les erreurs`() = runTest {
        val viewModel = MainViewModel(FakeSettingsStore(), FakeHermesClient(error = IllegalStateException("serveur indisponible")))

        viewModel.testConnection()

        assertFalse(viewModel.uiState.value.isBusy)
        assertEquals("Erreur: serveur indisponible", viewModel.uiState.value.status)
    }

    @Test fun `envoie un message et ajoute la reponse a la conversation`() = runTest {
        val client = FakeHermesClient(response = HermesResponse(message = "Bonjour Jean"))
        val viewModel = MainViewModel(FakeSettingsStore(), client)
        viewModel.updateMessage("  Salut  ")

        viewModel.sendMessage()

        val state = viewModel.uiState.value
        assertEquals("", state.messageDraft)
        assertEquals("Réponse reçue.", state.status)
        assertEquals(listOf(ChatMessage("Vous", "Salut"), ChatMessage("Hermes", "Bonjour Jean")), state.messages)
        assertEquals("Salut", client.lastRequest?.input?.text)
    }

    @Test fun `ignore les messages vides`() = runTest {
        val client = FakeHermesClient()
        val viewModel = MainViewModel(FakeSettingsStore(), client)
        viewModel.updateMessage("   ")

        viewModel.sendMessage()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals(0, client.sendCount)
    }
}

private class FakeSettingsStore(initial: HermesSettings = HermesSettings()) : SettingsRepository {
    private val settingsFlow = MutableStateFlow(initial)
    var savedSettings: HermesSettings? = null
    override val settings = settingsFlow

    override suspend fun save(baseUrl: String, token: String) {
        savedSettings = HermesSettings(baseUrl.trim().trimEnd('/'), token.trim())
        settingsFlow.value = savedSettings!!
    }
}

private class FakeHermesClient(
    private val health: HermesHealth = HermesHealth("ok"),
    private val response: HermesResponse = HermesResponse(message = "OK"),
    private val error: Throwable? = null
) : HermesApi {
    var lastRequest: MobileRequest? = null
    var sendCount = 0

    override suspend fun health(baseUrl: String, token: String): HermesHealth {
        error?.let { throw it }
        return health
    }

    override suspend fun sendRequest(baseUrl: String, token: String, request: MobileRequest): HermesResponse {
        error?.let { throw it }
        sendCount++
        lastRequest = request
        return response
    }
}
