package io.github.mangi.eta.food

import io.github.mangi.eta.data.db.FoodCuisineOperationEntity
import io.github.mangi.eta.data.db.FoodOutboxState
import java.time.LocalDate
import kotlinx.serialization.Serializable

internal enum class CuisineOperationKind(val method: String) {
    SHOPPING_ADD("POST"),
    SHOPPING_PATCH("PATCH"),
    SHOPPING_DELETE("DELETE"),
    RECIPE_SHOPPING("POST"),
    SHOPPING_UNDO("POST"),
    PANTRY_ENTRY_PATCH("PATCH");

    val isPantry: Boolean get() = this == PANTRY_ENTRY_PATCH
}

internal data class CuisineOperationStatus(
    val operationId: String,
    val kind: CuisineOperationKind,
    val state: FoodOutboxState,
    val attempts: Int,
    val result: CuisineOperationResult?,
    val lastError: String?,
    val awaitingRefresh: Boolean,
)

internal val cuisineReplayableStates = setOf(FoodOutboxState.PENDING, FoodOutboxState.SENT, FoodOutboxState.UNCERTAIN)

internal fun FoodCuisineOperationEntity.toStatus() = CuisineOperationStatus(
    operationId, kind, state, attempts, result(), lastError, awaitingRefresh,
)

private fun FoodCuisineOperationEntity.result(): CuisineOperationResult? = resultJson?.let {
    if (kind.isPantry) FoodContract.decode<PantryEntryResult>(it) else FoodContract.decode<ShoppingOperationResult>(it)
}

@Serializable
internal data class ShoppingDraftLine(
    val grocyProductId: Long?,
    val name: String,
    val quantity: ShoppingLineQuantity,
    val aisleId: Long?,
    val origin: ShoppingOrigin,
)

internal fun ShoppingAddRequest.draft(
    shopping: ShoppingResponse?, pantry: PantryResponse?, product: CatalogProduct?,
): ShoppingDraftLine? {
    val productId = (item as? ShoppingAddItem.Product)?.grocyProductId
    val current = shopping?.items?.firstOrNull { productId != null && it.grocyProductId == productId }
    val stock = pantry?.items?.firstOrNull { productId != null && it.grocyProductId == productId }
    val catalogProduct = product?.takeIf { productId == it.id }
    val name = (item as? ShoppingAddItem.FreeText)?.name?.trim()
        ?: catalogProduct?.name ?: current?.name ?: stock?.name ?: return null
    val unit = (catalogProduct?.stockUnit ?: stock?.unit)?.takeIf { it.grocyQuId == item.quantity.grocyQuId }
    val knownQuantity = shopping?.items?.firstOrNull { it.quantity.grocyQuId == item.quantity.grocyQuId }?.quantity
    val quantity = ShoppingLineQuantity(
        item.quantity.amount, item.quantity.grocyQuId,
        unit?.unitCode ?: knownQuantity?.unitCode, unit?.name ?: knownQuantity?.unitName,
    )
    return ShoppingDraftLine(productId, name, quantity, current?.aisleId, ShoppingOrigin.Manual)
}

internal fun RecipeShoppingRequest.drafts(
    recipe: RecipeDefinition, availability: Availability, pantry: PantryResponse, shopping: ShoppingResponse?,
): List<ShoppingDraftLine> {
    val stock = pantry.items.associateBy { it.grocyProductId }
    val missingItems = (if (availability.servings == servings) availability else availability.recompute(recipe, servings))
        .items.associateBy { it.ingredientId }
    return recipe.ingredients.mapNotNull { ingredient ->
        if (ingredientIds?.contains(ingredient.id) ?: !ingredient.optional) {
            val product = stock[ingredient.grocyProductId] ?: return@mapNotNull null
            val missing = missingItems[ingredient.id]?.missing ?: return@mapNotNull null
            val unit = product.unit.unitCode ?: return@mapNotNull null
            val amount = convertShoppingAmount(missing.amount, missing.unit, unit) ?: return@mapNotNull null
            if (amount <= 0) return@mapNotNull null
            val origin = if (mealSlot != null) {
                ShoppingOrigin.MealSlot(recipe.id, recipeVersion, ingredient.id, mealSlot)
            } else ShoppingOrigin.Recipe(recipe.id, recipeVersion, ingredient.id)
            val aisleId = shopping?.items?.firstOrNull { it.grocyProductId == product.grocyProductId }?.aisleId
            ShoppingDraftLine(
                product.grocyProductId, product.name, ShoppingLineQuantity(amount, product.unit.grocyQuId, unit, product.unit.name),
                aisleId, origin,
            )
        } else null
    }
}

internal fun projectPendingShopping(
    cache: FoodCache<ShoppingResponse>?, operations: List<FoodCuisineOperationEntity>,
): ShoppingSnapshot {
    val items = cache?.value?.items.orEmpty().associateByTo(linkedMapOf()) { it.id }
    val json = FoodContract.json
    for (operation in operations) {
        when (operation.kind) {
            CuisineOperationKind.SHOPPING_ADD, CuisineOperationKind.RECIPE_SHOPPING -> {
                val drafts = operation.projectionJson?.let { json.decodeFromString<List<ShoppingDraftLine>>(it) }.orEmpty()
                val present = items.values.flatMap { it.origins }.filter { it.belongsTo(operation.operationId) }
                val result = operation.result() as? ShoppingOperationResult
                drafts.forEachIndexed { index, draft ->
                    val ingredient = draft.origin.ingredientId()
                    if (present.any { it.origin.ingredientId() == ingredient }) return@forEachIndexed
                    val confirmedLine = result?.lines?.firstOrNull { it.ingredientId == ingredient }
                    if (result != null && confirmedLine?.state !in setOf(
                            ShoppingOperationLineState.APPLIED, ShoppingOperationLineState.UNCERTAIN,
                        )
                    ) return@forEachIndexed
                    // Negative IDs are local draft keys. The queue sequence survives a process restart.
                    val id = -(operation.sequence * 1_000 + index)
                    val origin = ShoppingContribution(
                        confirmedLine?.contributionId ?: "${operation.operationId}:draft:$index",
                        ShoppingQuantity(draft.quantity.amount, checkNotNull(draft.quantity.grocyQuId)), draft.origin,
                    )
                    items[id] = ShoppingItem(
                        id, draft.grocyProductId, draft.name, draft.quantity, draft.aisleId,
                        ShoppingItemState.TODO, listOf(origin), "",
                    )
                }
            }
            CuisineOperationKind.SHOPPING_PATCH -> {
                val id = checkNotNull(operation.target).toLong()
                val request = json.decodeFromString<ShoppingPatchRequest>(operation.bodyJson)
                val line = items[id]?.takeIf { it.revision == request.expectedRevision } ?: continue
                val patch = request.patch
                val product = patch.grocyProductId?.let { productId -> items.values.firstOrNull { it.grocyProductId == productId } }
                val quantity = patch.quantity?.let { quantity ->
                    val known = items.values.firstOrNull { it.quantity.grocyQuId == quantity.grocyQuId }?.quantity
                    ShoppingLineQuantity(quantity.amount, quantity.grocyQuId, known?.unitCode, known?.unitName)
                } ?: line.quantity
                items[id] = line.copy(
                    grocyProductId = patch.grocyProductId ?: line.grocyProductId,
                    name = patch.name?.trim() ?: product?.name ?: line.name,
                    aisleId = if (patch.grocyProductId == null) line.aisleId else product?.aisleId,
                    quantity = quantity,
                    state = patch.state?.let { ShoppingItemState.valueOf(it.name) } ?: line.state,
                )
            }
            CuisineOperationKind.SHOPPING_DELETE -> {
                val id = checkNotNull(operation.target).toLong()
                val request = json.decodeFromString<ShoppingDeleteRequest>(operation.bodyJson)
                if (items[id]?.revision == request.expectedRevision) items.remove(id)
            }
            CuisineOperationKind.SHOPPING_UNDO -> {
                val request = json.decodeFromString<ShoppingUndoRequest>(operation.bodyJson)
                val result = operation.result() as? ShoppingOperationResult
                val rejected = result?.lines.orEmpty().filter {
                    it.state != ShoppingOperationLineState.APPLIED && it.state != ShoppingOperationLineState.UNCERTAIN
                }.mapTo(hashSetOf()) { it.contributionId }
                items.values.removeAll { item ->
                    item.state == ShoppingItemState.TODO && item.origins.isNotEmpty() && item.origins.all {
                        it.belongsTo(request.sourceOperationId) && it.contributionId !in rejected
                    }
                }
            }
            CuisineOperationKind.PANTRY_ENTRY_PATCH -> Unit
        }
    }
    return ShoppingSnapshot(cache, items.values.toList())
}

private fun ShoppingOrigin.ingredientId(): String? = when (this) {
    is ShoppingOrigin.Recipe -> ingredientId
    is ShoppingOrigin.MealSlot -> ingredientId
    ShoppingOrigin.Manual -> null
}

private fun ShoppingContribution.belongsTo(operationId: String) = contributionId.startsWith("$operationId:")

internal fun projectPendingPantry(
    pantry: PantryResponse, operations: List<FoodCuisineOperationEntity>, today: LocalDate,
): PantryResponse {
    val locationKinds = pantry.items.flatMap { it.batches }.mapNotNull { batch ->
        batch.locationKind?.let { batch.locationId to it }
    }.toMap()
    val patchesByEntry = operations.filter { it.kind.isPantry }.groupBy { checkNotNull(it.target).toLong() }
    return pantry.copy(items = pantry.items.map { item ->
        val batches = item.batches.map { base ->
            val patches = patchesByEntry[base.entryId].orEmpty()
            patches.fold(base) { batch, op ->
                val patch = FoodContract.json.decodeFromString<PantryEntryPatchRequest>(op.bodyJson)
                if (base.revision != patch.expectedRevision) return@fold batch
                (op.result() as? PantryEntryResult)?.entry ?: batch.copy(
                    amount = patch.amount ?: batch.amount,
                    bestBeforeDate = when (val date = patch.bestBeforeDate) {
                        PantryDatePatch.Unchanged -> batch.bestBeforeDate
                        PantryDatePatch.Clear -> null
                        is PantryDatePatch.Set -> date.date
                    },
                    locationId = patch.locationId ?: batch.locationId,
                    locationKind = if (patch.locationId == null || patch.locationId == batch.locationId) {
                        batch.locationKind
                    } else locationKinds[patch.locationId],
                )
            }
        }
        if (batches == item.batches) return@map item

        val amount = item.amount + batches.sumOf { it.amount } - item.batches.sumOf { it.amount }
        val opened = item.amountOpened + batches.filter { it.opened }.sumOf { it.amount } -
            item.batches.filter { it.opened }.sumOf { it.amount }
        val date = batches.filter { it.amount > 0 }.mapNotNull { it.bestBeforeDate }.minOrNull()
        val status = when {
            amount <= 0 -> PantryStatus.OUT_OF_STOCK
            date != null && date < today.toString() -> PantryStatus.EXPIRED
            date != null && date <= today.plusDays(3).toString() -> PantryStatus.DUE_SOON
            amount < item.minStockAmount -> PantryStatus.BELOW_MIN
            else -> PantryStatus.AVAILABLE
        }
        item.copy(
            amount = amount.coerceAtLeast(0.0), amountOpened = opened.coerceAtLeast(0.0),
            bestBeforeDate = date, status = status, batches = batches,
        )
    })
}
