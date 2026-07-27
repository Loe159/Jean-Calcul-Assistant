package fr.loevan.jeancalcul.observability

import fr.loevan.jeancalcul.domain.ActionApprovalStatus
import fr.loevan.jeancalcul.domain.AuditEvent
import fr.loevan.jeancalcul.domain.AuditOutcome
import fr.loevan.jeancalcul.domain.AuditRepository
import fr.loevan.jeancalcul.domain.ExecutionReceipt
import fr.loevan.jeancalcul.domain.PolicyAuditEvent
import fr.loevan.jeancalcul.domain.PolicyAuditLogger
import fr.loevan.jeancalcul.domain.PolicyAuditStage
import fr.loevan.jeancalcul.domain.PolicyDecisionRecord
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes callback-based policy/tool events into one durable event per action. */
@Singleton
class PersistentAuditLogger private constructor(
    private val repository: AuditRepository,
    private val redactor: AuditRedactor,
    private val scope: CoroutineScope,
) : ToolAuditLogger,
    PolicyAuditLogger {
    @Inject
    constructor(
        repository: AuditRepository,
        redactor: AuditRedactor,
    ) : this(repository, redactor, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    internal constructor(
        repository: AuditRepository,
        redactor: AuditRedactor,
        scope: CoroutineScope,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(repository, redactor, scope)

    private val updates = Channel<AuditUpdate>(Channel.UNLIMITED)
    private val currentSessionId = AtomicReference<String?>(null)

    init {
        scope.launch {
            for (update in updates) {
                when (update) {
                    is AuditUpdate.Flush -> update.completion.complete(Unit)
                    else -> runCatching { apply(update) }
                }
            }
        }
    }

    override fun log(event: ToolAuditEvent) {
        updates.trySend(AuditUpdate.Tool(event, currentSessionId.get()))
    }

    override fun log(event: PolicyAuditEvent) {
        updates.trySend(AuditUpdate.Policy(event, currentSessionId.get()))
    }

    fun setSessionId(sessionId: String) {
        require(sessionId.isNotBlank())
        currentSessionId.set(sessionId)
    }

    fun clearSessionId() {
        currentSessionId.set(null)
    }

    internal suspend fun awaitIdle() {
        val completion = CompletableDeferred<Unit>()
        updates.send(AuditUpdate.Flush(completion))
        completion.await()
    }

    private suspend fun apply(update: AuditUpdate) {
        val actionId = update.actionId
        val existing = repository.getEvent(actionId)
        val merged =
            when (update) {
                is AuditUpdate.Policy -> mergePolicy(existing, update.event, update.sessionId)
                is AuditUpdate.Tool -> mergeTool(existing, update.event, update.sessionId)
                is AuditUpdate.Flush -> return
            }
        repository.upsertEvent(merged)
    }

    private fun mergePolicy(
        existing: AuditEvent?,
        event: PolicyAuditEvent,
        sessionId: String?,
    ): AuditEvent {
        val previousPolicy = existing?.policy
        val policy =
            PolicyDecisionRecord(
                decision = event.decision,
                reason = event.reason,
                justification = redactor.text(event.justification).orEmpty(),
                approvalStatus = event.approvalStatus ?: previousPolicy?.approvalStatus,
                approvalMethod = event.approvalMethod ?: previousPolicy?.approvalMethod,
            )
        val outcome = policyOutcome(existing?.outcome, event)
        return AuditEvent(
            actionId = event.actionId,
            sessionId = existing?.sessionId ?: sessionId,
            origin = event.origin,
            toolName = event.toolName,
            toolVersion = event.toolVersion,
            redactedArguments = redactor.arguments(event.arguments),
            riskLevel = event.riskLevel,
            policy = policy,
            execution = existing?.execution,
            outcome = outcome,
            occurredAtEpochMillis =
                minOf(
                    existing?.occurredAtEpochMillis ?: Long.MAX_VALUE,
                    event.occurredAtEpochMillis,
                ),
            updatedAtEpochMillis = maxOf(existing?.updatedAtEpochMillis ?: 0, event.occurredAtEpochMillis),
        )
    }

    private fun mergeTool(
        existing: AuditEvent?,
        event: ToolAuditEvent,
        sessionId: String?,
    ): AuditEvent {
        val result = event.result
        val execution =
            result?.let {
                ExecutionReceipt(
                    succeeded = it.isSuccess,
                    replayed = it.replayed,
                    durationMillis = event.durationMillis,
                    resultSummary = redactor.text(it.output?.toString()),
                    errorCode = redactor.text(it.error?.code),
                    errorMessage = redactor.text(it.error?.message),
                )
            } ?: existing?.execution
        val outcome = toolOutcome(existing?.outcome, event)
        return AuditEvent(
            actionId = event.actionId,
            sessionId = existing?.sessionId ?: sessionId,
            origin = existing?.origin,
            toolName = event.toolName,
            toolVersion = event.toolVersion,
            redactedArguments = redactor.arguments(event.arguments),
            riskLevel = existing?.riskLevel,
            policy = existing?.policy,
            execution = execution,
            outcome = outcome,
            occurredAtEpochMillis =
                minOf(
                    existing?.occurredAtEpochMillis ?: Long.MAX_VALUE,
                    event.occurredAtEpochMillis,
                ),
            updatedAtEpochMillis = maxOf(existing?.updatedAtEpochMillis ?: 0, event.occurredAtEpochMillis),
        )
    }

    private fun policyOutcome(
        previous: AuditOutcome?,
        event: PolicyAuditEvent,
    ): AuditOutcome =
        when {
            event.stage == PolicyAuditStage.APPROVAL && event.approvalStatus == ActionApprovalStatus.EXPIRED ->
                AuditOutcome.EXPIRED
            event.stage == PolicyAuditStage.APPROVAL && event.approvalStatus == ActionApprovalStatus.REJECTED ->
                if (event.approvalApproved == false) AuditOutcome.CANCELLED else AuditOutcome.DENIED
            event.decision == PolicyDecisionType.DENY ||
                event.decision == PolicyDecisionType.OPEN_SYSTEM_PANEL -> AuditOutcome.DENIED
            else -> previous ?: AuditOutcome.PENDING
        }

    private fun toolOutcome(
        previous: AuditOutcome?,
        event: ToolAuditEvent,
    ): AuditOutcome =
        when {
            event.result?.error?.code == "ACTION_EXPIRED" -> AuditOutcome.EXPIRED
            event.result?.error?.code == "POLICY_AUTHORIZATION_REQUIRED" -> AuditOutcome.DENIED
            event.result?.isSuccess == true -> AuditOutcome.SUCCESS
            event.result?.isSuccess == false -> AuditOutcome.FAILURE
            else -> previous ?: AuditOutcome.PENDING
        }

    private sealed interface AuditUpdate {
        val actionId: String

        data class Policy(
            val event: PolicyAuditEvent,
            val sessionId: String?,
        ) : AuditUpdate {
            override val actionId: String = event.actionId
        }

        data class Tool(
            val event: ToolAuditEvent,
            val sessionId: String?,
        ) : AuditUpdate {
            override val actionId: String = event.actionId
        }

        data class Flush(val completion: CompletableDeferred<Unit>) : AuditUpdate {
            override val actionId: String = "flush"
        }
    }
}
