package io.github.mangi.eta.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.remote.ActionCommand
import io.github.mangi.eta.agent.device.notifications.remote.ActionDetails
import io.github.mangi.eta.agent.device.notifications.remote.ActionProposal
import io.github.mangi.eta.agent.device.notifications.remote.ActionRecord
import io.github.mangi.eta.agent.device.notifications.remote.ActionType
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationClient
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive

/**
 * Suggested actions are proposals. Each step is explicit: PREPARE asks the homelab to
 * prepare it (calendar: the Secrétaire suspends on its native approval), the user then
 * approves the exact summary shown, bound by its fingerprint. Drafts are never sent.
 */
private sealed interface ActionDialog {
    data class Working(val label: String) : ActionDialog
    data class Approve(val entry: NotificationHistoryRepository.RecentEntry, val record: ActionRecord) : ActionDialog
    data class Draft(val record: ActionRecord) : ActionDialog
    data class Duration(val entry: NotificationHistoryRepository.RecentEntry, val proposal: ActionProposal,
                        val start: String) : ActionDialog
    data class Notice(val title: String, val message: String) : ActionDialog
}

internal class BriefActionController(
    val onAction: (NotificationHistoryRepository.RecentEntry, ActionProposal) -> Unit,
    val dialogs: @Composable () -> Unit,
)

@Composable
internal fun rememberBriefActionController(
    repository: NotificationHistoryRepository,
    onOpen: (NotificationHistoryRepository.RecentEntry) -> Unit,
): BriefActionController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ActionDialog?>(null) }
    var duration by remember { mutableStateOf("60") }

    fun feedback(entry: NotificationHistoryRepository.RecentEntry, accepted: Boolean, type: ActionType) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                repository.feedback(entry.key, entry.postedAt,
                    if (accepted) NotificationUserFeedback.ACTION_ACCEPTED else NotificationUserFeedback.ACTION_REJECTED,
                    actionType = type.name)
                MastraNotificationSync.request(context)
            }
        }
    }

    fun command(entry: NotificationHistoryRepository.RecentEntry, actionId: String, label: String,
                command: String, fingerprint: String? = null, details: ActionDetails? = null) {
        val pairing = MastraConnection.load(context)
        if (pairing == null) {
            dialog = ActionDialog.Notice(label, "Homelab non configuré. Réglages → Notifications intelligentes → Homelab.")
            return
        }
        dialog = ActionDialog.Working(label)
        scope.launch {
            dialog = try {
                val record = withContext(Dispatchers.IO) {
                    MastraNotificationClient(pairing).command(actionId,
                        ActionCommand(deviceId = pairing.deviceId, command = command, fingerprint = fingerprint, details = details))
                }
                when (record.state) {
                    "AWAITING_APPROVAL" -> ActionDialog.Approve(entry, record)
                    "DRAFT_READY" -> ActionDialog.Draft(record)
                    "EXECUTED" -> ActionDialog.Notice(label, record.result ?: "Action effectuée")
                    "REJECTED" -> null
                    "FAILED" -> ActionDialog.Notice(label, "Échec : ${record.error ?: "erreur inconnue"}")
                    else -> ActionDialog.Notice(label, "État : ${record.state}")
                }
            } catch (e: MastraHttpException) {
                ActionDialog.Notice(label, when (e.code) {
                    "MISSING_DETAILS" -> "Date ou heure manquante : ajoutez l'événement depuis l'agenda."
                    "INVALID_STATE" -> "Cette suggestion a déjà été traitée."
                    "FINGERPRINT_MISMATCH" -> "La proposition a changé : rouvrez-la pour la vérifier."
                    else -> "Homelab indisponible (${e.status}). Réessayez plus tard."
                })
            } catch (_: Exception) {
                ActionDialog.Notice(label, "Homelab injoignable. Réessayez plus tard.")
            }
        }
    }

    val onAction: (NotificationHistoryRepository.RecentEntry, ActionProposal) -> Unit = { entry, proposal ->
        when (proposal.type) {
            ActionType.OPEN_SOURCE_APP -> onOpen(entry)
            ActionType.CREATE_CALENDAR_EVENT -> {
                val start = proposal.payload["start"]?.jsonPrimitive?.contentOrNullSafe()
                val end = proposal.payload["end"]?.jsonPrimitive?.contentOrNullSafe()
                when {
                    start == null -> dialog = ActionDialog.Notice(proposal.label,
                        "Date ou heure introuvable dans la notification : rien n'est deviné.")
                    end == null -> { duration = "60"; dialog = ActionDialog.Duration(entry, proposal, start) }
                    else -> command(entry, proposal.actionId, proposal.label, "PREPARE")
                }
            }
            else -> command(entry, proposal.actionId, proposal.label, "PREPARE")
        }
    }

    val dialogs: @Composable () -> Unit = {
        when (val d = dialog) {
            is ActionDialog.Working -> CouiNoticeDialog(visible = true, title = d.label,
                message = "Préparation sur le homelab…", onDismissRequest = { dialog = null })
            is ActionDialog.Approve -> CouiConfirmDialog(
                visible = true, title = d.record.label, message = d.record.approval?.summary,
                confirmText = "Valider", cancelText = "Refuser",
                onConfirm = {
                    feedback(d.entry, true, d.record.type)
                    command(d.entry, d.record.actionId, d.record.label, "APPROVE", d.record.approval?.fingerprint)
                },
                onDismissRequest = {
                    feedback(d.entry, false, d.record.type)
                    command(d.entry, d.record.actionId, d.record.label, "REJECT")
                },
            )
            is ActionDialog.Draft -> CouiConfirmDialog(
                visible = true, title = "Brouillon (non envoyé)", message = d.record.draft,
                confirmText = "Copier", cancelText = "Fermer",
                onConfirm = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("Brouillon", d.record.draft.orEmpty()))
                    dialog = null
                },
                onDismissRequest = { dialog = null },
            )
            is ActionDialog.Duration -> CouiInputDialog(
                visible = true, title = d.proposal.label,
                message = "Durée du rendez-vous en minutes (aucune durée n'est choisie à votre place).",
                value = duration, onValueChange = { duration = it.filter(Char::isDigit).take(4) },
                confirmText = "Continuer", confirmEnabled = duration.toIntOrNull()?.let { it in 5..1440 } == true,
                onConfirm = {
                    val end = runCatching { OffsetDateTime.parse(d.start).plusMinutes(duration.toLong()).toString() }.getOrNull()
                    if (end == null) dialog = ActionDialog.Notice(d.proposal.label, "Heure de début invalide.")
                    else command(d.entry, d.proposal.actionId, d.proposal.label, "PREPARE", details = ActionDetails(end = end))
                },
                onDismissRequest = { dialog = null },
            )
            is ActionDialog.Notice -> CouiNoticeDialog(visible = true, title = d.title, message = d.message,
                onDismissRequest = { dialog = null })
            null -> Unit
        }
    }
    return BriefActionController(onAction, dialogs)
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
