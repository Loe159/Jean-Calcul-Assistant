package io.github.mangi.eta.food

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCuisineProjectionsTest {
    private val shopping = FoodContract.decode<ShoppingResponse>(foodFixture("shopping"))
    private val zone = ZoneId.of("Europe/Paris")

    @Test
    fun shoppingAggregatesContributionsOfSameProductAndUnitAcrossBasketStates() {
        val original = shopping.items.first()
        val recipeOrigin = ShoppingOrigin.Recipe("fixture-recipe", 1, "ing_courgette")
        val todo = original.copy(
            id = 25,
            quantity = original.quantity.copy(amount = 100.0),
            state = ShoppingItemState.TODO,
            origins = listOf(ShoppingContribution("fixture:1", ShoppingQuantity(100.0, 1), recipeOrigin)),
        )
        val otherUnit = todo.copy(id = 26, quantity = ShoppingLineQuantity(1.0, 3, UnitCode.PIECE, "Pièce"))
        val projected = projectShopping(shopping.items + todo + otherUnit, shopping.aisles)
        val aisle = projected.aisles.first()
        assertEquals("Légumes", aisle.name)
        assertEquals(2, aisle.rows.size)
        val grams = aisle.rows.single { it.quantity.grocyQuId == 1L }
        assertEquals(listOf(20L, 22L, 25L), grams.lines.map { it.id })
        assertEquals(100.0, grams.remaining, 0.0)
        assertEquals(250.0, grams.inBasket, 0.0)
        assertEquals(250.0, grams.stocked, 0.0)
        assertEquals(600.0, grams.quantity.amount, 0.0)
        assertEquals(listOf(original.origins.single().origin, recipeOrigin), grams.origins.map { it.origin })
        assertEquals(2, projected.remainingCount)
        assertEquals(2, projected.inBasketCount)
    }

    @Test
    fun ingredientCoverageMeasuresPartialAmountsAndExcludesStockedRows() {
        val ingredient = recipeFixture().ingredients.single { it.id == "ing_courgette" }
        val covered = ingredientShoppingCoverage(ingredient, MeasuredQuantity(0.5, UnitCode.KG), shopping.items)
        assertEquals(ShoppingCoverageStatus.PARTIAL, covered.status)
        assertEquals(MeasuredQuantity(0.25, UnitCode.KG), covered.covered)
        assertEquals(
            ShoppingCoverageStatus.FULL,
            ingredientShoppingCoverage(ingredient, MeasuredQuantity(200.0, UnitCode.G), shopping.items).status,
        )
        assertEquals(
            ShoppingCoverageStatus.NONE,
            ingredientShoppingCoverage(ingredient, MeasuredQuantity(1.0, UnitCode.PIECE), shopping.items).status,
        )
    }

    @Test
    fun pantryGroupingUsesLotLocationAndSortsUndatedLotsLast() {
        val pantry = FoodContract.decode<PantryResponse>(foodFixture("pantry"))
        val item = pantry.items.first()
        val batch = item.batches.single()
        val lots = listOf(
            batch.copy(stockEntryId = "late", bestBeforeDate = "2026-12-09"),
            batch.copy(stockEntryId = "none", bestBeforeDate = null),
            batch.copy(stockEntryId = "early", bestBeforeDate = "2026-11-30"),
            batch.copy(stockEntryId = "frozen", locationKind = LocationKind.FREEZER),
            batch.copy(stockEntryId = "legacy", locationKind = null),
        )
        val groups = projectPantryLots(pantry.copy(items = listOf(item.copy(batches = lots))))
        assertEquals(listOf(LocationKind.FRIDGE, LocationKind.FREEZER, LocationKind.OTHER), groups.map { it.kind })
        assertEquals(listOf("early", "late", "none"), groups.first().lots.map { it.batch.stockEntryId })
    }

    @Test
    fun weekCrossesMonthBoundaryAndHighlightsTheNextSlotInHouseholdTime() {
        val meal = FoodContract.decode<MealWeek>(foodFixture("meal-week")).slots.single().copy(date = "2026-12-01")
        val weekStart = LocalDate.parse("2026-11-30")
        val days = projectMealWeek(weekStart, zone, Instant.parse("2026-12-01T12:10:00Z"), listOf(meal))
        assertEquals(7, days.size)
        assertEquals(weekStart, days.first().date)
        assertEquals(LocalDate.parse("2026-12-06"), days.last().date)
        assertEquals(14, days.flatMap { it.slots }.size)
        val today = days.single { it.isToday }
        assertEquals(LocalDate.parse("2026-12-01"), today.date)
        assertNull(today.slots.first().meal)
        assertEquals(meal, today.slots.single { it.isTodaySlot }.meal)
        val afterMidnight = projectMealWeek(weekStart, zone, Instant.parse("2026-11-30T23:30:00Z"))
        assertEquals(MealSlotKey("2026-12-01", Meal.LUNCH), afterMidnight.flatMap { it.slots }.single { it.isTodaySlot }.key)
        assertFalse(projectMealWeek(weekStart, zone, Instant.parse("2026-12-01T21:00:00Z"))
            .flatMap { it.slots }.any { it.isTodaySlot })
    }

    @Test
    fun briefRanksImminentMealsAndUrgentExpiryThenDeduplicatesRecipes() {
        val now = Instant.parse("2026-11-25T16:00:00Z")
        val modern = FoodContract.decode<BriefCardsV2Response>(foodFixture("brief-cards-v2"))
        val meal = modern.cards.filterIsInstance<BriefCardV2.MealPlanned>().single()
        val shoppingCard = modern.cards.filterIsInstance<BriefCardV2.ShoppingList>().single()
        val legacy = FoodContract.decode<BriefCardsResponse>(foodFixture("brief-cards"))
        val recommendation = legacy.cards.filterIsInstance<BriefCard.RecipeRecommendation>().single().copy(recipeId = meal.recipeId)
        val expiry = BriefCard.ExpiringProducts(
            "expiry", 0, "À cuisiner", "", listOf(ExpiringProduct(3, "Courgettes", "2026-11-25", ExpiryStatus.DUE_SOON)),
            listOf(meal.recipeId, "fixture-other"),
        )
        val response = modern.copy(cards = listOf(recommendation, shoppingCard, expiry, meal))
        val selected = selectCuisineBriefCards(null, response, now, zone).filterIsInstance<CuisineBriefCard.Server>()
        assertEquals(listOf(meal, expiry.copy(suggestedRecipeIds = listOf("fixture-other"))), selected.map { it.card })

        val active = FoodContract.json.decodeFromString<CookingSession>(foodFixture("cooking-session"))
            .copy(status = CookingStatus.ACTIVE, recipeId = meal.recipeId)
        val withActive = selectCuisineBriefCards(active, modern, now, zone)
        assertEquals(listOf(CuisineBriefCard.ActiveSession(active), CuisineBriefCard.Server(shoppingCard)), withActive)
        assertEquals(
            listOf(CuisineBriefCard.ActiveSession(active)),
            selectCuisineBriefCards(active, response, Instant.parse(response.validUntil), zone),
        )
        assertTrue(selectCuisineBriefCards(null, null, now, zone).isEmpty())
    }
}
