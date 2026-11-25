package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.Surface
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

private object CookingPreviewData {
    val step = FoodPreviewData.cooking
    val states = CookingScreenState(FoodPreviewData.pasta.shortTitle, 2, 7, step, setOf(0, 1))
    val steps = listOf(
        "Préparer les ingrédients", "Émincer l’ail", step.step.title, "Cuire les pâtes",
        "Garder l’eau de cuisson", "Mélanger", "Servir",
    ).mapIndexed { i, title ->
        step.step.copy(
            id = "preview-step-${i + 1}", title = title, order = i + 1,
            durationSeconds = if (i == 2) step.step.durationSeconds else null,
            timerSuggestion = if (i == 2) step.step.timerSuggestion else null,
        )
    }
    val ingredients = FoodPreviewData.ingredients.mapIndexed { index, row ->
        val tracked = index < 6
        row.portion.ingredient.copy(grocyProductId = if (tracked) index.toLong() + 1 else null)
    }
    val recipe = RecipeDefinition(
        id = "preview-cooking", version = 1, grocyRecipeId = null, title = FoodPreviewData.pasta.title,
        description = "", servings = 2, prepTimeMinutes = 10, cookTimeMinutes = 15, activeTimeMinutes = 15,
        difficulty = Difficulty.EASY, mealTypes = listOf(MealType.DINNER), cuisine = null, ingredients = ingredients,
        steps = steps, equipment = emptyList(), tags = emptyList(),
        source = RecipeSource(RecipeSourceKind.MANUAL, null, null, null), image = null, nutritionEstimate = null,
        allergenCheck = AllergenCheck.NOT_GUARANTEED, createdAt = "2026-01-12T18:00:00Z", updatedAt = "2026-01-12T18:00:00Z",
    )
    private val session = CookingSession(
        "preview-cooking-session", recipe.id, 1, recipe, 2, steps[2].id, listOf(steps[0].id, steps[1].id),
        emptyList(), emptyList(), emptyList(), CookingStatus.ACTIVE, recipe.createdAt, null,
        StockConsumptionStatus.NOT_REQUESTED, 1, recipe.updatedAt,
    )
    val progress = session.progress()
    val locations = listOf(PantryLocation(1, "Frigo"), PantryLocation(2, "Congélateur"))
    private val pantry = PantryResponse(
        1, recipe.updatedAt, locations, FoodPreviewData.ingredients.mapIndexedNotNull { index, row ->
            val quantity = row.stock?.inStock ?: return@mapIndexedNotNull null
            val name = row.portion.ingredient.name
            PantryItem(index.toLong() + 1, name, name, quantity.amount, 0.0,
                StockUnit(1, quantity.unit.name, quantity.unit), 1, null, PantryStatus.AVAILABLE,
                0.0, StockConfidence.CONFIRMED, emptyList())
        },
    )
    val form = CompletionForm("2", rows = session.completionRows(pantry), locations = locations, selectedLocation = 1)
    val partial = CompletionReplay(
        StockConsumptionStatus.PARTIAL,
        OperationResult(1, "op_preview", OperationState.PARTIAL, false, listOf(
            OperationLine(ingredients.first().id, 1, Quantity(200.0, UnitCode.G), null, null,
                OperationLineState.NEEDS_CONVERSION, null),
        )), withoutStock = false,
    )
    val timerActions = TimerActions({}, {}, { _, _ -> }, {})
}

@Composable
private fun PreviewCooking(
    state: CookingScreenState = CookingPreviewData.states,
    overlay: @Composable () -> Unit = {},
) {
    CouiPreviewTheme {
        CompositionLocalProvider(LocalFoodSheetPreview provides { title, close, body -> OpenFoodSheetPreview(title, close, body) }) {
            CookingScreen(
                state, onMinimize = {}, onPrevious = {}, onNext = {}, onSheet = {}, onIngredient = {},
                onTimerDrawer = {}, onStartTimer = {}, onNewTimer = {}, onTextSize = {}, onAllowExact = {},
                timerActions = CookingPreviewData.timerActions, overlay = overlay,
            )
        }
    }
}

@Composable
private fun OpenFoodSheetPreview(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    val colors = COUITheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.mask), contentAlignment = Alignment.BottomCenter) {
        Surface(
            Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.78f),
            shape = SquircleShape(28.dp),
            color = colors.surface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                Surface(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp),
                    shape = SquircleShape(2.dp), color = colors.labelTertiary) {}
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CuisineIconButton(COUIIcons.Basic.Close, stringResource(R.string.food_close), onClose, neutralFocus = true)
                    Text(title, style = COUITheme.textStyles.headlineS)
                }
                content()
            }
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingScreenPreview() = PreviewCooking()

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingAlarmPreview() = PreviewCooking(
    CookingPreviewData.states.copy(
        timers = listOf(FoodPreviewData.running, FoodPreviewData.done),
        stepTimer = FoodPreviewData.running,
    ),
)

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingDrawerPreview() = PreviewCooking(
    CookingPreviewData.states.copy(
        timers = listOf(FoodPreviewData.running, FoodPreviewData.paused), drawerExpanded = true,
    ),
)

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingLargeTextPreview() = PreviewCooking(CookingPreviewData.states.copy(largeText = true))

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingLastStepPreview() = PreviewCooking(
    CookingPreviewData.states.copy(
        index = 6, completed = (0..5).toSet(),
        step = FoodPreviewData.noTimerStep.copy(number = 7,
            step = CookingPreviewData.steps.last(), instruction = "Répartis les pâtes dans les assiettes. Ajoute le basilic."),
    ),
)

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingIngredientsSheetPreview() = PreviewCooking {
    CookingIngredientsSheet(FoodPreviewData.checklist.copy(stockKnown = false), { _, _ -> }, {})
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingStepsSheetPreview() = PreviewCooking {
    val progress = CookingPreviewData.progress
    CookingStepsSheet(progress, progress.steps.take(2).map { it.id }.toSet(), emptySet(), {}, {})
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingHowToSheetPreview() = PreviewCooking {
    CookingHowToSheet(CookingPreviewData.step.step, {})
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingAssistantSheetPreview() = PreviewCooking {
    val proposal = AssistantProposal.AdjustDuration("preview-proposal", "Prolonger la cuisson de 2 min",
        ProposalImpact.MAJOR, DurationPatch(CookingPreviewData.step.step.id, 840))
    AssistantAction(AssistantResponse(1, "req_preview", FoodPreviewData.suggestion, listOf(proposal), false),
        null, false, emptyList(), {}, {}, {})
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingOptionsSheetPreview() = PreviewCooking {
    CookingOptionsSheet(true, 2, false, {}, {}, {}, {}, {})
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingIngredientAdjustmentPreview() = PreviewCooking {
    CookingIngredientSheet(FoodPreviewData.spaghetti.portion, false, {}, {})
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingConflictSheetPreview() {
    CouiPreviewTheme {
        CompositionLocalProvider(LocalFoodSheetPreview provides { title, close, body -> OpenFoodSheetPreview(title, close, body) }) {
            FoodPage(stringResource(R.string.food_detail), {}, overlay = {
                CookingConflictSheet(FoodPreviewData.session, {}, {}, {})
            }) {
                RecipeOverview(CookingPreviewData.recipe, null)
                FoodAction(R.string.food_start_cooking, primary = true, onClick = {})
            }
        }
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingCompletionPreview() {
    CouiPreviewTheme {
        CookingCompletionScreen(CompletionScreenState(CookingPreviewData.recipe, CookingPreviewData.form), CompletionActions())
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingCompletionPartialPreview() {
    CouiPreviewTheme {
        CookingCompletionScreen(CompletionScreenState(CookingPreviewData.recipe, CookingPreviewData.form,
            favorite = true, replay = CookingPreviewData.partial), CompletionActions())
    }
}

@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun CookingDockPreview() {
    CouiPreviewTheme {
        CompositionLocalProvider(LocalCookingDock provides {
            CookingDockContent(FoodPreviewData.session, FoodPreviewData.running, {})
        }) {
            FoodPage(stringResource(R.string.food_home), {}) {
                RecipeOverview(CookingPreviewData.recipe, null)
                FoodAction(R.string.food_start_cooking, primary = true, onClick = {})
            }
        }
    }
}
