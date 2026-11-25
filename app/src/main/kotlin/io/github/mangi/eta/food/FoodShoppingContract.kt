package io.github.mangi.eta.food

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable internal data class ShoppingQuantity(val amount: Double, val grocyQuId: Long)

@Serializable
internal data class ShoppingLineQuantity(
    val amount: Double, val grocyQuId: Long?, val unitCode: UnitCode?, val unitName: String?,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
internal sealed interface ShoppingOrigin {
    @Serializable @SerialName("MANUAL")
    data object Manual : ShoppingOrigin

    @Serializable @SerialName("RECIPE")
    data class Recipe(val recipeId: String, val recipeVersion: Int, val ingredientId: String) : ShoppingOrigin

    @Serializable @SerialName("MEAL_SLOT")
    data class MealSlot(
        val recipeId: String, val recipeVersion: Int, val ingredientId: String, val slot: MealSlotKey,
    ) : ShoppingOrigin
}

@Serializable
internal data class ShoppingContribution(
    val contributionId: String, val quantity: ShoppingQuantity, val origin: ShoppingOrigin,
)

@Serializable internal enum class ShoppingItemState { TODO, IN_BASKET, STOCKED }
@Serializable internal enum class ShoppingBasketState { TODO, IN_BASKET }
@Serializable internal data class ShoppingAisle(val id: Long?, val name: String)

@Serializable
internal data class ShoppingItem(
    val id: Long,
    val grocyProductId: Long?,
    val name: String,
    val quantity: ShoppingLineQuantity,
    val aisleId: Long?,
    val state: ShoppingItemState,
    val origins: List<ShoppingContribution>,
    val revision: String,
)

@Serializable
internal data class ShoppingResponse(
    override val schemaVersion: Int,
    val listId: Long,
    val revision: String,
    val updatedAt: String,
    val aisles: List<ShoppingAisle>,
    val items: List<ShoppingItem>,
) : FoodResponse

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
internal sealed interface ShoppingAddItem {
    val quantity: ShoppingQuantity

    @Serializable @SerialName("PRODUCT")
    data class Product(val grocyProductId: Long, override val quantity: ShoppingQuantity) : ShoppingAddItem

    @Serializable @SerialName("FREE_TEXT")
    data class FreeText(val name: String, override val quantity: ShoppingQuantity) : ShoppingAddItem
}

@Serializable internal data class ShoppingAddRequest(val operationId: String, val item: ShoppingAddItem)
@Serializable internal data class ShoppingDeleteRequest(val operationId: String, val expectedRevision: String)

@Serializable
internal data class ShoppingPatch(
    val name: String? = null,
    val grocyProductId: Long? = null,
    val quantity: ShoppingQuantity? = null,
    val state: ShoppingBasketState? = null,
)

@Serializable
internal data class ShoppingPatchRequest(
    val operationId: String, val expectedRevision: String, val patch: ShoppingPatch,
)

@Serializable
internal data class RecipeShoppingRequest(
    val operationId: String,
    val recipeVersion: Int,
    val servings: Int,
    val ingredientIds: List<String>? = null,
    val mealSlot: MealSlotKey? = null,
)

@Serializable internal data class ShoppingUndoRequest(val operationId: String, val sourceOperationId: String)
@Serializable internal data class PutAwayNewProduct(val name: String, val stockQuId: Long)

@Serializable
internal data class PutAwayLine(
    val itemId: Long,
    val expectedRevision: String,
    val quantity: ShoppingQuantity,
    val locationId: Long,
    val bestBeforeDate: String?,
    val grocyProductId: Long? = null,
    val newProduct: PutAwayNewProduct? = null,
)

@Serializable internal data class PutAwayRequest(val operationId: String, val lines: List<PutAwayLine>)

@Serializable
internal enum class ShoppingOperationLineState {
    APPLIED, SKIPPED, NEEDS_PRODUCT, NEEDS_CONVERSION, CONFLICT, NOT_REVERSIBLE, FAILED, UNCERTAIN,
}

@Serializable
internal data class ShoppingOperationLine(
    val itemId: Long?,
    val contributionId: String?,
    val ingredientId: String?,
    val transactionId: String?,
    val state: ShoppingOperationLineState,
    val message: String?,
)

@Serializable
internal data class ShoppingOperationResult(
    override val schemaVersion: Int,
    override val operationId: String,
    val replayed: Boolean,
    val state: OperationState,
    val lines: List<ShoppingOperationLine>,
) : CuisineOperationResult
