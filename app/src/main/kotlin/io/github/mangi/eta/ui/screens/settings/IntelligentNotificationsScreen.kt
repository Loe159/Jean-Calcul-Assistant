package io.github.mangi.eta.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.GameNotificationKind
import io.github.mangi.eta.agent.device.notifications.LocalNotificationModel
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterAction
import io.github.mangi.eta.agent.device.notifications.NotificationFilterConfig
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationGameSettings
import io.github.mangi.eta.agent.device.notifications.NotificationLabelRule
import io.github.mangi.eta.agent.device.notifications.NotificationLabelRules
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.mangi.eta.ui.components.coui.PreferenceSection
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.ExpandableGroupContent
import io.github.suqi8.coui.kmp.basic.ExpandableGroupHeader
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.MarkPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private val WORK_RULES = listOf(
    "PR à revoir ou valider" to "Immédiat · conservée sur Android",
    "Échec CI à corriger" to "Immédiat · conservé sur Android",
    "Message direct d'un collègue" to "Immédiat · conservé sur Android",
    "Tâche ChatGPT ou Codex terminée" to "Ce soir · dans le briefing",
    "Build ou déploiement réussi" to "Ce soir · dans le briefing",
    "Commentaire automatique d'un bot" to "Indésirable · replié dans le briefing",
    "Activité répétitive des bots" to "Indésirable · repliée dans le briefing",
)

private val AGENDA_RULES = listOf(
    "Rendez-vous aujourd'hui" to "Immédiat · conservé sur Android",
    "Changement ou annulation" to "Immédiat · conservé sur Android",
    "Invitation nécessitant une réponse" to "Immédiat · conservée sur Android",
    "Rendez-vous futurs confirmés" to "Ce soir · conservés dans le briefing",
    "Rappels sans échéance proche" to "Ce soir, si l'échéance future est explicite",
    "Événements suggérés" to "Indésirable · repliés dans le briefing",
    "Rappels explicitement expirés" to "Indésirable · repliés dans le briefing",
)

private val SYSTEM_RULES = listOf(
    "Batterie critique, stockage saturé, panne" to "Immédiat · conservé sur Android",
    "Sauvegarde échouée" to "Immédiat · conservée sur Android",
    "Mise à jour système disponible" to "Ce soir · dans le briefing",
    "Sauvegarde ou synchronisation réussie" to "Ce soir · dans le briefing",
    "Mise à jour d'application terminée" to "Indésirable · repliée dans le briefing",
    "Conseils et suggestions Android" to "Indésirable · repliés dans le briefing",
    "Notifications techniques de routine" to "Indésirable · repliées dans le briefing",
)

private val SHOPPING_RULES = listOf(
    "Messages d'acheteurs et vendeurs" to "Immédiat · conservés sur Android",
    "Problèmes de paiement et litiges" to "Immédiat · conservés sur Android",
    "Livraisons nécessitant une action" to "Immédiat · conservées sur Android",
    "Annonces correspondant à une recherche" to "Ce soir · dans le briefing",
    "Commandes et expéditions" to "Ce soir · dans le briefing",
    "Publicités et offres sponsorisées" to "Indésirable · repliées dans le briefing",
    "Recommandations hors recherche" to "Indésirable · repliées dans le briefing",
    "Relances commerciales" to "Indésirable · repliées dans le briefing",
)

private val NEWS_RULES = listOf(
    "Actualité majeure ou urgente" to "Immédiat · conservée sur Android",
    "Veille IA, tech, cybersécurité" to "Ce soir · dans le briefing",
    "Actualités économiques et politiques" to "Ce soir · dans le briefing",
    "Articles de médias suivis" to "Ce soir · dans le briefing",
    "Informations locales utiles" to "Ce soir · dans le briefing",
    "Articles recommandés sans demande" to "Indésirable · repliés dans le briefing",
    "Titres répétés et sensationnalistes" to "Indésirable · repliés dans le briefing",
)

/**
 * A category whose priority table folds under a native COUI expandable header, as ColorOS lists
 * read-only details. The rows are informative, so they are not clickable.
 */
@Composable
private fun PriorityGroup(
    title: String,
    summary: String,
    rules: List<Pair<String, String>>,
    footer: String? = null,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    PreferenceSection(title = title) {
        ExpandableGroupHeader(
            title = "Priorités",
            expanded = expanded,
            onExpandedChange = { expanded = it },
            summary = summary,
        )
        ExpandableGroupContent(expanded = expanded) {
            rules.forEach { (name, priority) -> BasicComponent(title = name, summary = priority) }
        }
    }
    if (expanded && footer != null) PreferenceFooter(text = footer)
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
    var learningEnabled by remember { mutableStateOf(NotificationAdaptiveSettings.enabled(context)) }
    var minLearning by remember { mutableStateOf(NotificationAdaptiveSettings.minExamples(context).toFloat()) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showAdvancedRules by rememberSaveable { mutableStateOf(false) }
    var showGameRules by rememberSaveable { mutableStateOf(false) }
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
        PreferenceSection(title = "Filtrage") {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = "Filtrage automatique",
                        summary = "Classe les notifications sur cet appareil",
                        checked = config.mode != NotificationFilterMode.OFF,
                        onCheckedChange = { enabled ->
                            selectMode(if (enabled) NotificationFilterMode.PERSONALIZED else NotificationFilterMode.OFF)
                        },
                    )
                }
            }
        }
        if (!installed || !listenerGranted) PreferenceSection(title = "Préparation locale") {
            CardListGroup {
                if (!installed) item {
                    ArrowPreference(
                        title = "Installer le modèle",
                        summary = "Modèle local · environ 302 Mo",
                        enabled = !busy,
                        onClick = {
                            action {
                                withContext(Dispatchers.IO) {
                                    LocalNotificationModel.install(context) { message -> status = message }
                                }
                            }
                        },
                    )
                }
                if (!listenerGranted) item {
                    ArrowPreference(
                        title = "Accès aux notifications Android",
                        summary = "À autoriser pour recevoir les notifications",
                        onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    )
                }
            }
        }
        PreferenceFooter(text = "Toutes les analyses et préférences restent sur cet appareil.")
        PreferenceSection(title = "Apprentissage et catégories") {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = "Apprentissage adaptatif",
                        summary = "Utilise uniquement vos actions réelles ; aucune notification n'est envoyée sur Internet",
                        checked = learningEnabled,
                        onCheckedChange = { learningEnabled = it; NotificationAdaptiveSettings.setEnabled(context, it) },
                    )
                }
                item {
                    ExpandableGroupHeader(
                        title = "Renommer les catégories",
                        expanded = showCategories,
                        onExpandedChange = { showCategories = it; if (!it) editingName = null },
                        summary = "Noms affichés dans le briefing",
                    )
                }
                if (showCategories) NotificationTopics.titles.forEach { (id, _) ->
                    item(key = "category_$id") {
                        ArrowPreference(
                            title = NotificationAdaptiveSettings.title(context, id),
                            summary = if (editingName == id) "Modification" else "Toucher pour renommer",
                            onClick = {
                                editingName = if (editingName == id) null else id
                                categoryName = NotificationAdaptiveSettings.title(context, id)
                            },
                        )
                    }
                    if (editingName == id) item(key = "category_edit_$id", showDivider = false) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            CouiTextField(value = categoryName, onValueChange = { categoryName = it.take(50) },
                                label = "Nom affiché", singleLine = true)
                            CouiButton(text = "Enregistrer", modifier = Modifier.align(Alignment.End), onClick = {
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
                if (confirmReset) {
                    item {
                        ArrowPreference(
                            title = "Confirmer la réinitialisation",
                            summary = "Efface les exemples et préférences locales",
                            onClick = { action {
                                withContext(Dispatchers.IO) { NotificationHistoryRepository(context).resetLearning() }
                                NotificationAdaptiveSettings.reset(context)
                                minLearning = 3f
                                confirmReset = false
                                NotificationActivityFeed.notifyChanged()
                                status = "Apprentissage réinitialisé"
                            } },
                        )
                    }
                    item { ArrowPreference(title = "Annuler", onClick = { confirmReset = false }) }
                } else item {
                    ArrowPreference(
                        title = "Réinitialiser l'apprentissage",
                        summary = "Conserve les notifications, efface les préférences apprises",
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
        PreferenceSection(title = "Messages et réseaux sociaux") {
            CardListGroup {
                listOf(
                    "Messages personnels" to "Immédiat · conservés sur Android",
                    "Mentions et identifications" to "Ce soir · visibles dans le briefing",
                    "Invitations et demandes d'amis" to "Ce soir · visibles dans le briefing",
                    "Activité sociale secondaire" to "Indésirable · repliée dans le briefing",
                    "Suggestions et recommandations" to "Indésirable · repliées dans le briefing",
                    "Publications des abonnements" to "Indésirable · repliées dans le briefing",
                ).forEach { (title, summary) -> item { BasicComponent(title = title, summary = summary) } }
            }
        }
        PriorityGroup(
            title = "Travail, GitHub et IA",
            summary = "Interventions urgentes · bilans ce soir · bots ignorés",
            rules = WORK_RULES,
        )
        PriorityGroup(
            title = "Agenda et rappels",
            summary = "Aujourd'hui et changements urgents · futur ce soir · suggestions ignorées",
            rules = AGENDA_RULES,
        )
        PriorityGroup(
            title = "Système",
            summary = "Alertes critiques conservées · informations ce soir · bruit ignoré",
            rules = SYSTEM_RULES,
            footer = "Services actifs, alertes de sécurité et notifications non effaçables restent protégés.",
        )
        PriorityGroup(
            title = "Achats et Leboncoin",
            summary = "Échanges et problèmes urgents · suivis ce soir · publicités ignorées",
            rules = SHOPPING_RULES,
            footer = "Les annonces hors critères ne sont écartées que si la notification indique une recommandation, pas sur une estimation de tes critères.",
        )
        PriorityGroup(
            title = "Actualités et veille",
            summary = "Événements majeurs immédiats · veille ce soir · recommandations ignorées",
            rules = NEWS_RULES,
            footer = "Les doublons sont détectés localement, par source et titre. Les alertes ambiguës sont conservées.",
        )
        PreferenceSection(title = "Jeux") {
            CardListGroup {
                item {
                    ExpandableGroupHeader(
                        title = "Filtrage des jeux",
                        expanded = showGameRules,
                        onExpandedChange = { showGameRules = it },
                        summary = "4 types supprimés · invitations conservées",
                    )
                }
                if (showGameRules) listOf(
                    GameNotificationKind.REWARD to "Récompenses et quêtes",
                    GameNotificationKind.STREAK to "Séries et rappels",
                    GameNotificationKind.EVENT to "Événements et nouveautés",
                    GameNotificationKind.PUZZLE to "Défis et puzzles",
                ).forEach { (kind, title) ->
                    item(key = kind) {
                        SwitchPreference(
                            title = title,
                            summary = if (kind in gameDeletes) "Supprimer automatiquement" else "Conserver",
                            checked = kind in gameDeletes,
                            onCheckedChange = { delete ->
                                NotificationGameSettings.setDelete(context, kind, delete)
                                gameDeletes = NotificationGameSettings.enabledKinds(context)
                            },
                        )
                    }
                }
            }
        }
        if (showGameRules) {
            PreferenceFooter(text = "Invitations d'amis : toujours conservées. Les notifications ambiguës restent visibles.")
        }
        PreferenceSection(title = "Historique local") {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = "Archives du briefing",
                        summary = "Copies retirées du briefing · consultables et restaurables pendant 7 jours",
                        onClick = { showArchives = true },
                    )
                }
            }
        }
        PreferenceSection(title = "Avancé") {
            CardListGroup {
                item {
                    ExpandableGroupHeader(
                        title = "Personnaliser le filtrage",
                        expanded = showAdvanced,
                        onExpandedChange = { showAdvanced = it },
                        summary = "Seuils, catégories et action automatique",
                    )
                }
                if (showAdvanced) item {
                    SliderPreference(
                        title = "Exemples minimum par préférence",
                        value = minLearning,
                        valueText = "${minLearning.roundToInt()} retours",
                        valueRange = 3f..20f,
                        steps = 16,
                        onValueChange = { minLearning = it },
                        onValueChangeFinished = {
                            val minimum = minLearning.roundToInt().coerceIn(3, 20)
                            NotificationAdaptiveSettings.setMinExamples(context, minimum)
                            minLearning = minimum.toFloat()
                        },
                    )
                }
            }
        }
        if (showAdvanced && config.mode != NotificationFilterMode.OFF) {
            PreferenceSection(title = "Catégories et seuils") {
                CardListGroup {
                    item {
                        ExpandableGroupHeader(
                            title = "Catégories et seuils",
                            expanded = showAdvancedRules,
                            onExpandedChange = { showAdvancedRules = it },
                            summary = "${config.labelRules.count { it.enabled }} catégories actives sur ${config.labelRules.size} · réglages avancés",
                        )
                    }
                    if (showAdvancedRules) {
                        config.labelRules.forEach { rule ->
                            item(key = "rule_${rule.label}") {
                                SwitchPreference(
                                    title = labelTitle(rule.label),
                                    summary = rule.label + " · score ≥ ${rule.minimumScore} % · écart ≥ ${rule.minimumMargin} points",
                                    checked = rule.enabled,
                                    onCheckedChange = { changeRule(rule.label, rule.copy(enabled = it)) },
                                )
                            }
                            item(key = "rule_toggle_${rule.label}") {
                                ExpandableGroupHeader(
                                    title = "Régler : ${labelTitle(rule.label)}",
                                    expanded = expandedRule == rule.label,
                                    onExpandedChange = { open ->
                                        expandedRule = if (open) rule.label else null
                                        editedLabel = rule.label
                                    },
                                    summary = "Seuils de filtrage indépendants pour cette catégorie",
                                )
                            }
                            if (expandedRule == rule.label) {
                                item(key = "rule_score_${rule.label}") {
                                    val score = scoreDrafts[rule.label] ?: rule.minimumScore.toFloat()
                                    SliderPreference(
                                        title = "Score minimum",
                                        value = score,
                                        valueText = "${score.roundToInt()} %",
                                        valueRange = 0f..100f,
                                        onValueChange = { value -> scoreDrafts = scoreDrafts + (rule.label to value) },
                                        onValueChangeFinished = {
                                            val chosen = (scoreDrafts[rule.label] ?: rule.minimumScore.toFloat()).roundToInt()
                                            changeRule(rule.label, rule.copy(minimumScore = chosen))
                                            scoreDrafts = scoreDrafts - rule.label
                                        },
                                    )
                                }
                                item(key = "rule_margin_${rule.label}") {
                                    val margin = marginDrafts[rule.label] ?: rule.minimumMargin.toFloat()
                                    SliderPreference(
                                        title = "Avance sur le 2e label",
                                        value = margin,
                                        valueText = "${margin.roundToInt()} points",
                                        valueRange = 0f..100f,
                                        onValueChange = { value -> marginDrafts = marginDrafts + (rule.label to value) },
                                        onValueChangeFinished = {
                                            val chosen = (marginDrafts[rule.label] ?: rule.minimumMargin.toFloat()).roundToInt()
                                            changeRule(rule.label, rule.copy(minimumMargin = chosen))
                                            marginDrafts = marginDrafts - rule.label
                                        },
                                    )
                                }
                                item(key = "rule_label_${rule.label}", showDivider = false) {
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        }
                        if (config.labelRules.size < NotificationLabelRules.MAX_LABELS) {
                            item(key = "rule_new", showDivider = false) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        }
                        item(key = "rule_restore") {
                            ArrowPreference(
                                title = "Restaurer les 7 labels d'origine",
                                summary = "Restaure les seuils 40 % / 8 points pour la publicité et 65 % / 20 points pour le spam.",
                                onClick = {
                                    saveConfig(config.copy(labelRules = NotificationLabelRules.defaults()))
                                    expandedRule = null
                                    status = "Labels et seuils d'origine restaurés."
                                },
                            )
                        }
                    }
                }
            }
            PreferenceSection(title = "Plage horaire") {
                CardListGroup {
                    item {
                        SwitchPreference(
                            title = "Seulement pendant une plage horaire",
                            checked = config.onlyQuietHours,
                            summary = "Heure locale · ${config.quietFrom} h à ${config.quietUntil} h",
                            onCheckedChange = { saveConfig(config.copy(onlyQuietHours = it)) },
                        )
                    }
                    if (config.onlyQuietHours) listOf(22 to 7, 0 to 7, 9 to 18).forEach { (from, until) ->
                        item(key = "quiet_$from") {
                            MarkPreference(
                                title = "$from h à $until h",
                                checked = config.quietFrom == from && config.quietUntil == until,
                                onClick = { saveConfig(config.copy(quietFrom = from, quietUntil = until)) },
                            )
                        }
                    }
                }
            }
            PreferenceSection(title = "Action automatique") {
                CardListGroup {
                    item {
                        MarkPreference(
                            title = "Reporter 1 heure",
                            summary = "Les notifications peuvent réapparaître ensuite",
                            checked = config.action == NotificationFilterAction.SNOOZE_1H,
                            onClick = { saveConfig(config.copy(action = NotificationFilterAction.SNOOZE_1H)) },
                        )
                    }
                    item {
                        MarkPreference(
                            title = "Reporter 24 heures",
                            summary = "Les notifications peuvent réapparaître ensuite",
                            checked = config.action == NotificationFilterAction.SNOOZE_24H,
                            onClick = { saveConfig(config.copy(action = NotificationFilterAction.SNOOZE_24H)) },
                        )
                    }
                    item {
                        MarkPreference(
                            title = "Supprimer définitivement",
                            summary = "Suppression Android irréversible · toutes les applications tierces éligibles",
                            checked = config.action == NotificationFilterAction.DISMISS,
                            onClick = {
                                saveConfig(config.copy(action = NotificationFilterAction.DISMISS))
                                status = "Suppression Android activée pour les applications tierces éligibles."
                            },
                        )
                    }
                }
            }
            PreferenceFooter(text = "Les alertes sensibles et importantes restent protégées.")
        }
        if (status.isNotBlank()) PreferenceFooter(text = status)
    }
}
