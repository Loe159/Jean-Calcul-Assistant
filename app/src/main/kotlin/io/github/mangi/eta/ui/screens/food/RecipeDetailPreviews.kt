package io.github.mangi.eta.ui.screens.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.mangi.eta.food.AvailabilityStatus
import io.github.mangi.eta.food.FoodResult
import io.github.mangi.eta.food.Meal
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.suqi8.coui.kmp.basic.SnackbarHostState

private object RecipeDetailPreviewData {
    val steps = listOf("Préparer les ingrédients", "Cuire les pâtes", "Lancer la sauce", "Assembler", "Servir")
    val missing = RecipeDetailUi(
        card = FoodPreviewData.pasta.copy(favorite = true), activeMinutes = 15, portions = 2, basePortions = 2,
        availability = FoodPreviewData.missing, stockAsOf = "2026-11-24T17:40:00Z",
        checklist = FoodPreviewData.checklist, steps = steps, favorite = true, allergenNotice = true,
    )
    val ready = missing.copy(
        card = FoodPreviewData.omelette, availability = FoodPreviewData.ready, favorite = false,
        checklist = FoodPreviewData.checklist.copy(ingredients = FoodPreviewData.checklist.ingredients.filter {
            it.status == AvailabilityStatus.AVAILABLE
        }),
        allergenNotice = false,
    )
    val cooking = missing.copy(cookingHere = true)
    val actions = RecipeDetailActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
}

@Composable
private fun DetailPreview(state: RecipeDetailUi, overlay: @Composable () -> Unit = {}) {
    CouiPreviewTheme {
        RecipeDetailContent(state, RecipeDetailPreviewData.actions, remember { SnackbarHostState() }, overlay = overlay)
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun RecipeDetailMissingPreview() = DetailPreview(RecipeDetailPreviewData.missing)

@CouiPhonePreviews
@Composable
private fun RecipeDetailReadyPreview() = DetailPreview(RecipeDetailPreviewData.ready)

@CouiPhonePreviews
@Composable
private fun RecipeDetailCookingPreview() = DetailPreview(RecipeDetailPreviewData.cooking)

@CouiPhonePreviews
@Composable
private fun RecipeDetailPlanSheetPreview() = DetailPreview(RecipeDetailPreviewData.missing) {
    PlanRecipeSheet(
        today = CuisineHomePreviewData.today, servings = 2, busy = false, error = null as FoodResult.Failure?,
        occupant = { _, meal -> if (meal == Meal.DINNER) "Curry de pois chiches" else null },
        onClose = {}, onConfirm = { _, _ -> },
    )
}
