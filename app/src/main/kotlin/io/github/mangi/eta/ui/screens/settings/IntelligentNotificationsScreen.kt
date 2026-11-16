package io.github.mangi.eta.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.LocalNotificationModel
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** The page is the only place where a model download can be requested. */
@Composable
internal fun IntelligentNotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(LocalNotificationModel.installed(context)) }
    var enabled by remember { mutableStateOf(NotificationObservation.enabled(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var events by remember { mutableStateOf(NotificationObservation.snapshot()) }
    var stats by remember { mutableStateOf(NotificationObservation.statistics()) }

    fun action(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { task() } catch (error: Exception) {
                status = error.message?.take(170) ?: "Erreur locale"
            } finally {
                busy = false
                installed = LocalNotificationModel.installed(context)
            }
        }
    }

    CouiScaffoldPage(title = "Notifications intelligentes", onBack = onBack, compactTitle = true) {
        Group(title = "Modèle local") {
            CouiPreference(title = "Horizon-Labs · multilingual-zeroshot-small",
                summary = if (installed) "Modèle installé · 302 Mo · traitement hors ligne" else
                    "Modèle non installé · environ 302 Mo à télécharger",
                showChevron = false)
            CouiPreference(
                title = if (installed) "Modèle prêt à tester" else "Installer le modèle",
                summary = "Téléchargement HTTPS des poids et du tokenizer uniquement, avec vérification SHA-256",
                enabled = !busy && !installed,
                onClick = {
                    action {
                        withContext(Dispatchers.IO) {
                            LocalNotificationModel.install(context) { message -> status = message }
                        }
                    }
                },
            )
            if (installed) {
                CouiPreference(title = "Supprimer les fichiers du modèle",
                    summary = "Désactive également le mode observation",
                    enabled = !busy, onClick = {
                        NotificationObservation.setEnabled(context, false)
                        enabled = false
                        LocalNotificationModel.delete(context)
                        installed = false
                        results = emptyList()
                        status = "Modèle désinstallé"
                    })
            }
        }
        Footer(text = "Les textes saisis et les notifications ne sont jamais envoyés à un serveur. Le réseau est utilisé uniquement pour installer les fichiers du modèle.")
        Group(title = "Tester une notification") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                CouiTextField(value = text, onValueChange = { text = it.take(512) },
                    label = "Titre et contenu d'une notification",
                    placeholder = "Ex. : Offre spéciale, -70 % aujourd'hui !",
                    singleLine = false, minLines = 3, maxLines = 5)
                CouiButton(text = if (busy) "Traitement local…" else "Classifier localement",
                    enabled = installed && !busy && text.isNotBlank(), role = CouiButtonRole.Primary,
                    onClick = {
                        results = emptyList()
                        action {
                            val classified = withContext(Dispatchers.IO) {
                                LocalNotificationModel.classify(context, text)
                            }
                            results = classified
                            status = "Inférence terminée sur cet appareil"
                        }
                    })
            }
            results.forEach { (label, score) ->
                CouiPreference(title = label,
                    summary = String.format(Locale.FRANCE, "%.1f %%", score * 100),
                    showChevron = false)
            }
        }
        Footer(text = "Les scores représentent la correspondance entre hypothèses, pas une probabilité calibrée de spam. Aucun message n'est supprimé.")
        Group(title = "Observation sans suppression") {
            CouiPreference(title = if (enabled) "Observation activée" else "Observation désactivée",
                summary = "Sur demande uniquement · maximum 1 notification par minute · aucune suppression",
                enabled = installed && !busy,
                onClick = {
                    if (!AgentNotificationHistoryService.isEnabled(context)) {
                        status = "Autorise d'abord l'accès aux notifications Android."
                    } else {
                        enabled = !enabled
                        NotificationObservation.setEnabled(context, enabled)
                        status = if (enabled) "Observation activée" else "Observation désactivée"
                    }
                })
            CouiPreference(title = "Autorisation Android",
                summary = if (AgentNotificationHistoryService.isEnabled(context)) "Accès accordé" else "Accès non accordé",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            CouiPreference(title = "Actualiser les résultats", summary = stats, onClick = {
                events = NotificationObservation.snapshot()
                stats = NotificationObservation.statistics()
            })
            events.forEach { row ->
                CouiPreference(title = row, showChevron = false)
            }
        }
        Footer(text = "Le mode observation conserve uniquement l'application, la catégorie et un score en mémoire vive (20 entrées maximum). Les messages, appels, codes de connexion, rappels et notifications système sont protégés. L'observation s'arrête sans modèle.")
        if (status.isNotBlank()) Footer(text = status)
        CouiPageBottomSpacer()
    }
}
