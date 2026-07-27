package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionApproval
import fr.loevan.jeancalcul.domain.ActionApprovalMethod
import fr.loevan.jeancalcul.domain.ActionApprovalReceipt
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AgentPolicyProfile
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.domain.PolicyEvaluationContext
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolDefinition

internal fun policyReceipt(
    definition: ToolDefinition,
    proposal: ActionProposal,
    availability: ToolAvailabilityContext,
    nowEpochMillis: Long = System.currentTimeMillis(),
): ActionApprovalReceipt {
    val engine = PolicyEngine()
    val decision =
        engine.evaluate(
            definition,
            proposal,
            PolicyEvaluationContext(
                profile = AgentPolicyProfile("tool-bridge-test", allowAutomaticReversibleActions = true),
                origin = ActionRequestOrigin.USER_TEXT,
                grantedAndroidPermissions = availability.grantedAndroidPermissions,
                isDeviceLocked = availability.isDeviceLocked,
                isAppForeground = true,
                nowEpochMillis = nowEpochMillis,
            ),
        )
    val approval =
        when (decision.type) {
            PolicyDecisionType.ALLOW -> null
            PolicyDecisionType.BIOMETRIC ->
                ActionApproval(proposal.actionId, true, ActionApprovalMethod.BIOMETRIC, nowEpochMillis)
            else -> ActionApproval(proposal.actionId, true, ActionApprovalMethod.USER_CONFIRMATION, nowEpochMillis)
        }
    return engine.issueReceipt(decision, nowEpochMillis, approval)
}
