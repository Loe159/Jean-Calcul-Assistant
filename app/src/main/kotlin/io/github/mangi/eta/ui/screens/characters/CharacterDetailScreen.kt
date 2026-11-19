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
import io.github.mangi.eta.agent.roleplay.CharacterCardFormat
import io.github.mangi.eta.agent.roleplay.CharacterMacros
import io.github.mangi.eta.agent.roleplay.RoleplayBinding
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.components.ItemDescriptionDialog
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

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
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = "Use my persona",
                        summary = store.persona.name.ifBlank { "No name configured" },
                        checked = store.usePersona,
                        onCheckedChange = { store.usePersona = it },
                    )
                }
                item {
                    ArrowPreference(
                        title = "Edit my persona",
                        onClick = { onNavigate(AppRoute.CharacterPersona) },
                    )
                }
            }
        }
        PreferenceCategory(
            title = "Greeting",
        )
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
        PreferenceCategory(title = "Manage")
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = "Edit character",
                        onClick = {
                            store.discardEditor()
                            onNavigate(AppRoute.CharacterEditor(id))
                        },
                        enabled = !store.busy,
                    )
                }
                item {
                    ArrowPreference(
                        title = "Story memory",
                        summary = "Story and relationship memory shared across this character's conversations",
                        onClick = { onNavigate(AppRoute.CharacterMemory(id)) },
                    )
                }
                item {
                    ArrowPreference(
                        title = "Duplicate character",
                        onClick = { store.duplicate(id) { onNavigate(AppRoute.CharacterDetail(it)) } },
                        enabled = !store.busy,
                    )
                }
                item {
                    ArrowPreference(
                        title = "Export PNG character card",
                        onClick = { pngExporter.launch(characterExportName(profile.card.name, "png")) },
                        enabled = !store.busy,
                    )
                }
                item {
                    ArrowPreference(
                        title = "Export JSON character card",
                        onClick = { jsonExporter.launch(characterExportName(profile.card.name, "json")) },
                        enabled = !store.busy,
                    )
                }
            }
        }
        if (store.compatibilityWarnings.isNotEmpty()) {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = "Compatibility notes",
                            summary = "${store.compatibilityWarnings.size} items handled according to supported compatibility",
                            onClick = { showCompatibility = !showCompatibility },
                        )
                    }
                    if (showCompatibility) {
                        store.compatibilityWarnings.forEach { warning ->
                            item {
                                Text(
                                    text = warning,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = COUITheme.textStyles.bodyM,
                                    color = COUITheme.colorScheme.labelSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    BasicComponent(
                        title = "Delete character",
                        titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                        onClick = { showDeleteConfirm = true },
                        enabled = !store.busy,
                    )
                }
            }
        }
        CouiPageBottomSpacer()
    }

    if (showDeleteConfirm && profile != null) {
        val message = "「${profile.card.name} will be removed along with its images and story memory. Existing conversations remain. This cannot be undone."
        val onConfirmDelete: () -> Unit = {
            showDeleteConfirm = false
            store.delete(id) { onBack() }
        }
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
