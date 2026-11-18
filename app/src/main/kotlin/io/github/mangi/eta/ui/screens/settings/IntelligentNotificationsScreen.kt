package io.github.mangi.eta.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.theme.CouiTheme
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.LocalNotificationModel
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import io.github.mangi.eta.agent.device.notifications.NotificationGameSettings
import io.github.mangi.eta.agent.device.notifications.GameNotificationKind
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
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
    val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
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
    var learningEnabled by remember { mutableStateOf(NotificationAdaptiveSettings.enabled(context)) }
    var minLearning by remember { mutableStateOf(NotificationAdaptiveSettings.minExamples(context).toFloat()) }
    var editCategory by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showAdvancedRules by remember { mutableStateOf(false) }
    var showGameRules by remember { mutableStateOf(false) }
    var showWorkRules by rememberSaveable { mutableStateOf(false) }
    var showAgendaRules by rememberSaveable { mutableStateOf(false) }
    var showSystemRules by rememberSaveable { mutableStateOf(false) }
    var gameDeletes by remember { mutableStateOf(NotificationGameSettings.enabledKinds(context)) }
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
                NotificationFilterMode.PERSONALIZED ->
                    "Filtrage automatique : configurez les catégories et l'action"
                else -> "Filtrage automatique"
            }
        }
    }

    var busy by remember { mutableStateOf(false) }

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
        Group(title = "Filtrage") {
            CouiSwitchPreference(
                title = "Filtrage automatique",
                summary = "Classe les notifications sur cet appareil",
                checked = config.mode != NotificationFilterMode.OFF,
                onCheckedChange = { enabled ->
                    selectMode(if (enabled) NotificationFilterMode.PERSONALIZED else NotificationFilterMode.OFF)
                },
            )
        }
        if (!installed || !listenerGranted) Group(title = "Préparation locale") {
            if (!installed) CouiPreference(
                title = "Installer le modèle",
                summary = "Modèle local · environ 302 Mo",
                enabled = !busy && !installed,
                onClick = {
                    action {
                        withContext(Dispatchers.IO) {
                            LocalNotificationModel.install(context) { message -> status = message }
                        }
                    }
                },
            )
            if (!listenerGranted) CouiPreference(
                title = "Accès aux notifications Android",
                summary = if (listenerGranted) "Autorisé" else "À autoriser pour recevoir les notifications",
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
        }
        Footer(text = "Toutes les analyses et préférences restent sur cet appareil.")
        Group(title = "Apprentissage et catégories") {
            CouiSwitchPreference(
                title = "Apprentissage adaptatif",
                summary = "Utilise uniquement vos actions réelles ; aucune notification n'est envoyée sur Internet",
                checked = learningEnabled,
                onCheckedChange = { learningEnabled = it; NotificationAdaptiveSettings.setEnabled(context, it) },
            )
            CouiPreference(
                title = if (editCategory == "@list") "Masquer les catégories" else "Renommer les catégories",
                summary = "Noms affichés dans le briefing",
                onClick = { editCategory = if (editCategory == "@list") null else "@list" },
            )
            if (editCategory == "@list") {
            NotificationTopics.titles.forEach { (id, title) ->
                CouiPreference(
                    title = NotificationAdaptiveSettings.title(context, id),
                    summary = if (editingName == id) "Modification" else "Toucher pour renommer",
                    onClick = { editingName = if (editingName == id) null else id
                        categoryName = NotificationAdaptiveSettings.title(context, id) },
                )
                if (editingName == id) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        CouiTextField(value = categoryName, onValueChange = { categoryName = it.take(50) },
                            label = "Nom affiché", singleLine = true)
                        CouiButton(text = "Enregistrer", modifier = Modifier.align(androidx.compose.ui.Alignment.End), onClick = {
                            val name = categoryName.trim()
                            if (name.length in 3..50) {
                                NotificationAdaptiveSettings.setTitle(context, id, name)
                                editingName = null
                                status = "Catégorie renommée"
                            } else status = "Nom invalide"
                        })
                    }
                }
            }
            }
            if (confirmReset) {
                CouiPreference(title = "Confirmer la réinitialisation", summary = "Efface les exemples et préférences locales",
                    onClick = { action {
                        withContext(Dispatchers.IO) { NotificationHistoryRepository(context).resetLearning() }
                        NotificationAdaptiveSettings.reset(context)
                        minLearning = 3f
                        confirmReset = false
                        NotificationActivityFeed.notifyChanged()
                        status = "Apprentissage réinitialisé"
                    } })
                CouiPreference(title = "Annuler", onClick = { confirmReset = false })
            } else {
                CouiPreference(title = "Réinitialiser l'apprentissage", summary = "Conserve les notifications, efface les préférences apprises",
                    onClick = { confirmReset = true })
            }
        }
        Group(title = "Messages et réseaux sociaux") {
            CouiPreference(
                title = "Messages personnels",
                summary = "Immédiat · conservés sur Android",
                showChevron = false,
            )
            CouiPreference(
                title = "Mentions et identifications",
                summary = "Ce soir · visibles dans le briefing",
                showChevron = false,
            )
            CouiPreference(
                title = "Invitations et demandes d'amis",
                summary = "Ce soir · visibles dans le briefing",
                showChevron = false,
            )
            CouiPreference(
                title = "Activité sociale secondaire",
                summary = "Indésirable · repliée dans le briefing",
                showChevron = false,
            )
            CouiPreference(
                title = "Suggestions et recommandations",
                summary = "Indésirable · repliées dans le briefing",
                showChevron = false,
            )
            CouiPreference(
                title = "Publications des abonnements",
                summary = "Indésirable · repliées dans le briefing",
                showChevron = false,
            )
        }
        Group(title = "Travail, GitHub et IA") {
            CouiPreference(
                title = if (showWorkRules) "Masquer les priorités" else "Voir les priorités",
                summary = "Interventions urgentes · bilans ce soir · bots ignorés",
                onClick = { showWorkRules = !showWorkRules },
            )
            if (showWorkRules) {
                val rules = listOf(
                    "PR à revoir ou valider" to "Immédiat · conservée sur Android",
                    "Échec CI à corriger" to "Immédiat · conservé sur Android",
                    "Message direct d'un collègue" to "Immédiat · conservé sur Android",
                    "Tâche ChatGPT ou Codex terminée" to "Ce soir · dans le briefing",
                    "Build ou déploiement réussi" to "Ce soir · dans le briefing",
                    "Commentaire automatique d'un bot" to "Indésirable · replié dans le briefing",
                    "Activité répétitive des bots" to "Indésirable · repliée dans le briefing",
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rules.forEach { (name, priority) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CouiText(text = name, kind = CouiTextKind.ItemTitle, color = colors.title)
                            CouiText(text = priority, kind = CouiTextKind.ItemSummary, color = colors.summary)
                        }
                    }
                }
            }
        }
        Group(title = "Agenda et rappels") {
            CouiPreference(
                title = if (showAgendaRules) "Masquer les priorités" else "Voir les priorités",
                summary = "Aujourd'hui et changements urgents · futur ce soir · suggestions ignorées",
                onClick = { showAgendaRules = !showAgendaRules },
            )
            if (showAgendaRules) {
                val rules = listOf(
                    "Rendez-vous aujourd'hui" to "Immédiat · conservé sur Android",
                    "Changement ou annulation" to "Immédiat · conservé sur Android",
                    "Invitation nécessitant une réponse" to "Immédiat · conservée sur Android",
                    "Rendez-vous futurs confirmés" to "Ce soir · conservés dans le briefing",
                    "Rappels sans échéance proche" to "Ce soir, si l'échéance future est explicite",
                    "Événements suggérés" to "Indésirable · repliés dans le briefing",
                    "Rappels explicitement expirés" to "Indésirable · repliés dans le briefing",
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rules.forEach { (name, priority) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CouiText(text = name, kind = CouiTextKind.ItemTitle,
                                color = colors.title)
                            CouiText(text = priority, kind = CouiTextKind.ItemSummary,
                                color = colors.summary)
                        }
                    }
                }
            }
        }
        Group(title = "Système") {
            CouiPreference(
                title = if (showSystemRules) "Masquer les priorités" else "Voir les priorités",
                summary = "Alertes critiques conservées · informations ce soir · bruit ignoré",
                onClick = { showSystemRules = !showSystemRules },
            )
            if (showSystemRules) {
                val rules = listOf(
                    "Batterie critique, stockage saturé, panne" to "Immédiat · conservé sur Android",
                    "Sauvegarde échouée" to "Immédiat · conservée sur Android",
                    "Mise à jour système disponible" to "Ce soir · dans le briefing",
                    "Sauvegarde ou synchronisation réussie" to "Ce soir · dans le briefing",
                    "Mise à jour d'application terminée" to "Indésirable · repliée dans le briefing",
                    "Conseils et suggestions Android" to "Indésirable · repliés dans le briefing",
                    "Notifications techniques de routine" to "Indésirable · repliées dans le briefing",
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    rules.forEach { (name, priority) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            CouiText(text = name, kind = CouiTextKind.ItemTitle,
                                color = colors.title)
                            CouiText(text = priority, kind = CouiTextKind.ItemSummary,
                                color = colors.summary)
                        }
                    }
                }
                Footer(text = "Services actifs, alertes de sécurité et notifications non effaçables restent protégés.")
            }
        }
        Group(title = "Jeux") {
            CouiPreference(
                title = if (showGameRules) "Masquer les règles des jeux" else "Filtrage des jeux",
                summary = "4 types supprimés · invitations conservées",
                onClick = { showGameRules = !showGameRules },
            )
            if (showGameRules) {
                listOf(
                    GameNotificationKind.REWARD to "Récompenses et quêtes",
                    GameNotificationKind.STREAK to "Séries et rappels",
                    GameNotificationKind.EVENT to "Événements et nouveautés",
                    GameNotificationKind.PUZZLE to "Défis et puzzles",
                ).forEach { (kind, title) ->
                    CouiSwitchPreference(
                        title = title,
                        summary = if (kind in gameDeletes) "Supprimer automatiquement" else "Conserver",
                        checked = kind in gameDeletes,
                        onCheckedChange = { delete ->
                            NotificationGameSettings.setDelete(context, kind, delete)
                            gameDeletes = NotificationGameSettings.enabledKinds(context)
                        },
                    )
                }
                Footer(text = "Invitations d'amis : toujours conservées. Les notifications ambiguës restent visibles.")
            }
        }
        Group(title = "Historique local") {
            CouiPreference(
                title = "Archives du briefing",
                summary = "Copies retirées du briefing · consultables et restaurables pendant 7 jours",
                onClick = { showArchives = true },
            )
        }
        var advancedVisible by remember { mutableStateOf(false) }
        CouiPreference(
            title = if (advancedVisible) "Masquer les règles avancées" else "Personnaliser le filtrage",
            summary = "Seuils, catégories et action automatique",
            onClick = { advancedVisible = !advancedVisible },
        )
        if (advancedVisible) {
            CouiSliderPreference(
                title = "Exemples minimum par préférence",
                value = minLearning,
                valueText = "${minLearning.roundToInt()} retours",
                valueRange = 3f..20f,
                onValueChange = { minLearning = it },
                onValueChangeFinished = {
                    val minimum = minLearning.roundToInt().coerceIn(3, 20)
                    NotificationAdaptiveSettings.setMinExamples(context, minimum)
                    minLearning = minimum.toFloat()
                },
            )
        if (config.mode != NotificationFilterMode.OFF) {
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
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
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
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
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
            Footer(text = "Les alertes sensibles et importantes restent protégées.")
        }
        }
        if (status.isNotBlank()) Footer(text = status)
        CouiPageBottomSpacer()
    }
}
