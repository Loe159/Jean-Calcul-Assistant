package io.github.mangi.eta.ui.screens.food

import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.components.availabilityLabel
import io.github.mangi.eta.ui.screens.home.NowBriefCardExtension
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.briefRealContent
import io.github.mangi.eta.ui.screens.home.brief.cards.foodBriefServerCards
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class FoodUiStateTest {
    private val session = FoodContract.json.decodeFromString<CookingSession>(foodFixture("cooking-session"))

    @Test fun quantitiesPreviewAndAvailabilityUseSelectedPortions() {
        val recipe = recipeFixture()
        val id = "ing_courgette"
        val portions = recipe.portions(4)
        assertEquals(Quantity(500.0, UnitCode.G), portions.single { it.ingredient.id == id }.quantity)
        val step = recipe.steps.first { it.instruction.contains("{{ing:$id}}") }
        assertTrue(recipe.renderStep(step, 4).contains("500 g"))
        val stock = FoodContract.decode<Availability>(foodFixture("availability")).recompute(recipe, 4)
        assertEquals(4, stock.servings)
        assertEquals(Quantity(500.0, UnitCode.G), stock.items.single { it.ingredientId == id }.required)
        assertEquals(R.string.food_stock_unknown, availabilityLabel(null))
        val item = stock.items.first()
        assertEquals(R.string.food_missing, availabilityLabel(
            item.copy(status = AvailabilityStatus.PARTIAL, missing = MeasuredQuantity(150.0, UnitCode.G)),
        ))
        assertEquals(R.string.food_known_available, availabilityLabel(item.copy(status = AvailabilityStatus.AVAILABLE)))
    }

    @Test fun stepAndTimerStateSurviveBeingProjectedFromPersistedSession() {
        val steps = session.recipeSnapshot.steps.sortedBy { it.order }
        val first = steps.first().id
        val second = steps[1].id
        val running = CookingTimer("t1", "Four", first, 480, TimerState.RUNNING, "2026-11-23T18:08:00Z", null)
        val paused = CookingTimer("t2", "Riz", second, 360, TimerState.PAUSED, null, 130)
        val timerState = session.copy(
            currentStepId = second,
            completedStepIds = listOf(first),
            skippedStepIds = emptyList(),
            timers = listOf(running, paused, running.copy(id = "t3", state = TimerState.CANCELLED)),
        )
        assertEquals(second, timerState.progress().step?.id)
        assertEquals(1, timerState.progress().finished)
        assertEquals(listOf(running, paused), timerState.activeTimers())
        val now = Instant.parse("2026-11-23T18:01:00.100Z").toEpochMilli()
        assertEquals(420L, running.secondsLeft(now))
        assertEquals(0L, running.secondsLeft(now + 900_000))
        assertEquals(130L, paused.secondsLeft(now + 900_000))
    }

    @Test fun completionDefaultsToExactPortionsAndEditsOnlyAffectConfirmedLines() {
        val changed = session.copy(
            servings = 4, ingredientOverrides = listOf(IngredientOverride("ing_eggs", Quantity(3.0, UnitCode.PIECE))),
        )
        val lines = changed.completionLines()
        assertEquals(MeasuredQuantity(500.0, UnitCode.G), lines.single { it.ingredientId == "ing_courgette" }.quantity)
        assertEquals(MeasuredQuantity(3.0, UnitCode.PIECE), lines.single { it.ingredientId == "ing_eggs" }.quantity)
        val edited = changed.completionLines(mapOf("ing_courgette" to 350.0, "ing_eggs" to 0.0))
        assertEquals(MeasuredQuantity(350.0, UnitCode.G), edited.single { it.ingredientId == "ing_courgette" }.quantity)
        assertFalse(edited.any { it.ingredientId == "ing_eggs" })
        assertEquals(lines, changed.completionLines())
    }

    @Test fun activeCookingPrecedesRealCardsEvenWhenServerCacheIsAbsent() {
        assertTrue(foodBriefServerCards(null, emptySet(), true).isEmpty())
        val leading = NowBriefCardExtension("food-active", 0, beforeRealCards = true) {}
        val trailing = NowBriefCardExtension("food:recommended", 40) {}
        val real = listOf(BriefCardId.NOTIFICATIONS, BriefCardId.CAR)
        fun order(extensions: List<NowBriefCardExtension>) =
            briefRealContent(real, extensions, { it.key }, { it.id })
        assertEquals(listOf("food-active", "notifications", "car"), order(listOf(leading)))
        assertEquals(listOf("food-active", "notifications", "car", "food:recommended"), order(listOf(trailing, leading)))
        val cache = FoodContract.decode<BriefCardsResponse>(foodFixture("brief-cards"))
        val cards = foodBriefServerCards(cache, emptySet(), true)
        assertEquals(cards.sortedByDescending { it.priority }, cards)
        assertTrue(foodBriefServerCards(cache, setOf(cards.first().id), true).none { it.id == cards.first().id })
        assertTrue(foodBriefServerCards(cache, emptySet(), false).isEmpty())
    }
}
