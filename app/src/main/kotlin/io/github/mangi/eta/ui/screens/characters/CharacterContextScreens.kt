package io.github.mangi.eta.ui.screens.characters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.CouiPageDefaults
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton

@Composable
internal fun CharacterPersonaScreen(store: CharacterLibraryStore, onBack: () -> Unit) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(title = "My persona", onBack = onBack) {
                CharacterPageMessage("Set your identity in the story. Choose it when starting a conversation; existing stories keep their original persona.")
                CharacterTextField("Name", store.personaDraft.name, { store.updatePersona(name = it) }, !store.busy, singleLine = true)
                CharacterTextField("Identity and relationship", store.personaDraft.description, { store.updatePersona(description = it) }, !store.busy, minLines = 6)
                CouiButton(
                    text = "Save persona",
                    onClick = { store.savePersona(onBack) },
                    enabled = !store.busy,
                    role = CouiButtonRole.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 12.dp),
                )
                CouiPageBottomSpacer()
            }
        }
        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(title = "My persona", onBack = onBack, modifier = Modifier.imePadding()) {
                item { CharacterPageMessage("Set your identity in the story. Choose it when starting a conversation; existing stories keep their original persona.") }
                item(key = "name") { CharacterTextField("Name", store.personaDraft.name, { store.updatePersona(name = it) }, !store.busy, singleLine = true) }
                item(key = "persona") { CharacterTextField("Identity and relationship", store.personaDraft.description, { store.updatePersona(description = it) }, !store.busy, minLines = 6) }
                item(key = "save") {
                    TextButton(
                        text = "Save persona",
                        onClick = { store.savePersona(onBack) },
                        enabled = !store.busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CharacterMemoryScreen(id: String, store: CharacterLibraryStore, onBack: () -> Unit) {
    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(title = "Story memory", onBack = onBack) {
                CharacterPageMessage("Record important events, relationships and agreements. Shared across this character's conversations and controlled by the memory switch.")
                CharacterTextField("Story and relationships", store.memoryDraft, store::updateMemory, !store.busy, minLines = 10)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CouiPageDefaults.horizontalMargin, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CouiButton(
                        text = "Reload",
                        onClick = { store.loadMemory(id, force = true) },
                        enabled = !store.busy,
                        modifier = Modifier.weight(1f),
                    )
                    CouiButton(
                        text = "Save memory",
                        onClick = { store.saveMemory(id) },
                        enabled = !store.busy,
                        role = CouiButtonRole.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                CouiPageBottomSpacer()
            }
        }
        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(title = "Story memory", onBack = onBack, modifier = Modifier.imePadding()) {
                item { CharacterPageMessage("Record important events, relationships and agreements. Shared across this character's conversations and controlled by the memory switch.") }
                item(key = "memory") {
                    CharacterTextField("Story and relationships", store.memoryDraft, store::updateMemory, !store.busy, minLines = 10)
                }
                item(key = "actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = "Reload",
                            onClick = { store.loadMemory(id, force = true) },
                            enabled = !store.busy,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "Save memory",
                            onClick = { store.saveMemory(id) },
                            enabled = !store.busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }
}
