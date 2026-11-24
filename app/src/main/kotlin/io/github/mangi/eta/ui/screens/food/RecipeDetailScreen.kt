package io.github.mangi.eta.ui.screens.food

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RecipeDetailScreen(
    recipeId: String,
    briefCardId: String?,
    initialServings: Int?,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    val cache by remember(repo, recipeId) { repo.recipe(recipeId) }.collectAsState(initial = null)
    val library by remember(repo, recipeId) { repo.library(recipeId) }.collectAsState(initial = null)
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var stockError by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedServings by rememberSaveable(recipeId) { mutableStateOf(initialServings) }
    var sheet by rememberSaveable { mutableStateOf<RecipeSheet?>(null) }
    var busy by remember { mutableStateOf(false) }
    val recipe = cache?.value
    val servings = selectedServings ?: recipe?.servings?.coerceIn(1, 12) ?: 2

    LaunchedEffect(repo, recipeId, refresh) { error = repo.refreshRecipe(recipeId) as? FoodResult.Failure }
    LaunchedEffect(repo, recipe?.id, recipe?.version, servings, refresh) {
        val target = recipe ?: return@LaunchedEffect
        delay(250)
        stockError = repo.refreshAvailability(target, servings) as? FoodResult.Failure
    }

    fun <T> write(action: suspend () -> FoodResult<T>, success: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val result = action()) {
                is FoodResult.Success -> success(result.value)
                is FoodResult.Failure -> error = result
            }
            busy = false
        }
    }

    val overlay: @Composable () -> Unit = {
        when (sheet) {
            RecipeSheet.OPTIONS -> FoodSheet(stringResource(R.string.food_options), { sheet = null }) {
                FoodAction(R.string.food_duplicate, enabled = !busy) {
                    write({ repo.duplicate(recipeId) }) {
                        sheet = null
                        onOpen(AppRoute.RecipeDetail(it.recipe.id))
                    }
                }
                FoodAction(R.string.food_adapt) {
                    sheet = null
                    onOpen(AppRoute.RecipeGenerate(recipeId, servings))
                }
                FoodAction(R.string.food_rating_notes) { sheet = RecipeSheet.RATING }
                FoodAction(R.string.food_delete) { sheet = RecipeSheet.DELETE }
                FoodErrorText(error)
            }
            RecipeSheet.RATING -> {
                var rating by rememberSaveable { mutableStateOf(library?.value?.rating?.toString().orEmpty()) }
                var notes by rememberSaveable { mutableStateOf(library?.value?.notes.orEmpty()) }
                FoodSheet(stringResource(R.string.food_rating_notes), { sheet = null }) {
                    FoodNumberField(rating, { rating = it }, R.string.food_rating)
                    CouiTextField(notes, { notes = it.take(2000) }, label = stringResource(R.string.food_notes), singleLine = false, maxLines = 6)
                    FoodErrorText(error)
                    val stars = rating.toIntOrNull()?.takeIf { it in 1..5 }
                    FoodAction(R.string.food_save, primary = true, enabled = !busy && (rating.isBlank() || stars != null)) {
                        write({ repo.updateLibrary(recipeId, UpdateRecipeLibraryRequest(rating = stars, notes = notes)) }) { sheet = null }
                    }
                }
            }
            RecipeSheet.DELETE -> CouiConfirmDialog(
                visible = true, title = stringResource(R.string.food_delete_recipe), message = null,
                confirmText = stringResource(R.string.food_delete), destructive = true, confirmEnabled = !busy,
                onDismissRequest = { sheet = null },
                onConfirm = {
                    sheet = null
                    write({ repo.archive(recipeId) }) { onBack() }
                },
            )
            null -> Unit
        }
    }

    FoodPage(stringResource(R.string.food_detail), onBack, actions = {
        if (recipe != null) {
            val favorite = library?.value?.favorite == true
            CouiTopAppBarIconButton(
                if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                stringResource(if (favorite) R.string.food_remove_favorite else R.string.food_add_favorite),
                enabled = !busy,
                onClick = { write({ repo.updateLibrary(recipeId, UpdateRecipeLibraryRequest(favorite = !favorite)) }) },
            )
            CouiTopAppBarIconButton(Icons.Outlined.MoreHoriz, stringResource(R.string.food_options), { sheet = RecipeSheet.OPTIONS })
        }
    }, overlay = overlay) {
        FoodErrorText(error)
        if (error != null) FoodAction(R.string.food_retry) { refresh++ }
        if (recipe == null) {
            if (error == null) FoodSupporting(stringResource(R.string.food_loading))
            return@FoodPage
        }

        val cachedAvailability by remember(repo, recipe.id, recipe.version) {
            repo.availability(recipe, recipe.servings)
        }.collectAsState(initial = null)
        val availability = cachedAvailability?.value?.let { stock ->
            if (stock.recipeId == recipe.id && stock.recipeVersion == recipe.version) stock.recompute(recipe, servings)
            else null
        }
        val selectedAvailability by remember(repo, recipe.id, recipe.version, servings) {
            repo.availability(recipe, servings)
        }.collectAsState(initial = null)
        val live = selectedAvailability?.value?.takeIf {
            it.servings == servings && it.recipeId == recipe.id && it.recipeVersion == recipe.version
        } ?: availability
        RecipeHero(recipe, servings, live)
        if (recipe.allergenCheck == AllergenCheck.NOT_GUARANTEED) {
            FoodSupporting(stringResource(R.string.food_allergen_notice))
        }
        FoodAction(R.string.food_start_cooking, primary = true, enabled = !busy && recipe.steps.isNotEmpty()) {
            if (!busy) {
                busy = true
                scope.launch {
                    val id = repo.startSession(recipe, servings, briefCardId)
                    onOpen(AppRoute.CookingSession(id))
                    busy = false
                }
            }
        }
        FoodAction(R.string.food_adapt) { onOpen(AppRoute.RecipeGenerate(recipeId, servings)) }
        FoodSection { PortionSelector(servings) { selectedServings = it } }
        FoodSection {
            FoodHeading(stringResource(R.string.food_ingredients))
            if (stockError != error) FoodErrorText(stockError)
            IngredientChecklist(recipe.portions(servings), live)
        }
        FoodSection {
            FoodHeading(stringResource(R.string.food_steps))
            recipe.steps.sortedBy { it.order }.forEach { step ->
                FoodHeading(step.title)
                Text(recipe.renderStep(step, servings))
            }
        }
    }
}

private enum class RecipeSheet { OPTIONS, RATING, DELETE }
