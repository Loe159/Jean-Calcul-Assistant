package io.github.mangi.eta.food

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

internal data class ShoppingSnapshot(
    val cache: FoodCache<ShoppingResponse>?,
    val items: List<ShoppingItem>,
)

internal data class ShoppingProductRow(
    val name: String,
    val grocyProductId: Long?,
    val quantity: ShoppingLineQuantity,
    val lines: List<ShoppingItem>,
) {
    val origins: List<ShoppingContribution> get() = lines.flatMap { it.origins }
    val remaining: Double get() = amount(ShoppingItemState.TODO)
    val inBasket: Double get() = amount(ShoppingItemState.IN_BASKET)
    val stocked: Double get() = amount(ShoppingItemState.STOCKED)

    private fun amount(state: ShoppingItemState) = lines.filter { it.state == state }.sumOf { it.quantity.amount }
}

internal data class ShoppingAisleGroup(val aisleId: Long?, val name: String?, val rows: List<ShoppingProductRow>)
internal data class ShoppingProjection(val aisles: List<ShoppingAisleGroup>, val remainingCount: Int, val inBasketCount: Int)

private data class ShoppingProductKey(val productId: Long?, val unitId: Long?, val ungroupedLineId: Long?)

internal fun projectShopping(items: List<ShoppingItem>, aisles: List<ShoppingAisle>): ShoppingProjection {
    val products = items.groupBy { item ->
        val unitId = item.quantity.grocyQuId
        val ungrouped = item.id.takeIf { item.grocyProductId == null || unitId == null }
        ShoppingProductKey(item.grocyProductId, unitId, ungrouped)
    }.values
    val byAisle = products.groupBy { lines -> lines.firstNotNullOfOrNull { it.aisleId } }
    val aisleOrder = (aisles.map { it.id } + byAisle.keys).distinct()
    val aisleNames = aisles.associate { it.id to it.name }
    val groups = aisleOrder.mapNotNull { id ->
        val rows = byAisle[id]?.map { lines ->
            val first = lines.first()
            val total = first.quantity.copy(amount = lines.sumOf { it.quantity.amount })
            ShoppingProductRow(first.name, first.grocyProductId, total, lines)
        } ?: return@mapNotNull null
        ShoppingAisleGroup(id, aisleNames[id], rows.sortedBy { it.name.lowercase() })
    }
    return ShoppingProjection(
        groups,
        products.count { lines -> lines.any { it.state == ShoppingItemState.TODO } },
        products.count { lines -> lines.any { it.state == ShoppingItemState.IN_BASKET } },
    )
}

internal enum class ShoppingCoverageStatus { NONE, PARTIAL, FULL }

internal data class ShoppingCoverage(val status: ShoppingCoverageStatus, val covered: MeasuredQuantity)

internal fun ingredientShoppingCoverage(
    ingredient: RecipeIngredient,
    missing: MeasuredQuantity,
    items: List<ShoppingItem>,
    grocyQuId: Long? = null,
): ShoppingCoverage {
    val productId = ingredient.grocyProductId
    val covered = if (productId == null) 0.0 else items.sumOf { line ->
        val quantity = line.quantity
        when {
            line.grocyProductId != productId || line.state == ShoppingItemState.STOCKED -> 0.0
            grocyQuId != null && grocyQuId == quantity.grocyQuId -> quantity.amount
            else -> quantity.unitCode?.let { convertShoppingAmount(quantity.amount, it, missing.unit) } ?: 0.0
        }
    }
    val status = when {
        covered <= 0 || missing.amount <= 0 -> ShoppingCoverageStatus.NONE
        covered + 1e-9 >= missing.amount -> ShoppingCoverageStatus.FULL
        else -> ShoppingCoverageStatus.PARTIAL
    }
    return ShoppingCoverage(status, MeasuredQuantity(covered.coerceAtMost(missing.amount), missing.unit))
}

private val massFactors = mapOf(UnitCode.MG to 0.001, UnitCode.G to 1.0, UnitCode.KG to 1_000.0)
private val volumeFactors = mapOf(UnitCode.ML to 1.0, UnitCode.CL to 10.0, UnitCode.L to 1_000.0)

internal fun convertShoppingAmount(amount: Double, from: UnitCode, to: UnitCode): Double? {
    if (from == to) return amount
    for (factors in listOf(massFactors, volumeFactors)) {
        val source = factors[from] ?: continue
        val target = factors[to] ?: continue
        return amount * source / target
    }
    return null
}

internal data class PantryLot(val product: PantryItem, val batch: PantryBatch)
internal data class PantryLocationGroup(val kind: LocationKind, val lots: List<PantryLot>)

internal fun projectPantryLots(pantry: PantryResponse): List<PantryLocationGroup> {
    val byLocation = pantry.items.flatMap { item -> item.batches.map { PantryLot(item, it) } }
        .groupBy { it.batch.locationKind ?: LocationKind.OTHER }
    return LocationKind.entries.mapNotNull { kind ->
        val lots = byLocation[kind] ?: return@mapNotNull null
        PantryLocationGroup(kind, lots.sortedWith(
            compareBy<PantryLot, String?>(nullsLast()) { it.batch.bestBeforeDate }
                .thenBy { it.product.name.lowercase() }.thenBy { it.batch.stockEntryId },
        ))
    }
}

internal data class ProjectedMealSlot(val key: MealSlotKey, val meal: MealSlot?, val isTodaySlot: Boolean)
internal data class ProjectedMealDay(val date: LocalDate, val isToday: Boolean, val slots: List<ProjectedMealSlot>)

internal fun projectMealWeek(
    weekStart: LocalDate, timeZone: ZoneId, now: Instant, slots: List<MealSlot> = emptyList(),
): List<ProjectedMealDay> {
    require(weekStart.dayOfWeek == DayOfWeek.MONDAY)
    val byKey = slots.associateBy { it.key }
    val localNow = now.atZone(timeZone)
    val today = localNow.toLocalDate()
    val todayMeal = Meal.entries.firstOrNull { !localNow.toLocalTime().isAfter(mealTime(it)) }
    return List(7) { offset ->
        val date = weekStart.plusDays(offset.toLong())
        ProjectedMealDay(date, date == today, Meal.entries.map { meal ->
            val key = MealSlotKey(date.toString(), meal)
            ProjectedMealSlot(key, byKey[key], date == today && meal == todayMeal)
        })
    }
}

internal fun MealWeek.project(now: Instant): List<ProjectedMealDay> =
    projectMealWeek(LocalDate.parse(weekStart), ZoneId.of(timeZone), now, slots)

private fun mealTime(meal: Meal): LocalTime = when (meal) {
    Meal.LUNCH -> LocalTime.NOON
    Meal.DINNER -> LocalTime.of(19, 0)
}

internal sealed interface CuisineBriefCard {
    data class ActiveSession(val session: CookingSession) : CuisineBriefCard
    data class Server(val card: BriefCardV2) : CuisineBriefCard
}

internal fun selectCuisineBriefCards(
    activeSession: CookingSession?,
    response: BriefCardsV2Response?,
    now: Instant,
    timeZone: ZoneId,
): List<CuisineBriefCard> = buildList {
    val featuredRecipes = mutableSetOf<String>()
    val active = activeSession?.takeIf { it.status == CookingStatus.ACTIVE }
    if (active != null) {
        add(CuisineBriefCard.ActiveSession(active))
        featuredRecipes += active.recipeId
    }
    if (response == null || !now.isBefore(Instant.parse(response.validUntil))) return@buildList

    val today = now.atZone(timeZone).toLocalDate()
    val ranked = response.cards.mapNotNull { card ->
        val rank = when (card) {
            is BriefCardV2.MealPlanned -> {
                val scheduled = Instant.parse(card.scheduledAt)
                if (scheduled.isBefore(now)) return@mapNotNull null
                if (Duration.between(now, scheduled) <= Duration.ofHours(4)) 5 else 2
            }
            is BriefCard.ExpiringProducts ->
                if (card.products.any { it.status == ExpiryStatus.EXPIRED || LocalDate.parse(it.bestBeforeDate) <= today }) 4 else 2
            is BriefCardV2.ShoppingList -> {
                if (card.remainingCount == 0 && card.inBasketCount == 0) return@mapNotNull null
                3
            }
            is BriefCard.RecipeRecommendation -> 1
        }
        card to rank
    }.sortedWith(compareByDescending<Pair<BriefCardV2, Int>> { it.second }.thenByDescending { it.first.priority })

    val selected = ranked.asSequence().map { it.first }.distinctBy { it.id }.filter { card ->
        val recipeId = when (card) {
            is BriefCardV2.MealPlanned -> card.recipeId
            is BriefCard.RecipeRecommendation -> card.recipeId
            else -> null
        }
        recipeId == null || featuredRecipes.add(recipeId)
    }.take(2 - size).toList()
    for (card in selected) {
        val deduplicated = if (card is BriefCard.ExpiringProducts) {
            card.copy(suggestedRecipeIds = card.suggestedRecipeIds.filter { featuredRecipes.add(it) })
        } else card
        add(CuisineBriefCard.Server(deduplicated))
    }
}
