package fr.loevan.jeancalcul.observability

import fr.loevan.jeancalcul.domain.ActionApprovalStatus
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AuditEvent
import fr.loevan.jeancalcul.domain.AuditFilter
import fr.loevan.jeancalcul.domain.AuditOutcome
import fr.loevan.jeancalcul.domain.AuditRepository
import fr.loevan.jeancalcul.domain.PolicyAuditEvent
import fr.loevan.jeancalcul.domain.PolicyAuditStage
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyReason
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditStage
import fr.loevan.jeancalcul.domain.ToolError
import fr.loevan.jeancalcul.domain.ToolResult
import fr.loevan.jeancalcul.domain.ToolRiskLevel
import fr.loevan.jeancalcul.security.SecretRedactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentAuditLoggerTest {
    private val repository = InMemoryAuditRepository()
    private val redactor = AuditRedactor(SecretRedactor())

    @Test
    fun `denied decisions and sensitive arguments are persisted safely`() =
        runTest {
            val logger = PersistentAuditLogger(repository, redactor, backgroundScope)
            logger.setSessionId("conversation-1")
            logger.log(policyEvent(decision = PolicyDecisionType.DENY, arguments = secretArguments()))
            logger.clearSessionId()
            logger.awaitIdle()

            val event = repository.getEvent(ACTION_ID)!!
            assertEquals(AuditOutcome.DENIED, event.outcome)
            assertEquals("conversation-1", event.sessionId)
            assertFalse(event.redactedArguments.contains("123456"))
            assertTrue(event.redactedArguments.contains(SecretRedactor.REDACTED))
        }

    @Test
    fun `rejected approval is recorded as cancellation`() =
        runTest {
            val logger = PersistentAuditLogger(repository, redactor, backgroundScope)
            logger.log(policyEvent(decision = PolicyDecisionType.CONFIRM))
            logger.log(
                policyEvent(PolicyDecisionType.CONFIRM).copy(
                    stage = PolicyAuditStage.APPROVAL,
                    approvalStatus = ActionApprovalStatus.REJECTED,
                    approvalApproved = false,
                    occurredAtEpochMillis = 2,
                ),
            )
            logger.awaitIdle()

            assertEquals(AuditOutcome.CANCELLED, repository.getEvent(ACTION_ID)?.outcome)
        }

    @Test
    fun `tool errors produce a terminal execution receipt`() =
        runTest {
            val logger = PersistentAuditLogger(repository, redactor, backgroundScope)
            val result =
                ToolResult(
                    actionId = ACTION_ID,
                    toolName = TOOL_NAME,
                    toolVersion = TOOL_VERSION,
                    error = ToolError("EXECUTION_FAILED", "token=top-secret-value"),
                )
            logger.log(
                ToolAuditEvent(
                    actionId = ACTION_ID,
                    toolName = TOOL_NAME,
                    toolVersion = TOOL_VERSION,
                    arguments = secretArguments(),
                    stage = ToolAuditStage.ERROR,
                    message = "failed",
                    occurredAtEpochMillis = 3,
                    durationMillis = 17,
                    result = result,
                ),
            )
            logger.awaitIdle()

            val event = repository.getEvent(ACTION_ID)!!
            assertEquals(AuditOutcome.FAILURE, event.outcome)
            assertEquals(17L, event.execution?.durationMillis)
            assertFalse(event.execution?.errorMessage.orEmpty().contains("top-secret-value"))
        }

    @Test
    fun `export applies a second redaction boundary`() =
        runTest {
            repository.upsertEvent(
                AuditEvent(
                    actionId = ACTION_ID,
                    toolName = TOOL_NAME,
                    toolVersion = TOOL_VERSION,
                    redactedArguments = "{\"api_key\":\"sensitive-value\"}",
                    outcome = AuditOutcome.DENIED,
                    occurredAtEpochMillis = 1,
                ),
            )
            val exported = RedactedAuditExporter(repository, redactor, Json { prettyPrint = true }).export()

            assertFalse(exported.contains("sensitive-value"))
            assertTrue(exported.contains(SecretRedactor.REDACTED))
        }

    private fun policyEvent(
        decision: PolicyDecisionType,
        arguments: kotlinx.serialization.json.JsonObject = buildJsonObject { },
    ) = PolicyAuditEvent(
        actionId = ACTION_ID,
        toolName = TOOL_NAME,
        toolVersion = TOOL_VERSION,
        arguments = arguments,
        origin = ActionRequestOrigin.USER_VOICE,
        riskLevel = ToolRiskLevel.R2,
        decision = decision,
        reason = PolicyReason.TOOL_DEFAULT,
        justification = "token=top-secret-value",
        stage = PolicyAuditStage.DECISION,
        occurredAtEpochMillis = 1,
    )

    private fun secretArguments() = buildJsonObject { put("otp", "123456") }

    private companion object {
        const val ACTION_ID = "action-1"
        const val TOOL_NAME = "audio.set_volume"
        const val TOOL_VERSION = "1.0.0"
    }
}

private class InMemoryAuditRepository : AuditRepository {
    private val events = MutableStateFlow<Map<String, AuditEvent>>(emptyMap())
    private val retentionDays = MutableStateFlow(30)

    override fun observeEvents(
        filter: AuditFilter,
        limit: Int,
        offset: Int,
    ): Flow<List<AuditEvent>> =
        events.map { current ->
            current.values
                .filter { filter.toolName == null || it.toolName == filter.toolName }
                .filter { filter.outcome == null || it.outcome == filter.outcome }
                .sortedByDescending(AuditEvent::occurredAtEpochMillis)
                .drop(offset)
                .take(limit)
        }

    override suspend fun getEvent(actionId: String): AuditEvent? = events.value[actionId]

    override suspend fun upsertEvent(event: AuditEvent) {
        events.value = events.value + (event.actionId to event)
    }

    override suspend fun eventsForExport(filter: AuditFilter): List<AuditEvent> = events.value.values.toList()

    override suspend fun purgeOlderThan(cutoffEpochMillis: Long): Int {
        val previous = events.value
        events.value = previous.filterValues { it.occurredAtEpochMillis >= cutoffEpochMillis }
        return previous.size - events.value.size
    }

    override fun observeRetentionDays(): Flow<Int> = retentionDays

    override suspend fun setRetentionDays(days: Int) {
        retentionDays.value = days
    }
}
