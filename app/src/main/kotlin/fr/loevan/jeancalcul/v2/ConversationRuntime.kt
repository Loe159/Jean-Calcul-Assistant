package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** No Android/UI dependencies: the voice entry point can use the same session. */
data class ToolCall(val id: String, val name: String, val arguments: String, val continuation: String = "", val continuationOwner: String = "")
data class ChatMessage(val role: String, val text: String,
    val toolCalls: List<ToolCall> = emptyList(), val toolCallId: String = "")
enum class RunStatus { IDLE, RUNNING, COMPLETED, STOPPED, FAILED, INTERRUPTED }
enum class RunError(val label: String) {
    CHATGPT_MODEL("Choisissez un modèle du compte ChatGPT dans les préférences."),
    CHATGPT_AUTH("Connectez ou reconnectez votre compte ChatGPT dans les préférences."),
    CHATGPT_PERMISSION("Autorisez l’utilisation de votre abonnement dans les réglages ChatGPT."),
    CONFIGURATION("Configurez un fournisseur dans les préférences."),
    AUTHENTICATION("Authentification refusée. Vérifiez la clé."),
    RATE_LIMIT("Limite du fournisseur atteinte. Réessayez plus tard."),
    NETWORK("Connexion interrompue ou délai dépassé."),
    PROTOCOL("Réponse du fournisseur invalide ou incomplète."),
    PROVIDER("Le fournisseur est indisponible."),
    STORAGE("Impossible d’enregistrer la conversation."),
    TOOL_POLICY("Demande d’outil refusée par la politique Android.")
}
class RunFailure(val kind: RunError, val terminalAuth: Boolean = false) : Exception(kind.name)
data class RunEvent(val runId: String, val kind: String, val time: Long = System.currentTimeMillis())
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val messages: List<ChatMessage> = emptyList(),
    val status: RunStatus = RunStatus.IDLE,
    val error: RunError? = null,
    val runId: String = "",
    val events: List<RunEvent> = emptyList(),
)
interface SessionStore { fun load(): Session; fun save(session: Session) }
interface ConversationProvider {
    suspend fun stream(messages: List<ChatMessage>, onText: suspend (String) -> Unit)
    suspend fun turn(messages: List<ChatMessage>, onText: suspend (String) -> Unit): List<ToolCall> {
        stream(messages, onText)
        return emptyList()
    }
    fun cancel()
}

/** Explicit allowlist. This read-only clock needs no Android permission or external service. */
object AndroidToolPolicy {
    fun permits(name: String) = name == "current_time"
    fun execute(call: ToolCall): String {
        if (!permits(call.name)) throw RunFailure(RunError.TOOL_POLICY)
        if (call.arguments.filterNot { it.isWhitespace() } != "{}") throw RunFailure(RunError.TOOL_POLICY)
        return java.time.Instant.now().toString()
    }
}

class ConversationRuntime(private val store: SessionStore) {
    private val mutable = MutableStateFlow(runCatching { store.load() }.getOrElse { Session(status = RunStatus.FAILED, error = RunError.STORAGE) }.let {
        if (it.status == RunStatus.RUNNING) it.copy(status = RunStatus.INTERRUPTED) else it
    })
    val session = mutable.asStateFlow()
    private val gate = Mutex()
    private var generation = 0
    @Volatile private var active: ConversationProvider? = null

    private fun update(value: Session) {
        try { store.save(value); mutable.value = value }
        catch (_: Exception) {
            mutable.value = value.copy(status = RunStatus.FAILED, error = RunError.STORAGE)
            throw RunFailure(RunError.STORAGE)
        }
    }
    private fun event(value: Session, kind: String) = value.copy(
        events = (value.events + RunEvent(value.runId, kind)).takeLast(200))

    suspend fun send(text: String, provider: ConversationProvider) = guardStorage { run(text.trim(), provider, false) }
    suspend fun retry(provider: ConversationProvider) = guardStorage { run("", provider, true) }
    private suspend fun guardStorage(block: suspend () -> Unit) {
        try { block() } catch (failure: RunFailure) { if (failure.kind != RunError.STORAGE) throw failure }
    }
    fun closeTransport() { active?.cancel() }
    private suspend fun run(text: String, provider: ConversationProvider, retry: Boolean) {
        val token: Int
        val request: List<ChatMessage>
        gate.withLock {
            val old = mutable.value
            if (old.status == RunStatus.RUNNING || (!retry && text.isBlank())) return
            val lastUser = old.messages.indexOfLast { it.role == "user" }
            if (retry && (lastUser < 0 || old.status !in setOf(RunStatus.FAILED, RunStatus.STOPPED, RunStatus.INTERRUPTED))) return
            request = if (retry) old.messages.take(lastUser + 1) else old.messages + ChatMessage("user", text)
            token = ++generation
            active = provider
            update(event(old.copy(messages = request + ChatMessage("assistant", ""),
                status = RunStatus.RUNNING, error = null, runId = UUID.randomUUID().toString()), "started"))
        }
        try {
            var context = request
            var completed = false
            for (round in 0 until 4) {
                val calls = provider.turn(context) { chunk ->
                    gate.withLock {
                        if (token != generation) throw CancellationException()
                        val current = mutable.value
                        val last = current.messages.last()
                        update(current.copy(messages = current.messages.dropLast(1) + last.copy(text = last.text + chunk)))
                    }
                }
                if (calls.isEmpty()) { completed = true; break }
                gate.withLock {
                    if (token != generation) throw CancellationException()
                    val current = mutable.value
                    // Journal the proposal before making any policy decision. No arguments in the journal.
                    val proposed = event(current, "tool_proposed")
                    update(proposed)
                    val results = calls.map { call ->
                        val result = AndroidToolPolicy.execute(call)
                        ChatMessage("tool", result, toolCallId = call.id)
                    }
                    val assistant = current.messages.last().copy(toolCalls = calls)
                    val next = current.messages.dropLast(1) + assistant + results
                    update(event(proposed.copy(messages = next + ChatMessage("assistant", "")), "tool_completed"))
                    context = next
                }
            }
            if (!completed) throw RunFailure(RunError.TOOL_POLICY)
            gate.withLock {
                if (token == generation) update(event(mutable.value.copy(status = RunStatus.COMPLETED), "completed"))
            }
        } catch (cancelled: CancellationException) {
            gate.withLock {
                if (token == generation) update(event(mutable.value.copy(status = RunStatus.STOPPED), "stopped"))
            }
            throw cancelled
        } catch (failure: Exception) {
            gate.withLock {
                if (token == generation) {
                    val kind = (failure as? RunFailure)?.kind ?: RunError.NETWORK
                    update(event(mutable.value.copy(status = RunStatus.FAILED, error = kind), kind.name))
                }
            }
        } finally {
            gate.withLock { if (token == generation) active = null }
        }
    }
    suspend fun stop() = guardStorage {
        gate.withLock {
            if (mutable.value.status == RunStatus.RUNNING) {
                ++generation
                active?.cancel(); active = null
                update(event(mutable.value.copy(status = RunStatus.STOPPED), "stopped"))
            }
        }
    }
    suspend fun reset() = guardStorage {
        stop()
        gate.withLock { update(Session()) }
    }
}
