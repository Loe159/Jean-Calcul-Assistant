package fr.loevan.jeancalcul.data.audit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_events",
    indices = [Index("occurredAtEpochMillis"), Index("toolName"), Index("outcome")],
)
data class AuditEventEntity(
    @PrimaryKey val actionId: String,
    val sessionId: String?,
    val origin: String?,
    val toolName: String,
    val toolVersion: String,
    val redactedArguments: String,
    val riskLevel: String?,
    val policyDecision: String?,
    val policyReason: String?,
    val policyJustification: String?,
    val approvalStatus: String?,
    val approvalMethod: String?,
    val executionSucceeded: Boolean?,
    val executionReplayed: Boolean?,
    val executionDurationMillis: Long?,
    val resultSummary: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val outcome: String,
    val occurredAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
