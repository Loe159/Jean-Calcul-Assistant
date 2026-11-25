package io.github.mangi.eta.ui.screens.food

import io.github.mangi.eta.food.*
import java.time.LocalDate

internal enum class CuisineContentState { READY, LOADING, EMPTY, ERROR }
internal enum class RecipeCardVariant { L, S, R }

internal data class RecipeCardUi(
    val title: String,
    val totalMinutes: Int,
    val portions: Int,
    val difficulty: Difficulty,
    val image: RecipeImage? = null,
    val shortTitle: String = title,
    val type: String? = null,
    val reason: String? = null,
    val favorite: Boolean = false,
)

internal fun RecipeSummary.toCardUi() = RecipeCardUi(
    title, prepTimeMinutes + cookTimeMinutes, servings, difficulty, image, favorite = favorite,
)

internal sealed interface RecipeAvailability {
    data object All : RecipeAvailability
    data class Partial(val count: Int) : RecipeAvailability
    data class Missing(val count: Int) : RecipeAvailability
    data object Unknown : RecipeAvailability
}

internal enum class StockVerification { VERIFIED, UNVERIFIED, OFFLINE }

internal data class RecipeAvailabilityUi(
    val availability: RecipeAvailability = RecipeAvailability.Unknown,
    val verification: StockVerification = StockVerification.VERIFIED,
    val state: CuisineContentState = CuisineContentState.READY,
)

/** Required ingredients only. Suggested substitutions still leave the original ingredient missing. */
internal fun Availability?.toAvailabilityUi(verification: StockVerification): RecipeAvailabilityUi {
    val required = this?.items.orEmpty().filter {
        it.status !in setOf(AvailabilityStatus.NOT_TRACKED, AvailabilityStatus.OPTIONAL_MISSING)
    }
    val missing = required.count { it.status == AvailabilityStatus.MISSING }
    val partial = required.count { it.status == AvailabilityStatus.PARTIAL }
    val status = when {
        missing > 0 -> RecipeAvailability.Missing(missing + partial)
        partial > 0 -> RecipeAvailability.Partial(partial)
        this == null || items.isEmpty() || required.any { it.status == AvailabilityStatus.UNKNOWN } -> RecipeAvailability.Unknown
        required.isEmpty() && summary.category != AvailabilityCategory.READY -> RecipeAvailability.Unknown
        else -> RecipeAvailability.All
    }
    return RecipeAvailabilityUi(status, verification)
}

internal data class IngredientRowUi(
    val portion: IngredientPortion,
    val stock: AvailabilityItem? = null,
    val inShoppingList: Boolean = false,
    val replaces: String? = null,
    val checked: Boolean = false,
    val enabled: Boolean = true,
) {
    val id get() = portion.ingredient.id
    val status get() = stock?.status ?: AvailabilityStatus.UNKNOWN
}

internal data class IngredientChecklistUi(
    val ingredients: List<IngredientRowUi>,
    val portions: Int,
    val stockKnown: Boolean,
    val state: CuisineContentState = CuisineContentState.READY,
)

internal data class CookingStepUi(
    val number: Int,
    val step: RecipeStep,
    val instruction: String,
    val quantityRanges: List<IntRange> = emptyList(),
    val ingredients: List<IngredientPortion> = emptyList(),
    val heat: String? = null,
    val tool: String? = null,
)

internal enum class CookingTextScale(val factor: Float) { NORMAL(1f), LARGE(1.22f) }

internal data class CookingTimerUi(
    val id: String,
    val label: String,
    val totalSeconds: Int,
    val secondsLeft: Long,
    val state: TimerState,
    val stepNumber: Int? = null,
) {
    val progress: Float get() = when (state) {
        TimerState.FINISHED -> 1f
        else -> (1f - secondsLeft.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
}

internal fun CookingTimer.toTimerUi(nowMillis: Long, stepNumber: Int? = null) =
    CookingTimerUi(id, label, durationSeconds, secondsLeft(nowMillis), state, stepNumber)

internal data class ActiveCookingUi(
    val sessionId: String,
    val title: String,
    val index: Int,
    val count: Int,
    val stepTitle: String?,
)

internal fun CookingSession.toActiveCookingUi(progress: CookingProgressState = progress()) =
    ActiveCookingUi(id, recipeSnapshot.title, progress.index, progress.steps.size, progress.step?.title)

internal enum class FoodStockPlace { FRIDGE, FREEZER, PANTRY, OTHER }

internal data class FoodStockRowUi(
    val id: String,
    val title: String,
    val amount: Double,
    val unit: StockUnit,
    val place: FoodStockPlace = FoodStockPlace.OTHER,
    val placeName: String? = null,
    val bestBefore: LocalDate? = null,
    val daysLeft: Long? = null,
    val status: PantryStatus = PantryStatus.AVAILABLE,
    val confidence: StockConfidence = StockConfidence.CONFIRMED,
)

internal enum class ShoppingItemStatus { TODO, IN_BASKET, STOCKED }
internal data class ShoppingItemUi(
    val id: String,
    val title: String,
    val quantity: Quantity,
    val status: ShoppingItemStatus,
    val origin: String? = null,
)

internal enum class MealSlotMove { NONE, SOURCE, TARGET }
internal data class MealSlotUi(
    val day: LocalDate,
    val meal: MealType,
    val recipe: RecipeCardUi? = null,
    val availability: RecipeAvailabilityUi? = null,
)

internal data class AssistantSuggestionAction(val id: String, val label: String)

internal fun RecipeDefinition.toCookingStepUi(
    step: RecipeStep,
    number: Int,
    portions: Int,
    overrides: List<IngredientOverride> = emptyList(),
): CookingStepUi {
    val ranges = mutableListOf<IntRange>()
    val instruction = renderStep(step, portions, overrides, ranges::add)
    return CookingStepUi(number, step, instruction, ranges, stepPortions(step, portions, overrides))
}
