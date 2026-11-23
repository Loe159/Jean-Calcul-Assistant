package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.suqi8.coui.kmp.basic.Text

@Composable
internal fun StockProductRow(product: PantryItem, onEdit: () -> Unit) {
    FoodSection {
        FoodHeading(product.name)
        Text(product.unit.unitCode?.let { FoodQuantities.formatFr(Quantity(product.amount, it)) }
            ?: "${product.amount} ${product.unit.name}")
        product.bestBeforeDate?.let { FoodSupporting(it) }
        ExpiryIndicator(product.status, product.confidence)
        FoodAction(R.string.food_correct_stock, onClick = onEdit)
    }
}

@Composable
internal fun ExpiryIndicator(status: PantryStatus, confidence: StockConfidence) {
    val label = when (status) {
        PantryStatus.DUE_SOON -> R.string.food_due_soon
        PantryStatus.EXPIRED -> R.string.food_expired
        PantryStatus.BELOW_MIN -> R.string.food_below_min
        PantryStatus.OUT_OF_STOCK -> R.string.food_out_of_stock
        PantryStatus.AVAILABLE -> null
    }
    if (label == null && confidence != StockConfidence.ESTIMATED) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { FoodSupporting(stringResource(it)) }
        if (confidence == StockConfidence.ESTIMATED) {
            Text(stringResource(R.string.food_estimated), color = io.github.suqi8.coui.kmp.theme.COUITheme.colorScheme.primary)
        }
    }
}

internal fun unitName(unit: UnitCode) = FoodQuantities.formatFr(Quantity(1.0, unit)).substringAfter(' ')

@Composable
internal fun OperationResultLines(result: OperationResult, ingredientNames: Map<String, String> = emptyMap()) {
    FoodSupporting(stringResource(when (result.state) {
        OperationState.SUCCEEDED -> R.string.food_succeeded
        OperationState.PARTIAL -> R.string.food_partial
        OperationState.FAILED -> R.string.food_failed
        OperationState.UNCERTAIN -> R.string.food_uncertain
    }))
    result.lines.filter { it.state != OperationLineState.SUCCEEDED }.forEach { line ->
        line.ingredientId?.let { ingredientNames[it] }?.let { Text(it) }
        val label = when (line.state) {
            OperationLineState.NEEDS_CONVERSION -> R.string.food_needs_conversion
            OperationLineState.INSUFFICIENT_STOCK -> R.string.food_insufficient_stock
            OperationLineState.UNCERTAIN -> R.string.food_uncertain
            OperationLineState.SKIPPED_NOT_TRACKED -> R.string.food_not_tracked
            else -> R.string.food_failed
        }
        FoodSupporting(stringResource(label))
        line.message?.let { FoodSupporting(it) }
    }
}
