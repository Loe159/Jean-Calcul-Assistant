package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.PolicyDecision
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyReason
import fr.loevan.jeancalcul.domain.ToolRiskLevel
import fr.loevan.jeancalcul.ui.ActionCardData
import fr.loevan.jeancalcul.ui.ActionCardState
import fr.loevan.jeancalcul.ui.ActionDetail
import fr.loevan.jeancalcul.ui.ActionRisk
import fr.loevan.jeancalcul.ui.ApprovalSheetState

internal fun PolicyDecision.toActionCardData(): ActionCardData =
    ActionCardData(
        title = summary.title,
        summary = summary.description,
        risk = riskLevel.toActionRisk(),
        origin = origin.displayName(),
        state = type.toActionCardState(),
        details = summary.parameters.map { ActionDetail(it.name, it.exactValue) },
    )

internal fun PolicyDecision.toApprovalSheetState(): ApprovalSheetState =
    when (type) {
        PolicyDecisionType.CONFIRM ->
            if (riskLevel.ordinal >= ToolRiskLevel.R3.ordinal) {
                ApprovalSheetState.DetailedConfirmation
            } else {
                ApprovalSheetState.SimpleConfirmation
            }
        PolicyDecisionType.BIOMETRIC -> ApprovalSheetState.Biometric
        PolicyDecisionType.OPEN_SYSTEM_PANEL -> ApprovalSheetState.OpenSystemPanel
        PolicyDecisionType.DENY ->
            when (reason) {
                PolicyReason.ACTION_EXPIRED -> ApprovalSheetState.Expired
                PolicyReason.DEVICE_LOCKED -> ApprovalSheetState.LockedScreen
                else -> ApprovalSheetState.Denied
            }
        PolicyDecisionType.ALLOW -> ApprovalSheetState.SimpleConfirmation
    }

private fun PolicyDecisionType.toActionCardState(): ActionCardState =
    when (this) {
        PolicyDecisionType.ALLOW -> ActionCardState.AutoAllowed
        PolicyDecisionType.CONFIRM -> ActionCardState.ConfirmationRequired
        PolicyDecisionType.BIOMETRIC -> ActionCardState.BiometricRequired
        PolicyDecisionType.OPEN_SYSTEM_PANEL -> ActionCardState.PermissionMissing
        PolicyDecisionType.DENY -> ActionCardState.Denied
    }

private fun ToolRiskLevel.toActionRisk(): ActionRisk = ActionRisk.valueOf(name)

private fun ActionRequestOrigin.displayName(): String =
    when (this) {
        ActionRequestOrigin.USER_VOICE -> "Commande vocale"
        ActionRequestOrigin.USER_TEXT -> "Saisie utilisateur"
        ActionRequestOrigin.MODEL_PROVIDER -> "Modele configure"
        ActionRequestOrigin.AGENT_BACKEND -> "Agent configure"
        ActionRequestOrigin.EXTERNAL_CONTENT -> "Contenu externe"
    }
