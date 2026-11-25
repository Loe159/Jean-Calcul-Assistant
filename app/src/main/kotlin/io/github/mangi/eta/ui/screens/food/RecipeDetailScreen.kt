package io.github.mangi.eta.ui.screens.food

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.SnackbarDuration
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.SnackbarResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

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
    val snackbars = remember { SnackbarHostState() }
    val cache by remember(repo, recipeId) { repo.recipe(recipeId) }.collectAsState(initial = null)
    val library by remember(repo, recipeId) { repo.library(recipeId) }.collectAsState(initial = null)
    val shopping by remember(repo) { repo.shopping() }.collectAsState(initial = null)
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var stockError by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedServings by rememberSaveable(recipeId) { mutableStateOf(initialServings) }
    var checked by rememberSaveable(recipeId) { mutableStateOf(listOf<String>()) }
    var sheet by rememberSaveable { mutableStateOf<RecipeSheet?>(null) }
    var busy by remember { mutableStateOf(false) }
    var conflictId by rememberSaveable(recipeId) { mutableStateOf<String?>(null) }
    var confirmReplace by rememberSaveable(recipeId) { mutableStateOf(false) }
    val activeSession by remember(repo) { repo.activeSession() }.collectAsState(initial = null)
    val recipe = cache?.value
    val servings = selectedServings ?: recipe?.servings?.coerceIn(RecipePortionRange) ?: 2

    LaunchedEffect(repo, recipeId, refresh) {
        error = repo.refreshRecipe(recipeId) as? FoodResult.Failure
        repo.refreshShopping()
    }
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

    fun start(replacing: String? = null) {
        val target = recipe ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                when (val result = repo.startSession(target, servings, briefCardId, replacing)) {
                    is CookingStart.Open -> {
                        conflictId = null
                        confirmReplace = false
                        onOpen(AppRoute.CookingSession(result.sessionId))
                    }
                    is CookingStart.Conflict -> {
                        conflictId = result.session.id
                        confirmReplace = false
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    if (recipe == null) {
        FoodPage(stringResource(R.string.food_detail), onBack, compactTitle = true) {
            FoodErrorText(error)
            if (error != null) FoodAction(R.string.food_retry) { refresh++ } else FoodLoading()
        }
        return
    }

    val cachedAvailability by remember(repo, recipe.id, recipe.version) {
        repo.availability(recipe, recipe.servings)
    }.collectAsState(initial = null)
    val selectedAvailability by remember(repo, recipe.id, recipe.version, servings) {
        repo.availability(recipe, servings)
    }.collectAsState(initial = null)
    val live = selectedAvailability?.value?.takeIf {
        it.servings == servings && it.recipeId == recipe.id && it.recipeVersion == recipe.version
    } ?: cachedAvailability?.value?.takeIf { it.recipeId == recipe.id && it.recipeVersion == recipe.version }
        ?.recompute(recipe, servings)
    val verification = when {
        live == null -> StockVerification.VERIFIED
        selectedAvailability?.value?.servings == servings && stockError == null -> StockVerification.VERIFIED
        stockError?.error == FoodError.OFFLINE -> StockVerification.OFFLINE
        else -> StockVerification.UNVERIFIED
    }
    val stock = live?.items.orEmpty().associateBy { it.ingredientId }
    val shoppingItems = shopping?.items.orEmpty()
    val rows = recipe.portions(servings).map { portion ->
        val item = stock[portion.ingredient.id]
        val covered = item?.missing?.let {
            ingredientShoppingCoverage(portion.ingredient, it, shoppingItems).status == ShoppingCoverageStatus.FULL
        } == true
        IngredientRowUi(portion, item, inShoppingList = covered, checked = portion.ingredient.id in checked)
    }
    val pending = rows.filter {
        !it.inShoppingList && !it.portion.ingredient.optional &&
            it.status in listOf(AvailabilityStatus.PARTIAL, AvailabilityStatus.MISSING)
    }
    val cookingHere = activeSession?.takeIf { it.status == CookingStatus.ACTIVE }?.recipeId == recipe.id
    val state = RecipeDetailUi(
        card = RecipeCardUi(
            recipe.title, recipe.prepTimeMinutes + recipe.cookTimeMinutes, servings, recipe.difficulty, recipe.image,
            favorite = library?.value?.favorite == true,
        ),
        activeMinutes = recipe.activeTimeMinutes,
        portions = servings,
        basePortions = recipe.servings,
        availability = if (live == null && stockError == null) RecipeAvailabilityUi(state = CuisineContentState.LOADING)
        else live.toAvailabilityUi(verification),
        stockAsOf = live?.stockAsOf,
        checklist = IngredientChecklistUi(rows, servings, stockKnown = live != null),
        steps = recipe.steps.sortedBy { it.order }.map { it.title },
        favorite = library?.value?.favorite == true,
        allergenNotice = recipe.allergenCheck == AllergenCheck.NOT_GUARANTEED,
        cookingHere = cookingHere,
        busy = busy,
        error = error?.let { stringResource(it.error.label()) },
        stockError = stockError?.takeIf { live != null }?.let { stringResource(it.error.label()) },
    )

    val addedMessage = pluralStringResource(R.plurals.food_added_to_shopping, pending.size, pending.size)
    val undoLabel = stringResource(R.string.food_undo)
    val plannedMessage = stringResource(R.string.food_added_to_planning)
    val actions = RecipeDetailActions(
        onBack = onBack,
        onFavorite = { favorite -> write({ repo.updateLibrary(recipeId, UpdateRecipeLibraryRequest(favorite = favorite)) }) },
        onPortions = { selectedServings = it },
        onToggleIngredient = { id, value -> checked = if (value) checked + id else checked - id },
        onAddMissing = {
            val operationId = UUID.randomUUID().toString()
            write({
                repo.addRecipeShoppingItems(recipe.id, RecipeShoppingRequest(
                    operationId, recipe.version, servings, pending.map { it.id },
                ))
            }) {
                scope.launch {
                    if (snackbars.showSnackbar(addedMessage, undoLabel, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                        repo.undoShopping(ShoppingUndoRequest(UUID.randomUUID().toString(), operationId))
                    }
                }
            }
        },
        onAdapt = { onOpen(AppRoute.RecipeGenerate(recipeId, servings)) },
        onPlan = { sheet = RecipeSheet.PLAN },
        onOptions = { sheet = RecipeSheet.OPTIONS },
        onStart = {
            val current = activeSession?.takeIf { it.status == CookingStatus.ACTIVE }
            if (current != null && current.recipeId == recipe.id) onOpen(AppRoute.CookingSession(current.id)) else start()
        },
        onRetry = { refresh++ },
    )

    val overlay: @Composable () -> Unit = {
        val conflict = activeSession?.takeIf { it.id == conflictId && it.status == CookingStatus.ACTIVE }
        if (conflict != null) {
            CookingConflictSheet(conflict.toActiveCookingUi(), onResume = {
                conflictId = null
                onOpen(AppRoute.CookingSession(conflict.id))
            }, onReplace = { confirmReplace = true }, onClose = {
                if (!busy) conflictId = null
            }, busy = busy)
            if (confirmReplace) {
                AbandonCookingDialog(replacing = true, busy = busy, onDismiss = { confirmReplace = false },
                    onConfirm = { start(conflict.id) })
            }
        }
        when (sheet) {
            RecipeSheet.PLAN -> PlanRecipeSheet(
                today = LocalDate.now(), servings = servings, busy = busy, error = error,
                occupant = { date, meal ->
                    val week by remember(repo, date) { repo.mealWeek(mondayOf(date).toString()) }.collectAsState(initial = null)
                    LaunchedEffect(repo, date) { repo.refreshMealWeek(mondayOf(date).toString()) }
                    week?.value?.slots?.firstOrNull { it.date == date.toString() && it.meal == meal }?.recipe?.title
                },
                onClose = { sheet = null },
            ) { date, meal ->
                val weekStart = mondayOf(date).toString()
                write({
                    val revision = repo.mealWeek(weekStart).first()?.value?.revision
                        ?: (repo.refreshMealWeek(weekStart) as? FoodResult.Success)?.value?.revision
                        ?: return@write FoodResult.Failure(FoodError.OFFLINE)
                    repo.putMealSlot(MealSlotKey(date.toString(), meal), PlanningPutRequest(
                        UUID.randomUUID().toString(), revision, recipe.id, recipe.version, servings,
                    ))
                }) {
                    sheet = null
                    scope.launch { snackbars.showSnackbar(plannedMessage) }
                }
            }
            RecipeSheet.OPTIONS -> FoodSheet(stringResource(R.string.food_options), { sheet = null }) {
                FoodAction(R.string.food_duplicate, enabled = !busy) {
                    write({ repo.duplicate(recipeId) }) {
                        sheet = null
                        onOpen(AppRoute.RecipeDetail(it.recipe.id))
                    }
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
                    FoodTextField(notes, { notes = it.take(2000) }, label = stringResource(R.string.food_notes), singleLine = false, maxLines = 6)
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

    RecipeDetailContent(
        state, actions, snackbars,
        // The banner repeats the main action when this recipe is the one cooking.
        dock = LocalCookingDock.current.takeUnless { cookingHere },
        overlay = overlay,
    )
}

private enum class RecipeSheet { PLAN, OPTIONS, RATING, DELETE }

/** Choose a day of the coming week and a meal; an occupied slot is named so replacing it is a visible choice. */
@Composable
internal fun PlanRecipeSheet(
    today: LocalDate,
    servings: Int,
    busy: Boolean,
    error: FoodResult.Failure?,
    occupant: @Composable (LocalDate, Meal) -> String?,
    onClose: () -> Unit,
    onConfirm: (LocalDate, Meal) -> Unit,
) {
    var day by rememberSaveable { mutableStateOf(today.toString()) }
    var meal by rememberSaveable { mutableStateOf(Meal.DINNER) }
    val date = LocalDate.parse(day)
    FoodSheet(stringResource(R.string.food_plan_title), onClose) {
        FoodChoices(
            stringResource(R.string.food_plan_day),
            (0L..6L).map { today.plusDays(it) }.map { it.toString() to dayLabel(it, today) },
            day,
        ) { day = it }
        FoodChoices(
            stringResource(R.string.food_plan_meal),
            listOf(Meal.LUNCH to stringResource(R.string.food_meal_lunch), Meal.DINNER to stringResource(R.string.food_meal_dinner)),
            meal,
        ) { meal = it }
        FoodSupporting(stringResource(R.string.food_plan_servings, portionLabel(servings)))
        occupant(date, meal)?.let { FoodSupporting(stringResource(R.string.food_plan_replaces, it)) }
        FoodErrorText(error)
        FoodAction(R.string.food_plan_confirm, primary = true, enabled = !busy) { onConfirm(date, meal) }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.food_today)
    today.plusDays(1) -> stringResource(R.string.food_tomorrow)
    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.FRANCE))
        .replaceFirstChar { it.titlecase(java.util.Locale.FRANCE) }
}
