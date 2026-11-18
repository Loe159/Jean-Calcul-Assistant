package io.github.mangi.eta.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.LocalNotificationModel
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterConfig
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterAction
import io.github.mangi.eta.agent.device.notifications.NotificationLabelRule
import io.github.mangi.eta.agent.device.notifications.NotificationLabelRules
import io.github.mangi.eta.agent.device.notifications.NotificationFilterCandidate
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.components.coui.CouiSelectPreference
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.CouiSliderPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private fun labelTitle(label: String): String = when (label) {
    "a promotional advertisement" -> "Publicité"
    "spam or a scam" -> "Spam ou arnaque"
    "a personal message" -> "Message personnel"
    "a security alert" -> "Alerte de sécurité"
    "a delivery update" -> "Suivi de livraison"
    "an appointment reminder" -> "Rappel de rendez-vous"
    "a useful service update" -> "Information utile"
    else -> label
}

/** The page is the only place where a model download can be requested. */
@Composable
internal fun IntelligentNotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(LocalNotificationModel.installed(context)) }
    var listenerGranted by remember { mutableStateOf(AgentNotificationHistoryService.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                installed = LocalNotificationModel.installed(context)
                listenerGranted = AgentNotificationHistoryService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var status by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(NotificationFilterSettings.load(context)) }
    var showArchives by rememberSaveable { mutableStateOf(false) }
    var showAdvancedRules by remember { mutableStateOf(false) }
    var expandedRule by remember { mutableStateOf<String?>(null) }
    var editedLabel by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }
    var scoreDrafts by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    var marginDrafts by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    fun saveConfig(next: NotificationFilterConfig) {
        NotificationFilterSettings.save(context, next)
        config = next
    }

    fun changeRule(old: String, new: NotificationLabelRule) {
        val updated = config.labelRules.map { if (it.label == old) new else it }
        if (!NotificationLabelRules.valid(updated)) {
            status = "Label invalide ou déjà utilisé (3–96 caractères, sans doublon)."
            return
        }
        saveConfig(config.copy(labelRules = updated))
    }

    fun selectMode(mode: NotificationFilterMode) {
        if (mode != NotificationFilterMode.OFF && !installed) {
            status = "Installez le modèle avant d'activer un mode."
        } else if (mode != NotificationFilterMode.OFF && !listenerGranted) {
            status = "Autorisez d'abord l'accès aux notifications Android."
        } else {
            saveConfig(config.copy(mode = mode, action =
                if (mode != NotificationFilterMode.PERSONALIZED &&
                    config.action == NotificationFilterAction.DISMISS)
                    NotificationFilterAction.SNOOZE_1H else config.action))
            status = when (mode) {
                NotificationFilterMode.OFF -> "Filtrage désactivé"
                NotificationFilterMode.OBSERVE -> "Observation activée : aucune notification masquée"
                NotificationFilterMode.CAUTIOUS -> "Filtrage prudent : publicités très certaines, masquées 1 heure"
                NotificationFilterMode.PERSONALIZED ->
                    "Filtrage automatique : configurez les catégories et l'action"
            }
        }
    }

    var busy by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    var events by remember { mutableStateOf(NotificationObservation.snapshot()) }
    var stats by remember { mutableStateOf(NotificationObservation.statistics()) }
    var diagnostic by remember { mutableStateOf(NotificationObservation.diagnostics(context)) }

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

    BackHandler(showArchives) { showArchives = false }
    if (showArchives) {
        ArchivedBriefNotificationsScreen(onBack = { showArchives = false })
        return
    }

    CouiScaffoldPage(title = if (largeText) "Notifications" else "Notifications intelligentes",
        onBack = onBack, compactTitle = true) {
        Group(title = "Préparation locale") {
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
            CouiPreference(
                title = "Accès aux notifications Android",
                summary = if (listenerGranted) "Autorisé" else "À autoriser pour recevoir les notifications",
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
        }
        Footer(text = "Les textes saisis et les notifications ne sont jamais envoyés à un serveur. Le réseau est utilisé uniquement pour installer les fichiers du modèle.")
        Group(title = "Historique local") {
            CouiPreference(
                title = "Archives du briefing",
                summary = "Copies retirées du briefing · consultables et restaurables pendant 7 jours",
                onClick = { showArchives = true },
            )
        }
        Group(title = "Modes de filtrage") {
            val options = listOf(
                NotificationFilterMode.OFF to ("Désactivé" to "Aucune classification en arrière-plan"),
                NotificationFilterMode.OBSERVE to ("Observer" to "Analyser sans modifier les notifications"),
                NotificationFilterMode.CAUTIOUS to ("Prudent" to "Reporter 1 heure les publicités très certaines"),
                NotificationFilterMode.PERSONALIZED to
                    ("Filtrage automatique" to "Toutes les applications éligibles · règles et horaires configurables"),
            )
            options.forEach { (mode, text) ->
                CouiSelectPreference(
                    title = text.first,
                    summary = text.second,
                    selected = config.mode == mode,
                    enabled = !busy && (mode == NotificationFilterMode.OFF ||
                        (installed && listenerGranted)),
                    onSelect = { selectMode(mode) },
                )
            }
        }
        if (!installed || !listenerGranted) {
            Footer(text = when {
                !installed && !listenerGranted ->
                    "Pour activer un mode, installez le modèle et autorisez l’accès aux notifications ci-dessus."
                !installed -> "Pour activer un mode, installez le modèle local ci-dessus."
                else -> "Pour activer un mode, autorisez l’accès aux notifications ci-dessus."
            })
        }
        Footer(text = "Les reports Android durent 1 h ou 24 h ; les notifications peuvent ensuite réapparaître. Le mode prudent ne supprime jamais définitivement.")

        if (config.mode == NotificationFilterMode.PERSONALIZED) {
            Group(title = "Règles personnalisées") {
                CouiPreference(
                    title = if (showAdvancedRules) "Masquer les catégories et seuils"
                        else "Catégories et seuils",
                    summary = "${config.labelRules.count { it.enabled }} catégories actives sur ${config.labelRules.size} · réglages avancés",
                    onClick = { showAdvancedRules = !showAdvancedRules },
                )
                if (showAdvancedRules) {
                    config.labelRules.forEach { rule ->
                        CouiSwitchPreference(
                            title = labelTitle(rule.label),
                            summary = rule.label + " · score ≥ ${rule.minimumScore} % · écart ≥ ${rule.minimumMargin} points",
                            checked = rule.enabled,
                            onCheckedChange = { changeRule(rule.label, rule.copy(enabled = it)) },
                        )
                        CouiPreference(
                            title = if (expandedRule == rule.label) "Masquer les réglages" else "Régler : ${labelTitle(rule.label)}",
                            summary = if (expandedRule == rule.label) "Modifier le label et ses deux seuils" else
                                "Seuils de filtrage indépendants pour cette catégorie",
                            onClick = {
                                expandedRule = if (expandedRule == rule.label) null else rule.label
                                editedLabel = rule.label
                            },
                        )
                        if (expandedRule == rule.label) {
                            CouiSliderPreference(
                                title = "Score minimum · ${labelTitle(rule.label)}",
                                value = scoreDrafts[rule.label] ?: rule.minimumScore.toFloat(),
                                valueText = "${(scoreDrafts[rule.label] ?: rule.minimumScore.toFloat()).roundToInt()} %",
                                valueRange = 0f..100f,
                                onValueChange = { value -> scoreDrafts = scoreDrafts + (rule.label to value) },
                                onValueChangeFinished = {
                                    val score = (scoreDrafts[rule.label] ?: rule.minimumScore.toFloat()).roundToInt()
                                    changeRule(rule.label, rule.copy(minimumScore = score))
                                    scoreDrafts = scoreDrafts - rule.label
                                },
                            )
                            CouiSliderPreference(
                                title = "Avance sur le 2e label · ${labelTitle(rule.label)}",
                                value = marginDrafts[rule.label] ?: rule.minimumMargin.toFloat(),
                                valueText = "${(marginDrafts[rule.label] ?: rule.minimumMargin.toFloat()).roundToInt()} points",
                                valueRange = 0f..100f,
                                onValueChange = { value -> marginDrafts = marginDrafts + (rule.label to value) },
                                onValueChangeFinished = {
                                    val margin = (marginDrafts[rule.label] ?: rule.minimumMargin.toFloat()).roundToInt()
                                    changeRule(rule.label, rule.copy(minimumMargin = margin))
                                    marginDrafts = marginDrafts - rule.label
                                },
                            )
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                CouiTextField(
                                    value = editedLabel,
                                    onValueChange = { editedLabel = it.take(96) },
                                    label = "Hypothèse en anglais (ex. : a gaming promotion)",
                                    singleLine = true,
                                )
                                CouiButton(text = "Enregistrer le label", onClick = {
                                    val proposed = editedLabel.trim()
                                    if (proposed != rule.label) {
                                        val newRule = rule.copy(label = proposed, enabled = false)
                                        val updated = config.labelRules.map {
                                            if (it.label == rule.label) newRule else it
                                        }
                                        if (NotificationLabelRules.valid(updated)) {
                                            saveConfig(config.copy(labelRules = updated))
                                            expandedRule = proposed
                                            status = "Label modifié. Réactivez la catégorie pour la filtrer."
                                        } else status = "Label invalide ou déjà présent."
                                    }
                                })
                                if (config.labelRules.size > 2) {
                                    CouiButton(text = "Retirer ce label", role = CouiButtonRole.Destructive,
                                        onClick = {
                                            saveConfig(config.copy(
                                                labelRules = config.labelRules.filterNot { it.label == rule.label }))
                                            expandedRule = null
                                            status = "Label retiré de la classification locale."
                                        })
                                }
                            }
                        }
                    }
                    if (config.labelRules.size < NotificationLabelRules.MAX_LABELS) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            CouiTextField(
                                value = newLabel,
                                onValueChange = { newLabel = it.take(96) },
                                label = "Nouvelle catégorie (en anglais)",
                                placeholder = "Ex. : a social media promotion",
                                singleLine = true,
                            )
                            CouiButton(text = "Ajouter le label", onClick = {
                                val proposed = newLabel.trim()
                                val updated = config.labelRules + NotificationLabelRule(proposed)
                                if (NotificationLabelRules.valid(updated)) {
                                    saveConfig(config.copy(labelRules = updated))
                                    expandedRule = proposed
                                    editedLabel = proposed
                                    newLabel = ""
                                    status = "Label ajouté, filtrage désactivé par défaut."
                                } else status = "Label invalide, en doublon ou limite atteinte."
                            })
                        }
                    }
                    CouiPreference(
                        title = "Restaurer les 7 labels d'origine",
                        summary = "Restaure les seuils 40 % / 8 points pour la publicité et 65 % / 20 points pour le spam.",
                        onClick = {
                            saveConfig(config.copy(labelRules = NotificationLabelRules.defaults()))
                            expandedRule = null
                            status = "Labels et seuils d'origine restaurés."
                        },
                    )
                }
                CouiSwitchPreference(title = "Seulement pendant une plage horaire", checked = config.onlyQuietHours,
                    summary = "Heure locale · ${config.quietFrom} h à ${config.quietUntil} h",
                    onCheckedChange = { saveConfig(config.copy(onlyQuietHours = it)) })
                if (config.onlyQuietHours) {
                    listOf(22 to 7, 0 to 7, 9 to 18).forEach { (from, until) ->
                        CouiSelectPreference(
                            title = "$from h à $until h",
                            selected = config.quietFrom == from && config.quietUntil == until,
                            onSelect = { saveConfig(config.copy(quietFrom = from, quietUntil = until)) },
                        )
                    }
                }
                CouiSelectPreference(
                    title = "Reporter 1 heure",
                    summary = "Les notifications peuvent réapparaître ensuite",
                    selected = config.action == NotificationFilterAction.SNOOZE_1H,
                    onSelect = { saveConfig(config.copy(action = NotificationFilterAction.SNOOZE_1H)) },
                )
                CouiSelectPreference(
                    title = "Reporter 24 heures",
                    summary = "Les notifications peuvent réapparaître ensuite",
                    selected = config.action == NotificationFilterAction.SNOOZE_24H,
                    onSelect = { saveConfig(config.copy(action = NotificationFilterAction.SNOOZE_24H)) },
                )
                CouiSelectPreference(
                    title = "Supprimer définitivement",
                    summary = "Suppression Android irréversible · toutes les applications tierces éligibles",
                    selected = config.action == NotificationFilterAction.DISMISS,
                    onSelect = {
                        saveConfig(config.copy(action = NotificationFilterAction.DISMISS))
                        status = "Suppression Android activée pour les applications tierces éligibles."
                    },
                )
            }
            Footer(text = "Modifier les catégories change les scores relatifs. Testez d’abord en mode Observer. Les alertes sensibles et importantes restent protégées.")
        }
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
                                LocalNotificationModel.classify(context, text, config.inferenceLabels())
                            }
                            results = classified
                            status = "Inférence terminée sur cet appareil"
                        }
                    })
            }
            if (results.isNotEmpty()) {
                val evaluation = NotificationFilterPolicy.evaluate(config, NotificationFilterCandidate(
                    app = "simulation.locale", safe = true, scores = results,
                    localHour = java.time.LocalTime.now().hour,
                ))
                CouiPreference(
                    title = "Simulation : " + when (evaluation.decision) {
                        io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision.DISMISS ->
                            "Suppression envisagée"
                        io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision.SNOOZE_1H,
                        io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision.SNOOZE_24H ->
                            "Report envisagé"
                        else -> "Conservation"
                    },
                    summary = evaluation.reason + " · les protections Android ne sont pas simulées",
                    showChevron = false,
                )
            }
            results.forEach { (label, score) ->
                CouiPreference(title = label,
                    summary = String.format(Locale.FRANCE, "%.1f %%", score * 100),
                    showChevron = false)
            }
        }
        Footer(text = "Les scores représentent la correspondance entre hypothèses, pas une probabilité calibrée de spam. La saisie manuelle n'entraîne aucune action sur les notifications.")
        Group(title = "État et diagnostic") {
            CouiPreference(
                title = "Réessayer l'analyse des notifications actives",
                summary = "Reprend les notifications non analysées toujours présentes " +
                    "sur Android · sans dépasser 5 analyses/minute",
                enabled = installed && listenerGranted,
                onClick = {
                    val count = NotificationObservation.retryActive(context)
                    status = if (count == 0)
                        "Aucune notification active récente à réévaluer"
                    else "$count notification(s) remise(s) dans la file locale"
                    diagnostic = NotificationObservation.diagnostics(context)
                    stats = NotificationObservation.statistics()
                },
            )
            CouiPreference(
                title = "Diagnostic de l'analyse locale",
                summary = diagnostic,
                onClick = {
                    diagnostic = NotificationObservation.diagnostics(context)
                    stats = NotificationObservation.statistics()
                    events = NotificationObservation.snapshot()
                },
            )
            CouiPreference(title = "Actualiser les résultats", summary = stats, onClick = {
                events = NotificationObservation.snapshot()
                stats = NotificationObservation.statistics()
                diagnostic = NotificationObservation.diagnostics(context)
            })
            events.forEach { row ->
                CouiPreference(title = row, showChevron = false)
            }
        }
        Footer(text = "Historique en mémoire vive (50 entrées maximum), sans texte ni identifiant de notification. Maximum 5 inférences/minute et 500/jour ; suspendu en économie d'énergie ou sous 20 % de batterie. Les changements de modes sont immédiats. Les notifications déjà reportées restent soumises au délai Android.")
        if (status.isNotBlank()) Footer(text = status)
        CouiPageBottomSpacer()
    }
}
