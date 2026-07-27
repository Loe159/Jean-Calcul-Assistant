package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.ActionApproval
import fr.loevan.jeancalcul.domain.ActionApprovalMethod
import fr.loevan.jeancalcul.domain.ActionApprovalStatus
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AgentPolicyProfile
import fr.loevan.jeancalcul.domain.DeterministicVolumeCommandInterpreter
import fr.loevan.jeancalcul.domain.PolicyDecision
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.domain.PolicyEvaluationContext
import fr.loevan.jeancalcul.domain.RelativeVolumeAdjustment
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolResult
import fr.loevan.jeancalcul.domain.VolumeCommandInterpretation
import fr.loevan.jeancalcul.observability.PerformanceTrace
import fr.loevan.jeancalcul.observability.PerformanceTraceEvent
import fr.loevan.jeancalcul.toolbridge.ToolRegistry
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Routes local commands through schema validation, policy approval, and the versioned tool registry. */
@Suppress("LongParameterList")
internal class VolumeCommandProcessor(
    private val interpreter: DeterministicVolumeCommandInterpreter,
    private val toolRegistry: ToolRegistry,
    private val availabilityContext: () -> ToolAvailabilityContext,
    private val policyEngine: PolicyEngine = PolicyEngine(),
    private val policyContext: (ToolAvailabilityContext, Long) -> PolicyEvaluationContext = ::localVoicePolicyContext,
    private val clock: () -> Long = System::currentTimeMillis,
    private val performanceTrace: PerformanceTrace = NoOpPerformanceTrace,
) : VoiceCommandProcessor {
    private var pendingAction: PendingPolicyAction? = null

    override fun process(transcript: String): VoiceCommandOutcome =
        when (val interpretation = interpreter.interpret(transcript)) {
            is VolumeCommandInterpretation.Ready -> review(interpretation.proposal)
            is VolumeCommandInterpretation.ConfirmationRequired -> prepareRelativeAdjustment(interpretation.adjustment)
            is VolumeCommandInterpretation.Invalid -> VoiceCommandOutcome.Invalid(interpretation.message)
        }

    override fun confirm(): VoiceCommandOutcome {
        val pending = pendingAction ?: return VoiceCommandOutcome.Invalid(NO_PENDING_ACTION)
        pendingAction = null
        val now = clock()
        val approval =
            ActionApproval(
                actionId = pending.proposal.actionId,
                approved = true,
                method = ActionApprovalMethod.USER_CONFIRMATION,
                decidedAtEpochMillis = now,
            )
        val receipt = policyEngine.issueReceipt(pending.decision, now, approval)
        return if (receipt.status == ActionApprovalStatus.AUTHORIZED) {
            execute(pending.proposal, receipt)
        } else {
            VoiceCommandOutcome.Failure("Cette action exige une approbation plus forte ou a expire.")
        }
    }

    override fun cancelPending() {
        pendingAction = null
    }

    private fun prepareRelativeAdjustment(adjustment: RelativeVolumeAdjustment): VoiceCommandOutcome {
        val readResult = executeAutomatic(interpreter.getVolumeProposal(adjustment.stream))
        val currentPercent =
            readResult?.output?.get("volumePercent")?.jsonPrimitive?.intOrNull
                ?: return VoiceCommandOutcome.Failure("Je n'ai pas pu lire le volume actuel.")
        val targetPercent = (currentPercent + adjustment.deltaPercent).coerceIn(0, 100)
        return review(interpreter.setMusicVolumeProposal(targetPercent))
    }

    private fun review(proposal: ActionProposal): VoiceCommandOutcome {
        val definition =
            toolRegistry.definitionFor(proposal)
                ?: return VoiceCommandOutcome.Failure("L'outil demande n'est pas enregistre.")
        val availability = availabilityContext()
        val now = clock()
        val decision = policyEngine.evaluate(definition, proposal, policyContext(availability, now))
        return when (decision.type) {
            PolicyDecisionType.ALLOW -> {
                val receipt = policyEngine.issueReceipt(decision, now)
                execute(proposal, receipt)
            }
            PolicyDecisionType.CONFIRM,
            PolicyDecisionType.BIOMETRIC,
            -> {
                pendingAction = PendingPolicyAction(proposal, decision)
                VoiceCommandOutcome.ApprovalRequired(decision)
            }
            PolicyDecisionType.OPEN_SYSTEM_PANEL -> VoiceCommandOutcome.PermissionRequired(decision)
            PolicyDecisionType.DENY -> VoiceCommandOutcome.Failure(decision.justification)
        }
    }

    @Suppress("ReturnCount")
    private fun executeAutomatic(proposal: ActionProposal): ToolResult? {
        val definition = toolRegistry.definitionFor(proposal) ?: return null
        val availability = availabilityContext()
        val now = clock()
        val decision = policyEngine.evaluate(definition, proposal, policyContext(availability, now))
        if (decision.type != PolicyDecisionType.ALLOW) return null
        val receipt = policyEngine.issueReceipt(decision, now)
        return toolRegistry.execute(proposal, availability, receipt)
    }

    private fun execute(
        proposal: ActionProposal,
        receipt: fr.loevan.jeancalcul.domain.ActionApprovalReceipt,
    ): VoiceCommandOutcome {
        val isVolumeWrite = proposal.toolName == "audio.set_volume"
        if (isVolumeWrite) performanceTrace.mark(PerformanceTraceEvent.VOLUME_REQUESTED)
        val result = toolRegistry.execute(proposal, availabilityContext(), receipt)
        if (isVolumeWrite && result.isSuccess) performanceTrace.mark(PerformanceTraceEvent.VOLUME_APPLIED)
        val observedPercent = result.output?.get("volumePercent")?.jsonPrimitive?.intOrNull
        return if (result.isSuccess && observedPercent != null) {
            VoiceCommandOutcome.Completed("Le volume de musique est maintenant a $observedPercent %.")
        } else {
            VoiceCommandOutcome.Failure(result.error?.message ?: "Je n'ai pas pu modifier le volume.")
        }
    }

    private data class PendingPolicyAction(
        val proposal: ActionProposal,
        val decision: PolicyDecision,
    )

    private companion object {
        const val NO_PENDING_ACTION = "Aucune action n'est en attente de confirmation."
    }
}

internal interface VoiceCommandProcessor {
    fun process(transcript: String): VoiceCommandOutcome

    fun confirm(): VoiceCommandOutcome

    fun cancelPending()
}

internal sealed interface VoiceCommandOutcome {
    data class Completed(val response: String) : VoiceCommandOutcome

    data class ApprovalRequired(val decision: PolicyDecision) : VoiceCommandOutcome

    data class PermissionRequired(val decision: PolicyDecision) : VoiceCommandOutcome

    data class Invalid(val message: String) : VoiceCommandOutcome

    data class Failure(val message: String) : VoiceCommandOutcome
}

private fun localVoicePolicyContext(
    availability: ToolAvailabilityContext,
    nowEpochMillis: Long,
): PolicyEvaluationContext =
    PolicyEvaluationContext(
        profile = AgentPolicyProfile(id = "local-assistant"),
        origin = ActionRequestOrigin.USER_VOICE,
        grantedAndroidPermissions = availability.grantedAndroidPermissions,
        isDeviceLocked = availability.isDeviceLocked,
        isAppForeground = availability.isAppForeground,
        nowEpochMillis = nowEpochMillis,
    )
