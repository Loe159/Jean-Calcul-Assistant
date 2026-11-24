package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.IngredientPortion
import io.github.mangi.eta.ui.screens.food.label
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.CheckboxLocation
import io.github.suqi8.coui.kmp.preference.CheckboxPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun RecipeHero(recipe: RecipeDefinition, availability: Availability?) {
    FoodSection {
        RecipePicture(recipe.image)
        Text(recipe.title, Modifier.semantics { heading() }, style = COUITheme.textStyles.headlineS)
        FoodSupporting(stringResource(R.string.food_recipe_times, recipe.prepTimeMinutes + recipe.cookTimeMinutes, recipe.activeTimeMinutes))
        FoodSupporting(stringArrayResource(R.array.food_difficulties)[recipe.difficulty.ordinal])
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
internal fun PortionSelector(servings: Int, title: Int = R.string.food_portions, onChange: (Int) -> Unit) {
    OverlayDropdownPreference(
        title = stringResource(title),
        items = (1..12).map(Int::toString),
        selectedIndex = servings - 1,
        modifier = Modifier.heightIn(min = 48.dp),
        onSelectedIndexChange = { onChange(it + 1) },
    )
}

@Composable
internal fun IngredientChecklist(ingredients: List<IngredientPortion>, availability: Availability? = null, showStock: Boolean = true) {
    val stock = availability?.items.orEmpty().associateBy { it.ingredientId }
    for (line in ingredients) {
        val ingredient = line.ingredient
        key(ingredient.id) {
            var checked by rememberSaveable { mutableStateOf(false) }
            val item = stock[ingredient.id]
            val details = buildList {
                ingredient.preparation?.let { add(it) }
                if (ingredient.optional) add(stringResource(R.string.food_optional))
                if (showStock) add(ingredientAvailabilityText(item))
                item?.substitutionAvailable?.let { add(stringResource(R.string.food_substitution, it.name)) }
                ingredient.substitutions.filterNot { it.name == item?.substitutionAvailable?.name }.forEach {
                    add(stringResource(R.string.food_substitution, it.name))
                    if (!it.note.isNullOrBlank()) add(it.note)
                }
            }
            CheckboxPreference(
                title = stringResource(R.string.food_consumed_amount, ingredient.name, line.display()),
                summary = details.joinToString("\n").ifEmpty { null },
                checked = checked,
                onCheckedChange = { checked = it },
                checkboxLocation = CheckboxLocation.Start,
                modifier = Modifier.heightIn(min = 48.dp),
            )
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
    FoodSupporting(ingredientAvailabilityText(item))
}

@Composable
private fun ingredientAvailabilityText(item: AvailabilityItem?): String {
    val label = availabilityLabel(item)
    return if (label == R.string.food_missing) {
        val missing = requireNotNull(item?.missing)
        stringResource(label, FoodQuantities.formatFr(Quantity(missing.amount, missing.unit)))
    } else stringResource(label)
}

internal fun stockDate(value: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.FRANCE)
        .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(value))
}.getOrDefault(value)
