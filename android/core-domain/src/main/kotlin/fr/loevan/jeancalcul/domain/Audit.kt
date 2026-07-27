package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class AuditOutcome {
    PENDING,
    SUCCESS,
    FAILURE,
    DENIED,
    CANCELLED,
    EXPIRED,
}

@Serializable
data class PolicyDecisionRecord(
    val decision: PolicyDecisionType,
    val reason: PolicyReason,
    val justification: String,
    val approvalStatus: ActionApprovalStatus? = null,
    val approvalMethod: ActionApprovalMethod? = null,
)

@Serializable
data class ExecutionReceipt(
    val succeeded: Boolean,
    val replayed: Boolean,
    val durationMillis: Long,
    val resultSummary: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
) {
    init {
        require(durationMillis >= 0)
        require(succeeded == (errorCode == null))
    }
}

/** Durable, already-redacted account of one local tool attempt. */
@Serializable
data class AuditEvent(
    val actionId: String,
    val sessionId: String? = null,
    val origin: ActionRequestOrigin? = null,
    val toolName: String,
    val toolVersion: String,
    val redactedArguments: String = "{}",
    val riskLevel: ToolRiskLevel? = null,
    val policy: PolicyDecisionRecord? = null,
    val execution: ExecutionReceipt? = null,
    val outcome: AuditOutcome = AuditOutcome.PENDING,
    val occurredAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = occurredAtEpochMillis,
) {
    init {
        require(actionId.isNotBlank())
        require(toolName.isNotBlank())
        require(toolVersion.isNotBlank())
        require(occurredAtEpochMillis >= 0)
        require(updatedAtEpochMillis >= occurredAtEpochMillis)
    }
}

data class AuditFilter(
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val toolName: String? = null,
    val outcome: AuditOutcome? = null,
) {
    init {
        require(fromEpochMillis == null || fromEpochMillis >= 0)
        require(toEpochMillis == null || toEpochMillis >= 0)
        require(fromEpochMillis == null || toEpochMillis == null || fromEpochMillis <= toEpochMillis)
        require(toolName?.isNotBlank() != false)
    }
}

interface AuditRepository {
    fun observeEvents(
        filter: AuditFilter = AuditFilter(),
        limit: Int,
        offset: Int = 0,
    ): Flow<List<AuditEvent>>

    suspend fun getEvent(actionId: String): AuditEvent?

    suspend fun upsertEvent(event: AuditEvent)

    suspend fun eventsForExport(filter: AuditFilter = AuditFilter()): List<AuditEvent>

    suspend fun purgeOlderThan(cutoffEpochMillis: Long): Int

    fun observeRetentionDays(): Flow<Int>

    suspend fun setRetentionDays(days: Int)
}
