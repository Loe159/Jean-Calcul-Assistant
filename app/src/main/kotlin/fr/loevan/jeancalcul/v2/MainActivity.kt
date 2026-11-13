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
import com.itos.coui.navigation.CouiNavigationItem
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
        BackHandler(enabled = page != "chat") { navigate(if (page == "gallery") "settings" else "chat") }
        ConversationLayout(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).testTag("header"),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (page != "chat") ActionIcon("Retour", Icons.AutoMirrored.Filled.ArrowBack, onClick = {
                        navigate(if (page == "gallery") "settings" else "chat")
                    })
                    Text(text = when (page) { "settings" -> "Préférences"; "gallery" -> "Galerie COUI"; else -> "Jean-Calcul" },
                        kind = CouiTextKind.Toolbar, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    if (page == "chat") {
                        ActionIcon("Nouveau", Icons.Default.Add, Modifier.testTag("reset")) {
                            focus.clearFocus()
                            if (messages.isEmpty() && draft.text.isBlank()) reset() else confirmReset = true
                        }
                        ActionIcon("Réglages", Icons.Default.Settings, Modifier.testTag("settings")) { navigate("settings") }
                    }
                    Box {
                        ActionIcon("Plus d’options", Icons.Default.MoreVert, Modifier.testTag("options")) { menu = true }
                        Menu(expanded = menu, onDismissRequest = { menu = false },
                            items = listOf(CouiMenuItem("gallery", "Galerie COUI"), CouiMenuItem("settings", "Préférences")),
                            onItemClick = { menu = false; navigate(it.value) })
                    }
                }
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
                    if (page == "chat") Composer(draft, if (chatGpt.usingPlan) {
                        if (chatGpt.authorized) "Abonnement ChatGPT · api.openai.com" else "ChatGPT · connexion requise"
                    } else config.destination, running, model::stop, ::send,
                        if (chatGpt.usingPlan && chatGpt.authorized) model::manageChatGptUsage else null)
                    // The keyboard gets the available height; destination controls return on dismissal.
                    val keyboard = WindowInsets.isImeVisible
                    if (!keyboard) DestinationBar(page, messages.isNotEmpty(), { navigate(it) }) {
                        if (messages.isEmpty() && draft.text.isBlank()) reset() else confirmReset = true
                    }
                }
            },
        ) { padding ->
            Crossfade(targetState = page, animationSpec = tween(180), label = "destination",
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { destination ->
                when (destination) {
                    "settings" -> Preferences(config, running, settingsError, model::configure, model) { navigate("gallery") }
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
private fun Composer(draft: TextFieldState, destination: String, running: Boolean, onStop: () -> Unit, onSend: () -> Unit, onUsage: (() -> Unit)? = null) {
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
        if (onUsage != null) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onUsage).padding(vertical = 8.dp)) {
            Text(text = "Gérer l’utilisation ChatGPT", kind = CouiTextKind.Description)
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
private fun DestinationBar(page: String, hasMessages: Boolean, onSelection: (String) -> Unit, onReset: () -> Unit) {
    val items = listOf(CouiNavigationItem("Chat", Icons.Default.Home, Icons.Default.Home),
        CouiNavigationItem("Réglages", Icons.Default.Settings, Icons.Default.Settings))
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("navigation"),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f, fill = false).widthIn(max = if (largeText) 600.dp else 300.dp)
            .fillMaxWidth().clip(JeanTokens.capsule).background(CouiTheme.colorScheme.card).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { index, item ->
                val selected = (page == "chat") == (index == 0)
                val source = remember { MutableInteractionSource() }
                Column(Modifier.weight(1f).clip(JeanTokens.capsule)
                    .background(if (selected) JeanTokens.userBubble() else Color.Transparent)
                    .couiButtonFeedback(interactionSource = source)
                    .clickable(source, indication = null, role = Role.Tab) { onSelection(if (index == 0) "chat" else "settings") }
                    .semantics { this.selected = selected }
                    .heightIn(min = 48.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(item.icon, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(CouiTheme.colorScheme.onSurface))
                    Text(text = item.title, kind = CouiTextKind.Body)
                }
            }
        }
        if (hasMessages && page == "chat") {
            Spacer(Modifier.width(8.dp))
            ActionIcon("Nouvelle conversation", Icons.Default.Add, accent = true, onClick = onReset)
        }
    }
}

@Composable
private fun Preferences(config: ProviderConfig, running: Boolean, settingsError: String?,
    onConfigure: (String, String, Boolean, String) -> Unit, model: ConversationViewModel, onGallery: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = editing) { focus.clearFocus(force = true); keyboard?.hide(); editing = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Group(title = "Abonnement ChatGPT", footer = "Utilisez votre compte ChatGPT. Les prochains envois transmettent l’historique au fournisseur sélectionné.") {
            ChatGptSettings(model, running)
        }
        Group(title = "Fournisseur API", footer = "Historique sur cet appareil. Outil autorisé : lecture de l’heure uniquement.") {
            Item(onClick = { editing = !editing }, modifier = Modifier.testTag("configure_provider"),
                title = { Text(text = "Configurer le fournisseur", kind = CouiTextKind.PreferenceTitle) },
                summary = { Text(text = config.destination, kind = CouiTextKind.PreferenceSummary) })
            if (editing) ProviderForm(config, running, settingsError, onConfigure)
            Item(onClick = model::useApiProvider, title = { Text(text = "Utiliser le fournisseur API", kind = CouiTextKind.PreferenceTitle) })
        }
        Group(title = "Apparence") {
            Item(title = { Text(text = "Thème du système", kind = CouiTextKind.PreferenceTitle) },
                summary = { Text(text = "Clair ou sombre selon Android", kind = CouiTextKind.PreferenceSummary, color = CouiTheme.colorScheme.onSurfaceVariant) })
            Divider()
            Item(modifier = Modifier.testTag("gallery"), onClick = onGallery,
                title = { Text(text = "Galerie COUI", kind = CouiTextKind.PreferenceTitle) },
                summary = { Text(text = "Couleurs, typographie et composants", kind = CouiTextKind.PreferenceSummary, color = CouiTheme.colorScheme.onSurfaceVariant) })
        }
    }
}

@Composable
private fun ChatGptSettings(model: ConversationViewModel, running: Boolean) {
    val account by model.chatGptUi.collectAsState()
    val message by model.chatGptMessage.collectAsState()
    val signingIn by model.signingIn.collectAsState()
    var modelMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = "Connectez votre compte pour utiliser votre abonnement ChatGPT.", kind = CouiTextKind.Body)
        val source = remember { MutableInteractionSource() }
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
            .background(Color.Black).couiButtonFeedback(interactionSource = source, enabled = !running && !signingIn)
            .clickable(source, indication = null, enabled = !running && !signingIn, role = Role.Button) { model.signInChatGpt(account.active.takeIf { it.isNotBlank() }) }
            .padding(16.dp).testTag("chatgpt_sign_in"), contentAlignment = Alignment.Center) {
            Text(text = "Continue with ChatGPT", kind = CouiTextKind.Body, color = Color.White)
        }
        if (account.accounts.isNotEmpty()) ChatGptAction("Ajouter un compte ChatGPT", { model.signInChatGpt(null) }, !running && !signingIn)
        if (signingIn) ChatGptAction("Annuler la connexion", model::cancelChatGptSignIn)
        account.accounts.forEach { saved ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(enabled = !running && !signingIn, role = Role.RadioButton) { model.selectChatGptAccount(saved.id) }
                .semantics { selected = account.active == saved.id }.padding(12.dp)) {
                Text(text = (if (account.active == saved.id) "✓ " else "") + saved.label +
                    if (saved.authorized) " · autorisé" else if (saved.signedIn) " · abonnement à autoriser" else " · déconnecté", kind = CouiTextKind.Body)
            }
        }
        if (account.active.isNotBlank()) {
            Text(text = if (account.authorized) "Compte autorisé · destination distante api.openai.com" else "Autorisation ChatGPT à renouveler", kind = CouiTextKind.Description)
            if (!account.usingPlan) ChatGptAction("Utiliser mon abonnement ChatGPT", model::useChatGpt, !running)
            ChatGptAction("Reconnecter ce compte", { model.signInChatGpt(account.active) }, !running && !signingIn)
            Box {
                ChatGptAction("Modèle : " + (account.models.find { it.id == account.model }?.label
                    ?: account.model.ifBlank { "Choisir après connexion" }), { modelMenu = true }, !running && account.models.isNotEmpty())
                Menu(expanded = modelMenu, onDismissRequest = { modelMenu = false },
                    items = account.models.map { CouiMenuItem(it.id, it.label) },
                    onItemClick = { modelMenu = false; model.selectChatGptModel(it.value) })
            }
            if (account.authorized) ChatGptAction("Actualiser les modèles", model::refreshChatGptModels, !running)
            ChatGptAction("Déconnecter ce compte", model::signOutChatGpt, !running)
        }
        message?.let { Text(text = it, kind = CouiTextKind.Description, modifier = Modifier.testTag("chatgpt_status")) }
        ChatGptAction("Gérer l’utilisation ChatGPT", model::manageChatGptUsage)
    }
}

@Composable
private fun ChatGptAction(label: String, action: () -> Unit, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
        .couiButtonFeedback(interactionSource = source, enabled = enabled)
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = action).padding(12.dp)) {
        Text(text = label, kind = CouiTextKind.Body, color = if (enabled) CouiTheme.colorScheme.onSurface else CouiTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProviderForm(config: ProviderConfig, running: Boolean, error: String?,
    onSave: (String, String, Boolean, String) -> Unit) {
    val endpoint = rememberTextFieldState(config.endpoint)
    val model = rememberTextFieldState(config.model)
    var key by remember { mutableStateOf("") }
    var local by rememberSaveable { mutableStateOf(config.local) }
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = config.destination, kind = CouiTextKind.Body)
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
