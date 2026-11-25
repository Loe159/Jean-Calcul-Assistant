package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Months
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

internal data class RecipesTabUi(
    val shortcuts: CuisineShortcutsUi = CuisineShortcutsUi(),
    val query: String = "",
    val filters: RecipeFilters = RecipeFilters(),
    val suggestionsState: CuisineContentState = CuisineContentState.LOADING,
    val suggestionsError: String? = null,
    val sections: RecipeSections = RecipeSections(emptyList(), emptyList()),
    val library: List<RecipeSummary> = emptyList(),
    val libraryState: CuisineContentState = CuisineContentState.LOADING,
    val libraryError: String? = null,
    /** Library served from the device cache after a failed refresh. */
    val offline: Boolean = false,
)

internal class RecipesTabActions(
    val onQuery: (String) -> Unit,
    val onFilters: (RecipeFilters) -> Unit,
    val onMoreFilters: () -> Unit,
    val onRecipe: (id: String, servings: Int?) -> Unit,
    val onTab: (CuisineTab) -> Unit,
    val onCreate: () -> Unit,
    val onRetrySuggestions: () -> Unit,
    val onRetryLibrary: () -> Unit,
)

@Composable
internal fun ColumnScope.RecipesTabContent(state: RecipesTabUi, actions: RecipesTabActions) {
    val intent = parseRecipeIntent(state.query)
    val searching = intent.words.isNotEmpty() || intent.maxMinutes != null || state.filters.active
    CuisineShortcuts(state.shortcuts, actions.onTab)
    CouiSearchBarBox(state.query, actions.onQuery, stringResource(R.string.food_recipe_search_hint))
    RecipeFilterChips(state.filters, intent, actions)

    val tonight = state.shortcuts.tonight
    if (!searching && tonight != null) {
        CuisineSectionHeader(stringResource(R.string.food_tonight), stringResource(R.string.food_tab_planning)) {
            actions.onTab(CuisineTab.PLANNING)
        }
        val reason = stringResource(
            if (tonight.meal == Meal.DINNER) R.string.food_planned_dinner else R.string.food_planned_lunch,
            portionLabel(tonight.servings),
        )
        RecipeCard(
            tonight.recipe.toCardUi().copy(reason = reason, portions = tonight.servings), RecipeCardVariant.L,
            availability = null, onClick = { actions.onRecipe(tonight.recipeId, tonight.servings) },
        )
    }

    CuisineSectionHeader(stringResource(R.string.food_with_what_i_have))
    when (state.suggestionsState) {
        CuisineContentState.LOADING -> Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(2) { RecipeCard(null, RecipeCardVariant.S, null, {}, state = CuisineContentState.LOADING) }
        }
        CuisineContentState.ERROR -> CuisineMessage(
            stringResource(R.string.food_suggestions_unavailable), detail = state.suggestionsError,
            icon = CuisineIcons.Offline, onRetry = actions.onRetrySuggestions,
        )
        else -> if (state.sections.withWhatIHave.isEmpty()) {
            FoodSupporting(stringResource(
                if (searching) R.string.food_recipe_empty_filters else R.string.food_nothing_ready,
            ))
        } else Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.sections.withWhatIHave.forEach { suggestion ->
                RecipeCard(
                    suggestion.recipe.toCardUi(), RecipeCardVariant.S, suggestion.availability.toAvailabilityUi(),
                    onClick = { actions.onRecipe(suggestion.recipe.id, null) },
                )
            }
        }
    }

    if (state.sections.useSoon.isNotEmpty()) {
        CuisineSectionHeader(stringResource(R.string.food_use_soon), stringResource(R.string.food_tab_pantry)) {
            actions.onTab(CuisineTab.PANTRY)
        }
        state.sections.useSoon.forEach { suggestion ->
            val reason = stringResource(R.string.food_uses_expiring, suggestion.expiringProducts.joinToString(", "))
            RecipeCard(
                suggestion.recipe.toCardUi().copy(reason = reason), RecipeCardVariant.R,
                suggestion.availability.toAvailabilityUi(),
                onClick = { actions.onRecipe(suggestion.recipe.id, null) },
            )
        }
    }

    CuisineSectionHeader(
        if (searching) pluralStringResource(R.plurals.food_recipe_results, state.library.size, state.library.size)
        else stringResource(R.string.food_all_recipes),
    )
    if (state.offline) {
        CuisineBadge(stringResource(R.string.food_library_offline), icon = CuisineIcons.Offline, compact = true)
    }
    when {
        state.libraryState == CuisineContentState.LOADING && state.library.isEmpty() ->
            repeat(3) { RecipeCard(null, RecipeCardVariant.R, null, {}, state = CuisineContentState.LOADING) }
        state.libraryState == CuisineContentState.ERROR && state.library.isEmpty() -> CuisineMessage(
            stringResource(R.string.food_library_unavailable), detail = state.libraryError,
            icon = CuisineIcons.Warning, isError = true, onRetry = actions.onRetryLibrary,
        )
        state.library.isEmpty() -> CuisineMessage(
            stringResource(R.string.food_no_recipes),
            detail = stringResource(if (searching) R.string.food_recipe_empty_filters else R.string.food_library_empty),
            icon = CuisineIcons.Utensils,
        )
        else -> state.library.forEach { recipe ->
            RecipeCard(recipe.toCardUi(), RecipeCardVariant.R, null, onClick = { actions.onRecipe(recipe.id, null) })
        }
    }
    FoodButton(stringResource(R.string.food_generate), actions.onCreate)
}

@Composable
private fun RecipeFilterChips(filters: RecipeFilters, intent: RecipeIntent, actions: RecipesTabActions) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val quick = filters.maxMinutes == 20
        FoodChoiceChip(stringResource(R.string.food_filter_max_minutes, 20), quick) {
            actions.onFilters(filters.copy(maxMinutes = if (quick) null else 20))
        }
        val easy = filters.difficulty == Difficulty.EASY
        FoodChoiceChip(stringResource(R.string.food_filter_easy), easy) {
            actions.onFilters(filters.copy(difficulty = if (easy) null else Difficulty.EASY))
        }
        FoodChoiceChip(stringResource(R.string.food_favorites), filters.favoritesOnly) {
            actions.onFilters(filters.copy(favoritesOnly = !filters.favoritesOnly))
        }
        val custom = (filters.maxMinutes != null && filters.maxMinutes != 20) || filters.difficulty?.let { it != Difficulty.EASY } == true
        val label = stringResource(R.string.food_more_filters)
        Chip(
            label = label, selected = custom, onClick = actions.onMoreFilters,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            icon = { Icon(COUIIcons.Tune, null, Modifier.size(16.dp)) },
        )
    }
    intent.maxMinutes?.let { FoodSupporting(stringResource(R.string.food_intent_minutes, it)) }
}

/** The « Plus de filtres » sheet; the quick chips above edit the same [RecipeFilters]. */
@Composable
internal fun RecipeFiltersSheet(filters: RecipeFilters, onChange: (RecipeFilters) -> Unit, onClose: () -> Unit) {
    FoodSheet(stringResource(R.string.food_more_filters), onClose) {
        val times = listOf<Int?>(null, 20, 30, 45, 60)
        FoodChoices(
            stringResource(R.string.food_filter_duration),
            times.map { it to if (it == null) stringResource(R.string.food_filter_any) else stringResource(R.string.food_filter_max_minutes, it) },
            filters.maxMinutes,
        ) { onChange(filters.copy(maxMinutes = it)) }
        val levels = stringArrayResource(R.array.food_difficulties)
        FoodChoices(
            stringResource(R.string.food_difficulty),
            listOf<Difficulty?>(null).plus(Difficulty.entries).map {
                it to if (it == null) stringResource(R.string.food_filter_any) else levels[it.ordinal]
            },
            filters.difficulty,
        ) { onChange(filters.copy(difficulty = it)) }
        CardListGroup {
            item { FoodSwitchRow(stringResource(R.string.food_favorites_only), filters.favoritesOnly) { onChange(filters.copy(favoritesOnly = it)) } }
        }
        if (filters.active) FoodAction(R.string.food_filter_reset) { onChange(RecipeFilters()) }
        FoodAction(R.string.food_filter_apply, primary = true, onClick = onClose)
    }
}

@Composable
internal fun CuisineSectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, Modifier.weight(1f).semantics { heading() }, style = COUITheme.textStyles.headlineS,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (action != null && onAction != null) {
            // A link, as in the prototype: no fill, primary label.
            TextButton(
                action, onAction, Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColorsBorderless(),
                textStyle = COUITheme.textStyles.buttonM, material = ButtonMaterial.None,
            )
        }
    }
}

@Composable
private fun CuisineShortcuts(shortcuts: CuisineShortcutsUi, onTab: (CuisineTab) -> Unit) {
    val colors = COUITheme.colorScheme
    val dash = stringResource(R.string.food_value_unknown)
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { modifier ->
            ShortcutTile(
                CuisineIcons.Fridge, colors.azure, shortcuts.stockCount?.toString() ?: dash,
                if (shortcuts.expiringCount > 0) pluralStringResource(R.plurals.food_expiring_count, shortcuts.expiringCount, shortcuts.expiringCount)
                else stringResource(R.string.food_products_in_stock),
                stringResource(R.string.food_tab_pantry), modifier,
            ) { onTab(CuisineTab.PANTRY) }
        },
        { modifier ->
            ShortcutTile(
                CuisineIcons.Cart, colors.green, shortcuts.shoppingRemaining?.toString() ?: dash,
                stringResource(R.string.food_shopping_remaining), stringResource(R.string.food_tab_shopping), modifier,
            ) { onTab(CuisineTab.SHOPPING) }
        },
        { modifier ->
            ShortcutTile(
                COUIIcons.Months, colors.blue, stringResource(R.string.food_tonight_short),
                shortcuts.tonight?.recipe?.title ?: stringResource(
                    if (shortcuts.planningKnown) R.string.food_nothing_planned else R.string.food_value_unknown,
                ),
                stringResource(R.string.food_tab_planning), modifier,
            ) { onTab(CuisineTab.PLANNING) }
        },
    )
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun ShortcutTile(
    icon: ImageVector,
    tint: Color,
    value: String,
    detail: String,
    destination: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = COUITheme.colorScheme
    Card(
        modifier.semantics {
            role = Role.Button
            contentDescription = "$destination · $value · $detail"
        },
        onClick = onClick,
    ) {
        // The Now Brief tint halo, allowed on the Cuisine shortcut tiles (docs/design/cuisine.md).
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            listOf(tint.copy(alpha = if (COUITheme.isDark) 0.22f else 0.14f), Color.Transparent), radius = 260f,
            center = androidx.compose.ui.geometry.Offset.Zero,
        ))) {
            Column(
                Modifier.fillMaxWidth().padding(12.dp).clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(shape = SquircleShape(10.dp), color = tint.copy(alpha = 0.18f)) {
                    Icon(icon, null, Modifier.padding(6.dp).size(18.dp), tint = cuisineTint(tint))
                }
                Text(value, style = COUITheme.textStyles.headlineM, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = COUITheme.textStyles.bodyXS, color = colors.labelSecondary, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

