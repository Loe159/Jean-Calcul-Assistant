package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.screens.food.FoodComponentPreview
import io.github.mangi.eta.ui.screens.food.CuisineNarrowFontPreview
import io.github.mangi.eta.ui.screens.food.FoodPreviewData
import io.github.mangi.eta.food.FoodQuantities
import io.github.mangi.eta.ui.screens.food.ShoppingItemStatus
import io.github.mangi.eta.ui.screens.food.ShoppingItemUi
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.preference.CheckboxLocation
import io.github.suqi8.coui.kmp.preference.CheckboxPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun ShoppingItemRow(
    item: ShoppingItemUi,
    storeMode: Boolean,
    onCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    pressed: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    val height = if (storeMode) 84.dp else 68.dp
    if (loading) {
        CuisineLoading(modifier.heightIn(min = height), lines = 1)
        return
    }
    val colors = COUITheme.colorScheme
    val quantity = item.quantityLabel ?: FoodQuantities.formatFr(item.quantity)
    val split = LocalDensity.current.fontScale <= 1.3f
    val title = if (split) item.title else stringResource(R.string.food_consumed_amount, item.title, quantity)
    val status = stringResource(when (item.status) {
        ShoppingItemStatus.TODO -> R.string.food_shopping_todo
        ShoppingItemStatus.IN_BASKET -> R.string.food_shopping_in_basket
        ShoppingItemStatus.STOCKED -> R.string.food_shopping_stocked
    })
    val rowModifier = modifier.heightIn(min = height)
    val ink = when {
        !enabled -> colors.disabledNeutral
        item.status == ShoppingItemStatus.IN_BASKET -> colors.labelSecondary
        else -> colors.labelPrimary
    }
    val editLabel = stringResource(R.string.food_shopping_edit, item.title)
    val quantitySlot: @Composable RowScope.() -> Unit = {
        if (split) Text(quantity, Modifier.align(Alignment.CenterVertically), style = if (storeMode) COUITheme.textStyles.headlineS else COUITheme.textStyles.headlineXS,
            color = ink)
        if (onEdit != null && !storeMode && item.status != ShoppingItemStatus.STOCKED) {
            CuisineIconButton(COUIIcons.Edit, editLabel, onEdit, enabled = enabled, ink = colors.labelSecondary)
        }
    }
    if (item.status == ShoppingItemStatus.STOCKED) {
        BasicComponent(
            title = title,
            summary = listOfNotNull(item.origin, status).joinToString(" · "),
            modifier = rowModifier,
            startAction = { Icon(CuisineIcons.Fridge, null, tint = cuisineTint(colors.green)) },
            endActions = quantitySlot,
        )
    } else {
        CheckboxPreference(
            title = title, checked = item.status == ShoppingItemStatus.IN_BASKET, onCheckedChange = onCheck,
            modifier = rowModifier.semantics {
                stateDescription = status
                toggleableState = ToggleableState(item.status == ShoppingItemStatus.IN_BASKET)
            },
            summary = listOfNotNull(item.origin, status.takeIf { item.status == ShoppingItemStatus.IN_BASKET })
                .joinToString(" · ").ifBlank { null },
            titleColor = BasicComponentDefaults.titleColor(color = ink),
            checkboxLocation = CheckboxLocation.Start, endActions = quantitySlot,
            holdDownState = pressed, enabled = enabled,
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@CouiComponentPreviews
@Composable
private fun ShoppingItemRowPreview() = FoodComponentPreview {
    Card {
        CardListGroup {
            item { ShoppingItemRow(FoodPreviewData.shopping, false, {}) }
            item { ShoppingItemRow(FoodPreviewData.basket, false, {}) }
            item { ShoppingItemRow(FoodPreviewData.stocked, false, {}) }
            item { ShoppingItemRow(FoodPreviewData.shopping, false, {}, pressed = true) }
            item { ShoppingItemRow(FoodPreviewData.basket, false, {}, enabled = false) }
            item { ShoppingItemRow(FoodPreviewData.shopping, false, {}, loading = true) }
            item { ShoppingItemRow(FoodPreviewData.shopping, true, {}) }
        }
    }
}

@CouiStressPreviews
@CuisineNarrowFontPreview
@Composable
private fun ShoppingItemRowStressPreview() = FoodComponentPreview {
    Card {
        CardListGroup {
            item { ShoppingItemRow(FoodPreviewData.shopping, true, {}) }
            item { ShoppingItemRow(FoodPreviewData.basket, false, {}) }
            item { ShoppingItemRow(FoodPreviewData.stocked, false, {}) }
        }
    }
}
