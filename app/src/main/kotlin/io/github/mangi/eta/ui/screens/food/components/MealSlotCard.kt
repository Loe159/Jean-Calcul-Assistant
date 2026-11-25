package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.screens.food.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The parent selects a source and then a target. A filled target can request confirmation. */
@Composable
internal fun MealSlotCard(
    slot: MealSlotUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    moving: MealSlotMove = MealSlotMove.NONE,
    loading: Boolean = false,
    pressed: Boolean = false,
) {
    val colors = COUITheme.colorScheme
    val recipe = slot.recipe
    val action = when {
        moving == MealSlotMove.SOURCE -> R.string.food_meal_moving
        moving == MealSlotMove.TARGET && recipe != null -> R.string.food_meal_replace
        moving == MealSlotMove.TARGET -> R.string.food_meal_drop
        recipe == null -> R.string.food_meal_add
        else -> null
    }?.let { stringResource(it) }
    val date = slot.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
    val meal = stringArrayResource(R.array.food_meal_slot_types)[slot.meal.ordinal]
    val label = listOfNotNull(
        date, meal, recipe?.title, recipe?.let { portionLabel(it.portions) },
        recipe?.let { stringResource(R.string.food_minutes, it.totalMinutes) },
        slot.availability?.let { availabilityText(it, compact = false) }, action,
    ).joinToString(" · ")
    val fill = when {
        moving != MealSlotMove.NONE -> colors.primary.copy(alpha = 0.12f)
        recipe == null && !loading -> colors.backgroundWithCard
        else -> colors.cardBackground
    }
    val needsBorder = moving != MealSlotMove.NONE || recipe == null && !loading
    val shape = SquircleShape(18.dp)
    Surface(
        modifier.fillMaxWidth(),
        shape = shape,
        color = fill,
        border = if (needsBorder) BorderStroke(1.dp, if (moving != MealSlotMove.NONE) colors.primary else colors.divider) else null,
    ) {
        Card(
            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                .then(if (loading) Modifier else Modifier.semantics { contentDescription = label; role = Role.Button }),
            cornerRadius = 18.dp,
            colors = CardDefaults.defaultColors(color = if (pressed) colors.press else colors.cardBackground.copy(alpha = 0f)),
            onClick = onClick.takeUnless { loading },
        ) {
            when {
                loading -> CuisineLoading()
                recipe == null -> Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(8.dp).clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = if (moving == MealSlotMove.TARGET) CuisineIcons.Swap else COUIIcons.Add
                    Icon(icon, null, Modifier.size(16.dp), tint = colors.labelSecondary)
                    FoodSupporting(requireNotNull(action))
                }
                else -> BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(8.dp).clearAndSetSemantics {}) {
                    val largeFont = LocalDensity.current.fontScale > 1.3f
                    val showPicture = maxWidth >= 108.dp && !largeFont
                    val shortMoveLabel = largeFont && moving == MealSlotMove.SOURCE && maxWidth < 150.dp
                    val metadata = if (showPicture && maxWidth < 190.dp) {
                        stringResource(R.string.food_meal_compact_metadata, recipe.totalMinutes, recipe.portions)
                    } else stringResource(
                        R.string.food_meal_metadata, stringResource(R.string.food_minutes, recipe.totalMinutes),
                        portionLabel(recipe.portions),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showPicture) RecipePicture(
                                recipe.image, Modifier.size(48.dp).clip(SquircleShape(14.dp)), placeholder = true,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(recipe.shortTitle, style = COUITheme.textStyles.bodyM.copy(fontWeight = FontWeight.SemiBold))
                                FoodSupporting(metadata)
                            }
                        }
                        slot.availability?.takeUnless {
                            it.availability == RecipeAvailability.All && it.verification == StockVerification.VERIFIED
                        }?.let { AvailabilityChip(it, compact = true) }
                        if (action != null) {
                            val moveLabel = if (shortMoveLabel) {
                                stringResource(R.string.food_meal_moving_short)
                            } else action
                            FoodSupporting(moveLabel)
                        }
                    }
                }
            }
        }
    }
}

@CouiComponentPreviews
@Composable
private fun MealSlotCardPreview() = FoodComponentPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlotCard(FoodPreviewData.noMeal, {}, Modifier.weight(1f))
        MealSlotCard(FoodPreviewData.meal.copy(availability = FoodPreviewData.ready), {}, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlotCard(FoodPreviewData.meal, {}, Modifier.weight(1f), moving = MealSlotMove.SOURCE)
        MealSlotCard(FoodPreviewData.noMeal, {}, Modifier.weight(1f), moving = MealSlotMove.TARGET)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlotCard(FoodPreviewData.meal, {}, Modifier.weight(1f), pressed = true)
        MealSlotCard(FoodPreviewData.noMeal, {}, Modifier.weight(1f), loading = true)
    }
    MealSlotCard(FoodPreviewData.meal.copy(recipe = FoodPreviewData.soup), {}, moving = MealSlotMove.TARGET)
}

@CouiStressPreviews
@CuisineNarrowFontPreview
@Composable
private fun MealSlotCardStressPreview() = FoodComponentPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealSlotCard(FoodPreviewData.meal, {}, Modifier.weight(1f), moving = MealSlotMove.SOURCE)
        MealSlotCard(FoodPreviewData.noMeal, {}, Modifier.weight(1f), moving = MealSlotMove.TARGET)
    }
    MealSlotCard(FoodPreviewData.meal.copy(recipe = FoodPreviewData.soup), {}, moving = MealSlotMove.TARGET)
}
