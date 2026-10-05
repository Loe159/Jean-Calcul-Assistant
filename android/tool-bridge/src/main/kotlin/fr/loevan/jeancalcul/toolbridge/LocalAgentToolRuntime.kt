package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionPolicyPreference
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AgentPolicyProfile
import fr.loevan.jeancalcul.domain.PolicyDecision
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.domain.PolicyEvaluationContext
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolCall
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.domain.ToolError
import fr.loevan.jeancalcul.domain.ToolResult

/**
 * Executes a tool chosen by the reasoning backend only through Jean Calcul's local policy and
 * versioned Android tool registry.
 *
 * The originating user turn is treated as consent for currently registered reversible R2 actions.
 * Higher-risk actions keep their normal confirmation/biometric floor.
 */
class LocalAgentToolRuntime(
    private val toolRegistry: ToolRegistry,
    private val availabilityContext: () -> fr.loevan.jeancalcul.domain.ToolAvailabilityContext,
    private val policyEngine: PolicyEngine,
    private val origin: ActionRequestOrigin,
    private val profileId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun availableTools(): List<ToolDefinition> = toolRegistry.availableDefinitions(availabilityContext())

    fun execute(call: ToolCall): ToolResult {
        val definition =
            toolRegistry.definitionForName(call.toolName)
                ?: return failure(call, "UNKNOWN_TOOL", "The requested Android tool is not registered.")
        val availability = availabilityContext()
        return if (toolRegistry.availableDefinitions(availability).none { it.name == definition.name }) {
            failure(
                call,
                "TOOL_UNAVAILABLE",
                "The requested Android tool is unavailable in the current device state.",
                definition.version,
            )
        } else {
            executeAvailable(call, definition, availability)
        }
    }

    private fun executeAvailable(
        call: ToolCall,
        definition: ToolDefinition,
        availability: ToolAvailabilityContext,
    ): ToolResult {
        val now = clock()
        val proposal =
            ActionProposal(
                actionId = call.callId,
                toolName = definition.name,
                toolVersion = definition.version,
                arguments = call.arguments,
                idempotencyKey = call.callId,
                expiresAtEpochMillis = now + TOOL_REQUEST_TTL_MILLIS,
            )
        val decision = evaluatePolicy(definition, proposal, availability, now)
        return if (decision.type == PolicyDecisionType.ALLOW) {
            val receipt = policyEngine.issueReceipt(decision, now)
            toolRegistry.execute(proposal, availability, receipt)
        } else {
            failure(call, decision.failureCode(), decision.justification, definition.version)
        }
    }

    private fun evaluatePolicy(
        definition: ToolDefinition,
        proposal: ActionProposal,
        availability: ToolAvailabilityContext,
        now: Long,
    ): PolicyDecision =
        policyEngine.evaluate(
            definition,
            proposal,
            PolicyEvaluationContext(
                profile =
                    AgentPolicyProfile(
                        id = profileId,
                        allowAutomaticReversibleActions = true,
                        confirmAgentActions = false,
                    ),
                origin = origin,
                grantedAndroidPermissions = availability.grantedAndroidPermissions,
                isDeviceLocked = availability.isDeviceLocked,
                isAppForeground = availability.isAppForeground,
                preferences =
                    listOf(
                        ActionPolicyPreference(
                            toolName = definition.name,
                            decision = PolicyDecisionType.ALLOW,
                        ),
                    ),
                nowEpochMillis = now,
            ),
        )

    private fun PolicyDecision.failureCode(): String =
        when (type) {
            PolicyDecisionType.CONFIRM -> "USER_CONFIRMATION_REQUIRED"
            PolicyDecisionType.BIOMETRIC -> "BIOMETRIC_REQUIRED"
            PolicyDecisionType.OPEN_SYSTEM_PANEL -> "ANDROID_PERMISSION_REQUIRED"
            PolicyDecisionType.DENY -> "POLICY_DENIED"
            PolicyDecisionType.ALLOW -> error("Allowed policy decisions do not have a failure code.")
        }

    private fun failure(
        call: ToolCall,
        code: String,
        message: String,
        version: String = toolRegistry.definitionForName(call.toolName)?.version ?: "1.0.0",
    ): ToolResult =
        ToolResult(
            actionId = call.callId,
            toolName = call.toolName,
            toolVersion = version,
            error = ToolError(code, message),
        )

    private companion object {
        const val TOOL_REQUEST_TTL_MILLIS = 120_000L
    }
}
