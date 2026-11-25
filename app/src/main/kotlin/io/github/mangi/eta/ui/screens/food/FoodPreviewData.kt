package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import java.time.LocalDate

/** Fictitious fixtures. All recipe imagery uses the offline placeholder. */
internal object FoodPreviewData {
    val missing = RecipeAvailabilityUi(RecipeAvailability.Missing(2))
    val partial = RecipeAvailabilityUi(RecipeAvailability.Partial(1))
    val ready = RecipeAvailabilityUi(RecipeAvailability.All)
    val offline = missing.copy(verification = StockVerification.OFFLINE)

    val pasta = RecipeCardUi("Pâtes à la tomate et au basilic", 25, 2, Difficulty.EASY,
        shortTitle = "Pâtes tomate", type = "Plat", reason = "Prévu à 19:30")
    val omelette = RecipeCardUi("Omelette aux herbes", 12, 2, Difficulty.EASY,
        shortTitle = "Omelette", type = "Œufs", favorite = true)
    val soup = RecipeCardUi("Velouté de potimarron", 40, 4, Difficulty.EASY,
        shortTitle = "Velouté", type = "Soupe", reason = "Potimarron à finir d’ici 2 jours")

    private fun ingredient(
        id: String, name: String, amount: Double?, unit: UnitCode,
        status: AvailabilityStatus = AvailabilityStatus.AVAILABLE, available: Double? = amount,
    ): IngredientRowUi {
        val quantity = Quantity(amount, unit)
        val source = RecipeIngredient(id, null, name, quantity,
            if (amount == null) Scaling.TO_TASTE else Scaling.LINEAR,
            preparation = null, group = null, optional = false, substitutions = emptyList())
        return IngredientRowUi(
            IngredientPortion(source, quantity),
            AvailabilityItem(id, status, quantity, available?.let { MeasuredQuantity(it, unit) },
                if (amount != null && available != null && available < amount) MeasuredQuantity(amount - available, unit) else null,
                null, null),
        )
    }

    val spaghetti = ingredient("preview-spaghetti", "Spaghettis", 200.0, UnitCode.G, available = 500.0)
    private val tomato = ingredient("preview-tomato", "Tomates pelées", 400.0, UnitCode.G)
    private val garlic = ingredient("preview-garlic", "Ail", 2.0, UnitCode.CLOVE, available = 6.0)
    private val oil = ingredient("preview-oil", "Huile d’olive", 4.0, UnitCode.CL, available = 60.0)
    val basil = ingredient("preview-basil", "Basilic frais", 1.0, UnitCode.BUNCH, AvailabilityStatus.MISSING, 0.0)
    val parmesan = ingredient("preview-parmesan", "Parmesan râpé", 40.0, UnitCode.G, AvailabilityStatus.PARTIAL, 30.0)
    val oregano = ingredient("preview-oregano", "Origan séché", 1.0, UnitCode.TSP).copy(replaces = "le basilic frais")
    private val salt = ingredient("preview-salt", "Sel", null, UnitCode.PINCH, AvailabilityStatus.NOT_TRACKED)
    val ingredients = listOf(spaghetti.copy(checked = true), tomato, garlic, oil, basil, parmesan, salt)
    val checklist = IngredientChecklistUi(ingredients, 2, stockKnown = true)

    private val sauceStep = RecipeStep(
        "preview-sauce", 3, "Lancer la sauce",
        "Fais chauffer {{ing:preview-oil}} d’huile d’olive à feu moyen. Ajoute l’ail 1 minute, puis {{ing:preview-tomato}} de tomates pelées. Laisse mijoter en remuant de temps en temps.",
        listOf(oil, garlic, tomato).map { IngredientRef(it.id, 1.0) },
        durationSeconds = 720, temperature = null, timerSuggestion = TimerSuggestion("Sauce", 720),
        equipment = listOf(Equipment.STOVE), image = null, tips = listOf("Remue régulièrement"),
    )
    private val recipe = RecipeDefinition(
        id = "preview-pasta", version = 1, grocyRecipeId = null, title = pasta.title, description = "Une recette de démonstration",
        servings = 2, prepTimeMinutes = 10, cookTimeMinutes = 15, activeTimeMinutes = 15,
        difficulty = Difficulty.EASY, mealTypes = listOf(MealType.DINNER), cuisine = null,
        ingredients = ingredients.map { it.portion.ingredient }, steps = listOf(sauceStep),
        equipment = listOf(Equipment.STOVE), tags = emptyList(),
        source = RecipeSource(RecipeSourceKind.MANUAL, null, null, null), image = null, nutritionEstimate = null,
        allergenCheck = AllergenCheck.NOT_GUARANTEED, createdAt = "2026-01-12T18:00:00Z", updatedAt = "2026-01-12T18:00:00Z",
    )
    val cooking = recipe.toCookingStepUi(sauceStep, 3, 2)
        .copy(heat = "Feu moyen", tool = "Sauteuse")
    val cookingFourPortions = recipe.toCookingStepUi(sauceStep, 3, 4).copy(heat = cooking.heat, tool = cooking.tool)
    val noTimerStep = CookingStepUi(5,
        sauceStep.copy(title = "Garder l’eau de cuisson", timerSuggestion = null, equipment = emptyList()),
        "Avant d’égoutter, prélève un petit verre d’eau de cuisson. Égoutte les pâtes.",
    )

    val checklistFourPortions: IngredientChecklistUi = run {
        val snapshot = Availability(1, recipe.id, recipe.version, recipe.servings, recipe.createdAt, recipe.createdAt,
            ingredients.mapNotNull { it.stock }, AvailabilitySummary(4.0 / 6.0, AvailabilityCategory.FEW_PURCHASES, 2))
        val scaled = snapshot.recompute(recipe, 4).items.associateBy { it.ingredientId }
        val rows = recipe.portions(4).map { portion -> IngredientRowUi(portion, scaled[portion.ingredient.id]) }
        IngredientChecklistUi(rows, 4, stockKnown = true)
    }

    val running = CookingTimerUi("preview-timer-sauce", "Sauce", 720, 380, TimerState.RUNNING, 3)
    val paused = CookingTimerUi("preview-timer-pasta", "Pâtes", 540, 260, TimerState.PAUSED, 4)
    val done = CookingTimerUi("preview-timer-water", "Eau", 480, 0, TimerState.FINISHED, 2)
    val doneToo = CookingTimerUi("preview-timer-mix", "Mélange", 60, 0, TimerState.FINISHED, 6)
    val session = ActiveCookingUi("preview-session", pasta.shortTitle, 3, 7, "Cuire les pâtes")

    private val grams = StockUnit(1, "g", UnitCode.G)
    val stock = FoodStockRowUi("preview-stock-spinach", "Épinards", 150.0, grams, FoodStockPlace.FRIDGE,
        bestBefore = LocalDate.of(2026, 1, 13), daysLeft = 1, status = PantryStatus.DUE_SOON)
    val pantry = FoodStockRowUi("preview-stock-pasta", "Spaghettis", 500.0, grams, FoodStockPlace.PANTRY, daysLeft = 300)
    val expired = stock.copy(id = "preview-stock-basil", title = "Basilic", amount = 20.0, daysLeft = -1,
        bestBefore = LocalDate.of(2026, 1, 11), status = PantryStatus.EXPIRED)

    val shopping = ShoppingItemUi("preview-buy-tomatoes", "Tomates cerises", Quantity(300.0, UnitCode.G),
        ShoppingItemStatus.TODO, origin = "Pour la salade de jeudi")
    val basket = ShoppingItemUi("preview-buy-apples", "Pommes", Quantity(1.0, UnitCode.KG), ShoppingItemStatus.IN_BASKET)
    val stocked = ShoppingItemUi("preview-buy-carrot", "Carottes", Quantity(1.0, UnitCode.KG), ShoppingItemStatus.STOCKED)

    val meal = MealSlotUi(LocalDate.of(2026, 1, 12), MealType.DINNER, pasta, missing)
    val noMeal = MealSlotUi(LocalDate.of(2026, 1, 13), MealType.LUNCH)
    const val suggestion = "Tu peux remplacer le basilic par de l’origan séché. Ajoute-le cinq minutes avant la fin."
    val suggestionActions = listOf(AssistantSuggestionAction("use", "Utiliser l’origan"), AssistantSuggestionAction("more", "Autre idée"))
}

@Composable
internal fun FoodComponentPreview(content: @Composable ColumnScope.() -> Unit) {
    CouiPreviewTheme(fullScreen = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Preview(name = "320 dp · 200 %", group = "Stress", widthDp = 320, fontScale = 2f, showBackground = true)
internal annotation class CuisineNarrowFontPreview
