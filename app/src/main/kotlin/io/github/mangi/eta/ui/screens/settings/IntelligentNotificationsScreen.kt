package io.github.mangi.eta.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import io.github.mangi.eta.agent.device.notifications.remote.HomelabPreferences
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationClient
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.mangi.eta.ui.components.coui.PreferenceSection
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.ExpandableGroupContent
import io.github.suqi8.coui.kmp.basic.ExpandableGroupHeader
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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


/**
 * Settings of the notification pipeline. Business decisions belong to the private homelab
 * (Mastra); this page manages the connection, the consents and the Android-side guards.
 */
@Composable
internal fun IntelligentNotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val scope = rememberCoroutineScope()
    var listenerGranted by remember { mutableStateOf(AgentNotificationHistoryService.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) listenerGranted = AgentNotificationHistoryService.isEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var status by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(NotificationFilterSettings.load(context)) }
    var showArchives by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(MastraConnection.enabled(context)) }
    var homelabHost by remember { mutableStateOf(MastraConnection.displayHost(context)) }
    var pairingDialog by remember { mutableStateOf(false) }
    var pairingText by remember { mutableStateOf("") }
    var confirmForget by remember { mutableStateOf(false) }
    var learningConsent by remember { mutableStateOf(MastraConnection.learningConsent(context)) }
    var lastSync by remember { mutableStateOf(MastraConnection.lastStatus(context)) }
    var preferences by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }

    fun action(task: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { task() } catch (error: Exception) {
                status = error.message?.take(170) ?: "Erreur"
            } finally {
                busy = false
                lastSync = MastraConnection.lastStatus(context)
            }
        }
    }

    fun loadPreferences() = action {
        val pairing = MastraConnection.load(context) ?: return@action
        preferences = withContext(Dispatchers.IO) { HomelabPreferences.list(pairing) }
    }

    LaunchedEffect(showPreferences, paired) { if (showPreferences && paired) loadPreferences() }

    BackHandler(showArchives) { showArchives = false }
    if (showArchives) {
        ArchivedBriefNotificationsScreen(onBack = { showArchives = false })
        return
    }

    CouiScaffoldPage(title = if (largeText) "Notifications" else "Notifications intelligentes",
        onBack = onBack, compactTitle = true) {
        PreferenceSection(title = "Homelab") {
            CardListGroup {
                if (!paired) item {
                    ArrowPreference(
                        title = "Associer le homelab",
                        summary = "Coller la chaîne affichée par « npm run mobile:device -- add »",
                        onClick = { pairingText = ""; pairingDialog = true },
                    )
                } else {
                    item {
                        BasicComponent(
                            title = homelabHost ?: "Homelab associé",
                            summary = lastSync ?: "Aucune synchronisation pour l'instant",
                        )
                    }
                    item {
                        ArrowPreference(
                            title = "Tester la connexion",
                            summary = "Vérifie l'authentification et le modèle local",
                            enabled = !busy,
                            onClick = { action {
                                val pairing = MastraConnection.load(context) ?: error("Homelab non configuré")
                                val health = withContext(Dispatchers.IO) { MastraNotificationClient(pairing).health() }
                                status = "Connecté · ${health.policyVersion}" + when (health.model.available) {
                                    true -> " · modèle local disponible"
                                    false -> " · modèle local indisponible : règles seules, inconnus conservés"
                                    null -> " · règles seules"
                                }
                            } },
                        )
                    }
                    item {
                        ArrowPreference(
                            title = "Synchroniser maintenant",
                            summary = "Envoie les notifications et retours en attente",
                            enabled = !busy,
                            onClick = { action {
                                val outcome = withContext(Dispatchers.IO) { MastraNotificationSync(context).run() }
                                NotificationActivityFeed.notifyChanged()
                                status = if (outcome == MastraNotificationSync.Outcome.DONE) "Synchronisation terminée"
                                    else "Synchronisation incomplète · nouvel essai automatique"
                            } },
                        )
                    }
                    item {
                        SwitchPreference(
                            title = "Apprentissage sur le homelab",
                            summary = "Conserver vos retours et exemples anonymisés pour améliorer les décisions",
                            checked = learningConsent,
                            onCheckedChange = { learningConsent = it; MastraConnection.setLearningConsent(context, it) },
                        )
                    }
                    item {
                        ArrowPreference(
                            title = "Dissocier ce téléphone",
                            summary = "Efface le secret de l'appareil ; l'historique local est conservé",
                            onClick = { confirmForget = true },
                        )
                    }
                }
            }
        }
        PreferenceFooter(text = "Les notifications sont analysées par votre homelab privé, sans service cloud. " +
            "Les codes numériques sont masqués avant l'envoi ; les icônes restent sur le téléphone.")
        PreferenceSection(title = "Android") {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = "Retrait automatique",
                        summary = "Retirer du panneau Android ce que le homelab juge différable ou sans importance",
                        checked = config.mode == NotificationFilterMode.PERSONALIZED,
                        onCheckedChange = { enabled ->
                            if (enabled && !listenerGranted) status = "Autorisez d'abord l'accès aux notifications Android."
                            else {
                                val next = config.copy(mode = if (enabled) NotificationFilterMode.PERSONALIZED else NotificationFilterMode.OFF)
                                NotificationFilterSettings.save(context, next)
                                config = next
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
        PreferenceFooter(text = "Toujours conservés : appels, alarmes, vraies conversations, codes, sécurité, paiements, " +
            "notifications non effaçables et applications système. Une décision tardive ou périmée n'efface rien.")
        if (status.isNotBlank()) PreferenceFooter(text = status)
        if (paired) PreferenceSection(title = "Préférences du homelab") {
            CardListGroup {
                item {
                    ExpandableGroupHeader(
                        title = "Préférences enregistrées",
                        expanded = showPreferences,
                        onExpandedChange = { showPreferences = it },
                        summary = "Issues de vos corrections · modifiables sans mise à jour de l'app",
                    )
                }
                if (showPreferences) {
                    val list = preferences
                    if (list == null) item { BasicComponent(title = "Chargement…") }
                    else if (list.isEmpty()) item { BasicComponent(title = "Aucune préférence", summary = "Corrigez une priorité depuis Mon briefing") }
                    else list.forEach { (id, label) ->
                        item(key = "pref_$id") {
                            ArrowPreference(title = label, summary = "Toucher pour supprimer", enabled = !busy, onClick = { action {
                                val pairing = MastraConnection.load(context) ?: return@action
                                withContext(Dispatchers.IO) { HomelabPreferences.delete(pairing, id) }
                                preferences = withContext(Dispatchers.IO) { HomelabPreferences.list(pairing) }
                                status = "Préférence supprimée"
                            } })
                        }
                    }
                }
            }
        }
        PreferenceSection(title = "Catégories et apprentissage") {
            CardListGroup {
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
                item {
                    ArrowPreference(
                        title = "Réinitialiser l'apprentissage",
                        summary = "Efface les retours locaux et, si associé, l'apprentissage du homelab",
                        onClick = { confirmReset = true },
                    )
                }
            }
        }
        PreferenceSection(title = "Règles initiales") {
            CardListGroup {
                listOf(
                    "Messages personnels" to "Immédiat · conservés sur Android",
                    "Mentions et identifications" to "Aujourd'hui · visibles dans le briefing",
                    "Invitations et demandes d'amis" to "Aujourd'hui · visibles dans le briefing",
                    "Invitations personnelles dans les jeux" to "Immédiat · conservées sur Android",
                    "Récompenses, quêtes et défis de jeux" to "Sans importance · repliés dans le briefing",
                    "Activité sociale secondaire" to "Sans importance · repliée dans le briefing",
                    "Suggestions et recommandations" to "Sans importance · repliées dans le briefing",
                    "Publications des abonnements" to "Sans importance · repliées dans le briefing",
                ).forEach { (title, summary) -> item { BasicComponent(title = title, summary = summary) } }
            }
        }
        PriorityGroup(title = "Travail, GitHub et IA", summary = "Interventions urgentes · bilans ce soir · bots ignorés", rules = WORK_RULES)
        PriorityGroup(title = "Agenda et rappels", summary = "Aujourd'hui et changements urgents · futur ce soir", rules = AGENDA_RULES)
        PriorityGroup(title = "Système", summary = "Alertes critiques conservées · bruit ignoré", rules = SYSTEM_RULES)
        PriorityGroup(title = "Achats", summary = "Échanges et problèmes urgents · publicités ignorées", rules = SHOPPING_RULES)
        PriorityGroup(title = "Actualités et veille", summary = "Événements majeurs immédiats · recommandations ignorées", rules = NEWS_RULES)
        PreferenceFooter(text = "Préférences initiales appliquées par le homelab. Vos corrections les remplacent.")
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
    }

    CouiInputDialog(
        visible = pairingDialog,
        title = "Associer le homelab",
        message = "La chaîne contient un secret : elle est chiffrée par Android Keystore puis effacée de cet écran.",
        value = pairingText,
        onValueChange = { pairingText = it.trim().take(600) },
        confirmText = "Associer",
        confirmEnabled = MastraConnection.parsePairing(pairingText) != null,
        onConfirm = {
            val pairing = MastraConnection.parsePairing(pairingText)
            pairingText = ""
            pairingDialog = false
            if (pairing == null) status = "Chaîne invalide (HTTPS requis)."
            else {
                MastraConnection.save(context, pairing)
                paired = true
                homelabHost = MastraConnection.displayHost(context)
                status = "Homelab associé. Les nouvelles notifications seront analysées."
                MastraNotificationSync.request(context, delayMs = 0)
            }
        },
        onDismissRequest = { pairingText = ""; pairingDialog = false },
    )
    CouiConfirmDialog(
        visible = confirmForget, title = "Dissocier ce téléphone ?",
        message = "Les notifications ne seront plus analysées. Révoquez aussi l'appareil sur le homelab.",
        confirmText = "Dissocier", destructive = true,
        onConfirm = {
            MastraConnection.forget(context)
            paired = false
            confirmForget = false
            status = "Homelab dissocié"
        },
        onDismissRequest = { confirmForget = false },
    )
    CouiConfirmDialog(
        visible = confirmReset, title = "Réinitialiser l'apprentissage ?",
        message = "Les notifications restent dans l'historique. Les préférences explicites du homelab sont conservées.",
        confirmText = "Réinitialiser", destructive = true,
        onConfirm = { confirmReset = false; action {
            withContext(Dispatchers.IO) {
                NotificationHistoryRepository(context).resetLearning()
                MastraConnection.load(context)?.let { MastraNotificationClient(it).resetLearning(includeUserPreferences = false) }
            }
            NotificationActivityFeed.notifyChanged()
            status = "Apprentissage réinitialisé"
        } },
        onDismissRequest = { confirmReset = false },
    )
}
