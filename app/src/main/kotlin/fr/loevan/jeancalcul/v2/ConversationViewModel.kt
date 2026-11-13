package fr.loevan.jeancalcul.v2

import android.app.Application
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val chatGpt = ChatGptAccounts(ChatGptSecureStore(application))
    val chatGptUi = chatGpt.ui
    private val mutableChatGptMessage = MutableStateFlow<String?>(if (chatGpt.ui.value.storageUnavailable) RunError.STORAGE.label else null)
    val chatGptMessage = mutableChatGptMessage.asStateFlow()
    private val mutableSigningIn = MutableStateFlow(false)
    val signingIn = mutableSigningIn.asStateFlow()
    private var loginJob: Job? = null
    @Volatile private var listener: LoopbackOAuth? = null
    init {
        if (chatGpt.ui.value.authorized) authAction { chatGpt.loadModels() }
    }
    private fun authAction(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableChatGptMessage.value = (error as? RunFailure)?.kind?.label ?: "Connexion ChatGPT impossible. Réessayez." }
        }
    }
    fun signInChatGpt(accountId: String? = null) {
        if (session.value.status == RunStatus.RUNNING || loginJob?.isActive == true) return
        loginJob = viewModelScope.launch(Dispatchers.IO) {
            mutableSigningIn.value = true
            mutableChatGptMessage.value = "Terminez l’autorisation dans le navigateur, puis revenez ici."
            var phase = "préparation"
            try {
                val callback = LoopbackOAuth(); listener = callback
                callback.use {
                    val attempt = chatGpt.attempt(callback.redirect, accountId)
                    phase = "navigateur"
                    withContext(Dispatchers.Main) { openBrowser(attempt.authorizationUrl(chatGpt.oauth.endpoints)) }
                    phase = "redirection"
                    val code = callback.await(attempt)
                    phase = "validation du compte"
                    val modelsLoaded = chatGpt.complete(attempt, code)
                    mutableChatGptMessage.value = if (modelsLoaded)
                        "Compte autorisé. Choisissez un modèle pour commencer."
                    else "Compte connecté. Catalogue indisponible : actualisez les modèles."
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                val reason = (error as? RunFailure)?.kind?.label ?: when (error) {
                    is OAuthNetworkFailure -> error.safeMessage
                    is java.net.SocketException -> "Écouteur de redirection interrompu."
                    is java.net.SocketTimeoutException -> "Délai de connexion dépassé."
                    is java.io.IOException -> "Erreur réseau."
                    else -> "Erreur inattendue."
                }
                mutableChatGptMessage.value = "Échec ($phase) : $reason"
            } finally { listener = null; mutableSigningIn.value = false }
        }
    }
    fun cancelChatGptSignIn() {
        listener?.close(); loginJob?.cancel()
        mutableSigningIn.value = false; mutableChatGptMessage.value = "Connexion annulée."
    }
    fun selectChatGptAccount(id: String) = authAction { runtime.stop(); chatGpt.select(id) }
    fun selectChatGptModel(id: String) = authAction { if (session.value.status != RunStatus.RUNNING) chatGpt.selectModel(id) }
    fun refreshChatGptModels() = authAction { if (session.value.status != RunStatus.RUNNING) chatGpt.loadModels() }
    fun useChatGpt() = authAction { runtime.stop(); chatGpt.usePlan() }
    fun useApiProvider() = authAction { runtime.stop(); chatGpt.useApi() }
    fun acknowledgeChatGpt() = authAction { chatGpt.welcomeSeen() }
    fun signOutChatGpt() = authAction {
        cancelChatGptSignIn(); runtime.stop()
        mutableChatGptMessage.value = "Déconnexion et révocation en cours…"
        val revoked = chatGpt.signOut()
        mutableChatGptMessage.value = if (revoked) "Compte déconnecté."
            else "Déconnecté sur cet appareil. Révocation distante non confirmée : déconnectez aussi l’application dans les réglages ChatGPT."
    }
    fun manageChatGptUsage() {
        try { openBrowser("https://chatgpt.com/settings/usage") }
        catch (_: Exception) { mutableChatGptMessage.value = "Aucun navigateur disponible." }
    }
    private fun openBrowser(url: String) {
        getApplication<Application>().startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private val mutableConfig = MutableStateFlow(settings.config())
    val config = mutableConfig.asStateFlow()
    private val mutableSettingsError = MutableStateFlow<String?>(null)
    val settingsError = mutableSettingsError.asStateFlow()
    fun send(text: String) { viewModelScope.launch(Dispatchers.IO) { runtime.send(text, provider()) } }
    fun retry() { viewModelScope.launch(Dispatchers.IO) { runtime.retry(provider()) } }
    fun stop() { viewModelScope.launch(Dispatchers.IO) { runtime.stop() } }
    fun reset() { viewModelScope.launch(Dispatchers.IO) { runtime.reset() } }
    private fun provider(): ConversationProvider = if (chatGpt.ui.value.usingPlan)
        ChatGptProvider(chatGpt, chatGpt.ui.value.active)
    else OpenAiProvider(config.value, settings::credential)
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
        listener?.close()
        runtime.closeTransport()
        super.onCleared()
    }
}
