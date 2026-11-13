package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only these non-credential views reach Compose, saved state or accessibility. */
data class ChatGptAccountView(val id: String, val label: String, val authorized: Boolean, val signedIn: Boolean)
data class ChatGptModelView(val id: String, val label: String)
data class ChatGptUi(
    val accounts: List<ChatGptAccountView> = emptyList(), val active: String = "",
    val usingPlan: Boolean = false, val authorized: Boolean = false, val model: String = "",
    val models: List<ChatGptModelView> = emptyList(), val welcome: Boolean = false, val storageUnavailable: Boolean = false,
)

internal class ChatGptAccounts(private val store: ChatGptStore, val oauth: ChatGptOAuth = ChatGptOAuth()) {
    private val gate = Mutex()
    private var storageUnavailable = false
    private var registration = runCatching { store.load() }.getOrElse { storageUnavailable = true; ChatGptRegistration() }
    private var models: List<ChatGptModel> = emptyList()
    private val mutable = MutableStateFlow(view())
    val ui = mutable.asStateFlow()
    private fun active(): ChatGptAccount = registration.accounts.firstOrNull { it.clientId == registration.active } ?: authFailed()
    private fun view(): ChatGptUi {
        val account = registration.accounts.firstOrNull { it.clientId == registration.active }
        val allowed = account?.tokens?.scopes?.contains(PLAN_SCOPE) == true
        return ChatGptUi(registration.accounts.mapIndexed { index, item ->
            ChatGptAccountView(item.clientId, (item.email.ifBlank { "Compte ${index + 1}" }) + " · " + item.clientId.takeLast(6),
                item.tokens?.scopes?.contains(PLAN_SCOPE) == true, item.tokens != null)
        }, registration.active, registration.enabled, allowed, account?.model.orEmpty(),
            models.map { ChatGptModelView(it.slug, it.name) }, allowed && !account.welcomed, storageUnavailable)
    }
    private fun save(value: ChatGptRegistration) {
        if (storageUnavailable) throw RunFailure(RunError.STORAGE)
        try { store.save(value) } catch (_: Exception) { throw RunFailure(RunError.STORAGE) }
        registration = value; mutable.value = view()
    }
    private fun replace(account: ChatGptAccount, activate: Boolean = false) {
        val existing = registration.accounts.firstOrNull { it.clientId == account.clientId }
        if (existing != null && existing.subject != account.subject) authFailed()
        save(registration.copy(accounts = if (existing == null) registration.accounts + account else registration.accounts.map { if (it.clientId == account.clientId) account else it },
            active = if (activate) account.clientId else registration.active,
            enabled = if (activate) true else registration.enabled))
    }
    suspend fun attempt(redirect: String, selected: String?): OAuthAttempt = gate.withLock {
        // Persist the host ID before opening any browser, including first use.
        save(registration)
        val existing = selected?.let { id -> registration.accounts.firstOrNull { it.clientId == id } ?: authFailed() }
        OAuthAttempt.create(redirect, registration.hostId, existing)
    }
    suspend fun complete(attempt: OAuthAttempt, code: OAuthCode): Boolean = gate.withLock {
        val incoming = oauth.exchange(attempt, code)
        val old = registration.accounts.firstOrNull { it.clientId == incoming.clientId }
        val account = incoming.copy(model = old?.model.orEmpty(), welcomed = old?.welcomed ?: false)
        models = emptyList()
        replace(account, activate = true)
        if (PLAN_SCOPE !in account.tokens!!.scopes) throw RunFailure(RunError.CHATGPT_PERMISSION)
        try { loadModelsUnlocked(); true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
    private fun freshUnlocked(): ChatGptAccount {
        val old = active(); val tokens = old.tokens ?: authFailed()
        if (PLAN_SCOPE !in tokens.scopes) throw RunFailure(RunError.CHATGPT_PERMISSION)
        if (tokens.expiresAt > System.currentTimeMillis() + 60_000) return old
        val renewed = try { oauth.refresh(old) } catch (error: RunFailure) {
            if (error.terminalAuth) replace(old.copy(tokens = null))
            throw error
        }
        replace(renewed)
        if (PLAN_SCOPE !in renewed.tokens!!.scopes) throw RunFailure(RunError.CHATGPT_PERMISSION)
        return renewed
    }
    suspend fun credentials(id: String): ChatGptAccount = gate.withLock {
        if (!registration.enabled || registration.active != id) authFailed()
        freshUnlocked().also { if (it.model.isBlank()) throw RunFailure(RunError.CHATGPT_MODEL) }
    }
    private fun loadModelsUnlocked() {
        val account = freshUnlocked()
        models = oauth.models(account)
        val selected = account.model.takeIf { name -> models.any { it.slug == name } }.orEmpty()
        replace(account.copy(model = selected))
    }
    suspend fun loadModels() = gate.withLock { loadModelsUnlocked() }
    suspend fun select(id: String) = gate.withLock {
        if (registration.accounts.none { it.clientId == id }) authFailed()
        models = emptyList(); save(registration.copy(active = id, enabled = true))
        if (active().tokens != null) loadModelsUnlocked()
    }
    suspend fun selectModel(id: String) = gate.withLock {
        if (models.none { it.slug == id }) throw RunFailure(RunError.CHATGPT_MODEL)
        replace(active().copy(model = id))
    }
    suspend fun useApi() = gate.withLock { save(registration.copy(enabled = false)) }
    suspend fun usePlan() = gate.withLock {
        active(); save(registration.copy(enabled = true))
    }
    suspend fun welcomeSeen() = gate.withLock { replace(active().copy(welcomed = true)) }
    suspend fun signOut(): Boolean = gate.withLock {
        val account = active()
        var revoked = account.tokens == null
        // A bounded retry; no token is retained locally if both remote attempts fail.
        repeat(2) { attempt ->
            if (!revoked) {
                if (attempt > 0) kotlinx.coroutines.delay(500)
                revoked = runCatching { oauth.revoke(account) }.getOrDefault(false)
            }
        }
        models = emptyList()
        replace(account.copy(tokens = null))
        revoked
    }
}
