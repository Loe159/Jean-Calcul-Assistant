package io.github.mangi.eta.food

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable internal enum class Meal { LUNCH, DINNER }
@Serializable internal data class MealSlotKey(val date: String, val meal: Meal)

@Serializable
internal data class MealSlot(
    val date: String,
    val meal: Meal,
    val recipeId: String,
    val recipeVersion: Int,
    val servings: Int,
    val recipe: RecipeSummary,
) {
    val key: MealSlotKey get() = MealSlotKey(date, meal)
}

@Serializable
internal data class MealWeek(
    override val schemaVersion: Int,
    val weekStart: String,
    val timeZone: String,
    val revision: String,
    val slots: List<MealSlot>,
) : FoodResponse

@Serializable
internal data class PlanningPutRequest(
    val operationId: String, val expectedRevision: String, val recipeId: String, val recipeVersion: Int, val servings: Int,
)

@Serializable internal data class PlanningDeleteRequest(val operationId: String, val expectedRevision: String)
@Serializable internal enum class PlanningMoveMode { MOVE, SWAP }

@Serializable
internal data class PlanningMoveRequest(
    val operationId: String,
    val expectedRevision: String,
    val from: MealSlotKey,
    val to: MealSlotKey,
    val mode: PlanningMoveMode,
)

@Serializable
internal data class PlanningWriteResult(
    override val schemaVersion: Int, val operationId: String, val replayed: Boolean, val weeks: List<MealWeek>,
) : FoodResponse

@Serializable internal enum class PlanningGenerateMode { PREVIEW, APPLY }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class GeneratePreviewRequest(
    val expectedRevision: String,
    val servings: Int? = null,
    val meals: List<Meal>? = null,
    @EncodeDefault val mode: PlanningGenerateMode = PlanningGenerateMode.PREVIEW,
) {
    init { require(mode == PlanningGenerateMode.PREVIEW) }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
internal data class GenerateApplyRequest(
    val operationId: String,
    val expectedRevision: String,
    val proposalId: String,
    @EncodeDefault val mode: PlanningGenerateMode = PlanningGenerateMode.APPLY,
) {
    init { require(mode == PlanningGenerateMode.APPLY) }
}

@Serializable
internal data class GeneratePreviewResult(
    override val schemaVersion: Int,
    val weekStart: String,
    val timeZone: String,
    val revision: String,
    val slots: List<MealSlot>,
    val proposalId: String,
    val expiresAt: String,
) : FoodResponse

@Serializable
internal enum class ShoppingSummaryWarningReason {
    NO_PRODUCT, NO_CONVERSION, NO_QUANTITY, UNKNOWN_BATCHES, PUT_AWAY_PENDING,
}

@Serializable
internal data class ShoppingSummaryWarning(
    val slot: MealSlotKey, val ingredientId: String, val grocyProductId: Long?, val reason: ShoppingSummaryWarningReason,
)

@Serializable
internal data class ShoppingSummaryItem(
    val grocyProductId: Long,
    val name: String,
    val grocyQuId: Long,
    val unitCode: UnitCode?,
    val missing: Double,
    val alreadyListed: Double,
    val toBuy: Double,
)

@Serializable
internal data class ShoppingSummary(
    override val schemaVersion: Int,
    val weekStart: String,
    val revision: String,
    val summaryToken: String,
    val stockAsOf: String,
    val items: List<ShoppingSummaryItem>,
    val warnings: List<ShoppingSummaryWarning>,
) : FoodResponse

@Serializable
internal data class PlanningShoppingRequest(val operationId: String, val expectedRevision: String, val summaryToken: String)
