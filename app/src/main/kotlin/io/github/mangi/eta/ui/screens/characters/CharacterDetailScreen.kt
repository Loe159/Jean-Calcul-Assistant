package io.github.mangi.eta.ui.screens.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.dialog.CouiDialogDefaults
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Divider
import com.itos.coui.layout.Group
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import io.github.mangi.eta.agent.roleplay.CharacterCardFormat
import io.github.mangi.eta.agent.roleplay.CharacterMacros
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.ItemDescriptionDialog
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSectionTitle
import io.github.mangi.eta.ui.components.coui.CouiSwitchPreference
import io.github.mangi.eta.ui.navigation.AppRoute
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal fun greetingTitle(index: Int) = if (index == 0) "Default greeting" else "Greeting ${index + 1}"

private data class CharacterTextPreview(val title: String, val text: String)

@Composable
internal fun CharacterDetailScreen(
    id: String,
    store: CharacterLibraryStore,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
    onStart: (RoleplayBinding, String) -> Unit,
) {
    var showCompatibility by rememberSaveable(id) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable(id) { mutableStateOf(false) }
    var preview by remember(id) { mutableStateOf<CharacterTextPreview?>(null) }
    val pngExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        if (it != null) store.export(id, CharacterCardFormat.PNG, it)
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it != null) store.export(id, CharacterCardFormat.JSON, it)
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) store.changeAvatar(id, uri)
    }
    val profile = store.selected?.takeIf { it.id == id }
    val userName = if (store.usePersona) store.persona.name.ifBlank { "User" } else "User"

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            val pageColors = CouiPageDefaults.themeColors()
            val dialogColors = CouiDialogDefaults.themeColors()
            CouiScaffoldPage(title = "", onBack = onBack, compactTitle = true) {
                if (profile == null) {
                    CharacterPageMessage(if (store.busy) "Loading character…" else "Could not load character. Go back and try again.")
                    return@CouiScaffoldPage
                }
                CharacterHero(
                    profile = profile,
                    busy = store.busy,
                    onChangeAvatar = { avatarPicker.launch("image/*") },
                    onToggleFavorite = { store.toggleFavorite(id) },
                    onStart = { store.startConversation(id, onStart) },
                    onReadDescription = {
                        preview = CharacterTextPreview(
                            "Character settings",
                            CharacterMacros.expand(profile.card.description, profile.card, userName),
                        )
                    },
                    userName = userName,
                )
                Group {
                    CouiSwitchPreference(
                        title = "Use my persona",
                        summary = store.persona.name.ifBlank { "No name configured" },
                        checked = store.usePersona,
                        onCheckedChange = { store.usePersona = it },
                    )
                    Divider()
                    CouiPreference(
                        title = "Edit my persona",
                        onClick = { onNavigate(AppRoute.CharacterPersona) },
                    )
                }
                CouiSectionTitle(text = "Greeting")
                val greetings = remember(profile.card, userName) {
                    (listOf(profile.card.firstMessage) + profile.card.alternateGreetings)
                        .map { CharacterMacros.expand(it, profile.card, userName) }
                }
                GreetingPager(
                    greetings = greetings,
                    selected = store.greetingIndex,
                    onSelect = { store.greetingIndex = it },
                    onRead = { index -> preview = CharacterTextPreview(greetingTitle(index), greetings[index]) },
                )
                Group(title = "Manage") {
                    CouiPreference(
                        title = "Edit character",
                        enabled = !store.busy,
                        onClick = {
                            store.discardEditor()
                            onNavigate(AppRoute.CharacterEditor(id))
                        },
                    )
                    Divider()
                    CouiPreference(
                        title = "Story memory",
                        summary = "Story and relationship memory shared across this character's conversations",
                        onClick = { onNavigate(AppRoute.CharacterMemory(id)) },
                    )
                    Divider()
                    CouiPreference(
                        title = "Duplicate character",
                        enabled = !store.busy,
                        onClick = { store.duplicate(id) { onNavigate(AppRoute.CharacterDetail(it)) } },
                    )
                    Divider()
                    CouiPreference(
                        title = "Export PNG character card",
                        enabled = !store.busy,
                        onClick = { pngExporter.launch(characterExportName(profile.card.name, "png")) },
                    )
                    Divider()
                    CouiPreference(
                        title = "Export JSON character card",
                        enabled = !store.busy,
                        onClick = { jsonExporter.launch(characterExportName(profile.card.name, "json")) },
                    )
                }
                if (store.compatibilityWarnings.isNotEmpty()) {
                    Group {
                        CouiPreference(
                            title = "Compatibility notes",
                            summary = "${store.compatibilityWarnings.size} items handled according to supported compatibility",
                            onClick = { showCompatibility = !showCompatibility },
                        )
                        if (showCompatibility) {
                            Divider()
                            store.compatibilityWarnings.forEach { warning ->
                                Text(
                                    text = warning,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
                Group {
                    CouiPreference(
                        title = "Delete character",
                        titleColor = dialogColors.destructive,
                        enabled = !store.busy,
                        showChevron = false,
                        onClick = { showDeleteConfirm = true },
                    )
                }
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(title = "", onBack = onBack, compactTitle = true) {
                if (profile == null) {
                    item { CharacterPageMessage(if (store.busy) "Loading character…" else "Could not load character. Go back and try again.") }
                    return@MiuixScaffoldPage
                }
                item(key = "hero") {
                    CharacterHero(
                        profile = profile,
                        busy = store.busy,
                        onChangeAvatar = { avatarPicker.launch("image/*") },
                        onToggleFavorite = { store.toggleFavorite(id) },
                        onStart = { store.startConversation(id, onStart) },
                        onReadDescription = {
                            preview = CharacterTextPreview(
                                "Character settings",
                                CharacterMacros.expand(profile.card.description, profile.card, userName),
                            )
                        },
                        userName = userName,
                    )
                }
                item(key = "persona") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        SwitchPreference(
                            title = "Use my persona",
                            summary = store.persona.name.ifBlank { "No name configured" },
                            checked = store.usePersona,
                            onCheckedChange = { store.usePersona = it },
                        )
                        ArrowPreference(title = "Edit my persona", onClick = { onNavigate(AppRoute.CharacterPersona) })
                    }
                }
                item(key = "greetings") {
                    SmallTitle("Greeting")
                    val greetings = remember(profile.card, userName) {
                        (listOf(profile.card.firstMessage) + profile.card.alternateGreetings)
                            .map { CharacterMacros.expand(it, profile.card, userName) }
                    }
                    GreetingPager(
                        greetings = greetings,
                        selected = store.greetingIndex,
                        onSelect = { store.greetingIndex = it },
                        onRead = { index -> preview = CharacterTextPreview(greetingTitle(index), greetings[index]) },
                    )
                }
                item(key = "management") {
                    SmallTitle("Manage")
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        ArrowPreference(title = "Edit character", enabled = !store.busy, onClick = {
                            store.discardEditor()
                            onNavigate(AppRoute.CharacterEditor(id))
                        })
                        ArrowPreference(title = "Story memory", summary = "Story and relationship memory shared across this character's conversations", onClick = {
                            onNavigate(AppRoute.CharacterMemory(id))
                        })
                        ArrowPreference(title = "Duplicate character", enabled = !store.busy, onClick = {
                            store.duplicate(id) { onNavigate(AppRoute.CharacterDetail(it)) }
                        })
                        ArrowPreference(
                            title = "Export PNG character card",
                            enabled = !store.busy,
                            onClick = { pngExporter.launch(characterExportName(profile.card.name, "png")) },
                        )
                        ArrowPreference(
                            title = "Export JSON character card",
                            enabled = !store.busy,
                            onClick = { jsonExporter.launch(characterExportName(profile.card.name, "json")) },
                        )
                    }
                }
                if (store.compatibilityWarnings.isNotEmpty()) {
                    item(key = "compatibility") {
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            ArrowPreference(
                                title = "Compatibility notes",
                                summary = "${store.compatibilityWarnings.size} items handled according to supported compatibility",
                                onClick = { showCompatibility = !showCompatibility },
                            )
                            if (showCompatibility) {
                                store.compatibilityWarnings.forEach { warning ->
                                    Text(
                                        text = warning,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "danger") {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        ArrowPreference(
                            title = "Delete character",
                            titleColor = BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.error),
                            enabled = !store.busy,
                            onClick = { showDeleteConfirm = true },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && profile != null) {
        val message = "「${profile.card.name} will be removed along with its images and story memory. Existing conversations remain. This cannot be undone."
        val onConfirmDelete: () -> Unit = {
            showDeleteConfirm = false
            store.delete(id) { onBack() }
        }
        when (LocalUiStyle.current) {
            AppearanceUiStyle.COUI -> {
                CouiConfirmDialog(
                    visible = true,
                    title = "Delete character",
                    message = message,
                    confirmText = "Delete",
                    confirmEnabled = !store.busy,
                    destructive = true,
                    onConfirm = onConfirmDelete,
                    onDismissRequest = { showDeleteConfirm = false },
                )
            }
            AppearanceUiStyle.MIUIX -> {
                WindowDialog(
                    show = true,
                    title = "Delete character",
                    summary = message,
                    onDismissRequest = { showDeleteConfirm = false },
                ) {
                    MiuixDialogActions(
                        confirmText = "Delete",
                        destructive = true,
                        confirmEnabled = !store.busy,
                        onCancel = { showDeleteConfirm = false },
                        onConfirm = onConfirmDelete,
                    )
                }
            }
        }
    }

    preview?.let { current ->
        ItemDescriptionDialog(
            title = current.title,
            description = current.text,
            onDismiss = { preview = null },
        )
    }
}

private fun characterExportName(name: String, extension: String): String {
    val basename = name.map { if (it.isISOControl() || it in "\\/:*?\"<>|") '_' else it }
        .joinToString("").trim().trimEnd('.').take(64).ifBlank { "Characters" }
    return "$basename.$extension"
}
