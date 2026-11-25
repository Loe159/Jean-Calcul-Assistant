package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Months
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal data class PlanningTabUi(
    val weekStart: LocalDate,
    val days: List<ProjectedMealDay> = emptyList(),
    val today: LocalDate,
    val state: CuisineContentState = CuisineContentState.LOADING,
    val error: String? = null,
    val moving: MealSlotKey? = null,
    val summary: ShoppingSummary? = null,
    val busy: Boolean = false,
) {
    val plannedCount: Int get() = days.sumOf { day -> day.slots.count { it.meal != null } }
    fun slot(key: MealSlotKey): MealSlot? = days.flatMap { it.slots }.firstOrNull { it.key == key }?.meal
}

internal class PlanningTabActions(
    val onWeek: (Long) -> Unit,
    val onSlot: (ProjectedMealSlot) -> Unit,
    val onCancelMove: () -> Unit,
    val onGenerate: () -> Unit,
    val onAddSummary: () -> Unit,
    val onRetry: () -> Unit,
)

internal fun Meal.toMealType() = if (this == Meal.LUNCH) MealType.LUNCH else MealType.DINNER

@Composable
internal fun ColumnScope.PlanningTabContent(state: PlanningTabUi, actions: PlanningTabActions) {
    PlanningHeader(state, actions)
    when {
        state.state == CuisineContentState.LOADING && state.days.isEmpty() -> repeat(4) { CuisineLoading() }
        state.state == CuisineContentState.ERROR && state.days.isEmpty() -> CuisineMessage(
            stringResource(R.string.food_planning_unavailable), detail = state.error, icon = CuisineIcons.Warning,
            isError = true, onRetry = actions.onRetry,
        )
        else -> {
            if (state.error != null) FoodErrorMessage(state.error)
            state.moving?.let { key ->
                val title = state.slot(key)?.recipe?.title.orEmpty()
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(CuisineIcons.Swap, null, Modifier.size(20.dp), tint = cuisineTint(COUITheme.colorScheme.primary))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.food_move_hint, title), Modifier.weight(1f), style = COUITheme.textStyles.bodyM)
                        TextButton(stringResource(R.string.food_cancel), actions.onCancelMove, Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColorsBorderless(), material = ButtonMaterial.None)
                    }
                }
            }
            if (state.plannedCount == 0 && state.moving == null) {
                CuisineMessage(
                    stringResource(R.string.food_nothing_planned_week), detail = stringResource(R.string.food_nothing_planned_detail),
                    icon = COUIIcons.Months,
                )
            }
            WeekGrid(state, actions)
            if (state.plannedCount > 0) SummaryCard(state, actions.onAddSummary)
        }
    }
}

@Composable
private fun PlanningHeader(state: PlanningTabUi, actions: PlanningTabActions) {
    val end = state.weekStart.plusDays(6)
    val range = if (state.weekStart.month == end.month) {
        "${state.weekStart.dayOfMonth} – ${end.format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE))}"
    } else {
        "${state.weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE))} – ${end.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE))}"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CuisineIconButton(COUIIcons.ChevronBackward, stringResource(R.string.food_previous_week), { actions.onWeek(-1) })
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.food_week_of, range), style = COUITheme.textStyles.headlineS, textAlign = TextAlign.Center)
            FoodSupporting(pluralStringResource(R.plurals.food_meals_planned, state.plannedCount, state.plannedCount))
        }
        CuisineIconButton(COUIIcons.ChevronForward, stringResource(R.string.food_next_week), { actions.onWeek(1) })
    }
    Button(
        actions.onGenerate, Modifier.fillMaxWidth(), enabled = !state.busy && state.days.isNotEmpty(), minHeight = 48.dp,
        colors = ButtonDefaults.buttonColors(color = COUITheme.colorScheme.primary.copy(alpha = 0.15f)),
    ) {
        Icon(CuisineIcons.Sparkle, null, Modifier.size(18.dp), tint = cuisineTint(COUITheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.food_generate_planning), style = COUITheme.textStyles.buttonM, color = cuisineTint(COUITheme.colorScheme.primary))
    }
}

@Composable
private fun WeekGrid(state: PlanningTabUi, actions: PlanningTabActions) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    if (!stacked) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(56.dp))
            listOf(R.string.food_meal_lunch, R.string.food_meal_dinner).forEach {
                Text(stringResource(it), Modifier.weight(1f), style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary, textAlign = TextAlign.Center)
            }
        }
    }
    state.days.forEach { day ->
        val past = day.date.isBefore(state.today)
        val label: @Composable (Modifier) -> Unit = { modifier -> DayLabel(day, modifier) }
        val slots: @Composable (Modifier) -> Unit = { modifier ->
            day.slots.forEach { projected ->
                val move = when {
                    state.moving == null -> MealSlotMove.NONE
                    state.moving == projected.key -> MealSlotMove.SOURCE
                    else -> MealSlotMove.TARGET
                }
                MealSlotCard(
                    MealSlotUi(day.date, projected.key.meal.toMealType(), projected.meal?.let {
                        it.recipe.toCardUi().copy(portions = it.servings)
                    }),
                    onClick = { actions.onSlot(projected) }, modifier = modifier, moving = move,
                )
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().alpha(if (past) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StackedDayLabel(day)
                slots(Modifier.fillMaxWidth())
            }
        } else {
            // MealSlotCard subcomposes: no intrinsic measurement here, a shared minimum height instead.
            Row(
                Modifier.fillMaxWidth().alpha(if (past) 0.6f else 1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                label(Modifier.width(50.dp).heightIn(min = SlotHeight))
                slots(Modifier.weight(1f).heightIn(min = SlotHeight))
            }
        }
    }
}

private val SlotHeight = 84.dp

@Composable
private fun StackedDayLabel(day: ProjectedMealDay) {
    val colors = COUITheme.colorScheme
    Text(
        day.date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.FRANCE)).replaceFirstChar { it.titlecase(Locale.FRANCE) },
        Modifier.padding(top = 8.dp, start = 4.dp).semantics { heading() },
        style = COUITheme.textStyles.headlineXS, color = if (day.isToday) cuisineTint(colors.primary) else colors.labelPrimary,
    )
}

@Composable
private fun DayLabel(day: ProjectedMealDay, modifier: Modifier) {
    val colors = COUITheme.colorScheme
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = SquircleShape(16.dp),
        color = if (day.isToday) colors.primary else colors.container8) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val ink = if (day.isToday) colors.onPrimary else colors.labelSecondary
            Text(day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.FRANCE)).replaceFirstChar { it.titlecase(Locale.FRANCE) }.trimEnd('.'),
                style = COUITheme.textStyles.bodyXS, color = ink)
            Text(day.date.dayOfMonth.toString(), style = COUITheme.textStyles.headlineS,
                color = if (day.isToday) colors.onPrimary else colors.labelPrimary)
        }
    }
}

@Composable
private fun SummaryCard(state: PlanningTabUi, onAdd: () -> Unit) {
    val colors = COUITheme.colorScheme
    val toBuy = state.summary?.items.orEmpty().filter { it.toBuy > 0 }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = SquircleShape(14.dp), color = colors.green.copy(alpha = 0.18f)) {
                Icon(CuisineIcons.Cart, null, Modifier.padding(10.dp).size(20.dp), tint = cuisineTint(colors.green))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when {
                    state.summary == null -> FoodSupporting(stringResource(R.string.food_checking_stock))
                    toBuy.isEmpty() -> {
                        Text(stringResource(R.string.food_week_shopping_complete), style = COUITheme.textStyles.headlineXS)
                        FoodSupporting(stringResource(R.string.food_week_nothing_to_add))
                    }
                    else -> {
                        Text(pluralStringResource(R.plurals.food_week_to_buy, toBuy.size, toBuy.size), style = COUITheme.textStyles.headlineXS)
                        FoodSupporting(toBuy.take(3).joinToString(", ") { it.name.lowercase(Locale.FRANCE) } + if (toBuy.size > 3) "…" else "")
                    }
                }
            }
            if (toBuy.isNotEmpty()) {
                Button(onAdd, enabled = !state.busy, minHeight = 48.dp, minWidth = 48.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(stringResource(R.string.food_add), style = COUITheme.textStyles.buttonM)
                }
            }
        }
    }
}

/** Actions on a planned meal: open, cook tonight, move, replace, remove. */
@Composable
internal fun MealSlotSheet(
    slot: MealSlot,
    today: LocalDate,
    onOpen: () -> Unit,
    onMove: () -> Unit,
    onReplace: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val date = LocalDate.parse(slot.date)
    FoodSheet(slot.recipe.title, onClose) {
        FoodSupporting(listOf(
            date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)).replaceFirstChar { it.titlecase(Locale.FRANCE) },
            stringResource(if (slot.meal == Meal.LUNCH) R.string.food_meal_lunch else R.string.food_meal_dinner),
            portionLabel(slot.servings),
        ).joinToString(" · "))
        Card(Modifier.fillMaxWidth()) {
            CardListGroup {
                item { SheetRow(stringResource(if (date == today) R.string.food_cook_now else R.string.food_open_recipe), onClick = onOpen) }
                item { SheetRow(stringResource(R.string.food_move_meal), stringResource(R.string.food_move_meal_detail), onMove) }
                item { SheetRow(stringResource(R.string.food_replace_meal), onClick = onReplace) }
                item { SheetRow(stringResource(R.string.food_remove_meal), onClick = onRemove, destructive = true) }
            }
        }
    }
}

@Composable
private fun SheetRow(title: String, summary: String? = null, onClick: () -> Unit, destructive: Boolean = false) {
    BasicComponent(
        title = title, summary = summary, onClick = onClick,
        titleColor = BasicComponentDefaults.titleColor(color = if (destructive) COUITheme.colorScheme.error else COUITheme.colorScheme.labelPrimary),
    )
}

/** Choose a recipe for an empty (or replaced) slot; cookable suggestions first. */
@Composable
internal fun PickRecipeSheet(
    slot: MealSlotKey,
    suggestions: List<RecipeSuggestion>,
    library: List<RecipeSummary>,
    onPick: (RecipeSummary) -> Unit,
    onClose: () -> Unit,
) {
    val date = LocalDate.parse(slot.date)
    FoodLazySheet(stringResource(R.string.food_pick_recipe), onClose) {
        item {
            FoodSupporting(
                date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE)).replaceFirstChar { it.titlecase(Locale.FRANCE) } +
                    " · " + stringResource(if (slot.meal == Meal.LUNCH) R.string.food_meal_lunch else R.string.food_meal_dinner),
            )
        }
        val suggested = suggestions.map { it.recipe.id }.toSet()
        val ordered = suggestions.map { it.recipe to it.availability } +
            library.filter { it.id !in suggested }.libraryOrder().map { it to null }
        if (ordered.isEmpty()) item { CuisineMessage(stringResource(R.string.food_no_recipes), icon = CuisineIcons.Utensils) }
        ordered.forEach { (recipe, availability) ->
            item(key = recipe.id) {
                RecipeCard(recipe.toCardUi(), RecipeCardVariant.R, availability?.toAvailabilityUi(), onClick = { onPick(recipe) })
            }
        }
    }
}

/** Preview of a generated week: nothing is written before « Appliquer ». */
@Composable
internal fun GeneratePreviewSheet(
    preview: GeneratePreviewResult?,
    error: String?,
    busy: Boolean,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    FoodSheet(stringResource(R.string.food_generate_planning), onClose) {
        when {
            preview == null && error == null -> FoodLoading()
            preview != null && preview.slots.isEmpty() -> FoodSupporting(stringResource(R.string.food_generate_nothing))
            preview != null -> {
                FoodSupporting(stringResource(R.string.food_generate_detail))
                Card(Modifier.fillMaxWidth()) {
                    CardListGroup {
                        preview.slots.forEach { slot ->
                            item {
                                BasicComponent(
                                    title = slot.recipe.title,
                                    summary = LocalDate.parse(slot.date).format(DateTimeFormatter.ofPattern("EEEE d", Locale.FRANCE))
                                        .replaceFirstChar { it.titlecase(Locale.FRANCE) } + " · " +
                                        stringResource(if (slot.meal == Meal.LUNCH) R.string.food_meal_lunch else R.string.food_meal_dinner),
                                )
                            }
                        }
                    }
                }
            }
        }
        error?.let { FoodErrorMessage(it) }
        FoodAction(R.string.food_cancel, onClick = onClose)
        FoodAction(R.string.food_generate_apply, primary = true, enabled = !busy && preview?.slots?.isNotEmpty() == true, onClick = onApply)
    }
}

internal class PlanningTabHolder(val state: PlanningTabUi, val actions: PlanningTabActions, val overlay: @Composable () -> Unit)

private enum class PlanningSheet { SLOT, PICK, GENERATE }

@Composable
internal fun rememberPlanningTab(onOpenRecipe: (String, Int) -> Unit, snackbars: SnackbarHostState): PlanningTabHolder {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var weekOffset by rememberSaveable { mutableLongStateOf(0L) }
    val weekStart = mondayOf(today).plusWeeks(weekOffset)
    val week by remember(repo, weekStart) { repo.mealWeek(weekStart.toString()) }.collectAsState(initial = null)
    val library by remember(repo) { repo.recipes() }.collectAsState(initial = null)
    var refresh by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var summary by remember { mutableStateOf<ShoppingSummary?>(null) }
    var suggestions by remember { mutableStateOf<List<RecipeSuggestion>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var movingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var movingMeal by rememberSaveable { mutableStateOf<Meal?>(null) }
    var sheet by rememberSaveable { mutableStateOf<PlanningSheet?>(null) }
    var sheetDate by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetMeal by rememberSaveable { mutableStateOf<Meal?>(null) }
    var preview by remember { mutableStateOf<GeneratePreviewResult?>(null) }
    var previewFailure by remember { mutableStateOf<FoodResult.Failure?>(null) }

    LaunchedEffect(repo, weekStart, refresh) {
        failure = repo.refreshMealWeek(weekStart.toString()) as? FoodResult.Failure
        summary = (repo.planningShoppingSummary(weekStart.toString()) as? FoodResult.Success)?.value
    }
    LaunchedEffect(repo) {
        suggestions = (repo.suggest(SuggestRequest(SuggestMode.FROM_PANTRY, defaultGenerationParams())) as? FoodResult.Success)
            ?.value?.suggestions.orEmpty()
    }

    val moving = movingDate?.let { date -> movingMeal?.let { MealSlotKey(date, it) } }
    val current = week?.value
    val state = PlanningTabUi(
        weekStart = weekStart,
        days = current?.project(Instant.now()) ?: emptyList(),
        today = today,
        state = when {
            current != null -> CuisineContentState.READY
            failure != null -> CuisineContentState.ERROR
            else -> CuisineContentState.LOADING
        },
        error = failure?.let { stringResource(it.error.label()) },
        moving = moving,
        summary = summary,
        busy = busy,
    )

    fun <T> write(action: suspend () -> FoodResult<T>, done: (T) -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            when (val result = action()) {
                is FoodResult.Success -> {
                    failure = null
                    done(result.value)
                    summary = (repo.planningShoppingSummary(weekStart.toString()) as? FoodResult.Success)?.value
                }
                // A stale revision means someone else changed the week: reload it rather than guess.
                is FoodResult.Failure -> {
                    failure = result
                    repo.refreshMealWeek(weekStart.toString())
                }
            }
            busy = false
        }
    }
    val revision = current?.revision

    val addedMessage = stringResource(R.string.food_week_added_to_shopping)
    val actions = PlanningTabActions(
        onWeek = { weekOffset += it },
        onSlot = { projected ->
            val from = moving
            when {
                from != null && from != projected.key && revision != null -> write({
                    repo.moveMealSlot(PlanningMoveRequest(UUID.randomUUID().toString(), revision, from, projected.key,
                        if (projected.meal != null) PlanningMoveMode.SWAP else PlanningMoveMode.MOVE))
                }) { movingDate = null; movingMeal = null }
                from != null -> { movingDate = null; movingMeal = null }
                else -> {
                    sheetDate = projected.key.date
                    sheetMeal = projected.key.meal
                    sheet = if (projected.meal != null) PlanningSheet.SLOT else PlanningSheet.PICK
                }
            }
        },
        onCancelMove = { movingDate = null; movingMeal = null },
        onGenerate = {
            val rev = revision ?: return@PlanningTabActions
            preview = null
            previewFailure = null
            sheet = PlanningSheet.GENERATE
            scope.launch {
                when (val result = repo.previewMealWeek(weekStart.toString(), GeneratePreviewRequest(rev))) {
                    is FoodResult.Success -> preview = result.value
                    is FoodResult.Failure -> previewFailure = result
                }
            }
        },
        onAddSummary = {
            val s = summary ?: return@PlanningTabActions
            val rev = revision ?: return@PlanningTabActions
            write({ repo.addPlanningShoppingItems(weekStart.toString(), PlanningShoppingRequest(UUID.randomUUID().toString(), rev, s.summaryToken)) }) {
                scope.launch { snackbars.showSnackbar(addedMessage) }
            }
        },
        onRetry = { refresh++ },
    )

    val overlay: @Composable () -> Unit = {
        val key = sheetDate?.let { date -> sheetMeal?.let { MealSlotKey(date, it) } }
        when (sheet) {
            PlanningSheet.SLOT -> current?.slots?.firstOrNull { it.key == key }?.let { slot ->
                MealSlotSheet(
                    slot, today,
                    onOpen = { sheet = null; onOpenRecipe(slot.recipeId, slot.servings) },
                    onMove = { sheet = null; movingDate = slot.date; movingMeal = slot.meal },
                    onReplace = { sheet = PlanningSheet.PICK },
                    onRemove = {
                        val rev = revision ?: return@MealSlotSheet
                        write({ repo.deleteMealSlot(slot.key, PlanningDeleteRequest(UUID.randomUUID().toString(), rev)) }) { sheet = null }
                    },
                    onClose = { sheet = null },
                )
            } ?: LaunchedEffect(Unit) { sheet = null }
            PlanningSheet.PICK -> if (key != null) PickRecipeSheet(key, suggestions, library?.value?.recipes.orEmpty(), onPick = { recipe ->
                val rev = revision ?: return@PickRecipeSheet
                val servings = current?.slots?.firstOrNull { it.key == key }?.servings ?: recipe.servings
                write({
                    repo.putMealSlot(key, PlanningPutRequest(UUID.randomUUID().toString(), rev, recipe.id, recipe.version, servings))
                }) { sheet = null }
            }, onClose = { sheet = null })
            PlanningSheet.GENERATE -> GeneratePreviewSheet(
                preview, (previewFailure ?: failure.takeIf { busy })?.let { stringResource(it.error.label()) }, busy,
                onApply = {
                    val p = preview ?: return@GeneratePreviewSheet
                    write({ repo.applyMealWeek(weekStart.toString(), GenerateApplyRequest(UUID.randomUUID().toString(), p.revision, p.proposalId)) }) {
                        sheet = null
                    }
                },
                onClose = { sheet = null },
            )
            null -> Unit
        }
    }
    return PlanningTabHolder(state, actions, overlay)
}
