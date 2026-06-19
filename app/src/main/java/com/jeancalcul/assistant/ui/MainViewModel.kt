package com.jeancalcul.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeancalcul.assistant.data.HermesSettings
import com.jeancalcul.assistant.data.SettingsStore
import com.jeancalcul.assistant.network.ClientInfo
import com.jeancalcul.assistant.network.HermesClient
import com.jeancalcul.assistant.network.MobileRequest
import com.jeancalcul.assistant.network.UserInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(val author: String, val text: String)

data class MainUiState(
    val settings: HermesSettings = HermesSettings(),
    val baseUrlDraft: String = HermesSettings().baseUrl,
    val tokenDraft: String = "",
    val isBusy: Boolean = false,
    val status: String = "Configure Hermes puis envoie un message.",
    val messageDraft: String = "",
    val messages: List<ChatMessage> = emptyList()
)

class MainViewModel(
    private val settingsStore: SettingsStore,
    private val hermesClient: HermesClient = HermesClient()
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collectLatest { settings ->
                _uiState.update { it.copy(settings = settings, baseUrlDraft = settings.baseUrl, tokenDraft = settings.token) }
            }
        }
    }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrlDraft = value) }
    fun updateToken(value: String) = _uiState.update { it.copy(tokenDraft = value) }
    fun updateMessage(value: String) = _uiState.update { it.copy(messageDraft = value) }

    fun saveSettings() = viewModelScope.launch {
        val state = _uiState.value
        settingsStore.save(state.baseUrlDraft, state.tokenDraft)
        _uiState.update { it.copy(status = "Configuration enregistrée.") }
    }

    fun testConnection() = viewModelScope.launch {
        runBusy("Connexion à Hermes...") { settings ->
            val health = hermesClient.health(settings.baseUrl, settings.token)
            if (health.isOk) {
                "Hermes OK: ${health.name ?: "Hermes"}${health.version?.let { " ($it)" } ?: ""}"
            } else {
                "Hermes répond avec le statut: ${health.status}"
            }
        }
    }

    fun sendMessage() = viewModelScope.launch {
        val text = _uiState.value.messageDraft.trim()
        if (text.isEmpty()) return@launch
        _uiState.update { it.copy(messageDraft = "", messages = it.messages + ChatMessage("Vous", text)) }
        runBusy("Hermes réfléchit...") { settings ->
            val response = hermesClient.sendRequest(
                settings.baseUrl,
                settings.token,
                MobileRequest(
                    requestId = UUID.randomUUID().toString(),
                    input = UserInput(text = text),
                    client = ClientInfo()
                )
            )
            _uiState.update { it.copy(messages = it.messages + ChatMessage("Hermes", response.displayText)) }
            "Réponse reçue."
        }
    }

    private suspend fun runBusy(waitingStatus: String, block: suspend (HermesSettings) -> String) {
        val settings = _uiState.value.settings
        _uiState.update { it.copy(isBusy = true, status = waitingStatus) }
        val status = runCatching { block(settings) }.getOrElse { "Erreur: ${it.message ?: it::class.simpleName}" }
        _uiState.update { it.copy(isBusy = false, status = status) }
    }
}
