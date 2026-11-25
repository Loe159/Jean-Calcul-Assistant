package io.github.mangi.eta.ui.screens.food

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.Quantity
import io.github.mangi.eta.food.RecipeStep
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun CookingIngredientsSheet(
    checklist: IngredientChecklistUi,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    FoodSheet(stringResource(R.string.food_ingredients), onClose) {
        FoodSupporting(stringResource(R.string.food_check_out_ingredients))
        IngredientChecklist(checklist, onToggle, showStock = false)
    }
}

@Composable
internal fun CookingStepsSheet(
    progress: CookingProgressState,
    completed: Set<String>,
    skipped: Set<String>,
    onStep: (String) -> Unit,
    onClose: () -> Unit,
) {
    FoodLazySheet(stringResource(R.string.food_steps), onClose) {
        item { FoodSupporting(stringResource(R.string.food_choose_step)) }
        itemsIndexed(progress.steps, key = { _, step -> step.id }) { index, step ->
            val status = when {
                index == progress.index -> R.string.food_step_current
                step.id in completed -> R.string.food_step_done
                step.id in skipped -> R.string.food_step_skipped
                else -> R.string.food_step_upcoming
            }
            CardListGroup {
                item {
                    CookingSheetRow(
                        title = stringResource(R.string.food_numbered_step, index + 1, step.title),
                        summary = listOfNotNull(
                            stringResource(status),
                            (step.timerSuggestion?.durationSeconds ?: step.durationSeconds)?.let { timerDuration(it.toLong()) },
                        ).joinToString(" · "),
                        onClick = { onStep(step.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun CookingHowToSheet(step: RecipeStep, onClose: () -> Unit) {
    FoodSheet(stringResource(R.string.food_how_to), onClose) {
        FoodHeading(step.title)
        RecipePicture(step.image)
        step.tips.forEach { FoodSupporting(it) }
        FoodAction(R.string.food_return_to_step, primary = true, onClick = onClose)
    }
}

@Composable
internal fun CookingOptionsSheet(
    awake: Boolean,
    servings: Int,
    busy: Boolean,
    onAwake: (Boolean) -> Unit,
    onServings: (Int) -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    FoodSheet(stringResource(R.string.food_cooking_options), onClose) {
        CardListGroup {
            item {
                FoodSwitchRow(stringResource(R.string.food_keep_awake), awake, onChange = onAwake)
            }
            item {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FoodHeading(stringResource(R.string.food_portions))
                    PortionStepper(servings, 1..maxOf(servings, 12), onServings, enabled = !busy, large = true)
                }
            }
            item { CookingSheetRow(stringResource(R.string.food_step_skip), enabled = !busy, onClick = onSkip) }
            item { CookingSheetRow(stringResource(R.string.food_abandon_cooking), enabled = !busy, onClick = onStop) }
        }
    }
}

@Composable
private fun CookingSheetRow(title: String, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    BasicComponent(
        enabled = enabled,
        endActions = { Icon(COUIIcons.ChevronForward, null, tint = COUITheme.colorScheme.labelTertiary) },
        onClick = onClick,
    ) {
        Column(Modifier.heightIn(min = 56.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = COUITheme.textStyles.headlineXS)
            if (summary != null) FoodSupporting(summary)
        }
    }
}

@Composable
internal fun CookingIngredientSheet(
    line: IngredientPortion,
    busy: Boolean,
    onApply: (Quantity?) -> Unit,
    onClose: () -> Unit,
) {
    val quantity = line.quantity
    var raw by rememberSaveable(line.ingredient.id) { mutableStateOf(quantity.amount?.let(::foodAmountInput).orEmpty()) }
    val amount = raw.amountOrNull()
    FoodSheet(line.ingredient.name, onClose) {
        FoodTextField(
            raw, { raw = it }, label = unitName(quantity.unit), keyboardType = KeyboardType.Decimal,
            errorLabel = R.string.food_invalid_consumption.takeIf { raw.isNotBlank() && (amount == null || amount < 0) },
        )
        FoodAction(R.string.food_save, primary = true, enabled = !busy && amount != null && amount >= 0) {
            onApply(quantity.copy(amount = amount))
        }
        FoodAction(R.string.food_reset_quantity, enabled = !busy) { onApply(null) }
    }
}

@Composable
internal fun CookingConflictSheet(
    session: ActiveCookingUi,
    onResume: () -> Unit,
    onReplace: () -> Unit,
    onClose: () -> Unit,
    busy: Boolean = false,
) {
    FoodSheet(stringResource(R.string.food_cooking_conflict), onClose) {
        FoodHeading(session.title)
        FoodSupporting(stringResource(R.string.food_step_of_count, session.index + 1, session.count))
        FoodAction(R.string.food_resume_current_recipe, primary = true, enabled = !busy, onClick = onResume)
        FoodAction(R.string.food_replace_cooking, enabled = !busy, onClick = onReplace)
    }
}

@Composable
internal fun AbandonCookingDialog(replacing: Boolean, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    CouiConfirmDialog(
        visible = true,
        title = stringResource(R.string.food_abandon_cooking_question),
        message = stringResource(if (replacing) R.string.food_replace_cooking_detail else R.string.food_abandon_cooking_detail),
        confirmText = stringResource(if (replacing) R.string.food_replace_cooking_confirm else R.string.food_abandon_cooking),
        destructive = true,
        confirmEnabled = !busy,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
}
