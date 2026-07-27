@file:Suppress("TooManyFunctions")

package fr.loevan.jeancalcul.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class PolicyDecisionType {
    ALLOW,
    CONFIRM,
    BIOMETRIC,
    OPEN_SYSTEM_PANEL,
    DENY,
}

@Serializable
enum class PolicyReason {
    TOOL_DEFAULT,
    USER_PREFERENCE,
    PROFILE_RESTRICTION,
    TOOL_MISMATCH,
    ACTION_EXPIRED,
    DEVICE_LOCKED,
    APP_NOT_FOREGROUND,
    PERMISSION_MISSING,
    RISK_REQUIRES_CONFIRMATION,
    RISK_REQUIRES_BIOMETRIC,
    RISK_DENIED,
}

@Serializable
enum class ActionRequestOrigin {
    USER_VOICE,
    USER_TEXT,
    MODEL_PROVIDER,
    AGENT_BACKEND,
    EXTERNAL_CONTENT,
}

data class AgentPolicyProfile(
    val id: String,
    val maximumRiskLevel: ToolRiskLevel = ToolRiskLevel.R4,
    val allowedToolNames: Set<String>? = null,
    val allowAutomaticReversibleActions: Boolean = false,
    val confirmAgentActions: Boolean = true,
) {
    init {
        require(id.isNotBlank())
        require(allowedToolNames?.none(String::isBlank) != false)
    }
}

/** A user override can target a tool and an exact subset of its parameters. */
data class ActionPolicyPreference(
    val toolName: String,
    val arguments: JsonObject = JsonObject(emptyMap()),
    val decision: PolicyDecisionType,
) {
    init {
        require(toolName.isNotBlank())
    }

    internal fun matches(proposal: ActionProposal): Boolean =
        proposal.toolName == toolName && arguments.all { (name, value) -> proposal.arguments[name] == value }
}

data class PolicyEvaluationContext(
    val profile: AgentPolicyProfile,
    val origin: ActionRequestOrigin,
    val grantedAndroidPermissions: Set<String> = emptySet(),
    val isDeviceLocked: Boolean,
    val isAppForeground: Boolean,
    val preferences: List<ActionPolicyPreference> = emptyList(),
    val nowEpochMillis: Long,
) {
    init {
        require(grantedAndroidPermissions.none(String::isBlank))
    }
}

data class ActionParameterSummary(
    val name: String,
    val exactValue: String,
)

data class ActionSummary(
    val title: String,
    val description: String,
    val parameters: List<ActionParameterSummary>,
)

data class PolicyDecision(
    val proposal: ActionProposal,
    val type: PolicyDecisionType,
    val reason: PolicyReason,
    val justification: String,
    val riskLevel: ToolRiskLevel,
    val origin: ActionRequestOrigin,
    val summary: ActionSummary,
    val missingAndroidPermissions: Set<String> = emptySet(),
    val evaluatedAtEpochMillis: Long,
)

@Serializable
enum class ActionApprovalMethod {
    USER_CONFIRMATION,
    BIOMETRIC,
}

/** The user's response to one exact policy decision. */
data class ActionApproval(
    val actionId: String,
    val approved: Boolean,
    val method: ActionApprovalMethod,
    val decidedAtEpochMillis: Long,
) {
    init {
        require(actionId.isNotBlank())
    }
}

@Serializable
enum class ActionApprovalStatus {
    AUTHORIZED,
    REJECTED,
    EXPIRED,
}

/**
 * Capability issued only by [PolicyEngine]. The tool bridge binds it to the complete proposal before execution.
 */
sealed interface ActionApprovalReceipt {
    val proposal: ActionProposal
    val decision: PolicyDecision
    val status: ActionApprovalStatus
    val method: ActionApprovalMethod?
    val issuedAtEpochMillis: Long
    val expiresAtEpochMillis: Long
}

enum class PolicyAuditStage {
    DECISION,
    APPROVAL,
}

data class PolicyAuditEvent(
    val actionId: String,
    val toolName: String,
    val toolVersion: String,
    val arguments: JsonObject,
    val origin: ActionRequestOrigin,
    val riskLevel: ToolRiskLevel,
    val decision: PolicyDecisionType,
    val reason: PolicyReason,
    val justification: String,
    val stage: PolicyAuditStage,
    val approvalStatus: ActionApprovalStatus? = null,
    val approvalMethod: ActionApprovalMethod? = null,
    val approvalApproved: Boolean? = null,
    val occurredAtEpochMillis: Long,
)

fun interface PolicyAuditLogger {
    fun log(event: PolicyAuditEvent)
}

/** Deterministic, platform-neutral policy matrix for every local tool action. */
class PolicyEngine(
    private val auditLogger: PolicyAuditLogger = PolicyAuditLogger { },
    private val receiptLifetimeMillis: Long = DEFAULT_RECEIPT_LIFETIME_MILLIS,
) {
    init {
        require(receiptLifetimeMillis > 0)
    }

    fun evaluate(
        definition: ToolDefinition,
        proposal: ActionProposal,
        context: PolicyEvaluationContext,
    ): PolicyDecision {
        val rule =
            proposalRule(definition, proposal, context)
                ?: accessRule(definition, context)
                ?: permissionRule(definition, context)
                ?: configuredRule(definition, proposal, context)
        return decide(definition, proposal, context, rule)
    }

    fun issueReceipt(
        decision: PolicyDecision,
        nowEpochMillis: Long,
        approval: ActionApproval? = null,
    ): ActionApprovalReceipt {
        val proposalExpiry = decision.proposal.expiresAtEpochMillis ?: Long.MAX_VALUE
        val receiptExpiry = minOf(proposalExpiry, nowEpochMillis + receiptLifetimeMillis)
        val status = approvalStatus(decision, approval, nowEpochMillis)
        return IssuedActionApprovalReceipt(
            proposal = decision.proposal,
            decision = decision,
            status = status,
            method = approval?.method,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = receiptExpiry,
        ).also { receipt ->
            audit(
                PolicyAuditEvent(
                    actionId = decision.proposal.actionId,
                    toolName = decision.proposal.toolName,
                    toolVersion = decision.proposal.toolVersion,
                    arguments = decision.proposal.arguments,
                    origin = decision.origin,
                    riskLevel = decision.riskLevel,
                    decision = decision.type,
                    reason = decision.reason,
                    justification = decision.justification,
                    stage = PolicyAuditStage.APPROVAL,
                    approvalStatus = receipt.status,
                    approvalMethod = receipt.method,
                    approvalApproved = approval?.approved,
                    occurredAtEpochMillis = nowEpochMillis,
                ),
            )
        }
    }

    private fun proposalRule(
        definition: ToolDefinition,
        proposal: ActionProposal,
        context: PolicyEvaluationContext,
    ): DecisionRule? =
        when {
            proposal.toolName != definition.name || proposal.toolVersion != definition.version ->
                DecisionRule(PolicyDecisionType.DENY, PolicyReason.TOOL_MISMATCH)
            proposal.expiresAtEpochMillis?.let { context.nowEpochMillis >= it } == true ->
                DecisionRule(PolicyDecisionType.DENY, PolicyReason.ACTION_EXPIRED)
            else -> null
        }

    private fun accessRule(
        definition: ToolDefinition,
        context: PolicyEvaluationContext,
    ): DecisionRule? =
        when {
            !context.profile.allows(definition) ->
                DecisionRule(PolicyDecisionType.DENY, PolicyReason.PROFILE_RESTRICTION)
            definition.riskLevel == ToolRiskLevel.R5 ->
                DecisionRule(PolicyDecisionType.DENY, PolicyReason.RISK_DENIED)
            context.isDeviceLocked && !definition.isSafeOnLockScreen() ->
                DecisionRule(PolicyDecisionType.DENY, PolicyReason.DEVICE_LOCKED)
            else -> null
        }

    private fun permissionRule(
        definition: ToolDefinition,
        context: PolicyEvaluationContext,
    ): DecisionRule? {
        val missingPermissions = definition.requiredAndroidPermissions - context.grantedAndroidPermissions
        if (missingPermissions.isEmpty()) return null

        return if (context.isAppForeground && !context.isDeviceLocked) {
            DecisionRule(PolicyDecisionType.OPEN_SYSTEM_PANEL, PolicyReason.PERMISSION_MISSING, missingPermissions)
        } else {
            val reason =
                if (context.isDeviceLocked) PolicyReason.DEVICE_LOCKED else PolicyReason.APP_NOT_FOREGROUND
            DecisionRule(PolicyDecisionType.DENY, reason, missingPermissions)
        }
    }

    private fun configuredRule(
        definition: ToolDefinition,
        proposal: ActionProposal,
        context: PolicyEvaluationContext,
    ): DecisionRule {
        val preferred = context.preferences.firstOrNull { it.matches(proposal) }?.decision
        val base = preferred ?: definition.defaultPolicy.toDecisionType()
        val riskAdjusted = base.enforceRiskFloor(definition.riskLevel, context.profile)
        val adjusted = riskAdjusted.enforceOrigin(context.origin, context.profile, definition.riskLevel)
        if (!context.isAppForeground && adjusted.requiresForeground(definition.riskLevel)) {
            return DecisionRule(PolicyDecisionType.DENY, PolicyReason.APP_NOT_FOREGROUND)
        }
        return DecisionRule(adjusted, adjusted.reasonFor(base, preferred))
    }

    private fun approvalStatus(
        decision: PolicyDecision,
        approval: ActionApproval?,
        nowEpochMillis: Long,
    ): ActionApprovalStatus =
        when {
            decision.proposal.expiresAtEpochMillis?.let { nowEpochMillis >= it } == true ->
                ActionApprovalStatus.EXPIRED
            decision.type == PolicyDecisionType.ALLOW && approval == null ->
                ActionApprovalStatus.AUTHORIZED
            approval?.isValidFor(decision, nowEpochMillis) != true ->
                ActionApprovalStatus.REJECTED
            decision.type == PolicyDecisionType.CONFIRM ->
                ActionApprovalStatus.AUTHORIZED
            decision.type == PolicyDecisionType.BIOMETRIC && approval.method == ActionApprovalMethod.BIOMETRIC ->
                ActionApprovalStatus.AUTHORIZED
            else -> ActionApprovalStatus.REJECTED
        }

    private fun decide(
        definition: ToolDefinition,
        proposal: ActionProposal,
        context: PolicyEvaluationContext,
        rule: DecisionRule,
    ): PolicyDecision =
        PolicyDecision(
            proposal = proposal,
            type = rule.type,
            reason = rule.reason,
            justification = rule.reason.justification(),
            riskLevel = definition.riskLevel,
            origin = context.origin,
            summary = definition.summaryFor(proposal),
            missingAndroidPermissions = rule.missingPermissions,
            evaluatedAtEpochMillis = context.nowEpochMillis,
        ).also { decision ->
            audit(
                PolicyAuditEvent(
                    actionId = proposal.actionId,
                    toolName = proposal.toolName,
                    toolVersion = proposal.toolVersion,
                    arguments = proposal.arguments,
                    origin = context.origin,
                    riskLevel = definition.riskLevel,
                    decision = rule.type,
                    reason = rule.reason,
                    justification = rule.reason.justification(),
                    stage = PolicyAuditStage.DECISION,
                    occurredAtEpochMillis = context.nowEpochMillis,
                ),
            )
        }

    private fun audit(event: PolicyAuditEvent) {
        runCatching { auditLogger.log(event) }
    }

    private data class DecisionRule(
        val type: PolicyDecisionType,
        val reason: PolicyReason,
        val missingPermissions: Set<String> = emptySet(),
    )

    private companion object {
        const val DEFAULT_RECEIPT_LIFETIME_MILLIS = 120_000L
    }
}

fun ActionApprovalReceipt.authorizes(
    proposal: ActionProposal,
    nowEpochMillis: Long,
    isDeviceLocked: Boolean = false,
    isAppForeground: Boolean = true,
): Boolean {
    val receiptMatches =
        status == ActionApprovalStatus.AUTHORIZED &&
            this.proposal == proposal &&
            decision.proposal == proposal &&
            nowEpochMillis < expiresAtEpochMillis
    val contextStillAllowsExecution =
        decision.riskLevel == ToolRiskLevel.R0 || (!isDeviceLocked && isAppForeground)
    return receiptMatches && contextStillAllowsExecution
}

private data class IssuedActionApprovalReceipt(
    override val proposal: ActionProposal,
    override val decision: PolicyDecision,
    override val status: ActionApprovalStatus,
    override val method: ActionApprovalMethod?,
    override val issuedAtEpochMillis: Long,
    override val expiresAtEpochMillis: Long,
) : ActionApprovalReceipt

private fun AgentPolicyProfile.allows(definition: ToolDefinition): Boolean =
    definition.riskLevel.ordinal <= maximumRiskLevel.ordinal &&
        (allowedToolNames == null || definition.name in allowedToolNames)

private fun ToolDefinition.isSafeOnLockScreen(): Boolean =
    riskLevel == ToolRiskLevel.R0 && availability.lockScreenConstraint == ToolLockScreenConstraint.AVAILABLE

private fun ToolDefaultPolicy.toDecisionType(): PolicyDecisionType =
    when (this) {
        ToolDefaultPolicy.ALLOW -> PolicyDecisionType.ALLOW
        ToolDefaultPolicy.CONFIRM -> PolicyDecisionType.CONFIRM
        ToolDefaultPolicy.BIOMETRIC -> PolicyDecisionType.BIOMETRIC
        ToolDefaultPolicy.OPEN_SYSTEM_PANEL -> PolicyDecisionType.OPEN_SYSTEM_PANEL
        ToolDefaultPolicy.DENY -> PolicyDecisionType.DENY
    }

private fun PolicyDecisionType.enforceRiskFloor(
    risk: ToolRiskLevel,
    profile: AgentPolicyProfile,
): PolicyDecisionType =
    when (risk) {
        ToolRiskLevel.R0,
        ToolRiskLevel.R1,
        -> this
        ToolRiskLevel.R2 ->
            if (this == PolicyDecisionType.ALLOW && !profile.allowAutomaticReversibleActions) {
                PolicyDecisionType.CONFIRM
            } else {
                this
            }
        ToolRiskLevel.R3 -> if (this == PolicyDecisionType.ALLOW) PolicyDecisionType.CONFIRM else this
        ToolRiskLevel.R4 ->
            when (this) {
                PolicyDecisionType.DENY,
                PolicyDecisionType.OPEN_SYSTEM_PANEL,
                -> this
                else -> PolicyDecisionType.BIOMETRIC
            }
        ToolRiskLevel.R5 -> PolicyDecisionType.DENY
    }

private fun PolicyDecisionType.enforceOrigin(
    origin: ActionRequestOrigin,
    profile: AgentPolicyProfile,
    risk: ToolRiskLevel,
): PolicyDecisionType {
    val agentNeedsConfirmation = profile.confirmAgentActions && origin.requiresExplicitApproval()
    return if (this == PolicyDecisionType.ALLOW && risk != ToolRiskLevel.R0 && agentNeedsConfirmation) {
        PolicyDecisionType.CONFIRM
    } else {
        this
    }
}

private fun ActionRequestOrigin.requiresExplicitApproval(): Boolean =
    this == ActionRequestOrigin.AGENT_BACKEND || this == ActionRequestOrigin.EXTERNAL_CONTENT

private fun PolicyDecisionType.reasonFor(
    base: PolicyDecisionType,
    preferred: PolicyDecisionType?,
): PolicyReason =
    when {
        this == PolicyDecisionType.BIOMETRIC && base != PolicyDecisionType.BIOMETRIC ->
            PolicyReason.RISK_REQUIRES_BIOMETRIC
        this == PolicyDecisionType.CONFIRM && base == PolicyDecisionType.ALLOW ->
            PolicyReason.RISK_REQUIRES_CONFIRMATION
        preferred != null -> PolicyReason.USER_PREFERENCE
        else -> PolicyReason.TOOL_DEFAULT
    }

private fun ActionApproval.isValidFor(
    decision: PolicyDecision,
    nowEpochMillis: Long,
): Boolean {
    val matchesDecision = actionId == decision.proposal.actionId && approved
    val timestampIsValid =
        decidedAtEpochMillis >= decision.evaluatedAtEpochMillis && decidedAtEpochMillis <= nowEpochMillis
    return matchesDecision && timestampIsValid
}

private fun PolicyDecisionType.requiresForeground(risk: ToolRiskLevel): Boolean =
    this != PolicyDecisionType.ALLOW || risk.ordinal >= ToolRiskLevel.R2.ordinal

private fun ToolDefinition.summaryFor(proposal: ActionProposal): ActionSummary =
    ActionSummary(
        title = "$name $version",
        description = description,
        parameters =
            proposal.arguments.entries
                .sortedBy(Map.Entry<String, JsonElement>::key)
                .map { (name, value) -> ActionParameterSummary(name, value.toString()) },
    )

private fun PolicyReason.justification(): String =
    when (this) {
        PolicyReason.TOOL_DEFAULT -> "The tool's declared default policy applies."
        PolicyReason.USER_PREFERENCE -> "The matching user preference applies to these exact parameters."
        PolicyReason.PROFILE_RESTRICTION -> "The active agent profile does not allow this tool or risk level."
        PolicyReason.TOOL_MISMATCH -> "The proposal does not match the evaluated tool name and version."
        PolicyReason.ACTION_EXPIRED -> "The action proposal has expired."
        PolicyReason.DEVICE_LOCKED -> "The lock screen restricts this action."
        PolicyReason.APP_NOT_FOREGROUND -> "This action requires the application to be in the foreground."
        PolicyReason.PERMISSION_MISSING -> "A required Android permission is missing."
        PolicyReason.RISK_REQUIRES_CONFIRMATION -> "This risk level cannot run without explicit confirmation."
        PolicyReason.RISK_REQUIRES_BIOMETRIC -> "This sensitive risk level requires biometric authentication."
        PolicyReason.RISK_DENIED -> "This risk level is denied by default."
    }
