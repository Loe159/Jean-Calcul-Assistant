package io.github.mangi.eta.ui.screens.food

import io.github.mangi.eta.food.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CuisineHomeModelsTest {
    private val data = CuisineHomePreviewData

    @Test
    fun `intent reads minutes, hours and accent-free words`() {
        assertEquals(RecipeIntent(listOf("pates"), 20), parseRecipeIntent("J’ai 20 min, pâtes"))
        assertEquals(RecipeIntent(emptyList(), 60), parseRecipeIntent("1 h"))
        assertEquals(RecipeIntent(listOf("œufs"), 45), parseRecipeIntent("Œufs 45 minutes"))
        assertTrue(data.omelette.matches(parseRecipeIntent("œufs"), RecipeFilters()))
        assertNull(parseRecipeIntent("curry").maxMinutes)
    }

    @Test
    fun `filters combine the intent limit, difficulty and favorites`() {
        val quick = parseRecipeIntent("20 min")
        assertTrue(data.omelette.matches(quick, RecipeFilters()))
        assertFalse(data.soup.matches(quick, RecipeFilters()))
        assertFalse(data.curry.matches(parseRecipeIntent(""), RecipeFilters(difficulty = Difficulty.EASY)))
        assertTrue(data.pasta.matches(parseRecipeIntent("tomate"), RecipeFilters(favoritesOnly = true)))
        assertFalse(data.omelette.matches(parseRecipeIntent(""), RecipeFilters(favoritesOnly = true)))
        // The stricter of the search limit and the filter wins.
        assertFalse(data.pasta.matches(parseRecipeIntent("30 min"), RecipeFilters(maxMinutes = 20)))
    }

    @Test
    fun `sections keep cookable recipes and those using expiring products`() {
        val sections = data.suggestions.toSections(parseRecipeIntent(""), RecipeFilters())
        assertEquals(listOf(data.omelette, data.gratin, data.salad), sections.withWhatIHave.map { it.recipe })
        assertEquals(listOf(data.gratin, data.soup), sections.useSoon.map { it.recipe })
        assertEquals(listOf("Courgettes", "Feta"), sections.useSoon.first().expiringProducts)
    }

    @Test
    fun `availability summary maps to the chip states`() {
        assertEquals(RecipeAvailability.All,
            AvailabilitySummary(1.0, AvailabilityCategory.READY, 0).toAvailabilityUi().availability)
        assertEquals(RecipeAvailability.Partial(1),
            AvailabilitySummary(0.8, AvailabilityCategory.READY_WITH_SUBSTITUTIONS, 1).toAvailabilityUi().availability)
        assertEquals(RecipeAvailability.Missing(3),
            AvailabilitySummary(0.4, AvailabilityCategory.MANY_PURCHASES, 3).toAvailabilityUi().availability)
    }

    @Test
    fun `tonight prefers dinner, then lunch, only for today`() {
        val today = LocalDate.of(2026, 11, 24)
        val lunch = data.tonight.copy(meal = Meal.LUNCH, recipe = data.salad, recipeId = data.salad.id)
        val week = MealWeek(1, "2026-11-23", "Europe/Paris", "r1", listOf(lunch))
        assertEquals(lunch, week.tonight(today))
        assertEquals(data.tonight, week.copy(slots = listOf(lunch, data.tonight)).tonight(today))
        assertNull(week.tonight(today.plusDays(1)))
        assertEquals(LocalDate.of(2026, 11, 23), mondayOf(today))
    }

    @Test
    fun `library lists favorites first then recently cooked`() {
        val cooked = data.curry.copy(lastCookedAt = "2026-11-20T19:00:00Z")
        val order = listOf(data.soup, cooked, data.pasta).libraryOrder()
        assertEquals(listOf(data.pasta, cooked, data.soup), order)
    }
}
