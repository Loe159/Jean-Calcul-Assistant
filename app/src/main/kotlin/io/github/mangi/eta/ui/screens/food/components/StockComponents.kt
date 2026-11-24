package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference

@Composable
internal fun StockProductRow(product: PantryItem, onEdit: () -> Unit) {
    val label = when (product.status) {
        PantryStatus.DUE_SOON -> R.string.food_due_soon
        PantryStatus.EXPIRED -> R.string.food_expired
        PantryStatus.BELOW_MIN -> R.string.food_below_min
        PantryStatus.OUT_OF_STOCK -> R.string.food_out_of_stock
        PantryStatus.AVAILABLE -> null
    }
    val quantity = product.unit.unitCode?.let { FoodQuantities.formatFr(Quantity(product.amount, it)) }
        ?: "${product.amount} ${product.unit.name}"
    val summary = listOfNotNull(
        quantity,
        product.bestBeforeDate,
        label?.let { stringResource(it) },
        if (product.confidence == StockConfidence.ESTIMATED) stringResource(R.string.food_estimated) else null,
    ).joinToString(" · ")
    ArrowPreference(title = product.name, summary = summary, onClick = onEdit)
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
