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
