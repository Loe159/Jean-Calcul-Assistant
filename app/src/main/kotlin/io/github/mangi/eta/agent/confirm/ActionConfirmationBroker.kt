package io.github.mangi.eta.agent.confirm

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal enum class ConfirmationDecision { APPROVED, DECLINED, TIMED_OUT, UNAVAILABLE }

/** Asks the user to confirm a sensitive action; blocks the calling tool thread until they answer. */
internal fun interface ActionConfirmer {
    fun confirm(request: ConfirmationRequest): ConfirmationDecision
}

/**
 * Hands a [ConfirmationRequest] to the popup and waits for the user's answer. The model never sees or sets the
 * decision: it can only come from [resolve], called by the popup with the id of the request it was opened for.
 * [show] opens the popup (or a notification leading to it) and returns false when it cannot.
 */
internal class ActionConfirmationBroker(
    private val timeoutMs: Long = 60_000L,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val show: (requestId: String) -> Boolean,
) : ActionConfirmer {
    private class Pending(val id: String, val request: ConfirmationRequest) {
        val answer = CompletableFuture<ConfirmationDecision>()
    }

    private val gate = ReentrantLock()
    @Volatile private var pending: Pending? = null

    /** The request the popup [requestId] was opened for; null once answered, expired or never issued. */
    fun requestFor(requestId: String): ConfirmationRequest? = pending?.takeIf { it.id == requestId }?.request

    /** Records the user's answer; false when [requestId] is not the request currently waiting. */
    fun resolve(requestId: String, decision: ConfirmationDecision): Boolean {
        val current = pending?.takeIf { it.id == requestId } ?: return false
        return current.answer.complete(decision)
    }

    override fun confirm(request: ConfirmationRequest): ConfirmationDecision = gate.withLock {
        val current = Pending(newId(), request)
        pending = current
        try {
            if (!show(current.id)) return@withLock ConfirmationDecision.UNAVAILABLE
            current.answer.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            ConfirmationDecision.TIMED_OUT
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ConfirmationDecision.DECLINED
        } finally {
            pending = null
        }
    }
}
