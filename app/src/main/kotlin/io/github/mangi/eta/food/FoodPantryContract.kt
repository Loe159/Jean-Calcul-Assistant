package io.github.mangi.eta.food

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

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
@Serializable internal enum class LocationKind { FRIDGE, FREEZER, PANTRY, OTHER }
@Serializable internal data class StockUnit(val grocyQuId: Long, val name: String, val unitCode: UnitCode?)
@Serializable internal data class PantryLocation(val id: Long, val name: String)

@Serializable
internal data class PantryBatch(
    val stockEntryId: String,
    val amount: Double,
    val bestBeforeDate: String?,
    val locationId: Long,
    val opened: Boolean,
    val entryId: Long? = null,
    val revision: String? = null,
    val locationKind: LocationKind? = null,
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

@Serializable internal enum class BarcodeSuggestionSource { OPEN_FOOD_FACTS }

@Serializable
internal data class BarcodeSuggestion(
    val name: String,
    val brand: String?,
    val quantityLabel: String?,
    val unit: UnitCode?,
    val imageUrl: String?,
    val source: BarcodeSuggestionSource,
)

@Serializable
internal data class BarcodeLookupResponse(
    override val schemaVersion: Int,
    val barcode: String,
    val product: CatalogProduct?,
    val suggestion: BarcodeSuggestion?,
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
    val barcode: String? = null,
) {
    init {
        require((grocyProductId == null) != (newProduct == null))
        require(barcode == null || barcode.isFoodBarcode())
    }
}

@Serializable
internal data class PantryConsumeRequest(
    val operationId: String,
    val amount: Double,
    val unit: UnitCode,
    val spoiled: Boolean? = null,
)

@Serializable(with = PantryDatePatchSerializer::class)
internal sealed interface PantryDatePatch {
    data object Unchanged : PantryDatePatch
    data object Clear : PantryDatePatch
    data class Set(val date: String) : PantryDatePatch
}

internal object PantryDatePatchSerializer : KSerializer<PantryDatePatch> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: PantryDatePatch) {
        (encoder as JsonEncoder).encodeJsonElement(when (value) {
            PantryDatePatch.Unchanged -> throw SerializationException("An unchanged date must be omitted")
            PantryDatePatch.Clear -> JsonNull
            is PantryDatePatch.Set -> JsonPrimitive(value.date)
        })
    }

    override fun deserialize(decoder: Decoder): PantryDatePatch =
        when (val value = (decoder as JsonDecoder).decodeJsonElement()) {
            JsonNull -> PantryDatePatch.Clear
            is JsonPrimitive -> if (value.isString) PantryDatePatch.Set(value.content)
                else throw SerializationException("Expected a pantry date")
            else -> throw SerializationException("Expected a pantry date")
        }
}

@Serializable
internal data class PantryEntryPatchRequest(
    val operationId: String,
    val expectedRevision: String,
    val amount: Double? = null,
    val bestBeforeDate: PantryDatePatch = PantryDatePatch.Unchanged,
    val locationId: Long? = null,
)

@Serializable internal enum class PantryEntryState { SUCCEEDED, UNCERTAIN }

@Serializable
internal data class PantryEntryResult(
    override val schemaVersion: Int,
    override val operationId: String,
    val replayed: Boolean,
    val state: PantryEntryState,
    val entry: PantryBatch?,
) : CuisineOperationResult
