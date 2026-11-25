package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.screens.food.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.math.BigDecimal
import kotlinx.coroutines.launch

internal data class CompletionDraft(
    val servingsMade: Int,
    val rating: Int?,
    val consumptions: List<CompleteConsumption>,
    val leftovers: Leftovers?,
)

internal data class CompletionRow(
    val ingredient: RecipeIngredient,
    val unit: UnitCode,
    val before: Double?,
    val amount: String,
    val selected: Boolean,
) {
    val parsed get() = amount.amountOrNull()?.takeIf { it > 0 }
    val tracked get() = ingredient.grocyProductId != null
    val valid get() = !selected || parsed != null
    val after get() = before?.let { (it - (if (selected) parsed ?: 0.0 else 0.0)).coerceAtLeast(0.0) }
    fun consumption() = ingredient.grocyProductId?.takeIf { selected }?.let { id ->
        parsed?.let { CompleteConsumption(ingredient.id, id, MeasuredQuantity(it, unit)) }
    }
}

internal fun foodAmountInput(amount: Double): String = BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()

internal fun CookingSession.completionRows(
    pantry: PantryResponse?,
    amounts: Map<String, String> = emptyMap(),
    selections: Map<String, Boolean> = emptyMap(),
): List<CompletionRow> {
    val stock = pantry?.items.orEmpty().groupBy { it.grocyProductId }
    return recipeSnapshot.portions(servings, ingredientOverrides).map { (ingredient, quantity) ->
        val compatible = stock[ingredient.grocyProductId].orEmpty().filter { it.unit.unitCode == quantity.unit }
        val before = compatible.takeIf { it.isNotEmpty() }?.sumOf { it.amount }
        val suggested = quantity.amount?.let { if (before == null) it else minOf(it, before) }
        CompletionRow(
            ingredient, quantity.unit, before,
            amounts[ingredient.id] ?: suggested?.let(::foodAmountInput).orEmpty(),
            ingredient.grocyProductId != null && (selections[ingredient.id] ?: ((suggested ?: 0.0) > 0)),
        )
    }
}

internal data class CompletionForm(
    val servings: String,
    val rating: String = "",
    val rows: List<CompletionRow> = emptyList(),
    val leftovers: String = "",
    val date: String = "",
    val selectedLocation: Long? = null,
    val locations: List<PantryLocation> = emptyList(),
) {
    val servingsMade get() = servings.toIntOrNull()?.takeIf { it > 0 }
    val stars get() = rating.toIntOrNull()?.takeIf { it in 1..5 }
    val restCount get() = if (leftovers.isBlank()) 0 else leftovers.toIntOrNull()
    val bestBefore get() = date.trim().dateOrNull()
    val validRestCount get() = restCount?.let { it in 0..(servingsMade ?: 0) } == true
    val validRating get() = rating.isBlank() || stars != null
    val validLocation get() = locations.any { it.id == selectedLocation }

    fun stockDraft(): CompletionDraft? {
        val made = servingsMade ?: return null
        val rest = restCount ?: return null
        if (!validRestCount || !validRating || rows.any { !it.valid }) return null
        val leftovers = if (rest > 0) {
            if (!validLocation) return null
            Leftovers(rest, bestBefore ?: return null, selectedLocation ?: return null)
        } else null
        return CompletionDraft(made, stars, rows.mapNotNull { it.consumption() }, leftovers)
    }

    fun exitDraft(initialServings: Int) =
        CompletionDraft(servingsMade ?: initialServings, stars, emptyList(), null)
}

internal data class CompletionReplay(
    val status: StockConsumptionStatus,
    val result: OperationResult?,
    val withoutStock: Boolean,
)

internal data class CompletionScreenState(
    val recipe: RecipeDefinition,
    val form: CompletionForm,
    val busy: Boolean = false,
    val favorite: Boolean = false,
    val favoriteBusy: Boolean = false,
    val cookLater: Boolean = false,
    val error: FoodResult.Failure? = null,
    val replay: CompletionReplay? = null,
)

internal data class CompletionActions(
    val servings: (String) -> Unit = {},
    val rating: (String) -> Unit = {},
    val amount: (String, String) -> Unit = { _, _ -> },
    val selection: (String, Boolean) -> Unit = { _, _ -> },
    val leftovers: (String) -> Unit = {},
    val date: (String) -> Unit = {},
    val location: (Long) -> Unit = {},
    val favorite: (Boolean) -> Unit = {},
    val cookLater: (Boolean) -> Unit = {},
    val confirmStock: (CompletionDraft) -> Unit = {},
    val exit: () -> Unit = {},
)

@Composable
internal fun CookingCompletionDestination(session: CookingSession, onBack: () -> Unit, onCookLater: (String) -> Unit) {
    val runtime = foodRuntime()
    val repo = runtime.repository
    val scope = rememberCoroutineScope()
    val recipe = session.recipeSnapshot
    val pantry by remember(repo) { repo.pantry() }.collectAsState(initial = null)
    val library by remember(repo, session.recipeId) { repo.library(session.recipeId) }.collectAsState(initial = null)
    val result by remember(repo, session.id) { repo.completionResult(session.id) }.collectAsState(initial = null)
    val request by remember(repo, session.id) { repo.completionRequest(session.id) }.collectAsState(initial = null)
    var servings by rememberSaveable { mutableStateOf(session.servings.toString()) }
    var rating by rememberSaveable { mutableStateOf("") }
    var leftovers by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf<Long?>(null) }
    var amounts by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    var selections by rememberSaveable { mutableStateOf(hashMapOf<String, Boolean>()) }
    var busy by remember { mutableStateOf(false) }
    var favoriteBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    val laterKey = "cook-later:${session.recipeId}"
    var cookLater by remember(laterKey) { mutableStateOf(runtime.cookingPreferences.getBoolean(laterKey, false)) }
    val complete = session.status == CookingStatus.COMPLETED
    LaunchedEffect(repo, complete) { if (!complete) repo.refreshPantry() }

    val locations = pantry?.value?.locations.orEmpty()
    val selectedLocation = location ?: locations.firstOrNull {
        it.name.contains("frigo", true) || it.name.contains("fridge", true) || it.name.contains("réfrig", true)
    }?.id
    val rows = remember(session, pantry, amounts, selections) { session.completionRows(pantry?.value, amounts, selections) }
    val form = CompletionForm(request?.servingsMade?.toString() ?: servings, request?.rating?.toString() ?: rating, rows,
        leftovers, date, selectedLocation, locations)
    fun confirm(draft: CompletionDraft) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                repo.completeSession(session.id, draft.servingsMade, draft.rating, draft.consumptions, draft.leftovers)
            } finally {
                busy = false
            }
        }
    }
    val exit: () -> Unit = {
        if (complete) onBack() else confirm(form.exitDraft(session.servings))
    }
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = !complete,
        onBackCompleted = exit,
    )
    CookingCompletionScreen(
        CompletionScreenState(
            recipe, form, busy, library?.value?.favorite == true, favoriteBusy, cookLater, error,
            if (complete) CompletionReplay(session.stockConsumptionStatus, result,
                request?.let { it.consumptions.isEmpty() && it.leftovers == null } == true) else null,
        ),
        CompletionActions(
            servings = { servings = it },
            rating = { rating = it },
            amount = { id, value -> amounts = HashMap(amounts).apply { put(id, value) } },
            selection = { id, checked ->
                selections = HashMap(selections).apply { put(id, checked) }
                val row = rows.first { it.ingredient.id == id }
                if (row.amount.isNotBlank()) amounts = HashMap(amounts).apply { put(id, row.amount) }
            },
            leftovers = { leftovers = it },
            date = { date = it },
            location = { location = it },
            favorite = { value ->
                if (!favoriteBusy) {
                    favoriteBusy = true
                    error = null
                    runtime.launch {
                        try {
                            error = repo.updateLibrary(recipe.id, UpdateRecipeLibraryRequest(favorite = value)) as? FoodResult.Failure
                        } finally {
                            favoriteBusy = false
                        }
                    }
                }
            },
            cookLater = { value ->
                cookLater = value
                runtime.cookingPreferences.edit().putBoolean(laterKey, value).apply()
                if (value) onCookLater(recipe.id)
            },
            confirmStock = ::confirm,
            exit = exit,
        ),
    )
}

@Composable
internal fun CookingCompletionScreen(state: CompletionScreenState, actions: CompletionActions) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val scroll = rememberScrollState()
    LaunchedEffect(state.replay != null) { scroll.scrollTo(0) }
    Scaffold(Modifier.imePadding(), containerColor = colors.backgroundWithCard) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).safeDrawingPadding()
            .padding(horizontal = 16.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CuisineIconButton(COUIIcons.Back, stringResource(R.string.food_back), actions.exit,
                            Modifier.align(Alignment.TopStart), enabled = !state.busy, neutralFocus = true)
                        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = 1f, size = 90.dp, strokeWidth = 4.dp,
                                colors = ProgressIndicatorDefaults.circularProgressIndicatorColors(foregroundColor = colors.green))
                            Icon(COUIIcons.Ok, null, Modifier.size(32.dp), tint = cuisineTint(colors.green))
                        }
                    }
                    Text(stringResource(R.string.food_recipe_completed), Modifier.semantics { heading() },
                        style = styles.displayS)
                }
                FoodSection {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RecipePicture(state.recipe.image, Modifier.size(72.dp).clip(SquircleShape(16.dp)), placeholder = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            FoodHeading(state.recipe.title)
                            val count = state.form.servingsMade ?: state.recipe.servings
                            FoodSupporting(pluralStringResource(R.plurals.food_servings_prepared, count, count))
                            FoodSupporting(stringResource(R.string.food_completion_steps_time,
                                state.recipe.steps.size, state.recipe.prepTimeMinutes + state.recipe.cookTimeMinutes))
                            if (state.replay != null) state.form.stars?.let {
                                FoodSupporting(stringResource(R.string.food_saved_rating, it))
                            }
                        }
                    }
                }
                if (state.replay != null) {
                    CompletionStatus(state.recipe, state.replay)
                } else {
                    CompletionStockForm(state.form, state.busy, actions)
                }
                CardListGroup {
                    item {
                        FoodSwitchRow(stringResource(R.string.food_favorite_recipe), state.favorite,
                            enabled = !state.favoriteBusy, onChange = actions.favorite)
                    }
                    item {
                        FoodSwitchRow(stringResource(R.string.food_cook_later), state.cookLater, onChange = actions.cookLater)
                    }
                }
                FoodErrorText(state.error)
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                FoodAction(if (state.replay == null) R.string.food_finish_without_stock else R.string.food_finish_short,
                    primary = state.replay != null, enabled = !state.busy, onClick = actions.exit)
            }
        }
    }
}

@Composable
private fun CompletionStockForm(form: CompletionForm, busy: Boolean, actions: CompletionActions) {
    FoodSection {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CuisineIcons.Fridge, null, Modifier.size(20.dp))
            FoodHeading(stringResource(R.string.food_stock_update_question))
        }
        val colors = COUITheme.colorScheme
        CuisineBadge(stringResource(R.string.food_stock_update_before_confirmation),
            icon = CuisineIcons.Warning, ink = cuisineTint(colors.orange), fill = colors.containerOrange)
        FoodSupporting(stringResource(R.string.food_consumed))
        form.rows.forEach { row ->
            key(row.ingredient.id) {
                HorizontalDivider()
                CompletionIngredient(row, busy, actions)
            }
        }
        FoodNumberField(form.servings, actions.servings, R.string.food_servings_made,
            errorLabel = R.string.food_invalid_servings.takeIf { form.servingsMade == null })
        FoodNumberField(form.leftovers, actions.leftovers, R.string.food_leftovers,
            errorLabel = R.string.food_invalid_leftovers.takeIf { !form.validRestCount })
        if ((form.restCount ?: 0) > 0) {
            FoodTextField(form.date, actions.date, stringResource(R.string.food_date),
                errorLabel = R.string.food_invalid_date.takeIf { form.bestBefore == null })
            if (form.locations.isEmpty()) {
                FoodErrorMessage(stringResource(R.string.food_no_leftover_location))
            } else {
                FoodChoices(stringResource(R.string.food_location), form.locations.map { (it.id as Long?) to it.name },
                    form.selectedLocation, { it?.let(actions.location) })
            }
        }
        FoodNumberField(form.rating, actions.rating, R.string.food_rating,
            errorLabel = R.string.food_invalid_rating.takeUnless { form.validRating })
        val draft = form.stockDraft()
        FoodAction(R.string.food_stock_update, primary = true, enabled = !busy && draft != null) {
            draft?.let(actions.confirmStock)
        }
    }
}

@Composable
private fun CompletionIngredient(row: CompletionRow, busy: Boolean, actions: CompletionActions) {
    val id = row.ingredient.id
    val before = row.before
    val summary = when {
        !row.tracked -> stringResource(R.string.food_not_tracked)
        before == null -> stringResource(R.string.food_stock_estimate_unavailable)
        else -> stringResource(R.string.food_stock_before_after,
            FoodQuantities.formatFr(Quantity(before, row.unit)),
            FoodQuantities.formatFr(Quantity(row.after, row.unit)))
    }
    val stack = LocalDensity.current.fontScale > 1.3f
    val description = stringResource(R.string.food_consumed_amount, row.ingredient.name, unitName(row.unit))
    val invalid = if (row.valid) null else stringResource(R.string.food_positive_consumption)
    val selection: @Composable (Modifier) -> Unit = { modifier ->
        val toggle = ToggleableState(row.selected)
        BasicComponent(
            modifier.heightIn(min = 56.dp).semantics { toggleableState = toggle },
            startAction = { Checkbox(toggle, onClick = null, enabled = row.tracked && !busy) },
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            enabled = row.tracked && !busy,
            role = Role.Checkbox,
            onClick = { actions.selection(id, !row.selected) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.ingredient.name, style = COUITheme.textStyles.headlineXS)
                FoodSupporting(summary)
            }
        }
    }
    val field: @Composable (Modifier) -> Unit = { modifier ->
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CouiTextField(
                row.amount, { actions.amount(id, it) }, Modifier.weight(1f), enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), showClearButton = false,
                fieldModifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = description
                    if (invalid != null) error(invalid)
                },
            )
            FoodSupporting(unitName(row.unit))
        }
    }
    if (row.selected && stack) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            selection(Modifier.fillMaxWidth())
            field(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selection(Modifier.weight(1f))
            if (row.selected) field(Modifier.width(112.dp))
        }
    }
    if (invalid != null) FoodErrorMessage(invalid)
}

@Composable
private fun CompletionStatus(recipe: RecipeDefinition, replay: CompletionReplay) {
    FoodSection {
        FoodHeading(stringResource(R.string.food_completion_replay))
        if (replay.withoutStock) {
            FoodSupporting(stringResource(R.string.food_stock_unchanged))
            FoodSupporting(stringResource(when (replay.status) {
                StockConsumptionStatus.SUCCEEDED -> R.string.food_history_recorded
                StockConsumptionStatus.PARTIAL -> R.string.food_history_partial
                StockConsumptionStatus.FAILED -> R.string.food_history_failed
                StockConsumptionStatus.UNCERTAIN -> R.string.food_uncertain
                else -> R.string.food_history_pending
            }))
        } else if (replay.result != null) {
            OperationResultLines(replay.result, recipe.ingredients.associate { it.id to it.name })
        } else {
            FoodSupporting(stringResource(when (replay.status) {
                StockConsumptionStatus.SUCCEEDED -> R.string.food_succeeded
                StockConsumptionStatus.PARTIAL -> R.string.food_partial
                StockConsumptionStatus.FAILED -> R.string.food_failed
                StockConsumptionStatus.UNCERTAIN -> R.string.food_uncertain
                else -> R.string.food_pending
            }))
        }
    }
}
