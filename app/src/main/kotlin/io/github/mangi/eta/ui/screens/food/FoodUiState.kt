package io.github.mangi.eta.ui.screens.food

import androidx.annotation.StringRes
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import java.time.Instant
import java.util.UUID

internal fun operationId() = "op_${UUID.randomUUID()}"
internal fun requestId() = "req_${UUID.randomUUID()}"

@StringRes
internal fun FoodError.label() = when (this) {
    FoodError.NOT_PAIRED -> R.string.food_not_paired
    FoodError.OFFLINE -> R.string.food_offline
    FoodError.SERVER_ERROR -> R.string.food_server_error
    FoodError.GROCY_UNAVAILABLE -> R.string.food_grocy_error
    FoodError.AI_DISABLED -> R.string.food_ai_disabled
}

@StringRes
internal fun AvailabilityCategory.label() = when (this) {
    AvailabilityCategory.READY -> R.string.food_ready
    AvailabilityCategory.READY_WITH_SUBSTITUTIONS -> R.string.food_ready_substitutions
    AvailabilityCategory.FEW_PURCHASES -> R.string.food_few_purchases
    AvailabilityCategory.MANY_PURCHASES -> R.string.food_many_purchases
}

internal data class IngredientPortion(val ingredient: RecipeIngredient, val quantity: Quantity) {
    fun display(): String {
        if (quantity.amount == 0.0) return FoodQuantities.formatFr(quantity)
        val scaled = FoodQuantities.scale(
            quantity, 1, 1, if (quantity.amount == null) Scaling.TO_TASTE else Scaling.FIXED, ingredient.rounding,
        )
        return FoodQuantities.formatFr(Quantity(scaled.displayAmount, scaled.unit))
    }
}

internal fun RecipeDefinition.portions(servings: Int, overrides: List<IngredientOverride> = emptyList()) =
    ingredients.map { ingredient ->
        val quantity = overrides.firstOrNull { it.ingredientId == ingredient.id }?.quantity
            ?: FoodQuantities.exact(ingredient.quantity, this.servings, servings, ingredient.scaling)
        IngredientPortion(ingredient, quantity)
    }

internal fun RecipeDefinition.renderStep(
    step: RecipeStep,
    servings: Int,
    overrides: List<IngredientOverride> = emptyList(),
    onQuantity: ((IntRange) -> Unit)? = null,
) = FoodQuantities.renderStep(
    step.instruction, ingredients.map { ScalableIngredient(it.id, it.quantity, it.scaling, it.rounding) },
    step.ingredientRefs, this.servings, servings, overrides.associate { it.ingredientId to it.quantity }, onQuantity,
)

internal fun RecipeDefinition.stepPortions(
    step: RecipeStep,
    servings: Int,
    overrides: List<IngredientOverride> = emptyList(),
): List<IngredientPortion> {
    val ingredientsById = portions(servings, overrides).associateBy { it.ingredient.id }
    return step.ingredientRefs.mapNotNull { ref ->
        val line = ingredientsById[ref.ingredientId] ?: return@mapNotNull null
        line.copy(quantity = line.quantity.copy(amount = line.quantity.amount?.times(ref.fraction)))
    }
}

internal data class CookingProgressState(
    val steps: List<RecipeStep>,
    val index: Int,
    val finished: Int,
    val remainingMinutes: Int,
) {
    val step get() = steps.getOrNull(index)
    val ratio get() = if (steps.isEmpty()) 0f else finished.toFloat() / steps.size
}

internal fun CookingSession.progress(): CookingProgressState {
    val steps = recipeSnapshot.steps.sortedBy { it.order }
    val resolved = (completedStepIds + skippedStepIds).toSet()
    val pending = steps.filterNot { it.id in resolved }
    val knownSeconds = pending.sumOf { it.durationSeconds ?: it.timerSuggestion?.durationSeconds ?: 0 }
    val estimatedTotal = recipeSnapshot.prepTimeMinutes + recipeSnapshot.cookTimeMinutes
    val fallbackMinutes = estimatedTotal / steps.size.coerceAtLeast(1)
    val remaining = (knownSeconds + 59) / 60 + pending.count { it.durationSeconds == null && it.timerSuggestion == null } * fallbackMinutes
    return CookingProgressState(steps, steps.indexOfFirst { it.id == currentStepId }.coerceAtLeast(0), steps.size - pending.size, remaining)
}

internal fun CookingSession.activeTimers() = timers.filterNot { it.state == TimerState.CANCELLED }

internal fun CookingTimer.secondsLeft(nowMillis: Long): Long = when (state) {
    TimerState.PAUSED -> remainingSeconds ?: 0
    TimerState.RUNNING -> endsAt?.let { ((Instant.parse(it).toEpochMilli() - nowMillis).coerceAtLeast(0) + 999) / 1000 } ?: 0
    else -> 0
}

internal fun String.amountOrNull() = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
internal fun String.listValues() = split(',').map(String::trim).filter(String::isNotEmpty).distinct()
internal fun String.dateOrNull() = runCatching { java.time.LocalDate.parse(this).toString() }.getOrNull()
