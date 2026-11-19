package io.github.mangi.eta.ui.screens.characters

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.roleplay.CharacterBookEntryDraft
import io.github.mangi.eta.agent.roleplay.CharacterWorldbook
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Switch

internal fun LazyListScope.characterWorldbookEditor(
    store: CharacterLibraryStore,
    expanded: Boolean,
    expandedEntry: Int?,
    onToggleExpanded: () -> Unit,
    onExpandEntry: (Int?) -> Unit,
) {
    val card = store.draft ?: return
    val book = card.worldbookDraft()
    val enabled = !store.busy
    item(key = "worldbook-title") { CharacterSectionHeader("Worldbook") }
    item(key = "worldbook-header") {
        val summary = "${book.entries.size} entries · ${if (expanded) "Collapse" else "Expand editor"}"
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = "Embedded worldbook",
                        summary = summary,
                        onClick = onToggleExpanded,
                    )
                }
            }
        }
    }
    if (!expanded) return
    item(key = "worldbook-name") {
        CharacterTextField("Worldbook name", book.name, { value -> store.updateWorldbook { it.copy(name = value) } }, enabled, singleLine = true)
    }
    item(key = "worldbook-depth") {
        CharacterTextField("Recent messages to scan (blank for default)", book.scanDepth?.toString().orEmpty(), { value ->
            optionalNonNegativeInt(value) { number -> store.updateWorldbook { it.copy(scanDepth = number) } }
        }, enabled, singleLine = true)
    }
    item(key = "worldbook-budget") {
        CharacterTextField("Token budget (blank for automatic)", book.tokenBudget?.toString().orEmpty(), { value ->
            optionalNonNegativeInt(value) { number -> store.updateWorldbook { it.copy(tokenBudget = number) } }
        }, enabled, singleLine = true)
    }
    item(key = "worldbook-recursive") {
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = "Recursive matching",
                        summary = "Use matched content to find related entries",
                        checked = book.recursiveScanning == true,
                        enabled = enabled,
                        onCheckedChange = { value -> store.updateWorldbook { it.copy(recursiveScanning = value) } },
                    )
                }
            }
        }
    }
    item(key = "worldbook-entries-title") { CharacterSectionHeader("Entries") }
    val unsupported = CharacterWorldbook.unsupportedEntries(card).associate { it.index to it.reasons }
    itemsIndexed(book.entries, key = { index, _ -> "worldbook-entry-$index" }) { index, entry ->
        val entryTitle = entry.name.take(120).ifBlank { "Entry ${index + 1}" }
        val entrySummary = when {
            !entry.enabled -> "Disabled"
            !unsupported[index].isNullOrEmpty() -> "Skipped: ${unsupported[index].orEmpty().joinToString("；")}"
            entry.constant -> "Always include in context"
            else -> entry.keys.take(4).joinToString("、").take(160).ifBlank { "No trigger keywords configured" }
        }
        val onToggleEntry: (Boolean) -> Unit = { value ->
            store.updateWorldbook { current ->
                current.copy(entries = current.entries.toMutableList().apply { this[index] = entry.copy(enabled = value) })
            }
        }
        val dark = COUITheme.isDark
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = entryTitle,
                        summary = entrySummary,
                        endActions = {
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = onToggleEntry,
                                enabled = enabled,
                            )
                        },
                        onClick = { onExpandEntry(if (expandedEntry == index) null else index) },
                    )
                }
                if (expandedEntry == index) {
                    item {
                        CharacterWorldbookEntryEditor(
                            entry = entry,
                            enabled = enabled,
                            unsupported = unsupported[index].orEmpty(),
                            onChange = { replacement ->
                                store.updateWorldbook { current -> current.copy(entries = current.entries.toMutableList().apply { this[index] = replacement }) }
                            },
                            onDelete = {
                                onExpandEntry(null)
                                store.updateWorldbook { current -> current.copy(entries = current.entries.filterIndexed { i, _ -> i != index }) }
                            },
                        )
                    }
                }
            }
        }
    }
    item(key = "worldbook-add-entry") {
        val onAddEntry: () -> Unit = {
            onExpandEntry(book.entries.size)
            store.updateWorldbook { it.copy(entries = it.entries + CharacterBookEntryDraft(insertionOrder = it.entries.size)) }
        }
        CouiButton(
            text = "Add entry",
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            onClick = onAddEntry,
        )
    }
}

@Composable
private fun CharacterWorldbookEntryEditor(
    entry: CharacterBookEntryDraft,
    enabled: Boolean,
    unsupported: List<String>,
    onChange: (CharacterBookEntryDraft) -> Unit,
    onDelete: () -> Unit,
) {
    if (unsupported.isNotEmpty()) {
        Text(
            "This entry is excluded from matching: ${unsupported.joinToString("；")}",
            style = COUITheme.textStyles.bodyM,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = COUITheme.colorScheme.labelSecondary,
        )
    }
    CharacterFieldGroupLabel("Content")
    CharacterTextField("Entry title", entry.name, { onChange(entry.copy(name = it)) }, enabled, singleLine = true, inGroup = true)
    CharacterTextField("Content", entry.content, { onChange(entry.copy(content = it)) }, enabled, minLines = 4, inGroup = true)
    CharacterFieldGroupLabel("Trigger")
    CharacterTextField("Primary keywords (one per line)", entry.keys.joinToString("\n"), { onChange(entry.copy(keys = it.lines())) }, enabled, inGroup = true)
    CharacterTextField("Secondary keywords (one per line)", entry.secondaryKeys.joinToString("\n"), { onChange(entry.copy(secondaryKeys = it.lines())) }, enabled, inGroup = true)
    SwitchPreference(
        title = "Also match secondary keywords",
        checked = entry.selective,
        enabled = enabled,
        onCheckedChange = { onChange(entry.copy(selective = it)) },
    )
    CharacterFieldGroupLabel("Insertion")
    SwitchPreference(
        title = "Permanent context",
        checked = entry.constant,
        enabled = enabled,
        onCheckedChange = { onChange(entry.copy(constant = it)) },
    )
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
    SwitchPreference(
        title = "Insert before character settings",
        summary = "When off, insert after character settings",
        checked = entry.position == "before_char",
        enabled = enabled,
        onCheckedChange = { onChange(entry.copy(position = if (it) "before_char" else "after_char")) },
    )
    CharacterTextField("Insertion order", entry.insertionOrder.toString(), { value ->
        value.toIntOrNull()?.takeIf { it >= 0 }?.let { onChange(entry.copy(insertionOrder = it)) }
    }, enabled, singleLine = true, inGroup = true)
    CouiButton(
        text = "Remove entry",
        enabled = enabled,
        role = CouiButtonRole.Destructive,
        onClick = onDelete,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private fun optionalNonNegativeInt(value: String, onValue: (Int?) -> Unit) {
    if (value.isBlank()) onValue(null)
    else value.toIntOrNull()?.takeIf { it >= 0 }?.let(onValue)
}
