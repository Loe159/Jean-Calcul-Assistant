package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun defaultGenerationParams(servings: Int = 2) = GenerationParams(
    servings, emptyList(), emptyList(), emptyList(), prioritizePantry = true, useExpiringFirst = true,
)

private val generateModeLabels = listOf(R.string.food_mode_pantry, R.string.food_mode_craving, R.string.food_mode_surprise, R.string.food_mode_import)

@Composable
internal fun RecipeGenerateScreen(recipeId: String?, initialServings: Int?, onBack: () -> Unit, onRecipe: (String) -> Unit) {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    var jobId by rememberSaveable { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var lastPayload by rememberSaveable { mutableStateOf("") }
    var lastRequestId by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(GenerateMode.FROM_PANTRY) }
    var prompt by rememberSaveable(mode) { mutableStateOf("") }
    var servings by rememberSaveable { mutableIntStateOf(initialServings ?: 2) }
    var time by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var meal by rememberSaveable { mutableStateOf<MealType?>(null) }
    var budget by rememberSaveable { mutableStateOf<Budget?>(null) }
    var cuisine by rememberSaveable { mutableStateOf("") }
    var exclusions by rememberSaveable { mutableStateOf("") }
    var allergies by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var prioritize by rememberSaveable { mutableStateOf(true) }
    var expiring by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(jobId, retry, cancelling) {
        val id = jobId ?: return@LaunchedEffect
        if (cancelling || job?.takeIf { it.jobId == id }?.state?.isActive() == false) return@LaunchedEffect
        error = null
        while (true) {
            when (val result = repo.job(id)) {
                is FoodResult.Success -> {
                    job = result.value
                    if (!result.value.state.isActive()) return@LaunchedEffect
                }
                is FoodResult.Failure -> {
                    error = result
                    return@LaunchedEffect
                }
            }
            delay(1_000)
        }
    }
    val adapt = recipeId != null
    val title = stringResource(if (adapt) R.string.food_adapt else R.string.food_generate)
    FoodPage(title, onBack) {
        FoodErrorText(error)
        if (jobId != null) {
            val current = job
            if (current == null || current.state.isActive()) {
                FoodSection {
                    FoodHeading(stringResource(current?.stageLabel() ?: R.string.food_job_queued))
                    if (error != null) FoodAction(R.string.food_retry) { retry++ }
                    FoodAction(R.string.food_job_cancel, enabled = !cancelling) {
                        val id = jobId ?: return@FoodAction
                        cancelling = true
                        scope.launch {
                            when (val result = repo.cancelJob(id)) {
                                is FoodResult.Success -> job = result.value
                                is FoodResult.Failure -> error = result
                            }
                            cancelling = false
                        }
                    }
                }
            } else {
                when (current.state) {
                    JobState.SUCCEEDED -> {
                        val recipes = current.result?.recipes.orEmpty()
                        if (recipes.isEmpty()) ListEmptyState(stringResource(R.string.food_no_recipes))
                        recipes.forEach { RecipeResultRow(it.recipe, it.availability) { onRecipe(it.recipe.id) } }
                    }
                    JobState.CANCELLED -> FoodSupporting(stringResource(R.string.food_job_cancelled))
                    JobState.FAILED -> {
                        val label = if (current.error?.code == "AI_DISABLED") R.string.food_ai_disabled else R.string.food_job_failed
                        FoodErrorMessage(stringResource(label))
                    }
                    else -> Unit
                }
                FoodAction(R.string.food_retry) {
                    jobId = null
                    job = null
                    lastRequestId = ""
                    error = null
                }
            }
            return@FoodPage
        }

        if (!adapt) {
            FoodChoices(title, GenerateMode.entries.map { it to stringResource(generateModeLabels[it.ordinal]) }, mode) { mode = it }
        }
        if (adapt || mode == GenerateMode.CRAVING || mode == GenerateMode.IMPORT_TEXT) {
            val label = when {
                adapt -> R.string.food_adapt_request
                mode == GenerateMode.IMPORT_TEXT -> R.string.food_import_text
                else -> R.string.food_mode_craving
            }
            FoodTextField(prompt, { prompt = it.take(if (mode == GenerateMode.IMPORT_TEXT && !adapt) 12000 else 1200) },
                label = stringResource(label), singleLine = false, minLines = 2, maxLines = 6)
        }
        PortionSelector(servings) { servings = it }
        if (!adapt) {
            FoodNumberField(time, { time = it }, R.string.food_max_time)
            EnumChoices(R.string.food_difficulty, Difficulty.entries, R.array.food_difficulties, difficulty) { difficulty = it }
            EnumChoices(R.string.food_meal_type, MealType.entries, R.array.food_meal_types, meal) { meal = it }
            EnumChoices(R.string.food_budget, Budget.entries, R.array.food_budgets, budget) { budget = it }
            FoodTextField(cuisine, { cuisine = it }, label = stringResource(R.string.food_cuisine))
            ListField(exclusions, { exclusions = it }, R.string.food_exclusions)
            ListField(allergies, { allergies = it }, R.string.food_allergies)
            EquipmentChoices(equipment) { equipment = ArrayList(it) }
            SwitchPreference(title = stringResource(R.string.food_prioritize_pantry), checked = prioritize, onCheckedChange = { prioritize = it })
            SwitchPreference(title = stringResource(R.string.food_prioritize_expiring), checked = expiring, onCheckedChange = { expiring = it })
        }
        val promptRequired = adapt || mode == GenerateMode.CRAVING || mode == GenerateMode.IMPORT_TEXT
        val parsedTime = time.toIntOrNull()?.takeIf { it in 1..1440 }
        FoodButton(title, primary = true, enabled = !busy && (!promptRequired || prompt.isNotBlank()) &&
            (adapt || time.isEmpty() || parsedTime != null), onClick = {
            val params = GenerationParams(servings, exclusions.listValues(), allergies.listValues(),
                equipment.map(Equipment::valueOf), prioritize, expiring, parsedTime, difficulty, meal,
                cuisine.trim().ifEmpty { null }, budget)
            val generation = GenerateRequest("", mode, params, prompt.trim().takeIf { mode == GenerateMode.CRAVING },
                prompt.trim().takeIf { mode == GenerateMode.IMPORT_TEXT })
            val adaptation = AdaptRequest("", prompt.trim(), servings)
            val payload = if (adapt) FoodContract.json.encodeToString(adaptation) else FoodContract.json.encodeToString(generation)
            if (lastPayload != payload || lastRequestId.isEmpty()) {
                lastPayload = payload
                lastRequestId = requestId()
            }
            busy = true
            error = null
            scope.launch {
                val result = if (recipeId == null) {
                    when (val created = repo.generate(generation.copy(requestId = lastRequestId))) {
                        is FoodResult.Success -> FoodResult.Success(created.value.jobId)
                        is FoodResult.Failure -> created
                    }
                } else {
                    when (val created = repo.adapt(recipeId, adaptation.copy(requestId = lastRequestId))) {
                        is FoodResult.Success -> FoodResult.Success(created.value.jobId)
                        is FoodResult.Failure -> created
                    }
                }
                when (result) {
                    is FoodResult.Success -> jobId = result.value
                    is FoodResult.Failure -> error = result
                }
                busy = false
            }
        })
    }
}

internal fun JobState.isActive() = this == JobState.QUEUED || this == JobState.RUNNING

private fun Job.stageLabel() = if (state == JobState.QUEUED) R.string.food_job_queued else when (progress.stage) {
    JobStage.PANTRY -> R.string.food_job_pantry
    JobStage.LIBRARY -> R.string.food_job_library
    JobStage.GENERATING -> R.string.food_job_generating
    JobStage.VALIDATING -> R.string.food_job_validating
    JobStage.AVAILABILITY -> R.string.food_job_availability
}

@Composable
private fun <T : Enum<T>> EnumChoices(label: Int, values: List<T>, names: Int, selected: T?, onSelect: (T?) -> Unit) {
    val localized = stringArrayResource(names)
    FoodChoices(stringResource(label), listOf(null to stringResource(R.string.food_any)) +
        values.map { it to localized[it.ordinal] }, selected, onSelect)
}

@Composable
internal fun EquipmentChoices(selected: List<String>, onChange: (List<String>) -> Unit) {
    FoodSupporting(stringResource(R.string.food_equipment))
    val names = stringArrayResource(R.array.food_equipment_names)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Equipment.entries.forEach {
            val checked = it.name in selected
            FoodChoiceChip(names[it.ordinal], checked) { onChange(if (checked) selected - it.name else selected + it.name) }
        }
    }
}

@Composable
internal fun ListField(value: String, onChange: (String) -> Unit, label: Int) {
    FoodTextField(value, onChange, label = stringResource(label),
        placeholder = stringResource(R.string.food_comma_separated))
}

@Composable
private fun RecipeResultRow(recipe: RecipeDefinition, availability: Availability, onOpen: () -> Unit) {
    FoodSection {
        RecipePicture(recipe.image)
        FoodHeading(recipe.title)
        FoodSupporting(stringResource(availability.summary.category.label()))
        FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(availability.stockAsOf)))
        FoodAction(R.string.food_brief_open, onClick = onOpen)
    }
}
