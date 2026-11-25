package io.github.mangi.eta.ui.screens.food

import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.components.CompletionForm
import io.github.mangi.eta.ui.screens.food.components.availabilityLabel
import io.github.mangi.eta.ui.screens.food.components.completionRows
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
        val finished = paused.copy(id = "t4", state = TimerState.FINISHED, remainingSeconds = null)
        val timerState = session.copy(
            currentStepId = second,
            completedStepIds = listOf(first),
            skippedStepIds = emptyList(),
            timers = listOf(running, paused, running.copy(id = "t3", state = TimerState.CANCELLED), finished),
        )
        assertEquals(second, timerState.progress().step?.id)
        assertEquals(1, timerState.progress().finished)
        assertEquals(listOf(running, paused, finished), timerState.activeTimers())
        val now = Instant.parse("2026-11-23T18:01:00.100Z").toEpochMilli()
        assertEquals(420L, running.secondsLeft(now))
        assertEquals(0L, running.secondsLeft(now + 900_000))
        assertEquals(130L, paused.secondsLeft(now + 900_000))
    }

    @Test fun backFromCompletionPreservesServingsAndRatingWithoutUsingStockEdits() {
        val rows = session.completionRows(null, amounts = mapOf("ing_eggs" to "3,5"),
            selections = session.recipeSnapshot.ingredients.associate { it.id to (it.id == "ing_eggs") })
        val form = CompletionForm("4", "5", rows, "1", "2026-11-28", 1, listOf(PantryLocation(1, "Frigo")))
        val confirmed = checkNotNull(form.stockDraft())
        assertEquals(1, confirmed.leftovers?.servings)
        assertEquals(3.5, confirmed.consumptions.single().quantity.amount, 0.0)
        val back = form.exitDraft(session.servings)
        assertEquals(4, back.servingsMade)
        assertEquals(5, back.rating)
        assertTrue(back.consumptions.isEmpty())
        assertNull(back.leftovers)
        assertEquals(back, form.copy(leftovers = "invalide").exitDraft(session.servings))
    }

    @Test fun completionDefaultsToExactPortionsAndEditsOnlyAffectConfirmedLines() {
        val changed = session.copy(
            servings = 4, ingredientOverrides = listOf(IngredientOverride("ing_eggs", Quantity(3.0, UnitCode.PIECE))),
        )
        val lines = changed.completionRows(null).mapNotNull { it.consumption() }
        assertEquals(MeasuredQuantity(500.0, UnitCode.G), lines.single { it.ingredientId == "ing_courgette" }.quantity)
        assertEquals(MeasuredQuantity(3.0, UnitCode.PIECE), lines.single { it.ingredientId == "ing_eggs" }.quantity)
        val edited = changed.completionRows(null, mapOf("ing_courgette" to "350"), mapOf("ing_eggs" to false))
            .mapNotNull { it.consumption() }
        assertEquals(MeasuredQuantity(350.0, UnitCode.G), edited.single { it.ingredientId == "ing_courgette" }.quantity)
        assertFalse(edited.any { it.ingredientId == "ing_eggs" })
        assertEquals(lines, changed.completionRows(null).mapNotNull { it.consumption() })
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
