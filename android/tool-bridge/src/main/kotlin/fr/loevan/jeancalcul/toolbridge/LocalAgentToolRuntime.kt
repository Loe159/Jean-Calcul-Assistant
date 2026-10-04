package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionPolicyPreference
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AgentPolicyProfile
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.domain.PolicyEvaluationContext
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
        if (toolRegistry.availableDefinitions(availability).none { it.name == definition.name }) {
            return failure(
                call,
                "TOOL_UNAVAILABLE",
                "The requested Android tool is unavailable in the current device state.",
                definition.version,
            )
        }

        val proposal =
            ActionProposal(
                actionId = call.callId,
                toolName = definition.name,
                toolVersion = definition.version,
                arguments = call.arguments,
                idempotencyKey = call.callId,
                expiresAtEpochMillis = clock() + TOOL_REQUEST_TTL_MILLIS,
            )
        val now = clock()
        val policyProfile =
            AgentPolicyProfile(
                id = profileId,
                allowAutomaticReversibleActions = true,
                confirmAgentActions = false,
            )
        val decision =
            policyEngine.evaluate(
                definition,
                proposal,
                PolicyEvaluationContext(
                    profile = policyProfile,
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

        if (decision.type != PolicyDecisionType.ALLOW) {
            val code =
                when (decision.type) {
                    PolicyDecisionType.CONFIRM -> "USER_CONFIRMATION_REQUIRED"
                    PolicyDecisionType.BIOMETRIC -> "BIOMETRIC_REQUIRED"
                    PolicyDecisionType.OPEN_SYSTEM_PANEL -> "ANDROID_PERMISSION_REQUIRED"
                    PolicyDecisionType.DENY -> "POLICY_DENIED"
                    PolicyDecisionType.ALLOW -> error("unreachable")
                }
            return failure(call, code, decision.justification, definition.version)
        }

        val receipt = policyEngine.issueReceipt(decision, now)
        return toolRegistry.execute(proposal, availability, receipt)
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
