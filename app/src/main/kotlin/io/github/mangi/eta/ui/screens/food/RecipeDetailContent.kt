package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Months
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

internal data class RecipeDetailUi(
    val card: RecipeCardUi,
    val activeMinutes: Int,
    val portions: Int,
    val basePortions: Int,
    val availability: RecipeAvailabilityUi,
    val stockAsOf: String?,
    val checklist: IngredientChecklistUi,
    val steps: List<String>,
    val favorite: Boolean,
    val allergenNotice: Boolean,
    /** The active cooking session is this recipe: the main action resumes it. */
    val cookingHere: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val stockError: String? = null,
)

internal class RecipeDetailActions(
    val onBack: () -> Unit,
    val onFavorite: (Boolean) -> Unit,
    val onPortions: (Int) -> Unit,
    val onToggleIngredient: (String, Boolean) -> Unit,
    val onAddMissing: () -> Unit,
    val onAdapt: () -> Unit,
    val onPlan: () -> Unit,
    val onOptions: () -> Unit,
    val onStart: () -> Unit,
    val onRetry: () -> Unit,
)

/** Portions offered on the recipe page; Mastra accepts up to 24 servings per recipe. */
internal val RecipePortionRange = 1..24

@Composable
internal fun RecipeDetailContent(
    state: RecipeDetailUi,
    actions: RecipeDetailActions,
    snackbars: SnackbarHostState,
    dock: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
) {
    val colors = COUITheme.colorScheme
    Scaffold(containerColor = colors.backgroundWithCard) { _ ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
                RecipeHero(state.card, state.portions, state.favorite, actions.onBack, actions.onFavorite)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecipeMetrics(state)
                    if (state.error != null) CuisineMessage(
                        state.error, icon = CuisineIcons.Warning, isError = true, onRetry = actions.onRetry,
                    )
                    AvailabilityBanner(state)
                    PortionsCard(state, actions.onPortions)
                    RecipeActions(actions)
                    CuisineSectionHeader(stringResource(R.string.food_ingredients))
                    IngredientChecklist(
                        state.checklist, actions.onToggleIngredient,
                        onAddMissing = actions.onAddMissing.takeUnless { state.busy },
                    )
                    if (state.steps.isNotEmpty()) {
                        CuisineSectionHeader(pluralStringResource(R.plurals.food_step_count, state.steps.size, state.steps.size))
                        StepsOverview(state.steps)
                    }
                    if (state.allergenNotice) FoodSupporting(stringResource(R.string.food_allergen_notice))
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                SnackbarHost(snackbars, Modifier.fillMaxWidth())
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(colors.backgroundWithCard.copy(alpha = 0f), colors.backgroundWithCard)))
                        .padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp),
                ) {
                    Button(
                        actions.onStart, Modifier.fillMaxWidth(), enabled = !state.busy && state.steps.isNotEmpty(),
                        minHeight = 56.dp, colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(CuisineIcons.Pot, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (state.cookingHere) R.string.food_resume_cooking else R.string.food_start_cooking),
                            style = COUITheme.textStyles.buttonL,
                        )
                    }
                }
                dock?.invoke()
                Spacer(Modifier.navigationBarsPadding())
            }
        }
        overlay()
    }
}

@Composable
private fun RecipeMetrics(state: RecipeDetailUi) {
    val levels = stringArrayResource(R.array.food_difficulties)
    val metrics = listOf(
        stringResource(R.string.food_total_time) to stringResource(R.string.food_minutes, state.card.totalMinutes),
        stringResource(R.string.food_active_time) to stringResource(R.string.food_minutes, state.activeMinutes),
        stringResource(R.string.food_difficulty) to levels[state.card.difficulty.ordinal],
    )
    val tile: @Composable (Pair<String, String>, Modifier) -> Unit = { (label, value), modifier ->
        Surface(modifier.semantics(mergeDescendants = true) {}, shape = SquircleShape(16.dp), color = COUITheme.colorScheme.cardBackground) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FoodSupporting(label)
                Text(value, style = COUITheme.textStyles.headlineS)
            }
        }
    }
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { metrics.forEach { tile(it, Modifier.fillMaxWidth()) } }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.forEach { tile(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/** The prototype's tinted banner: what is missing, in words, with the stock date. */
@Composable
private fun AvailabilityBanner(state: RecipeDetailUi) {
    val colors = COUITheme.colorScheme
    val status = state.availability.availability
    val fill = when {
        state.availability.verification != StockVerification.VERIFIED -> colors.cardBackground
        status is RecipeAvailability.All -> colors.containerGreen
        status is RecipeAvailability.Partial -> colors.containerOrange
        status is RecipeAvailability.Missing -> colors.containerRed
        else -> colors.cardBackground
    }
    val rows = state.checklist.ingredients.filterNot { it.portion.ingredient.optional }
    val missing = rows.filter { it.status == AvailabilityStatus.MISSING }.map { it.portion.ingredient.name }
    val partial = rows.filter { it.status == AvailabilityStatus.PARTIAL }.mapNotNull { row ->
        row.stock?.inStock?.let {
            stringResource(R.string.food_partial_line, row.portion.ingredient.name,
                FoodQuantities.formatFr(Quantity(it.amount, it.unit)), row.portion.display())
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = SquircleShape(24.dp), color = fill.copy(alpha = if (COUITheme.isDark) 1f else 0.7f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AvailabilityChip(state.availability)
            val details = buildList {
                if (missing.isNotEmpty()) add(pluralStringResource(R.plurals.food_missing_line, missing.size, missing.joinToString(", ")))
                addAll(partial)
            }
            if (details.isNotEmpty()) Text(details.joinToString("\n"), style = COUITheme.textStyles.bodyM)
            state.stockAsOf?.let { FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(it))) }
            state.stockError?.let { FoodErrorMessage(it) }
        }
    }
}

@Composable
private fun PortionsCard(state: RecipeDetailUi, onPortions: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        val stacked = LocalDensity.current.fontScale > 1.3f
        val label: @Composable () -> Unit = {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.food_portions), style = COUITheme.textStyles.headlineXS)
                FoodSupporting(stringResource(R.string.food_recipe_for, portionLabel(state.basePortions)))
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                label()
                PortionStepper(state.portions, RecipePortionRange, onPortions, enabled = !state.busy)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { label() }
                PortionStepper(state.portions, RecipePortionRange, onPortions, enabled = !state.busy)
            }
        }
    }
}

@Composable
private fun RecipeActions(actions: RecipeDetailActions) {
    val items: List<Triple<ImageVector, Int, () -> Unit>> = listOf(
        Triple(CuisineIcons.Sparkle, R.string.food_adapt_short, actions.onAdapt),
        Triple(COUIIcons.Months, R.string.food_plan_short, actions.onPlan),
        Triple(COUIIcons.More, R.string.food_more_options, actions.onOptions),
    )
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (icon, label, onClick) ->
            val text = stringResource(label)
            Card(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = text }, onClick = onClick) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(8.dp).clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(icon, null, Modifier.size(22.dp), tint = COUITheme.colorScheme.labelPrimary)
                    Text(text, style = COUITheme.textStyles.bodyS, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun StepsOverview(steps: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            steps.forEachIndexed { index, title ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(Modifier.size(28.dp), shape = SquircleShape(14.dp), color = COUITheme.colorScheme.container8) {
                        Box(contentAlignment = Alignment.Center) {
                            Text((index + 1).toString(), style = COUITheme.textStyles.buttonS)
                        }
                    }
                    Text(title, Modifier.weight(1f), style = COUITheme.textStyles.bodyL)
                }
            }
        }
    }
}
