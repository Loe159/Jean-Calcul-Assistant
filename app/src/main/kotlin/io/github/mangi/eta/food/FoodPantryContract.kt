package io.github.mangi.eta.food

import kotlinx.serialization.Serializable

@Serializable internal enum class AvailabilityStatus { AVAILABLE, PARTIAL, MISSING, UNKNOWN, NOT_TRACKED, OPTIONAL_MISSING }

@Serializable
internal enum class AvailabilityCategory { READY, READY_WITH_SUBSTITUTIONS, FEW_PURCHASES, MANY_PURCHASES }

@Serializable internal data class AvailabilityRequest(val servings: Int)
@Serializable internal data class AvailableSubstitution(val name: String, val grocyProductId: Long)

@Serializable
internal data class AvailabilityItem(
    val ingredientId: String,
    val status: AvailabilityStatus,
    val required: Quantity,
    val inStock: MeasuredQuantity?,
    val missing: MeasuredQuantity?,
    val reason: String?,
    val substitutionAvailable: AvailableSubstitution?,
)

@Serializable
internal data class AvailabilitySummary(val availableRatio: Double, val category: AvailabilityCategory, val missingCount: Int)

@Serializable
internal data class Availability(
    override val schemaVersion: Int,
    val recipeId: String,
    val recipeVersion: Int,
    val servings: Int,
    val computedAt: String,
    val stockAsOf: String,
    val items: List<AvailabilityItem>,
    val summary: AvailabilitySummary,
) : FoodResponse

@Serializable internal enum class PantryStatus { AVAILABLE, DUE_SOON, EXPIRED, OUT_OF_STOCK, BELOW_MIN }
@Serializable internal enum class StockConfidence { CONFIRMED, ESTIMATED }
@Serializable internal data class StockUnit(val grocyQuId: Long, val name: String, val unitCode: UnitCode?)
@Serializable internal data class PantryLocation(val id: Long, val name: String)

@Serializable
internal data class PantryBatch(
    val stockEntryId: String,
    val amount: Double,
    val bestBeforeDate: String?,
    val locationId: Long,
    val opened: Boolean,
)

@Serializable
internal data class PantryItem(
    val grocyProductId: Long,
    val name: String,
    val normalizedName: String,
    val amount: Double,
    val amountOpened: Double,
    val unit: StockUnit,
    val locationId: Long,
    val bestBeforeDate: String?,
    val status: PantryStatus,
    val minStockAmount: Double,
    val confidence: StockConfidence,
    val batches: List<PantryBatch>,
)

@Serializable
internal data class PantryResponse(
    override val schemaVersion: Int,
    val stockAsOf: String,
    val locations: List<PantryLocation>,
    val items: List<PantryItem>,
) : FoodResponse

@Serializable
internal data class CatalogProduct(
    val id: Long,
    val name: String,
    val stockUnit: StockUnit,
    val locationId: Long,
    val minStockAmount: Double,
)

@Serializable internal data class CatalogQuantityUnit(val id: Long, val name: String, val unitCode: UnitCode?)

@Serializable
internal data class CatalogResponse(
    override val schemaVersion: Int,
    val products: List<CatalogProduct>,
    val locations: List<PantryLocation>,
    val quantityUnits: List<CatalogQuantityUnit>,
) : FoodResponse

@Serializable internal data class NewPantryProduct(val name: String, val unit: UnitCode, val locationId: Long)

@Serializable
internal data class PantryAddRequest(
    val operationId: String,
    val amount: Double,
    val unit: UnitCode,
    val grocyProductId: Long? = null,
    val newProduct: NewPantryProduct? = null,
    val locationId: Long? = null,
    val bestBeforeDate: String? = null,
    val estimated: Boolean? = null,
) {
    init {
        require((grocyProductId == null) != (newProduct == null))
    }
}

@Serializable
internal data class PantryConsumeRequest(
    val operationId: String,
    val amount: Double,
    val unit: UnitCode,
    val spoiled: Boolean? = null,
)
