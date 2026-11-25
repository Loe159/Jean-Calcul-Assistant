package io.github.mangi.eta.ui.screens.food

import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Settings
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import kotlinx.coroutines.launch

@Composable
internal fun FoodHomeScreen(onBack: () -> Unit, onOpen: (AppRoute) -> Unit) {
    var pantry by rememberSaveable { mutableStateOf(false) }
    val recipes = stringResource(R.string.food_recipes)
    val inventory = stringResource(R.string.food_inventory)
    val slots = if (pantry) {
        inventoryPageSlots(expiringInitially = false)
    } else {
        recipeLibraryPageSlots(
            onGenerate = { onOpen(AppRoute.RecipeGenerate()) },
            onRecipe = { id, servings -> onOpen(AppRoute.RecipeDetail(id, servings = servings)) },
        )
    }
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = pantry,
        onBackCompleted = { pantry = false },
    )
    FoodPage(stringResource(R.string.food_home), { if (pantry) pantry = false else onBack() }, actions = {
        CouiTopAppBarIconButton(
            COUIIcons.Settings, stringResource(R.string.food_preferences), { onOpen(AppRoute.FoodPreferences) },
            Modifier.size(48.dp),
        )
    }, overlay = slots.overlay) {
        CouiChipTabs(listOf(false to recipes, true to inventory), pantry, { pantry = it })
        slots.content()
    }
}

@Composable
private fun recipeLibraryPageSlots(onGenerate: () -> Unit, onRecipe: (String, Int?) -> Unit): FoodPageSlots {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    val cached by remember(repo) { repo.recipes() }.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var libraryError by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var suggestions by remember { mutableStateOf<List<RecipeSuggestion>?>(null) }
    var suggestionError by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var suggesting by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var params by remember { mutableStateOf(defaultGenerationParams()) }

    LaunchedEffect(repo, refresh) { libraryError = repo.refreshRecipes() as? FoodResult.Failure }

    fun suggest() {
        if (suggesting) return
        showSuggestions = true
        suggesting = true
        suggestionError = null
        scope.launch {
            val declared = (repo.preferences() as? FoodResult.Success)?.value?.declared
            params = defaultGenerationParams(declared?.defaultServings ?: 2)
            when (val result = repo.suggest(SuggestRequest(SuggestMode.FROM_PANTRY, params))) {
                is FoodResult.Success -> suggestions = result.value.suggestions
                is FoodResult.Failure -> suggestionError = result
            }
            suggesting = false
        }
    }

    return FoodPageSlots(
        content = {
            FoodAction(R.string.food_generate, primary = true, onClick = onGenerate)
            FoodAction(R.string.food_suggest, enabled = !suggesting, onClick = ::suggest)
            FoodErrorText(libraryError)
            if (libraryError != null) FoodAction(R.string.food_retry) { refresh++ }
            CouiSearchBarBox(query, { query = it }, stringResource(R.string.food_search))
            val recipes = cached?.value?.recipes.orEmpty()
                .filter { it.title.contains(query.trim(), ignoreCase = true) || it.tags.any { tag -> tag.contains(query.trim(), ignoreCase = true) } }
                .sortedWith(compareByDescending<RecipeSummary> { it.favorite }.thenBy { it.title.lowercase() })

            if (cached == null && libraryError == null) FoodLoading()
            if (cached != null && recipes.isEmpty()) ListEmptyState(stringResource(R.string.food_no_recipes))
            if (recipes.isNotEmpty()) {
                CardListGroup {
                    recipes.forEach { recipe ->
                        item {
                            ArrowPreference(
                                title = recipe.title,
                                summary = stringResource(R.string.food_minutes, recipe.prepTimeMinutes + recipe.cookTimeMinutes),
                                onClick = { onRecipe(recipe.id, null) },
                            )
                        }
                    }
                }
            }
        },
        overlay = {
            if (showSuggestions) {
                FoodLazySheet(stringResource(R.string.food_suggest), { showSuggestions = false }) {
                    if (suggestionError != null || suggesting) item {
                        FoodErrorText(suggestionError)
                        if (suggesting) FoodLoading()
                        else FoodAction(R.string.food_retry, onClick = ::suggest)
                    }
                    val suggestionsByCategory = suggestions?.groupBy { it.availability.category }?.toSortedMap()
                    if (!suggesting && suggestionsByCategory?.isEmpty() == true) item {
                        ListEmptyState(stringResource(R.string.food_no_recipes))
                    }
                    if (!suggestionsByCategory.isNullOrEmpty()) item {
                        FoodSupporting(stringResource(R.string.food_suggestion_stock))
                    }
                    suggestionsByCategory?.forEach { (category, results) ->
                        item(key = category.name) { FoodHeading(stringResource(category.label())) }
                        items(results, key = { it.recipe.id }) { suggestion ->
                            SuggestionRow(suggestion, params.servings) {
                                showSuggestions = false
                                onRecipe(suggestion.recipe.id, params.servings)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SuggestionRow(suggestion: RecipeSuggestion, servings: Int, onOpen: () -> Unit) {
    val repo = foodRuntime().repository
    val id = suggestion.recipe.id
    val detail by remember(repo, id) { repo.recipe(id) }.collectAsState(initial = null)
    LaunchedEffect(id, servings) {
        if (suggestion.availability.missingCount == 0) return@LaunchedEffect
        val recipe = detail?.value ?: (repo.refreshRecipe(id) as? FoodResult.Success)?.value?.recipe
        if (recipe != null) repo.refreshAvailability(recipe, servings)
    }
    FoodSection {
        FoodHeading(suggestion.recipe.title)
        val recipe = detail?.value
        if (recipe != null) {
            val cache by remember(recipe, servings) { repo.availability(recipe, servings) }.collectAsState(initial = null)
            val availability = cache?.value
            if (availability != null) {
                FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(availability.stockAsOf)))
                val names = recipe.ingredients.associate { it.id to it.name }
                availability.items.filter { it.missing != null }.forEach {
                    names[it.ingredientId]?.let { name -> FoodSupporting(name) }
                    IngredientAvailability(it)
                }
            } else FoodSupporting(stringResource(R.string.food_stock_unknown))
        } else if (suggestion.availability.missingCount > 0) {
            FoodSupporting(stringResource(R.string.food_stock_unknown))
        }
        FoodAction(R.string.food_brief_open, onClick = onOpen)
    }
}
