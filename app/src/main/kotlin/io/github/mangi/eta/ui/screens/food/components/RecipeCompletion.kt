package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.screens.food.FoodNumberField
import io.github.mangi.eta.ui.screens.food.amountOrNull
import io.github.mangi.eta.ui.screens.food.completionLines
import io.github.mangi.eta.ui.screens.food.dateOrNull
import io.github.mangi.eta.ui.screens.food.portions
import io.github.suqi8.coui.kmp.basic.Text

internal data class CompletionDraft(
    val servingsMade: Int,
    val rating: Int?,
    val consumptions: List<CompleteConsumption>,
    val leftovers: Leftovers?,
)

@Composable
internal fun RecipeCompletion(
    session: CookingSession,
    locations: List<PantryLocation>,
    busy: Boolean,
    onConfirm: (CompletionDraft) -> Unit,
) {
    var servings by rememberSaveable { mutableStateOf(session.servings.toString()) }
    var leftovers by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf<Long?>(null) }
    var rating by rememberSaveable { mutableStateOf("") }
    val selectedLocation = location ?: locations.firstOrNull {
        it.name.contains("frigo", true) || it.name.contains("fridge", true) || it.name.contains("réfrig", true)
    }?.id
    val amounts = mutableMapOf<String, Double>()
    var validAmounts = true
    val ingredients = session.recipeSnapshot.portions(session.servings, session.ingredientOverrides)
    FoodHeading(session.recipeSnapshot.title)
    FoodNumberField(servings, { servings = it }, R.string.food_servings_made)
    FoodHeading(stringResource(R.string.food_consumed))
    ingredients.forEach { line ->
        val (ingredient, quantity) = line
        key(ingredient.id) {
            if (ingredient.grocyProductId == null) {
                Text(stringResource(R.string.food_consumed_amount, ingredient.name, line.display()))
                FoodSupporting(stringResource(R.string.food_not_tracked))
            } else {
                var amount by rememberSaveable {
                    mutableStateOf(quantity.amount?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty())
                }
                val parsed = amount.amountOrNull()
                if (amount.isNotBlank() && (parsed == null || parsed < 0)) validAmounts = false
                amounts[ingredient.id] = parsed ?: 0.0
                CouiTextField(amount, { amount = it }, label = stringResource(R.string.food_consumed_amount, ingredient.name, unitName(quantity.unit)),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            }
        }
    }
    FoodNumberField(leftovers, { leftovers = it }, R.string.food_leftovers)
    val leftoverServings = if (leftovers.isBlank()) 0 else leftovers.toIntOrNull()
    if (leftoverServings == null || leftoverServings != 0) {
        CouiTextField(date, { date = it }, label = stringResource(R.string.food_date))
        FoodChoices(stringResource(R.string.food_location), locations.map { it.id to it.name }, selectedLocation) { location = it }
    }
    FoodNumberField(rating, { rating = it }, R.string.food_rating)
    val servingsMade = servings.toIntOrNull()?.takeIf { it > 0 }
    val stars = rating.toIntOrNull()?.takeIf { it in 1..5 }
    val bestBefore = date.trim().dateOrNull()
    val locationValid = locations.any { it.id == selectedLocation }
    val leftoversValid = servingsMade != null && leftoverServings != null && leftoverServings in 0..servingsMade &&
        (leftoverServings == 0 || (bestBefore != null && locationValid))
    FoodAction(R.string.food_confirm, primary = true, enabled = !busy && leftoversValid && validAmounts &&
        (rating.isBlank() || stars != null)) {
        val rest = if (requireNotNull(leftoverServings) > 0) {
            Leftovers(leftoverServings, requireNotNull(bestBefore), requireNotNull(selectedLocation))
        } else null
        onConfirm(CompletionDraft(requireNotNull(servingsMade), stars, session.completionLines(amounts), rest))
    }
}

@Composable
internal fun CompletionStatus(session: CookingSession, result: OperationResult?) {
    FoodSection {
        FoodHeading(session.recipeSnapshot.title)
        if (result != null) {
            OperationResultLines(result, session.recipeSnapshot.ingredients.associate { it.id to it.name })
        } else {
            val label = when (session.stockConsumptionStatus) {
                StockConsumptionStatus.SUCCEEDED -> R.string.food_succeeded
                StockConsumptionStatus.PARTIAL -> R.string.food_partial
                StockConsumptionStatus.FAILED -> R.string.food_failed
                StockConsumptionStatus.UNCERTAIN -> R.string.food_uncertain
                else -> R.string.food_pending
            }
            FoodSupporting(stringResource(label))
        }
    }
}
