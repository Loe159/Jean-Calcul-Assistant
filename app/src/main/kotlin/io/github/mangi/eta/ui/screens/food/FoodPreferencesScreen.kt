package io.github.mangi.eta.ui.screens.food

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.components.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun FoodPreferencesScreen(onBack: () -> Unit) {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf<Preferences?>(null) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun load(action: suspend () -> FoodResult<Preferences>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val result = action()) {
                is FoodResult.Success -> preferences = result.value
                is FoodResult.Failure -> error = result
            }
            busy = false
        }
    }
    LaunchedEffect(repo) { load(repo::preferences) }
    FoodPage(stringResource(R.string.food_preferences), onBack) {
        FoodErrorText(error)
        val current = preferences
        if (current == null) {
            if (busy) FoodSupporting(stringResource(R.string.food_loading))
            else FoodAction(R.string.food_retry) { load(repo::preferences) }
            return@FoodPage
        }
        val declared = current.declared
        var allergies by rememberSaveable(declared.allergies) { mutableStateOf(declared.allergies.joinToString(", ")) }
        var exclusions by rememberSaveable(declared.excludedIngredients) { mutableStateOf(declared.excludedIngredients.joinToString(", ")) }
        var dietary by rememberSaveable(declared.dietary) { mutableStateOf(declared.dietary.joinToString(", ")) }
        var equipment by rememberSaveable(declared.equipment) { mutableStateOf(ArrayList(declared.equipment.map { it.name })) }
        var servings by rememberSaveable(declared.defaultServings) { mutableIntStateOf(declared.defaultServings) }
        var time by rememberSaveable(declared.maxWeeknightMinutes) { mutableStateOf(declared.maxWeeknightMinutes.toString()) }
        var cuisines by rememberSaveable(declared.cuisinesLiked) { mutableStateOf(declared.cuisinesLiked.joinToString(", ")) }
        FoodSection {
            ListField(allergies, { allergies = it }, R.string.food_allergies)
            ListField(exclusions, { exclusions = it }, R.string.food_exclusions)
            ListField(dietary, { dietary = it }, R.string.food_diet)
            EquipmentChoices(equipment) { equipment = ArrayList(it) }
            FoodSupporting(stringResource(R.string.food_default_servings))
            PortionSelector(servings) { servings = it }
            FoodNumberField(time, { time = it }, R.string.food_weeknight_minutes)
            ListField(cuisines, { cuisines = it }, R.string.food_liked_cuisines)
            val minutes = time.toIntOrNull()?.takeIf { it in 1..1440 }
            FoodAction(R.string.food_save, primary = true, enabled = !busy && minutes != null) {
                val update = DeclaredPreferences(allergies.listValues(), exclusions.listValues(), dietary.listValues(),
                    equipment.map(Equipment::valueOf), servings, requireNotNull(minutes), cuisines.listValues())
                load { repo.savePreferences(UpdatePreferencesRequest(update)) }
            }
        }
        if (current.inferred.isNotEmpty()) {
            FoodHeading(stringResource(R.string.food_inferred))
            current.inferred.forEach { inference ->
                FoodSection {
                    FoodHeading(inference.key)
                    FoodSupporting(inference.value.toString())
                    FoodSupporting(stringResource(R.string.food_confidence, (inference.confidence * 100).roundToInt()))
                    FoodAction(R.string.food_remove_inference, enabled = !busy) { load { repo.deleteInferred(inference.key) } }
                }
            }
        }
    }
}
