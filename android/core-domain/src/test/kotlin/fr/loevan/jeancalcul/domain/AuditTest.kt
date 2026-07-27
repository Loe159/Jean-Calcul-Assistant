package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AuditTest {
    @Test
    fun `execution receipts keep success and error mutually exclusive`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionReceipt(
                succeeded = true,
                replayed = false,
                durationMillis = 1,
                errorCode = "UNEXPECTED",
            )
        }
    }

    @Test
    fun `audit event carries policy and execution records independently`() {
        val event =
            AuditEvent(
                actionId = "action-1",
                origin = ActionRequestOrigin.USER_TEXT,
                toolName = "audio.get_volume",
                toolVersion = "1.0.0",
                riskLevel = ToolRiskLevel.R0,
                policy =
                    PolicyDecisionRecord(
                        PolicyDecisionType.ALLOW,
                        PolicyReason.TOOL_DEFAULT,
                        "default",
                        ActionApprovalStatus.AUTHORIZED,
                    ),
                execution = ExecutionReceipt(true, false, 2, resultSummary = "{}"),
                outcome = AuditOutcome.SUCCESS,
                occurredAtEpochMillis = 1,
                updatedAtEpochMillis = 3,
            )

        assertEquals(AuditOutcome.SUCCESS, event.outcome)
        assertEquals(PolicyDecisionType.ALLOW, event.policy?.decision)
        assertEquals(2L, event.execution?.durationMillis)
    }
}
