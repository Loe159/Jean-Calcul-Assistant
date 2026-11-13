package fr.loevan.jeancalcul.v2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = ProviderSettings(application)
    private val runtime = ConversationRuntime(FileSessionStore(application))
    val session = runtime.session
    private val mutableConfig = MutableStateFlow(settings.config())
    val config = mutableConfig.asStateFlow()
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError = mutableSettingsError.asStateFlow()
    fun send(text: String) { viewModelScope.launch(Dispatchers.IO) { runtime.send(text, provider()) } }
    fun retry() { viewModelScope.launch(Dispatchers.IO) { runtime.retry(provider()) } }
    fun stop() { viewModelScope.launch(Dispatchers.IO) { runtime.stop() } }
    fun reset() { viewModelScope.launch(Dispatchers.IO) { runtime.reset() } }
    private fun provider() = OpenAiProvider(config.value, settings::credential)
    fun configure(endpoint: String, model: String, local: Boolean, key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (session.value.status == RunStatus.RUNNING) return@launch
                val next = ProviderConfig(endpoint.trim(), model.trim(), local)
                settings.save(next, key)
                mutableConfig.value = next
                mutableSettingsError.value = "Configuration enregistrée. Connexion non vérifiée."
            } catch (failure: Exception) {
                mutableSettingsError.value = (failure as? RunFailure)?.kind?.label ?: "Stockage sécurisé indisponible."
            }
        }
    }
    override fun onCleared() {
        runtime.closeTransport()
        super.onCleared()
    }
}
