package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Timer
import io.github.suqi8.coui.kmp.icon.extended.Edit
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
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
    val quantity = stockAmount(product.amount, product.unit)
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

@Composable
internal fun FoodStockRow(
    item: FoodStockRowUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressed: Boolean = false,
    state: CuisineContentState = CuisineContentState.READY,
) {
    if (state == CuisineContentState.LOADING) {
        CuisineLoading(modifier.heightIn(min = 68.dp))
        return
    }
    val colors = COUITheme.colorScheme
    val (icon, defaultPlace, fill) = when (item.place) {
        FoodStockPlace.FRIDGE -> Triple(CuisineIcons.Fridge, R.string.food_place_fridge, colors.containerAzure)
        FoodStockPlace.FREEZER -> Triple(CuisineIcons.Freezer, R.string.food_place_freezer, colors.containerBlue)
        FoodStockPlace.PANTRY -> Triple(CuisineIcons.Pantry, R.string.food_place_pantry, colors.containerOrange)
        FoodStockPlace.OTHER -> Triple(CuisineIcons.Pantry, null, colors.container8)
    }
    val invalidQuantity = state == CuisineContentState.ERROR
    val quantity = if (invalidQuantity) stringResource(R.string.food_invalid_stock_quantity)
        else stockAmount(item.amount, item.unit)
    val place = item.placeName ?: defaultPlace?.let { stringResource(it) }
    val expiry = item.daysLeft?.let { expiryText(it) } ?: item.bestBefore?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.FRANCE).format(it)
    } ?: when (item.status) {
        PantryStatus.EXPIRED -> stringResource(R.string.food_expired)
        PantryStatus.DUE_SOON -> stringResource(R.string.food_due_soon)
        else -> null
    }
    val stockStatus = when (item.status) {
        PantryStatus.BELOW_MIN -> stringResource(R.string.food_below_min)
        PantryStatus.OUT_OF_STOCK -> stringResource(R.string.food_out_of_stock)
        else -> null
    }
    val estimated = if (item.confidence == StockConfidence.ESTIMATED) stringResource(R.string.food_estimated) else null
    val label = listOfNotNull(item.title, quantity, place, expiry, stockStatus, estimated).joinToString(" · ")
    val useTrailingExpiry = LocalDensity.current.fontScale <= 1.3f
    val summary = listOfNotNull(quantity, place, if (useTrailingExpiry) null else expiry, stockStatus, estimated)
        .joinToString(" · ")
    BasicComponent(
        title = item.title,
        summary = summary,
        summaryColor = if (invalidQuantity) BasicComponentDefaults.summaryColor(color = cuisineTint(colors.error))
            else BasicComponentDefaults.summaryColor(),
        modifier = modifier.heightIn(min = 68.dp).semantics {
            contentDescription = label
            role = Role.Button
        },
        startAction = {
            val iconFill = if (invalidQuantity && enabled) colors.containerRed else fill
            Surface(Modifier.size(36.dp), shape = SquircleShape(12.dp), color = iconFill) {
                Box(contentAlignment = Alignment.Center) {
                    val ink = when {
                        !enabled -> colors.disabledNeutral
                        invalidQuantity -> cuisineTint(colors.error)
                        else -> colors.labelPrimary
                    }
                    Icon(if (invalidQuantity) CuisineIcons.Warning else icon, null, Modifier.size(21.dp), tint = ink)
                }
            }
        },
        endActions = {
            if (useTrailingExpiry && item.daysLeft != null) ExpiryLabel(item.daysLeft, enabled = enabled)
            else if (useTrailingExpiry && expiry != null) FoodSupporting(expiry)
            Icon(COUIIcons.Edit, null, Modifier.padding(start = 8.dp).size(18.dp),
                tint = if (enabled) colors.labelSecondary else colors.disabledNeutral)
        },
        onClick = onClick, enabled = enabled, holdDownState = pressed,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun ExpiryLabel(daysLeft: Long?, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    if (loading) {
        val label = stringResource(R.string.food_loading)
        Surface(
            modifier.width(60.dp).height(12.dp).semantics { contentDescription = label },
            shape = SquircleShape(6.dp), color = COUITheme.colorScheme.container8,
        ) {}
        return
    }
    val colors = COUITheme.colorScheme
    val tint = when {
        !enabled -> colors.disabledNeutral
        daysLeft == null || daysLeft > 3 -> colors.labelSecondary
        daysLeft <= 0 -> cuisineTint(colors.error)
        else -> cuisineTint(colors.orange)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (daysLeft != null && daysLeft <= 3) {
            Icon(if (daysLeft <= 0) CuisineIcons.Warning else COUIIcons.Timer, null, Modifier.size(14.dp), tint = tint)
        }
        Text(expiryText(daysLeft), style = COUITheme.textStyles.bodyXS, color = tint)
    }
}

@Composable
private fun expiryText(daysLeft: Long?): String = when {
    daysLeft == null -> stringResource(R.string.food_expiry_unknown)
    daysLeft == -1L -> stringResource(R.string.food_expired_yesterday)
    daysLeft < 0 -> pluralStringResource(R.plurals.food_expired_days, (-daysLeft).toInt(), -daysLeft)
    daysLeft == 0L -> stringResource(R.string.food_expiry_today)
    daysLeft == 1L -> stringResource(R.string.food_expiry_tomorrow)
    daysLeft < 14 -> pluralStringResource(R.plurals.food_expiry_days, daysLeft.toInt(), daysLeft)
    daysLeft < 60 -> {
        val weeks = (daysLeft / 7.0).roundToInt()
        pluralStringResource(R.plurals.food_expiry_weeks, weeks, weeks)
    }
    daysLeft < 700 -> stringResource(R.string.food_expiry_months, (daysLeft / 30.0).roundToInt())
    else -> stringResource(R.string.food_expiry_long)
}

private fun stockAmount(amount: Double, unit: StockUnit) =
    unit.unitCode?.let { FoodQuantities.formatFr(Quantity(amount, it)) } ?: "$amount ${unit.name}"

@CouiComponentPreviews
@Composable
private fun ExpiryLabelPreview() = FoodComponentPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ExpiryLabel(12)
        ExpiryLabel(21)
        ExpiryLabel(180)
        ExpiryLabel(900)
    }
    ExpiryLabel(3)
    ExpiryLabel(1)
    ExpiryLabel(0)
    ExpiryLabel(-1)
    ExpiryLabel(-5)
    ExpiryLabel(null)
    ExpiryLabel(null, loading = true)
}

@CouiComponentPreviews
@Composable
private fun FoodStockRowPreview() = FoodComponentPreview {
    Card {
        CardListGroup {
            item { FoodStockRow(FoodPreviewData.pantry, {}) }
            item { FoodStockRow(FoodPreviewData.stock, {}) }
            item { FoodStockRow(FoodPreviewData.expired, {}) }
            item { FoodStockRow(FoodPreviewData.pantry.copy(title = "Petits pois", place = FoodStockPlace.FREEZER), {}) }
            item { FoodStockRow(FoodPreviewData.stock, {}, pressed = true) }
            item { FoodStockRow(FoodPreviewData.stock, {}, enabled = false) }
            item { FoodStockRow(FoodPreviewData.stock, {}, state = CuisineContentState.ERROR) }
            item { FoodStockRow(FoodPreviewData.stock, {}, state = CuisineContentState.LOADING) }
        }
    }
}
