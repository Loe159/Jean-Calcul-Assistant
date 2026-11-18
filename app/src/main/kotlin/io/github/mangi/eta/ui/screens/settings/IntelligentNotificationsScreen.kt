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
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterConfig
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterAction
import io.github.mangi.eta.agent.device.notifications.NotificationAppRule
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
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
    var status by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(NotificationFilterSettings.load(context)) }
    var selectedApp by remember { mutableStateOf("") }
    var confirmPermanent by remember { mutableStateOf(false) }
    var recentApps by remember { mutableStateOf(NotificationObservation.recentPackages()) }

    fun saveConfig(next: NotificationFilterConfig) {
        NotificationFilterSettings.save(context, next)
        config = next
    }

    fun selectMode(mode: NotificationFilterMode) {
        if (mode != NotificationFilterMode.OFF && !installed) {
            status = "Installe le modèle avant d'activer un mode."
        } else if (mode != NotificationFilterMode.OFF &&
            !AgentNotificationHistoryService.isEnabled(context)) {
            status = "Autorise d'abord l'accès aux notifications Android."
        } else {
            saveConfig(config.copy(mode = mode, action =
                if (mode != NotificationFilterMode.PERSONALIZED &&
                    config.action == NotificationFilterAction.DISMISS)
                    NotificationFilterAction.SNOOZE_1H else config.action))
            status = when (mode) {
                NotificationFilterMode.OFF -> "Filtrage désactivé"
                NotificationFilterMode.OBSERVE -> "Observation activée : aucune notification masquée"
                NotificationFilterMode.CAUTIOUS -> "Filtrage prudent : publicités très certaines, masquées 1 heure"
                NotificationFilterMode.PERSONALIZED -> "Personnalisé : configure les applications autorisées ci-dessous"
            }
        }
    }

    var busy by remember { mutableStateOf(false) }
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
                        selectMode(NotificationFilterMode.OFF)
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
        Footer(text = "Les scores représentent la correspondance entre hypothèses, pas une probabilité calibrée de spam. La saisie manuelle n'entraîne aucune action sur les notifications.")
        Group(title = "Modes de filtrage") {
            val options = listOf(
                NotificationFilterMode.OFF to ("Désactivé" to "Aucune classification en arrière-plan"),
                NotificationFilterMode.OBSERVE to ("1 · Observer" to "Analyser sans jamais modifier les notifications"),
                NotificationFilterMode.CAUTIOUS to ("2 · Prudent" to "Masquer 1 heure uniquement les publicités très certaines"),
                NotificationFilterMode.PERSONALIZED to ("3 · Personnalisé" to "Catégories, applications et plages horaires"),
            )
            options.forEach { (mode, text) ->
                CouiPreference(
                    title = text.first,
                    summary = (if (config.mode == mode) "Sélectionné · " else "") + text.second,
                    enabled = !busy,
                    onClick = { selectMode(mode) },
                )
            }
        }
        Footer(text = "Les masquages temporaires utilisent le report Android : les notifications peuvent réapparaître après 1 h ou 24 h. Le score du modèle n'est pas une probabilité de spam. Aucune suppression définitive en mode prudent.")

        if (config.mode == NotificationFilterMode.PERSONALIZED) {
            Group(title = "Règles personnalisées") {
                CouiSwitchPreference(title = "Publicités", checked = config.filterAds,
                    summary = "Autoriser le filtrage des promotions dans les applications choisies",
                    onCheckedChange = { saveConfig(config.copy(filterAds = it)) })
                CouiSwitchPreference(title = "Spams et arnaques", checked = config.filterSpam,
                    summary = "Activable seulement pour les applications autorisées · prudence recommandée",
                    onCheckedChange = { saveConfig(config.copy(filterSpam = it)) })
                CouiSwitchPreference(title = "Seulement pendant une plage horaire", checked = config.onlyQuietHours,
                    summary = "Par défaut : 22 h à 7 h, heure locale",
                    onCheckedChange = { saveConfig(config.copy(onlyQuietHours = it)) })
                if (config.onlyQuietHours) {
                    CouiPreference(
                        title = "Horaire : ${config.quietFrom} h – ${config.quietUntil} h",
                        summary = "Changer : 22–7 → 0–7 → 9–18 → 22–7",
                        onClick = {
                            val (from, until) = when (config.quietFrom to config.quietUntil) {
                                22 to 7 -> 0 to 7
                                0 to 7 -> 9 to 18
                                else -> 22 to 7
                            }
                            saveConfig(config.copy(quietFrom = from, quietUntil = until))
                        },
                    )
                }
                CouiPreference(title = "Action : " + when (config.action) {
                    NotificationFilterAction.SNOOZE_1H -> "Masquer 1 heure"
                    NotificationFilterAction.SNOOZE_24H -> "Masquer 24 heures"
                    NotificationFilterAction.DISMISS -> "Supprimer définitivement"
                }, summary = "Changer entre report 1 h et report 24 h",
                    onClick = {
                        saveConfig(config.copy(action = when (config.action) {
                            NotificationFilterAction.SNOOZE_1H -> NotificationFilterAction.SNOOZE_24H
                            else -> NotificationFilterAction.SNOOZE_1H
                        }))
                    })
                CouiPreference(title = "Suppression définitive (risquée)",
                    summary = "Désactivée par défaut · aucun retour arrière possible · confiance 99,9 % minimum",
                    onClick = { confirmPermanent = true })
                if (confirmPermanent) {
                    Footer(text = "Attention : supprimer une notification est irréversible. Android ne peut pas la restaurer. Cette option n'agit que sur les applications explicitement autorisées et conserve les protections sensibles.")
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        CouiButton(text = "Confirmer la suppression définitive", role = CouiButtonRole.Destructive,
                            onClick = {
                                saveConfig(config.copy(action = NotificationFilterAction.DISMISS))
                                confirmPermanent = false
                            })
                        CouiButton(text = "Annuler", onClick = { confirmPermanent = false })
                    }
                }
            }
            Footer(text = "Mode personnalisé : par défaut, aucune application n'est filtrée. Ajoute explicitement les applications ci-dessous. Les conversations, OTP, banques, alertes et notifications système restent protégés.")

            Group(title = "Applications autorisées") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CouiTextField(value = selectedApp, onValueChange = { selectedApp = it.take(200) },
                        label = "Identifiant de l'application",
                        placeholder = "com.exemple.boutique",
                        singleLine = true)
                    CouiButton(text = "Autoriser le filtrage pour cette application",
                        enabled = NotificationFilterSettings.isValidPackageName(selectedApp.trim()),
                        role = CouiButtonRole.Primary,
                        onClick = {
                            val pkg = selectedApp.trim()
                            if (NotificationFilterSettings.isValidPackageName(pkg)) {
                                saveConfig(config.copy(appRules =
                                    config.appRules + (pkg to NotificationAppRule.FILTER)))
                                status = "$pkg : filtrage autorisé (avec protections)"
                            }
                        })
                    CouiButton(text = "Toujours conserver les notifications de cette application",
                        enabled = NotificationFilterSettings.isValidPackageName(selectedApp.trim()),
                        onClick = {
                            val pkg = selectedApp.trim()
                            if (NotificationFilterSettings.isValidPackageName(pkg)) {
                                saveConfig(config.copy(appRules =
                                    config.appRules + (pkg to NotificationAppRule.KEEP)))
                                status = "$pkg : notifications toujours conservées"
                            }
                        })
                }
                (config.appRules.keys + recentApps).distinct().sorted().take(100).forEach { pkg ->
                    val rule = config.appRules[pkg] ?: NotificationAppRule.DEFAULT
                    CouiPreference(title = pkg,
                        summary = when (rule) {
                            NotificationAppRule.DEFAULT -> "Non filtré · toucher pour autoriser"
                            NotificationAppRule.FILTER -> "Filtrage autorisé · toucher pour protéger"
                            NotificationAppRule.KEEP -> "Toujours conserver · toucher pour réinitialiser"
                        },
                        onClick = {
                            val next = when (rule) {
                                NotificationAppRule.DEFAULT -> NotificationAppRule.FILTER
                                NotificationAppRule.FILTER -> NotificationAppRule.KEEP
                                NotificationAppRule.KEEP -> NotificationAppRule.DEFAULT
                            }
                            val rules = config.appRules.toMutableMap()
                            if (next == NotificationAppRule.DEFAULT) rules.remove(pkg) else rules[pkg] = next
                            saveConfig(config.copy(appRules = rules))
                        })
                }
            }
        }
        Group(title = "Observation sans suppression") {
            CouiPreference(title = "Mode actif : ${config.mode.name.lowercase()}",
                summary = "Changer dans « Modes de filtrage » ci-dessus",
                showChevron = false)
            CouiPreference(title = "Autorisation Android",
                summary = if (AgentNotificationHistoryService.isEnabled(context)) "Accès accordé" else "Accès non accordé",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            CouiPreference(title = "Actualiser les résultats", summary = stats, onClick = {
                events = NotificationObservation.snapshot()
                stats = NotificationObservation.statistics()
                recentApps = NotificationObservation.recentPackages()
            })
            events.forEach { row ->
                CouiPreference(title = row, showChevron = false)
            }
        }
        Footer(text = "Historique en mémoire vive (50 entrées maximum), sans texte ni identifiant de notification. Maximum 1 inférence/minute et 100/jour ; suspendu en économie d'énergie ou sous 20 % de batterie. Les changements de modes sont immédiats. Les notifications déjà reportées restent soumises au délai Android.")
        if (status.isNotBlank()) Footer(text = status)
        CouiPageBottomSpacer()
    }
}
