@file:Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ActionCardState {
    Proposed,
    AutoAllowed,
    ConfirmationRequired,
    BiometricRequired,
    PermissionMissing,
    Executing,
    Succeeded,
    Failed,
    Denied,
    Cancelled,
}

enum class ActionRisk {
    R0,
    R1,
    R2,
    R3,
    R4,
    R5,
}

data class ActionCardData(
    val title: String,
    val summary: String,
    val risk: ActionRisk,
    val origin: String,
    val state: ActionCardState,
    val result: String? = null,
)

/** A presentation-only action summary; policy decisions and execution remain outside core-ui. */
@Composable
fun ActionCard(
    data: ActionCardData,
    modifier: Modifier = Modifier,
    onDetails: (() -> Unit)? = null,
) {
    val surfaceState = if (data.state == ActionCardState.Failed) GlassSurfaceState.Error else GlassSurfaceState.Normal
    GlassSurface(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = actionDescription(data) },
        variant = GlassSurfaceVariant.Card,
        state = surfaceState,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = actionStateLabel(data.state), style = MaterialTheme.typography.labelSmall)
            Text(text = data.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text = data.summary, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(state = data.risk.toStatusBadge())
                StatusBadge(state = data.state.toStatusBadge())
            }
            Text(
                text = "Origine : ${data.origin}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            data.result?.let { result ->
                Text(text = "Résultat : $result", style = MaterialTheme.typography.bodyMedium)
            }
            onDetails?.let { callback ->
                JeanCalculButton(
                    label = "Afficher les détails",
                    variant = JeanCalculButtonVariant.Ghost,
                    onClick = callback,
                )
            }
        }
    }
}

enum class ApprovalSheetState {
    SimpleConfirmation,
    DetailedConfirmation,
    Biometric,
    OpenSystemPanel,
    Denied,
    Expired,
    LockedScreen,
}

@Composable
fun ApprovalSheet(
    state: ApprovalSheetState,
    action: ActionCardData,
    justification: String,
    modifier: Modifier = Modifier,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Approbation : ${action.title}" },
        variant = GlassSurfaceVariant.Modal,
        state = if (state == ApprovalSheetState.Denied) GlassSurfaceState.Error else GlassSurfaceState.Normal,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = approvalTitle(state), style = MaterialTheme.typography.headlineMedium)
            Text(text = action.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text = action.summary, style = MaterialTheme.typography.bodyLarge)
            StatusBadge(state = action.risk.toStatusBadge())
            GlassSurface(variant = GlassSurfaceVariant.Panel) {
                Column {
                    Text(text = "Justification", style = MaterialTheme.typography.labelSmall)
                    Text(text = justification, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state == ApprovalSheetState.Biometric) {
                PrivacyIndicator(state = PrivacyIndicatorState.BiometricRequired)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onReject?.let { callback ->
                    JeanCalculButton(
                        label = "Refuser",
                        variant = JeanCalculButtonVariant.Destructive,
                        onClick = callback,
                    )
                }
                onApprove?.let { callback ->
                    JeanCalculButton(
                        label = if (state == ApprovalSheetState.OpenSystemPanel) "Ouvrir Android" else "Continuer",
                        onClick = callback,
                    )
                }
            }
        }
    }
}

private fun ActionRisk.toStatusBadge(): StatusBadgeState =
    when (this) {
        ActionRisk.R0 -> StatusBadgeState.RiskR0
        ActionRisk.R1 -> StatusBadgeState.RiskR1
        ActionRisk.R2 -> StatusBadgeState.RiskR2
        ActionRisk.R3 -> StatusBadgeState.RiskR3
        ActionRisk.R4 -> StatusBadgeState.RiskR4
        ActionRisk.R5 -> StatusBadgeState.RiskR5
    }

private fun ActionCardState.toStatusBadge(): StatusBadgeState =
    when (this) {
        ActionCardState.Proposed,
        ActionCardState.ConfirmationRequired,
        ActionCardState.BiometricRequired,
        -> StatusBadgeState.Warning

        ActionCardState.AutoAllowed,
        ActionCardState.Executing,
        -> StatusBadgeState.Active

        ActionCardState.Succeeded -> StatusBadgeState.Success
        ActionCardState.Failed -> StatusBadgeState.Error
        ActionCardState.Denied,
        ActionCardState.Cancelled,
        ActionCardState.PermissionMissing,
        -> StatusBadgeState.Inactive
    }

private fun actionStateLabel(state: ActionCardState): String =
    when (state) {
        ActionCardState.Proposed -> "Action proposée"
        ActionCardState.AutoAllowed -> "Autorisation automatique"
        ActionCardState.ConfirmationRequired -> "Confirmation requise"
        ActionCardState.BiometricRequired -> "Biométrie requise"
        ActionCardState.PermissionMissing -> "Permission manquante"
        ActionCardState.Executing -> "Exécution en cours"
        ActionCardState.Succeeded -> "Action réussie"
        ActionCardState.Failed -> "Échec de l’action"
        ActionCardState.Denied -> "Action refusée"
        ActionCardState.Cancelled -> "Action annulée"
    }

private fun actionDescription(data: ActionCardData): String =
    "${actionStateLabel(data.state)} : ${data.title}, risque ${data.risk.name}, origine ${data.origin}"

private fun approvalTitle(state: ApprovalSheetState): String =
    when (state) {
        ApprovalSheetState.SimpleConfirmation -> "Confirmer l’action"
        ApprovalSheetState.DetailedConfirmation -> "Vérifier avant de continuer"
        ApprovalSheetState.Biometric -> "Confirmer avec Android"
        ApprovalSheetState.OpenSystemPanel -> "Ouvrir le panneau Android"
        ApprovalSheetState.Denied -> "Action refusée"
        ApprovalSheetState.Expired -> "Demande expirée"
        ApprovalSheetState.LockedScreen -> "Déverrouillez l’appareil"
    }
