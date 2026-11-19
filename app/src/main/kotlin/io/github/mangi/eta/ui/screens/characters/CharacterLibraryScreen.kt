package io.github.mangi.eta.ui.screens.characters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.roleplay.CharacterProfile
import io.github.mangi.eta.ui.app.CharacterLibraryStore
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add

internal val CharacterCardPadding = 12.dp
private val CharacterGridSpacing = 12.dp

@Composable
internal fun CharacterLibraryScreen(
    store: CharacterLibraryStore,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) store.importCard(uri) { onNavigate(AppRoute.CharacterDetail(it)) }
    }
    val importCard = {
        importer.launch(arrayOf("image/png", "application/json", "text/plain", "application/octet-stream"))
    }
    val createCharacter = {
        store.discardEditor()
        onNavigate(AppRoute.CharacterEditor())
    }
    val showAddTile = store.query.isBlank() && store.characters.isNotEmpty()
    val tiles: List<CharacterProfile?> = store.filteredCharacters + if (showAddTile) listOf(null) else emptyList()
    val rows = tiles.chunked(2)

    CouiLazyScaffoldPage(
        title = "Characters",
        onBack = onBack,
        actions = {
            CouiTopAppBarIconButton(
                icon = Icons.Rounded.FileUpload,
                contentDescription = "Import character card",
                enabled = !store.busy,
                onClick = importCard,
            )
            CouiTopAppBarIconButton(
                icon = COUIIcons.Add,
                contentDescription = "Create character",
                enabled = !store.busy,
                onClick = createCharacter,
            )
        },
    ) {
        item(key = "search") {
            CouiSearchBarBox(
                query = store.query,
                onQueryChange = { store.query = it },
                placeholder = "Search names or tags",
            )
        }
        when {
            store.busy && store.characters.isEmpty() -> {
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(size = 28.dp)
                    }
                }
            }
            store.characters.isEmpty() -> {
                item(key = "empty-library") {
                    ListEmptyState(
                        title = "No characters yet",
                        summary = "Create a character or import a PNG or JSON card to start",
                        icon = Icons.Rounded.SportsBar,
                        actionText = "Create character",
                        onActionClick = createCharacter,
                    )
                }
            }
            store.filteredCharacters.isEmpty() -> {
                item(key = "empty-search") {
                    ListEmptyState(
                        title = "No matching characters",
                        summary = "Try another keyword. Search matches names and tags.",
                        icon = Icons.Rounded.SearchOff,
                    )
                }
            }
        }
        items(rows, key = { row -> row.joinToString("|") { it?.id ?: "add" } }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = CharacterGridSpacing),
                horizontalArrangement = Arrangement.spacedBy(CharacterGridSpacing),
            ) {
                row.forEach { profile ->
                    if (profile == null) {
                        CharacterAddTile(
                            enabled = !store.busy,
                            onCreate = createCharacter,
                            onImport = importCard,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        CharacterPortraitCard(
                            profile = profile,
                            onClick = { if (!store.busy) onNavigate(AppRoute.CharacterDetail(profile.id)) },
                            onToggleFavorite = { store.toggleFavorite(profile.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item(key = "persona") {
            PreferenceCategory(title = "Mine")
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = "My persona",
                            summary = "Your name and identity in the story",
                            onClick = { onNavigate(AppRoute.CharacterPersona) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CharacterPageMessage(text: String) {
    PreferenceFooter(text = text)
}
