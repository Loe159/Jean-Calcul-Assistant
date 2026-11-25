package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Settings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cuisine page: four internal tabs (docs/design/cuisine.md); back from another tab returns to Recettes. */
@Composable
internal fun FoodHomeScreen(onBack: () -> Unit, onOpen: (AppRoute) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(CuisineTab.RECIPES) }
    var filtersSheet by rememberSaveable { mutableStateOf(false) }
    val recipes = rememberRecipesTab(
        onOpen = onOpen, onTab = { tab = it }, onMoreFilters = { filtersSheet = true },
    )
    val pantry = if (tab == CuisineTab.PANTRY) inventoryPageSlots(expiringInitially = false) else null
    val snackbars = remember { SnackbarHostState() }
    val shopping = if (tab == CuisineTab.SHOPPING) rememberShoppingTab(snackbars) else null
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = tab != CuisineTab.RECIPES,
        onBackCompleted = { tab = CuisineTab.RECIPES },
    )
    CuisineHomeScreen(
        tab = tab,
        onTab = { tab = it },
        today = LocalDate.now(),
        onBack = { if (tab != CuisineTab.RECIPES) tab = CuisineTab.RECIPES else onBack() },
        onCreate = { onOpen(AppRoute.RecipeGenerate()) },
        onPreferences = { onOpen(AppRoute.FoodPreferences) },
        bottomAction = shopping?.takeIf { it.state.inBasket > 0 }?.let { holder ->
            { ShoppingBottomAction(holder.state, holder.actions.onPutAway) }
        },
        overlay = {
            if (filtersSheet) RecipeFiltersSheet(recipes.state.filters, recipes.actions.onFilters) { filtersSheet = false }
            pantry?.overlay?.invoke()
            shopping?.overlay?.invoke()
            Box(Modifier.fillMaxSize()) { SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter)) }
        },
    ) { current ->
        when (current) {
            CuisineTab.RECIPES -> RecipesTabContent(recipes.state, recipes.actions)
            CuisineTab.PANTRY -> pantry?.content?.invoke()
            CuisineTab.SHOPPING -> shopping?.let { ShoppingTabContent(it.state, it.actions) }
            CuisineTab.PLANNING -> CuisineMessage(
                stringResource(R.string.food_tab_coming), icon = CuisineIcons.Utensils,
            )
        }
    }
}

@Composable
internal fun CuisineHomeScreen(
    tab: CuisineTab,
    onTab: (CuisineTab) -> Unit,
    today: LocalDate,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onPreferences: () -> Unit,
    bottomAction: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.(CuisineTab) -> Unit,
) {
    // One scroll position per tab, kept while switching (docs/design/cuisine.md, navigation rules).
    val scrolls: Map<CuisineTab, ScrollState> = CuisineTab.entries.associateWith { rememberScrollState() }
    val labels = listOf(
        CuisineTab.RECIPES to stringResource(R.string.food_tab_recipes),
        CuisineTab.PANTRY to stringResource(R.string.food_tab_pantry),
        CuisineTab.SHOPPING to stringResource(R.string.food_tab_shopping),
        CuisineTab.PLANNING to stringResource(R.string.food_tab_planning),
    )
    FoodPage(
        stringResource(R.string.food_home), onBack,
        actions = {
            CouiTopAppBarIconButton(COUIIcons.Add, stringResource(R.string.food_generate), onCreate, Modifier.size(48.dp))
            CouiTopAppBarIconButton(COUIIcons.Settings, stringResource(R.string.food_preferences), onPreferences, Modifier.size(48.dp))
        },
        overlay = overlay,
        scrollState = scrolls.getValue(tab),
        bottomAction = bottomAction,
    ) {
        FoodSupporting(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
            .replaceFirstChar { it.titlecase(Locale.FRANCE) })
        CouiChipTabs(labels, tab, onTab)
        content(tab)
    }
}

internal class RecipesTabHolder(val state: RecipesTabUi, val actions: RecipesTabActions)

@Composable
private fun rememberRecipesTab(
    onOpen: (AppRoute) -> Unit,
    onTab: (CuisineTab) -> Unit,
    onMoreFilters: () -> Unit,
): RecipesTabHolder {
    val repo = foodRuntime().repository
    val today = remember { LocalDate.now() }
    val monday = mondayOf(today).toString()

    var query by rememberSaveable { mutableStateOf("") }
    var maxMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var difficulty by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val filters = RecipeFilters(maxMinutes, difficulty, favoritesOnly)

    val library by remember(repo) { repo.recipes() }.collectAsState(initial = null)
    val pantry by remember(repo) { repo.pantry() }.collectAsState(initial = null)
    val shopping by remember(repo) { repo.shopping() }.collectAsState(initial = null)
    val week by remember(repo, monday) { repo.mealWeek(monday) }.collectAsState(initial = null)

    var libraryRefresh by remember { mutableIntStateOf(0) }
    var libraryFailure by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var libraryLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(repo, libraryRefresh) {
        libraryFailure = repo.refreshRecipes() as? FoodResult.Failure
        libraryLoaded = true
    }
    LaunchedEffect(repo, monday) {
        repo.refreshPantry()
        repo.refreshShopping()
        repo.refreshMealWeek(monday)
    }

    var suggestionsRefresh by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf<List<RecipeSuggestion>?>(null) }
    var suggestionFailure by remember { mutableStateOf<FoodResult.Failure?>(null) }
    LaunchedEffect(repo, suggestionsRefresh) {
        suggestionFailure = null
        val servings = (repo.preferences() as? FoodResult.Success)?.value?.declared?.defaultServings ?: 2
        when (val result = repo.suggest(SuggestRequest(SuggestMode.FROM_PANTRY, defaultGenerationParams(servings)))) {
            is FoodResult.Success -> suggestions = result.value.suggestions
            is FoodResult.Failure -> suggestionFailure = result
        }
    }

    val intent = parseRecipeIntent(query)
    val recipes = library?.value?.recipes.orEmpty()
    val (stockCount, expiring) = pantry?.value?.shortcutCounts() ?: (null to 0)
    val remaining = shopping?.let { snapshot ->
        snapshot.cache?.value?.let { projectShopping(snapshot.items, it.aisles).remainingCount }
    }
    val state = RecipesTabUi(
        shortcuts = CuisineShortcutsUi(
            stockCount = stockCount, expiringCount = expiring, shoppingRemaining = remaining,
            tonight = week?.value?.tonight(today), planningKnown = week != null,
        ),
        query = query,
        filters = filters,
        suggestionsState = when {
            suggestions != null -> CuisineContentState.READY
            suggestionFailure != null -> CuisineContentState.ERROR
            else -> CuisineContentState.LOADING
        },
        suggestionsError = suggestionFailure?.let { stringResource(it.error.label()) },
        sections = suggestions.orEmpty().toSections(intent, filters),
        library = recipes.filter { it.matches(intent, filters) }.libraryOrder(),
        libraryState = when {
            library != null -> CuisineContentState.READY
            libraryFailure != null -> CuisineContentState.ERROR
            libraryLoaded -> CuisineContentState.EMPTY
            else -> CuisineContentState.LOADING
        },
        libraryError = libraryFailure?.let { stringResource(it.error.label()) },
        offline = library != null && libraryFailure != null,
    )
    val actions = RecipesTabActions(
        onQuery = { query = it },
        onFilters = {
            maxMinutes = it.maxMinutes
            difficulty = it.difficulty
            favoritesOnly = it.favoritesOnly
        },
        onMoreFilters = onMoreFilters,
        onRecipe = { id, servings -> onOpen(AppRoute.RecipeDetail(id, servings = servings)) },
        onTab = onTab,
        onCreate = { onOpen(AppRoute.RecipeGenerate()) },
        onRetrySuggestions = { suggestionsRefresh++ },
        onRetryLibrary = { libraryRefresh++ },
    )
    return RecipesTabHolder(state, actions)
}
