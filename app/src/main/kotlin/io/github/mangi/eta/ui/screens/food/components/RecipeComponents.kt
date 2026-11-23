package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.IngredientPortion
import io.github.mangi.eta.ui.screens.food.label
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Text

@Composable
internal fun RecipeHero(recipe: RecipeDefinition, servings: Int, availability: Availability?) {
    FoodSection {
        RecipePicture(recipe.image)
        FoodHeading(recipe.title)
        FoodSupporting(stringResource(R.string.food_recipe_times, recipe.prepTimeMinutes + recipe.cookTimeMinutes, recipe.activeTimeMinutes))
        FoodSupporting(stringArrayResource(R.array.food_difficulties)[recipe.difficulty.ordinal])
        FoodSupporting(stringResource(R.string.food_servings, servings))
        if (recipe.description.isNotBlank()) Text(recipe.description)
        if (availability == null) {
            FoodSupporting(stringResource(R.string.food_stock_unknown))
        } else {
            FoodSupporting(stringResource(availability.summary.category.label()))
            FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(availability.stockAsOf)))
        }
    }
}

@Composable
internal fun PortionSelector(servings: Int, onChange: (Int) -> Unit) {
    FoodChoices(stringResource(R.string.food_servings, servings), listOf(2, 3, 4, 6).map { it to it.toString() }, servings, onChange)
    val less = stringResource(R.string.food_less_servings)
    val more = stringResource(R.string.food_more_servings)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FoodButton("−", { onChange(servings - 1) }, enabled = servings > 1,
            modifier = Modifier.semantics { contentDescription = less })
        Text(servings.toString())
        FoodButton("+", { onChange(servings + 1) }, enabled = servings < 12,
            modifier = Modifier.semantics { contentDescription = more })
    }
}

@Composable
internal fun IngredientChecklist(ingredients: List<IngredientPortion>, availability: Availability? = null, showStock: Boolean = true) {
    val stock = availability?.items.orEmpty().associateBy { it.ingredientId }
    for (line in ingredients) {
        val ingredient = line.ingredient
        var checked by rememberSaveable(ingredient.id) { mutableStateOf(false) }
        val item = stock[ingredient.id]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val name = ingredient.name
            Checkbox(state = androidx.compose.ui.state.ToggleableState(checked), onClick = { checked = !checked },
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = name })
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.food_consumed_amount, name, line.display()))
                ingredient.preparation?.let { FoodSupporting(it) }
                if (ingredient.optional) FoodSupporting(stringResource(R.string.food_optional))
                if (showStock) IngredientAvailability(item)
                item?.substitutionAvailable?.let { SubstitutionSuggestion(it.name) }
                ingredient.substitutions.filterNot { it.name == item?.substitutionAvailable?.name }.forEach {
                    SubstitutionSuggestion(it.name, it.note)
                }
            }
        }
    }
}

internal fun availabilityLabel(item: AvailabilityItem?): Int = when (item?.status) {
    AvailabilityStatus.AVAILABLE -> R.string.food_known_available
    AvailabilityStatus.NOT_TRACKED -> R.string.food_not_tracked
    AvailabilityStatus.PARTIAL, AvailabilityStatus.MISSING, AvailabilityStatus.OPTIONAL_MISSING ->
        if (item.missing != null) R.string.food_missing else R.string.food_stock_unknown
    else -> R.string.food_stock_unknown
}

@Composable
internal fun IngredientAvailability(item: AvailabilityItem?) {
    val label = availabilityLabel(item)
    FoodSupporting(if (label == R.string.food_missing) {
        val missing = requireNotNull(item?.missing)
        stringResource(label, FoodQuantities.formatFr(Quantity(missing.amount, missing.unit)))
    } else stringResource(label))
}

@Composable
internal fun SubstitutionSuggestion(name: String, note: String? = null) {
    FoodSupporting(stringResource(R.string.food_substitution, name))
    if (!note.isNullOrBlank()) FoodSupporting(note)
}

internal fun stockDate(value: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.FRANCE)
        .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(value))
}.getOrDefault(value)
