package fr.loevan.jeancalcul.v2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalView
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogAction
import com.itos.coui.dialog.Dialog
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.*
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            V2App()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun V2App(model: ConversationViewModel = viewModel()) {
    JeanTheme {
        // OAuth callback is received by the app while the browser is foreground.
        // Defer its network exchange until this activity is resumed.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, model) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> model.onAppForegroundChanged(true)
                    Lifecycle.Event.ON_PAUSE -> model.onAppForegroundChanged(false)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                model.onAppForegroundChanged(true)
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                model.onAppForegroundChanged(false)
            }
        }
        val focus = LocalFocusManager.current
        val inputView = LocalView.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val haptic = LocalHapticFeedback.current
        val draft = rememberTextFieldState()
        var page by rememberSaveable { mutableStateOf("chat") }
        var menu by remember { mutableStateOf(false) }
        var confirmReset by remember { mutableStateOf(false) }
        val session by model.session.collectAsState()
        val config by model.config.collectAsState()
        val settingsError by model.settingsError.collectAsState()
        val chatGpt by model.chatGptUi.collectAsState()
        val messages = session.messages
        val running = session.status == RunStatus.RUNNING
        fun parent(destination: String) = when (destination) {
            "gallery", "providers", "provider_form" -> "settings"
            "models" -> "providers"
            else -> "chat"
        }
        fun navigate(destination: String) {
            focus.clearFocus(force = true)
            keyboardController?.hide()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            page = destination
        }
        fun reset() { model.reset(); draft.clearText(); page = "chat"; confirmReset = false }
        fun send() {
            if (draft.text.isNotBlank()) {
                model.send(draft.text.toString().trim()); draft.clearText()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        // Dispose the chat input session before the final hide request. A pending
        // IME show from text input can otherwise win over hide during navigation.
        LaunchedEffect(page) {
            if (page != "chat") {
                withFrameNanos { }
                focus.clearFocus(force = true)
                // The editor was removed from the composition. Refresh the platform
                // connection too, so the IME cannot keep consuming Back for that editor.
                inputView.context.getSystemService(InputMethodManager::class.java).restartInput(inputView)
                keyboardController?.hide()
            }
        }
        BackHandler(enabled = page != "chat") { navigate(parent(page)) }
        ConversationLayout(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).testTag("header"),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (page == "chat") {
                        ActionIcon("Options", Icons.Default.Menu, Modifier.testTag("settings")) { navigate("settings") }
                        Column(Modifier.weight(1f).clickable(role = Role.Button) { navigate("providers") }
                            .testTag("model_header"), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (chatGpt.usingPlan) "ChatGPT" else "API compatible", kind = CouiTextKind.Description,
                                color = CouiTheme.colorScheme.onSurfaceVariant)
                            Text(text = if (chatGpt.usingPlan) chatGpt.models.find { it.id == chatGpt.model }?.label
                                ?: chatGpt.model.ifBlank { "Choisir un modèle" } else config.model.ifBlank { "Choisir un modèle" },
                                kind = CouiTextKind.Toolbar, textAlign = TextAlign.Center, maxLines = 1)
                        }
                        Box {
                            ActionIcon("Plus d’options", Icons.Default.MoreHoriz, Modifier.testTag("options")) { menu = true }
                            Menu(expanded = menu, onDismissRequest = { menu = false },
                                items = listOf(CouiMenuItem("new", "Nouvelle conversation"), CouiMenuItem("gallery", "Galerie COUI")),
                                onItemClick = {
                                    menu = false
                                    if (it.value == "new") {
                                        if (messages.isEmpty() && draft.text.isBlank()) reset() else confirmReset = true
                                    } else navigate("gallery")
                                })
                        }
                    } else {
                        ActionIcon("Retour", Icons.AutoMirrored.Filled.ArrowBack, onClick = { navigate(parent(page)) })
                        Text(text = when (page) {
                            "settings" -> "Options"; "providers" -> "Fournisseurs"; "models" -> "Modèles"
                            "provider_form" -> "Fournisseur API"; else -> "Galerie COUI"
                        }, kind = CouiTextKind.PageTitle, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    if (page == "chat") Composer(draft, if (chatGpt.usingPlan) {
                        if (chatGpt.authorized) "Abonnement ChatGPT · api.openai.com" else "ChatGPT · connexion requise"
                    } else config.destination, running, model::stop, ::send)
                }
            },
        ) { padding ->
            Crossfade(targetState = page, animationSpec = tween(180), label = "destination",
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { destination ->
                when (destination) {
                    "settings" -> Preferences(config, chatGpt, model, { navigate("providers") },
                        { navigate("provider_form") }, { navigate("gallery") })
                    "providers" -> ProviderSelection(config, chatGpt, running, model, { navigate("models") }, { navigate("provider_form") })
                    "models" -> ModelSelection(config, chatGpt, running, model, { navigate("provider_form") })
                    "provider_form" -> ProviderForm(config, running, settingsError, model::configure)
                    "gallery" -> DesignGallery()
                    else -> Conversation(session, model::retry)
                }
            }
        }
        Dialog(visible = chatGpt.welcome && chatGpt.usingPlan, onDismissRequest = model::acknowledgeChatGpt,
            title = "Vous utilisez votre abonnement ChatGPT",
            message = "Les requêtes éligibles utilisent votre abonnement ou vos crédits ChatGPT. Gérez l’utilisation et les limites dans les réglages ChatGPT.",
            confirmAction = CouiDialogAction("D’accord", model::acknowledgeChatGpt))
        Dialog(visible = confirmReset, onDismissRequest = { confirmReset = false },
            title = "Effacer cette conversation ?", message = "Les messages enregistrés sur cet appareil et le brouillon seront supprimés.",
            confirmAction = CouiDialogAction("Effacer", { reset() }),
            dismissAction = CouiDialogAction("Annuler", { confirmReset = false }))
    }
}

@Composable
private fun ConversationLayout(
    modifier: Modifier,
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier.background(CouiTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
        topBar()
        Box(Modifier.weight(1f).fillMaxWidth()) { content(PaddingValues()) }
        bottomBar()
    }
}

@Composable
private fun Composer(draft: TextFieldState, destination: String, running: Boolean, onStop: () -> Unit, onSend: () -> Unit) {
    val colors = CouiTheme.colorScheme
    Column(Modifier.padding(horizontal = JeanTokens.edge, vertical = 8.dp).then(ComposerViewport)
        .shadow(JeanTokens.elevation, JeanTokens.capsule).clip(JeanTokens.capsule)
        .background(colors.surface).padding(16.dp)) {
        Box(Modifier.fillMaxWidth()) {
          BasicTextField(state = draft,
            textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent), lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("composer")
                .semantics { contentDescription = "Message à Jean-Calcul" })
          if (draft.text.isEmpty()) Text(text = "Demander à Jean-Calcul…", kind = CouiTextKind.Body, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = destination, kind = CouiTextKind.Description,
                color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (running) ActionIcon("Arrêter", Icons.Default.Close, Modifier.testTag("stop"), accent = true, onClick = onStop)
            else ActionIcon("Envoyer", Icons.AutoMirrored.Filled.Send, Modifier.testTag("send"),
                enabled = draft.text.isNotBlank(), accent = true, onClick = onSend)
        }
    }
}

@Composable
private fun Conversation(session: Session, onRetry: () -> Unit) {
    val messages = session.messages
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag(if (messages.isEmpty()) "empty" else "messages"),
        contentPadding = PaddingValues(24.dp), verticalArrangement = if (messages.isEmpty()) Arrangement.Center else Arrangement.spacedBy(16.dp)) {
        if (messages.isEmpty()) item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Comment puis-je vous aider ?", kind = CouiTextKind.PageTitle)
                Spacer(Modifier.height(16.dp))
                Text(text = "Connectez votre compte ChatGPT dans les préférences.\nLes conversations restent sur cet appareil.", kind = CouiTextKind.Body,
                    color = CouiTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            items(messages) { if (it.role != "tool" && it.text.isNotEmpty()) MessageBubble(it.text, outgoing = it.role == "user") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = session.error?.label ?: when (session.status) {
                        RunStatus.RUNNING -> "Réponse en cours…"
                        RunStatus.COMPLETED -> "Réponse terminée"
                        RunStatus.STOPPED -> "Réponse arrêtée"
                        RunStatus.INTERRUPTED -> "Réponse interrompue à la fermeture"
                        else -> "Prêt"
                    }, kind = CouiTextKind.Description, modifier = Modifier.testTag("run_status"))
                    if (session.status in setOf(RunStatus.FAILED, RunStatus.STOPPED, RunStatus.INTERRUPTED)) {
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onRetry).heightIn(min = 48.dp)
                            .padding(12.dp).testTag("retry"), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Réessayer", kind = CouiTextKind.Body)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MessageBubble(text: String, outgoing: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start) {
        Text(text = text, kind = CouiTextKind.Body, modifier = Modifier.fillMaxWidth(.88f)
            .clip(if (outgoing) JeanTokens.outgoing else JeanTokens.incoming)
            .background(if (outgoing) JeanTokens.userBubble() else CouiTheme.colorScheme.card).padding(16.dp))
    }
}

@Composable
private fun EtaRow(title: String, summary: String? = null, icon: ImageVector, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp)
        .then(if (onClick == null) Modifier else Modifier.couiButtonFeedback(interactionSource = source, enabled = enabled)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick))
        .padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(icon, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(CouiTheme.colorScheme.accent))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(text = title, kind = CouiTextKind.PreferenceTitle)
            if (summary != null) Text(text = summary, kind = CouiTextKind.PreferenceSummary,
                color = CouiTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Text(text = "✓", kind = CouiTextKind.Body, color = CouiTheme.colorScheme.accent)
        else if (onClick != null) Image(Icons.Default.ChevronRight, null, Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurfaceVariant))
    }
}

@Composable
private fun Preferences(config: ProviderConfig, account: ChatGptUi, model: ConversationViewModel,
    onProviders: () -> Unit, onConfigure: () -> Unit, onGallery: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Group(title = "Fournisseur IA") {
            EtaRow("Fournisseur de modèle", if (account.usingPlan) {
                "ChatGPT / " + (account.models.find { it.id == account.model }?.label ?: account.model.ifBlank { "Aucun modèle" })
            } else "API compatible / " + config.model.ifBlank { "Aucun modèle" }, Icons.Default.Cloud,
                Modifier.testTag("choose_provider"), onClick = onProviders)
        }
        Group(title = "Compte et configuration") {
            EtaRow("Fournisseur API", config.destination, Icons.Default.Tune,
                Modifier.testTag("configure_provider"), onClick = onConfigure)
            Divider()
            EtaRow("Utilisation ChatGPT", "Compte, connexion et limites", Icons.Default.Person,
                onClick = { model.manageChatGptUsage() })
        }
        Group(title = "Apparence") {
            EtaRow("Thème du système", "Clair ou sombre selon Android", Icons.Default.Palette)
            Divider()
            EtaRow("Galerie COUI", "Couleurs, typographie et composants", Icons.Default.GridView,
                Modifier.testTag("gallery"), onClick = onGallery)
        }
        Text(text = "Historique conservé sur cet appareil · outil autorisé : lecture de l’heure UTC.",
            kind = CouiTextKind.Description, color = CouiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    }
}

@Composable
private fun ProviderSelection(config: ProviderConfig, account: ChatGptUi, running: Boolean,
    model: ConversationViewModel, onModels: () -> Unit, onConfigure: () -> Unit) {
    val message by model.chatGptMessage.collectAsState()
    val signingIn by model.signingIn.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Group(title = "Fournisseur de modèle") {
            EtaRow("ChatGPT", if (account.authorized) "Abonnement · api.openai.com" else "Connectez votre compte ci-dessous",
                Icons.Default.AutoAwesome, selected = account.usingPlan,
                enabled = !running && account.active.isNotBlank(), onClick = model::useChatGpt)
            Divider()
            EtaRow("API compatible", config.destination, Icons.Default.Cloud,
                selected = !account.usingPlan, enabled = !running, onClick = model::useApiProvider)
        }
        Group(title = "Modèle") {
            EtaRow("Choisir un modèle", if (account.usingPlan) account.models.find { it.id == account.model }?.label
                ?: account.model.ifBlank { "Connectez ChatGPT d’abord" } else config.model.ifBlank { "Configurer un modèle API" },
                Icons.Default.SmartToy, onClick = onModels)
        }
        Group(title = "Configuration") {
            EtaRow("Configurer l’API", "Adresse, modèle et clé", Icons.Default.Tune,
                Modifier.testTag("provider_config_link"), onClick = onConfigure)
            Divider()
            EtaRow("Continue with ChatGPT", if (signingIn) "Autorisation en cours" else "Ouvrir la connexion sécurisée",
                Icons.Default.Person, Modifier.testTag("chatgpt_sign_in"), enabled = !running && !signingIn,
                onClick = { model.signInChatGpt(account.active.takeIf { it.isNotBlank() }) })
            if (signingIn) {
                Divider()
                EtaRow("Annuler la connexion", icon = Icons.Default.Close, onClick = model::cancelChatGptSignIn)
            }
        }
        if (account.accounts.isNotEmpty()) Group(title = "Comptes ChatGPT") {
            account.accounts.forEachIndexed { index, saved ->
                if (index > 0) Divider()
                EtaRow(saved.label, if (saved.authorized) "Autorisé" else "À reconnecter", Icons.Default.Person,
                    selected = account.active == saved.id, enabled = !running && !signingIn,
                    onClick = { model.selectChatGptAccount(saved.id) })
            }
            Divider()
            EtaRow("Ajouter un compte", icon = Icons.Default.Add, enabled = !running && !signingIn,
                onClick = { model.signInChatGpt(null) })
            Divider()
            EtaRow("Déconnecter ce compte", icon = Icons.AutoMirrored.Filled.Logout, enabled = !running,
                onClick = model::signOutChatGpt)
        }
        if (account.authorized) Group(title = "Compte") {
            EtaRow("Actualiser les modèles", icon = Icons.Default.Refresh, enabled = !running,
                onClick = model::refreshChatGptModels)
            Divider()
            EtaRow("Gérer l’utilisation", "Ouvrir les réglages ChatGPT", Icons.AutoMirrored.Filled.OpenInNew,
                onClick = model::manageChatGptUsage)
        }
        message?.let { Text(text = it, kind = CouiTextKind.Description,
            modifier = Modifier.padding(24.dp).testTag("chatgpt_status")) }
    }
}

@Composable
private fun ModelSelection(config: ProviderConfig, account: ChatGptUi, running: Boolean,
    model: ConversationViewModel, onConfigure: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        if (account.usingPlan) {
            Group(title = "Modèles ChatGPT") {
                if (account.models.isEmpty()) EtaRow("Aucun modèle disponible",
                    "Connectez votre compte ou actualisez le catalogue dans Fournisseurs", Icons.Default.Info)
                account.models.forEachIndexed { index, entry ->
                    if (index > 0) Divider()
                    EtaRow(entry.label, entry.id, Icons.Default.SmartToy,
                        selected = entry.id == account.model, enabled = !running,
                        onClick = { model.selectChatGptModel(entry.id) })
                }
            }
        } else Group(title = "Modèle API") {
            EtaRow(config.model.ifBlank { "Aucun modèle configuré" },
                "L’identifiant du modèle est défini par votre fournisseur API", Icons.Default.SmartToy,
                onClick = onConfigure)
        }
    }
}

@Composable
private fun ProviderForm(config: ProviderConfig, running: Boolean, error: String?,
    onSave: (String, String, Boolean, String) -> Unit) {
    val endpoint = rememberTextFieldState(config.endpoint)
    val model = rememberTextFieldState(config.model)
    var key by remember { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf(config.local) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
      Group(title = "Connexion API", footer = "Les requêtes transmettent l’historique au serveur indiqué. HTTPS est requis.") {
       Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = config.destination, kind = CouiTextKind.Body,
            color = CouiTheme.colorScheme.onSurfaceVariant)
        ProviderField("URL de base (https://…/v1)", endpoint, "provider_endpoint")
        ProviderField("Identifiant du modèle", model, "provider_model")
        Text(text = "Clé API · vide pour supprimer la clé", kind = CouiTextKind.Description)
        BasicTextField(value = key, onValueChange = { key = it }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = CouiTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, CouiTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp).testTag("provider_key").semantics { contentDescription = "Clé API" })
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = local, enabled = !running,
            role = Role.Checkbox, onValueChange = { local = it })
            .padding(12.dp).semantics { contentDescription = "Destination locale déclarée" }) {
            Text(text = if (local) "✓ Serveur local déclaré" else "Serveur distant · toucher pour changer", kind = CouiTextKind.Body)
        }
        Text(text = "HTTPS requis. Vérifiez l’hôte : le libellé local ne garantit pas son emplacement. Une requête envoie l’historique à cet hôte.", kind = CouiTextKind.Description)
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(JeanTokens.capsule)
            .background(JeanTokens.userBubble()).clickable(enabled = !running, role = Role.Button) {
                onSave(endpoint.text.toString(), model.text.toString(), local, key); key = ""
            }.padding(12.dp).testTag("save_provider")) {
            Text(text = if (running) "Arrêtez la réponse pour modifier" else "Enregistrer le fournisseur", kind = CouiTextKind.Body)
        }
        error?.let { Text(text = it, kind = CouiTextKind.Description) }
       }
      }
    }
}

@Composable
private fun ProviderField(label: String, state: TextFieldState, tag: String) {
    Column {
        Text(text = label, kind = CouiTextKind.Description)
        BasicTextField(state = state, lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = rememberCouiTextStyle(CouiTextKind.Body).copy(color = CouiTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(CouiTheme.colorScheme.accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, CouiTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp).testTag(tag).semantics { contentDescription = label })
    }
}

@Composable
private fun DesignGallery() {
    var dialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("design_gallery")) {
        Group(title = "Palette et typographie") {
            Item(title = { Text(text = "Jean-Calcul", kind = CouiTextKind.PageTitle) },
                summary = { Text(text = "Texte principal · texte secondaire", kind = CouiTextKind.Body, color = CouiTheme.colorScheme.onSurfaceVariant) })
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(CouiTheme.colorScheme.accent, CouiTheme.colorScheme.surface, JeanTokens.userBubble()).forEach {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(it))
                }
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MessageBubble("Message utilisateur", true)
            MessageBubble("Exemple visuel de bulle entrante — aucune réponse IA.", false)
        }
        Group(title = "Interactions") {
            Item(onClick = { dialog = true }, modifier = Modifier.testTag("gallery_dialog"),
                title = { Text(text = "Ouvrir un dialogue", kind = CouiTextKind.PreferenceTitle) })
        }
        Spacer(Modifier.height(24.dp))
    }
    Dialog(visible = dialog, onDismissRequest = { dialog = false }, title = "Dialogue COUI",
        message = "Exemple de composant natif.", confirmAction = CouiDialogAction("Fermer", { dialog = false }))
}

/** Foundation interaction when alpha7 has no general-purpose public button. */
@Composable
private fun ActionIcon(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val colors = CouiTheme.colorScheme
    Box(modifier.size(48.dp).clip(JeanTokens.capsule)
        .background(if (accent && enabled) colors.accent else Color.Transparent)
        .couiButtonFeedback(interactionSource = source, enabled = enabled)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Image(icon, contentDescription = null, modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(if (!enabled) colors.onSurfaceVariant else if (accent) colors.onAccent else colors.onSurface))
    }
}
