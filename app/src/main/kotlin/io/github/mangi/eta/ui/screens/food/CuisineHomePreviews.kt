package io.github.mangi.eta.ui.screens.food

import androidx.compose.runtime.Composable
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import java.time.LocalDate

/** Synthetic library for the Cuisine page previews. */
internal object CuisineHomePreviewData {
    val today: LocalDate = LocalDate.of(2026, 11, 24)

    private fun summary(id: String, title: String, prep: Int, cook: Int, servings: Int = 2, favorite: Boolean = false,
        difficulty: Difficulty = Difficulty.EASY, tags: List<String> = emptyList()) = RecipeSummary(
        "preview-$id", 1, title, "", servings, prep, cook, difficulty, listOf(MealType.DINNER), "française", tags,
        image = null, favorite = favorite,
    )

    val pasta = summary("pasta", "Pâtes à la tomate et au basilic", 10, 15, favorite = true, tags = listOf("pâtes"))
    val omelette = summary("omelette", "Omelette aux herbes", 5, 7, tags = listOf("œufs"))
    val soup = summary("soup", "Velouté de potimarron", 15, 25, servings = 4, tags = listOf("soupe"))
    val curry = summary("curry", "Curry de pois chiches", 10, 25, servings = 4, difficulty = Difficulty.MEDIUM)
    val salad = summary("salad", "Salade de lentilles", 15, 0)
    val gratin = summary("gratin", "Gratin de courgettes et feta", 15, 30, difficulty = Difficulty.MEDIUM)

    val library = listOf(pasta, omelette, soup, curry, salad, gratin).libraryOrder()

    private fun suggestion(recipe: RecipeSummary, category: AvailabilityCategory, missing: Int, score: Double, vararg reasons: String) =
        RecipeSuggestion(recipe, AvailabilitySummary(if (missing == 0) 1.0 else 0.7, category, missing), score, reasons.toList())

    val suggestions = listOf(
        suggestion(omelette, AvailabilityCategory.READY, 0, 92.0),
        suggestion(gratin, AvailabilityCategory.READY, 0, 88.0, "USES_EXPIRING:Courgettes", "USES_EXPIRING:Feta"),
        suggestion(salad, AvailabilityCategory.READY_WITH_SUBSTITUTIONS, 1, 75.0),
        suggestion(soup, AvailabilityCategory.FEW_PURCHASES, 1, 64.0, "USES_EXPIRING:Potimarron"),
        suggestion(pasta, AvailabilityCategory.FEW_PURCHASES, 2, 60.0),
    )

    val tonight = MealSlot(today.toString(), Meal.DINNER, pasta.id, 1, 2, pasta)

    val recipes = RecipesTabUi(
        shortcuts = CuisineShortcutsUi(25, 3, 7, tonight, planningKnown = true),
        suggestionsState = CuisineContentState.READY,
        sections = suggestions.toSections(RecipeIntent(emptyList(), null), RecipeFilters()),
        library = library,
        libraryState = CuisineContentState.READY,
    )
    val loading = RecipesTabUi()
    val offlineEmpty = RecipesTabUi(
        shortcuts = CuisineShortcutsUi(planningKnown = false),
        suggestionsState = CuisineContentState.ERROR,
        suggestionsError = "Homelab injoignable",
        library = listOf(pasta, omelette),
        libraryState = CuisineContentState.READY,
        offline = true,
    )
    val filtered = recipes.copy(
        query = "20 min œufs",
        filters = RecipeFilters(difficulty = Difficulty.EASY),
        sections = suggestions.toSections(parseRecipeIntent("20 min œufs"), RecipeFilters(difficulty = Difficulty.EASY)),
        library = library.filter { it.matches(parseRecipeIntent("20 min œufs"), RecipeFilters(difficulty = Difficulty.EASY)) },
    )

    val actions = RecipesTabActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {})
}

@Composable
private fun RecipesPreview(state: RecipesTabUi) {
    CouiPreviewTheme {
        CuisineHomeScreen(CuisineTab.RECIPES, {}, CuisineHomePreviewData.today, {}, {}, {}) {
            RecipesTabContent(state, CuisineHomePreviewData.actions)
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CuisineRecipesPreview() = RecipesPreview(CuisineHomePreviewData.recipes)

@CouiPhonePreviews
@Composable
private fun CuisineRecipesLoadingPreview() = RecipesPreview(CuisineHomePreviewData.loading)

@CouiPhonePreviews
@Composable
private fun CuisineRecipesOfflinePreview() = RecipesPreview(CuisineHomePreviewData.offlineEmpty)

@CouiPhonePreviews
@Composable
private fun CuisineRecipesFilteredPreview() = RecipesPreview(CuisineHomePreviewData.filtered)

@CouiPhonePreviews
@Composable
private fun CuisineRecipesFiltersSheetPreview() {
    CouiPreviewTheme {
        CuisineHomeScreen(CuisineTab.RECIPES, {}, CuisineHomePreviewData.today, {}, {}, {}, overlay = {
            RecipeFiltersSheet(RecipeFilters(maxMinutes = 30), {}, {})
        }) {
            RecipesTabContent(CuisineHomePreviewData.recipes, CuisineHomePreviewData.actions)
        }
    }
}

private object ShoppingPreviewData {
    private val aisles = listOf(ShoppingAisle(1, "Fruits et légumes"), ShoppingAisle(2, "Crèmerie"), ShoppingAisle(null, "Autres"))
    private var nextId = 1L
    private fun item(name: String, amount: Double, unit: UnitCode, aisle: Long?, state: ShoppingItemState,
        origin: ShoppingOrigin = ShoppingOrigin.Manual, product: Long? = nextId) = ShoppingItem(
        nextId++, product, name, ShoppingLineQuantity(amount, 1, unit, unit.name.lowercase()), aisle, state,
        listOf(ShoppingContribution("c$nextId", ShoppingQuantity(amount, 1), origin)), "r$nextId",
    )
    private val thursday = ShoppingOrigin.MealSlot(CuisineHomePreviewData.salad.id, 1, "tomato", MealSlotKey("2026-11-26", Meal.LUNCH))
    private val pasta = ShoppingOrigin.Recipe(CuisineHomePreviewData.pasta.id, 1, "basil")
    private val items = listOf(
        item("Tomates cerises", 300.0, UnitCode.G, 1, ShoppingItemState.TODO, thursday),
        item("Basilic frais", 1.0, UnitCode.BUNCH, 1, ShoppingItemState.TODO, pasta),
        item("Pommes", 1.0, UnitCode.KG, 1, ShoppingItemState.IN_BASKET),
        item("Carottes", 1.0, UnitCode.KG, 1, ShoppingItemState.STOCKED),
        item("Parmesan râpé", 40.0, UnitCode.G, 2, ShoppingItemState.TODO, pasta),
        item("Yaourts nature", 4.0, UnitCode.PIECE, 2, ShoppingItemState.IN_BASKET),
        item("Serviettes", 1.0, UnitCode.PACK, null, ShoppingItemState.TODO, product = null),
    )
    private val projection = projectShopping(items, aisles)
    private val titles = mapOf(
        CuisineHomePreviewData.salad.id to CuisineHomePreviewData.salad.title,
        CuisineHomePreviewData.pasta.id to "Pâtes tomate",
    )

    fun state(storeMode: Boolean = false): ShoppingTabUi {
        val (groups, stocked) = projection.toTabUi(titles, "", storeMode)
        return ShoppingTabUi(CuisineContentState.READY, groups = groups, stocked = stocked,
            inBasket = projection.inBasketCount, total = projection.remainingCount + projection.inBasketCount, storeMode = storeMode)
    }
    val empty = ShoppingTabUi(CuisineContentState.READY)
    val actions = ShoppingTabActions({}, {}, { _, _ -> }, {}, {}, {}, {})
}

@Composable
private fun ShoppingPreview(state: ShoppingTabUi, overlay: @Composable () -> Unit = {}) {
    CouiPreviewTheme {
        CuisineHomeScreen(
            CuisineTab.SHOPPING, {}, CuisineHomePreviewData.today, {}, {}, {},
            bottomAction = if (state.inBasket > 0) ({ ShoppingBottomAction(state) {} }) else null,
            overlay = overlay,
        ) { ShoppingTabContent(state, ShoppingPreviewData.actions) }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CuisineShoppingPreview() = ShoppingPreview(ShoppingPreviewData.state())

@CouiPhonePreviews
@Composable
private fun CuisineShoppingStorePreview() = ShoppingPreview(ShoppingPreviewData.state(storeMode = true))

@CouiPhonePreviews
@Composable
private fun CuisineShoppingEmptyPreview() = ShoppingPreview(ShoppingPreviewData.empty)

@CouiPhonePreviews
@Composable
private fun CuisineShoppingPutAwayPreview() {
    val state = ShoppingPreviewData.state()
    ShoppingPreview(state) {
        PutAwaySheet(state.basketRows, { "Réfrigérateur" }, busy = false, error = null, onClose = {}, onConfirm = {})
    }
}

private object PlanningPreviewData {
    private val d = CuisineHomePreviewData
    private fun slot(day: Int, meal: Meal, recipe: RecipeSummary, servings: Int = 2) =
        MealSlot(d.today.minusDays(1).plusDays(day.toLong()).toString(), meal, recipe.id, 1, servings, recipe)
    private val week = MealWeek(1, "2026-11-23", "Europe/Paris", "r1", listOf(
        slot(0, Meal.DINNER, d.gratin), slot(1, Meal.DINNER, d.pasta), slot(2, Meal.DINNER, d.curry, 4),
        slot(3, Meal.LUNCH, d.salad), slot(5, Meal.DINNER, d.soup, 4),
    ))
    private val now = java.time.LocalDateTime.of(2026, 11, 24, 18, 40).atZone(java.time.ZoneId.of("Europe/Paris")).toInstant()
    private val summary = ShoppingSummary(1, "2026-11-23", "r1", "t1", "2026-11-24T17:00:00Z", listOf(
        ShoppingSummaryItem(1, "Basilic frais", 1, UnitCode.BUNCH, 1.0, 0.0, 1.0),
        ShoppingSummaryItem(2, "Lentilles vertes", 1, UnitCode.G, 250.0, 0.0, 250.0),
        ShoppingSummaryItem(3, "Lait de coco", 1, UnitCode.CL, 40.0, 0.0, 40.0),
        ShoppingSummaryItem(4, "Feta", 1, UnitCode.G, 200.0, 0.0, 200.0),
    ), emptyList())
    val state = PlanningTabUi(LocalDate.parse("2026-11-23"), week.project(now), d.today, CuisineContentState.READY, summary = summary)
    val moving = state.copy(moving = MealSlotKey("2026-11-24", Meal.DINNER))
    val empty = PlanningTabUi(LocalDate.parse("2026-11-23"), week.copy(slots = emptyList()).project(now), d.today, CuisineContentState.READY)
    val preview = GeneratePreviewResult(1, "2026-11-23", "Europe/Paris", "r1", listOf(
        slot(2, Meal.LUNCH, d.omelette), slot(4, Meal.DINNER, d.salad),
    ), "p1", "2026-11-24T19:10:00Z")
    val actions = PlanningTabActions({}, {}, {}, {}, {}, {})
}

@Composable
private fun PlanningPreview(state: PlanningTabUi, overlay: @Composable () -> Unit = {}) {
    CouiPreviewTheme {
        CuisineHomeScreen(CuisineTab.PLANNING, {}, CuisineHomePreviewData.today, {}, {}, {}, overlay = overlay) {
            PlanningTabContent(state, PlanningPreviewData.actions)
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CuisinePlanningPreview() = PlanningPreview(PlanningPreviewData.state)

@CouiPhonePreviews
@Composable
private fun CuisinePlanningMovingPreview() = PlanningPreview(PlanningPreviewData.moving)

@CouiPhonePreviews
@Composable
private fun CuisinePlanningEmptyPreview() = PlanningPreview(PlanningPreviewData.empty)

@CouiPhonePreviews
@Composable
private fun CuisinePlanningGeneratePreview() = PlanningPreview(PlanningPreviewData.state) {
    GeneratePreviewSheet(PlanningPreviewData.preview, null, false, {}, {})
}

private object PantryPreviewData {
    private val grams = StockUnit(1, "g", UnitCode.G)
    private val pieces = StockUnit(2, "pièce", UnitCode.PIECE)
    private var id = 0L
    private fun product(name: String, amount: Double, unit: StockUnit, location: Long, days: Long?, kind: LocationKind?) = PantryItem(
        ++id, name, name.lowercase(), amount, 0.0, unit, location,
        days?.let { CuisineHomePreviewData.today.plusDays(it).toString() },
        if (days != null && days <= 2) PantryStatus.DUE_SOON else PantryStatus.AVAILABLE, 0.0, StockConfidence.CONFIRMED,
        listOf(PantryBatch("s$id", amount, days?.let { CuisineHomePreviewData.today.plusDays(it).toString() }, location, false,
            locationKind = kind)),
    )
    val pantry = PantryResponse(1, "2026-11-24T17:40:00Z",
        listOf(PantryLocation(1, "Réfrigérateur"), PantryLocation(2, "Congélateur"), PantryLocation(3, "Placard")),
        listOf(
            product("Courgettes", 3.0, pieces, 1, 1, LocationKind.FRIDGE),
            product("Épinards", 150.0, grams, 1, 1, LocationKind.FRIDGE),
            product("Feta", 200.0, grams, 1, 5, LocationKind.FRIDGE),
            product("Potimarron", 1.0, pieces, 3, 2, LocationKind.PANTRY),
            product("Petits pois", 500.0, grams, 2, 120, LocationKind.FREEZER),
            product("Spaghettis", 500.0, grams, 3, 300, LocationKind.PANTRY),
            product("Lentilles vertes", 250.0, grams, 3, null, null),
        ))
    val empty = pantry.copy(items = emptyList())
}

@Composable
private fun PantryPreview(pantry: PantryResponse?, soonOnly: Boolean = false) {
    CouiPreviewTheme {
        CuisineHomeScreen(CuisineTab.PANTRY, {}, CuisineHomePreviewData.today, {}, {}, {}) {
            InventoryContent(pantry, CuisineHomePreviewData.today, null, null, soonOnly, "", {}, {}, {}, {}, {}, {}, onFindRecipe = {})
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CuisinePantryPreview() = PantryPreview(PantryPreviewData.pantry)

@CouiPhonePreviews
@Composable
private fun CuisinePantryEmptyPreview() = PantryPreview(PantryPreviewData.empty)
