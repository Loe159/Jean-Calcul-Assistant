package io.github.mangi.eta.ui.screens.food

import io.github.mangi.eta.food.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShoppingTabModelsTest {
    private var id = 0L
    private fun line(name: String, product: Long?, aisle: Long?, state: ShoppingItemState, origin: ShoppingOrigin = ShoppingOrigin.Manual) =
        ShoppingItem(++id, product, name, ShoppingLineQuantity(1.0, 1, UnitCode.PIECE, "pièce"), aisle, state,
            listOf(ShoppingContribution("c$id", ShoppingQuantity(1.0, 1), origin)), "r$id")

    private val aisles = listOf(ShoppingAisle(1, "Légumes"), ShoppingAisle(null, "Autres"))
    private val slot = ShoppingOrigin.MealSlot("salad", 1, "tomato", MealSlotKey("2026-11-26", Meal.LUNCH))
    private val items = listOf(
        line("Tomates", 10, 1, ShoppingItemState.TODO, slot),
        line("Tomates", 10, 1, ShoppingItemState.IN_BASKET, ShoppingOrigin.Recipe("pasta", 1, "tomato")),
        line("Pommes", 11, 1, ShoppingItemState.IN_BASKET),
        line("Carottes", 12, 1, ShoppingItemState.STOCKED),
        line("Serviettes", null, null, ShoppingItemState.TODO),
    )
    private val projection = projectShopping(items, aisles)

    @Test
    fun `a product with one line left to buy stays to buy, with its meal origin`() {
        val (groups, stocked) = projection.toTabUi(mapOf("salad" to "Salade"), "", storeMode = false)
        val tomatoes = groups.flatMap { it.rows }.single { it.row.name == "Tomates" }
        assertEquals(ShoppingRowState.TODO, tomatoes.state)
        assertEquals(ShoppingOriginUi.Meal(LocalDate.of(2026, 11, 26), Meal.LUNCH, "Salade"), tomatoes.origin)
        assertEquals(listOf("Carottes"), stocked.map { it.row.name })
        assertEquals(listOf("Légumes", "Autres"), groups.map { it.name })
    }

    @Test
    fun `store mode lists what is left aisle by aisle, then the basket`() {
        val (groups, stocked) = projection.toTabUi(emptyMap(), "", storeMode = true)
        assertEquals(listOf("Légumes", "Autres", STORE_BASKET), groups.map { it.name })
        assertEquals(listOf("Pommes"), groups.last().rows.map { it.row.name })
        assertEquals(1, stocked.size)
    }

    @Test
    fun `search filters rows and free text lines cannot be stocked`() {
        val (groups, _) = projection.toTabUi(emptyMap(), "servi", storeMode = false)
        val towels = groups.single().rows.single()
        assertEquals("Serviettes", towels.row.name)
        assertEquals(false, towels.canStock)
    }
}
