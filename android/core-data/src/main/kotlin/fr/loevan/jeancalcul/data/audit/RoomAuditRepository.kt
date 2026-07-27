package fr.loevan.jeancalcul.data.audit

import fr.loevan.jeancalcul.domain.ActionApprovalMethod
import fr.loevan.jeancalcul.domain.ActionApprovalStatus
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AuditEvent
import fr.loevan.jeancalcul.domain.AuditFilter
import fr.loevan.jeancalcul.domain.AuditOutcome
import fr.loevan.jeancalcul.domain.AuditRepository
import fr.loevan.jeancalcul.domain.ExecutionReceipt
import fr.loevan.jeancalcul.domain.PolicyDecisionRecord
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyReason
import fr.loevan.jeancalcul.domain.ToolRiskLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomAuditRepository
    @Inject
    constructor(
        private val dao: AuditDao,
        private val retentionPreferences: AuditRetentionPreferences,
    ) : AuditRepository {
        override fun observeEvents(
            filter: AuditFilter,
            limit: Int,
            offset: Int,
        ): Flow<List<AuditEvent>> {
            require(limit > 0)
            require(offset >= 0)
            return dao.observeEvents(
                filter.fromEpochMillis,
                filter.toEpochMillis,
                filter.toolName,
                filter.outcome?.name,
                limit,
                offset,
            ).map { events -> events.map(AuditEventEntity::toDomain) }
        }

        override suspend fun getEvent(actionId: String): AuditEvent? = dao.getEvent(actionId)?.toDomain()

        override suspend fun upsertEvent(event: AuditEvent) {
            dao.upsertEvent(event.toEntity())
            purgeExpired(System.currentTimeMillis(), retentionPreferences.retentionDays.first())
        }

        override suspend fun eventsForExport(filter: AuditFilter): List<AuditEvent> =
            dao.eventsForExport(
                filter.fromEpochMillis,
                filter.toEpochMillis,
                filter.toolName,
                filter.outcome?.name,
            ).map(AuditEventEntity::toDomain)

        override suspend fun purgeOlderThan(cutoffEpochMillis: Long): Int {
            require(cutoffEpochMillis >= 0)
            return dao.purgeOlderThan(cutoffEpochMillis)
        }

        override fun observeRetentionDays(): Flow<Int> = retentionPreferences.retentionDays

        override suspend fun setRetentionDays(days: Int) {
            retentionPreferences.setRetentionDays(days)
            purgeExpired(System.currentTimeMillis(), days)
        }

        private suspend fun purgeExpired(
            nowEpochMillis: Long,
            days: Int,
        ) {
            val retentionMillis = days * MILLIS_PER_DAY
            dao.purgeOlderThan((nowEpochMillis - retentionMillis).coerceAtLeast(0))
        }

        private companion object {
            const val MILLIS_PER_DAY = 86_400_000L
        }
    }

private fun AuditEvent.toEntity() =
    AuditEventEntity(
        actionId = actionId,
        sessionId = sessionId,
        origin = origin?.name,
        toolName = toolName,
        toolVersion = toolVersion,
        redactedArguments = redactedArguments,
        riskLevel = riskLevel?.name,
        policyDecision = policy?.decision?.name,
        policyReason = policy?.reason?.name,
        policyJustification = policy?.justification,
        approvalStatus = policy?.approvalStatus?.name,
        approvalMethod = policy?.approvalMethod?.name,
        executionSucceeded = execution?.succeeded,
        executionReplayed = execution?.replayed,
        executionDurationMillis = execution?.durationMillis,
        resultSummary = execution?.resultSummary,
        errorCode = execution?.errorCode,
        errorMessage = execution?.errorMessage,
        outcome = outcome.name,
        occurredAtEpochMillis = occurredAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

private fun AuditEventEntity.toDomain() =
    AuditEvent(
        actionId = actionId,
        sessionId = sessionId,
        origin = origin?.let(ActionRequestOrigin::valueOf),
        toolName = toolName,
        toolVersion = toolVersion,
        redactedArguments = redactedArguments,
        riskLevel = riskLevel?.let(ToolRiskLevel::valueOf),
        policy =
            policyDecision?.let {
                PolicyDecisionRecord(
                    decision = PolicyDecisionType.valueOf(it),
                    reason = PolicyReason.valueOf(requireNotNull(policyReason)),
                    justification = policyJustification.orEmpty(),
                    approvalStatus = approvalStatus?.let(ActionApprovalStatus::valueOf),
                    approvalMethod = approvalMethod?.let(ActionApprovalMethod::valueOf),
                )
            },
        execution =
            executionSucceeded?.let { succeeded ->
                ExecutionReceipt(
                    succeeded = succeeded,
                    replayed = executionReplayed ?: false,
                    durationMillis = executionDurationMillis ?: 0,
                    resultSummary = resultSummary,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                )
            },
        outcome = AuditOutcome.valueOf(outcome),
        occurredAtEpochMillis = occurredAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
