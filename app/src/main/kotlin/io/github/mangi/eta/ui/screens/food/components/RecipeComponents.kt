package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.screens.food.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.preference.CheckboxLocation
import io.github.suqi8.coui.kmp.preference.CheckboxPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic

@Composable
internal fun RecipeOverview(recipe: RecipeDefinition, availability: Availability?) {
    FoodSection {
        RecipePicture(recipe.image)
        Text(recipe.title, Modifier.semantics { heading() }, style = COUITheme.textStyles.headlineS)
        FoodSupporting(stringResource(R.string.food_recipe_times, recipe.prepTimeMinutes + recipe.cookTimeMinutes, recipe.activeTimeMinutes))
        FoodSupporting(stringArrayResource(R.array.food_difficulties)[recipe.difficulty.ordinal])
        if (recipe.description.isNotBlank()) Text(recipe.description)
        if (availability == null) {
            FoodSupporting(stringResource(R.string.food_stock_unknown))
        } else {
            FoodSupporting(stringResource(availability.summary.category.label()))
            FoodSupporting(stringResource(R.string.food_stock_as_of, stockDate(availability.stockAsOf)))
        }
    }
}

@Composable
internal fun PortionSelector(servings: Int, title: Int = R.string.food_portions, onChange: (Int) -> Unit) {
    OverlayDropdownPreference(
        title = stringResource(title),
        items = (1..12).map(Int::toString),
        selectedIndex = servings - 1,
        modifier = Modifier.heightIn(min = 48.dp),
        onSelectedIndexChange = { onChange(it + 1) },
    )
}

@Composable
internal fun IngredientChecklist(ingredients: List<IngredientPortion>, availability: Availability? = null, showStock: Boolean = true) {
    val stock = availability?.items.orEmpty().associateBy { it.ingredientId }
    for (line in ingredients) {
        val ingredient = line.ingredient
        key(ingredient.id) {
            var checked by rememberSaveable { mutableStateOf(false) }
            val item = stock[ingredient.id]
            val details = buildList {
                ingredient.preparation?.let { add(it) }
                if (ingredient.optional) add(stringResource(R.string.food_optional))
                if (showStock) add(ingredientAvailabilityText(item))
                item?.substitutionAvailable?.let { add(stringResource(R.string.food_substitution, it.name)) }
                ingredient.substitutions.filterNot { it.name == item?.substitutionAvailable?.name }.forEach {
                    add(stringResource(R.string.food_substitution, it.name))
                    if (!it.note.isNullOrBlank()) add(it.note)
                }
            }
            CheckboxPreference(
                title = stringResource(R.string.food_consumed_amount, ingredient.name, line.display()),
                summary = details.joinToString("\n").ifEmpty { null },
                checked = checked,
                onCheckedChange = { checked = it },
                checkboxLocation = CheckboxLocation.Start,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

internal fun availabilityLabel(item: AvailabilityItem?): Int = when (item?.status) {
    AvailabilityStatus.AVAILABLE -> R.string.food_known_available
    AvailabilityStatus.NOT_TRACKED -> R.string.food_not_tracked
    AvailabilityStatus.PARTIAL, AvailabilityStatus.MISSING, AvailabilityStatus.OPTIONAL_MISSING ->
        if (item.missing != null) R.string.food_missing else R.string.food_stock_unknown
    else -> R.string.food_stock_unknown
}

@Composable
internal fun IngredientAvailability(item: AvailabilityItem?) {
    FoodSupporting(ingredientAvailabilityText(item))
}

@Composable
private fun ingredientAvailabilityText(item: AvailabilityItem?): String {
    val label = availabilityLabel(item)
    return if (label == R.string.food_missing) {
        val missing = requireNotNull(item?.missing)
        stringResource(label, FoodQuantities.formatFr(Quantity(missing.amount, missing.unit)))
    } else stringResource(label)
}

internal fun stockDate(value: String): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.FRANCE)
        .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(value))
}.getOrDefault(value)

@Composable
internal fun RecipeCard(
    recipe: RecipeCardUi?,
    variant: RecipeCardVariant,
    availability: RecipeAvailabilityUi?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: CuisineContentState = CuisineContentState.READY,
    onRetry: (() -> Unit)? = null,
    pressed: Boolean = false,
) {
    val cardModifier = if (variant == RecipeCardVariant.S) modifier.width(172.dp) else modifier.fillMaxWidth()
    val contentState = if (recipe == null && state == CuisineContentState.READY) CuisineContentState.EMPTY else state
    val clickable = recipe != null && contentState == CuisineContentState.READY
    val largeFont = LocalDensity.current.fontScale > 1.3f
    val label = if (clickable) listOf(
        requireNotNull(recipe).title,
        stringResource(R.string.food_minutes, recipe.totalMinutes),
        portionLabel(recipe.portions),
        availability?.let { availabilityText(it, compact = false) },
        if (recipe.favorite) stringResource(R.string.food_favorite) else null,
    ).filterNotNull().joinToString(" · ") else null
    Card(
        modifier = cardModifier.then(if (label == null) Modifier else Modifier.semantics {
            role = Role.Button
            contentDescription = label
        }),
        colors = CardDefaults.defaultColors(color = if (pressed) COUITheme.colorScheme.cardPressed else COUITheme.colorScheme.cardBackground),
        onClick = onClick.takeIf { clickable },
    ) {
        when (contentState) {
            CuisineContentState.LOADING -> RecipeCardLayout(variant, {
                Surface(it, color = COUITheme.colorScheme.container8) {}
            }) { CuisineLoading(lines = 3) }
            CuisineContentState.ERROR -> RecipeCardLayout(variant, {
                RecipePicture(null, it, placeholder = true)
            }) {
                CuisineMessage(
                    stringResource(R.string.food_recipe_unavailable),
                    detail = stringResource(R.string.food_recipe_load_failed),
                    onRetry = onRetry,
                    isError = true,
                )
            }
            CuisineContentState.EMPTY -> CuisineMessage(
                stringResource(R.string.food_no_recipes),
                detail = stringResource(R.string.food_recipe_empty_filters),
                icon = CuisineIcons.Utensils,
            )
            CuisineContentState.READY -> {
                val item = requireNotNull(recipe)
                RecipeCardLayout(variant, { imageModifier ->
                    Box(imageModifier.clearAndSetSemantics {}) {
                        RecipePicture(item.image, Modifier.matchParentSize(), placeholder = true, showLabel = variant == RecipeCardVariant.L)
                        if (availability?.verification == StockVerification.OFFLINE && variant == RecipeCardVariant.L) {
                            val labelId = if (largeFont) R.string.food_saved_on_device_short else R.string.food_saved_on_device
                            CuisineBadge(stringResource(labelId), Modifier.padding(8.dp), CuisineIcons.Offline)
                        }
                        if (item.favorite && variant == RecipeCardVariant.S) {
                            Icon(COUIIcons.FavoritesFill, null, Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                                tint = COUITheme.colorScheme.red)
                        }
                    }
                }) {
                    RecipeCardDetails(item, variant, availability)
                }
            }
        }
    }
}

@Composable
private fun RecipeCardLayout(
    variant: RecipeCardVariant,
    image: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    if (variant == RecipeCardVariant.R) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbnail = if (LocalDensity.current.fontScale > 1.3f) 64.dp else 92.dp
            image(Modifier.size(thumbnail).clip(SquircleShape(16.dp)))
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column {
            image(Modifier.fillMaxWidth().height(if (variant == RecipeCardVariant.L) 168.dp else 112.dp))
            Box(Modifier.padding(if (variant == RecipeCardVariant.L) 16.dp else 12.dp)) { content() }
        }
    }
}

@Composable
private fun RecipeCardDetails(recipe: RecipeCardUi, variant: RecipeCardVariant, availability: RecipeAvailabilityUi?) {
    val styles = COUITheme.textStyles
    val colors = COUITheme.colorScheme
    val large = variant == RecipeCardVariant.L
    val compact = variant == RecipeCardVariant.S
    Column(
        Modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(if (large) 6.dp else 4.dp),
    ) {
        if (large) recipe.reason?.let { Text(it, style = styles.bodyS, color = cuisineTint(colors.orange)) }
        Text(if (compact) recipe.shortTitle else recipe.title, style = if (large) styles.headlineM else styles.headlineXS)
        if (variant == RecipeCardVariant.R) recipe.reason?.let { FoodSupporting(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(COUIIcons.Timer, null, Modifier.size(14.dp), tint = colors.labelSecondary)
                FoodSupporting(stringResource(R.string.food_minutes, recipe.totalMinutes))
            }
            if (!compact) FoodSupporting(portionLabel(recipe.portions))
            FoodSupporting(stringArrayResource(R.array.food_difficulties)[recipe.difficulty.ordinal])
        }
        if (availability != null) AvailabilityChip(availability, compact = !large)
    }
}

@Composable
internal fun RecipeHero(
    recipe: RecipeCardUi,
    portions: Int,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    // Derive a stable dark veil from the corresponding COUI neutral in each mode.
    val scrim = if (COUITheme.isDark) colors.backgroundWithCard else colors.labelPrimary.copy(alpha = 1f)
    val favoriteLabel = stringResource(if (isFavorite) R.string.food_remove_favorite else R.string.food_add_favorite)
    val backLabel = stringResource(R.string.food_back)
    Box(modifier.fillMaxWidth().heightIn(min = 300.dp)) {
        RecipePicture(recipe.image, Modifier.matchParentSize(), placeholder = true, showLabel = true)
        Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.TopCenter).background(
            Brush.verticalGradient(listOf(scrim.copy(alpha = 0.34f), scrim.copy(alpha = 0f))),
        ))
        Column(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                CuisineNeutralFocus {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = backLabel },
                        material = false, backgroundColor = scrim.copy(alpha = 0.72f), cornerRadius = 24.dp,
                        minHeight = 48.dp, minWidth = 48.dp,
                    ) {
                        Icon(COUIIcons.Back, null, tint = colors.onPrimary)
                    }
                    IconButton(
                        onClick = { onFavorite(!isFavorite) },
                        modifier = Modifier.size(48.dp).semantics {
                            contentDescription = favoriteLabel
                            role = Role.Button
                            toggleableState = ToggleableState(isFavorite)
                        },
                        material = false, backgroundColor = scrim.copy(alpha = 0.72f), cornerRadius = 24.dp,
                        minHeight = 48.dp, minWidth = 48.dp,
                    ) {
                        Icon(
                            if (isFavorite) COUIIcons.FavoritesFill else COUIIcons.Favorites, null,
                            tint = if (isFavorite) colors.redVariant else colors.onPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(104.dp))
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(
                    0f to scrim.copy(alpha = 0f),
                    0.24f to scrim.copy(alpha = 0.74f),
                    1f to scrim.copy(alpha = 0.9f),
                )).padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val subtitle = listOfNotNull(recipe.type, portionLabel(portions)).joinToString(" · ")
                CuisineBadge(subtitle, ink = colors.onPrimary, fill = scrim.copy(alpha = 0.5f), compact = true)
                Text(recipe.title, Modifier.semantics { heading() }, color = colors.onPrimary,
                    style = COUITheme.textStyles.displayS)
            }
        }
    }
}

@Composable
internal fun PortionStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val colors = COUITheme.colorScheme
    val target = if (large) 56.dp else 48.dp
    Surface(modifier, shape = SquircleShape(32.dp), color = colors.container8) {
        Row(
            Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CuisineIconButton(CuisineIcons.Minus, stringResource(R.string.food_less_servings), { onChange((value - 1).coerceIn(range)) },
                size = target, enabled = enabled && !range.isEmpty() && value > range.first, fill = colors.secondaryButton)
            val label = portionLabel(value)
            Column(
                Modifier.widthIn(min = 72.dp).clearAndSetSemantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ink = if (enabled) colors.labelPrimary else colors.disabledNeutral
                Text(value.toString(), style = COUITheme.textStyles.displayXS, color = ink)
                Text(pluralStringResource(R.plurals.food_portion_word, value), style = COUITheme.textStyles.bodyXS,
                    color = if (enabled) colors.labelSecondary else colors.disabledNeutral)
            }
            CuisineIconButton(COUIIcons.Add, stringResource(R.string.food_more_servings), { onChange((value + 1).coerceIn(range)) },
                size = target, enabled = enabled && !range.isEmpty() && value < range.last, fill = colors.secondaryButton)
        }
    }
}

@Composable
internal fun portionLabel(value: Int) = pluralStringResource(R.plurals.food_portion_count, value, value)

@Composable
internal fun AvailabilityChip(
    model: RecipeAvailabilityUi,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = COUITheme.colorScheme
    val text = availabilityText(model, compact)
    val liveModifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    if (model.state == CuisineContentState.LOADING) {
        Row(liveModifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(size = 16.dp)
            FoodSupporting(text)
        }
    } else {
        val status = model.availability
        val (icon, ink, fill) = when {
            model.state == CuisineContentState.ERROR -> Triple(CuisineIcons.Warning, colors.error, colors.containerRed)
            status == RecipeAvailability.Unknown -> Triple(COUIIcons.Info, colors.labelSecondary, colors.container8)
            status == RecipeAvailability.All -> Triple(COUIIcons.Ok, colors.green, colors.containerGreen)
            status is RecipeAvailability.Partial -> Triple(CuisineIcons.Warning, colors.orange, colors.containerOrange)
            else -> Triple(CuisineIcons.Basket, colors.red, colors.containerRed)
        }
        val uncertain = model.verification != StockVerification.VERIFIED && model.state == CuisineContentState.READY
        CuisineBadge(
            text, liveModifier, if (uncertain) CuisineIcons.Offline else icon,
            if (uncertain || (status == RecipeAvailability.Unknown && model.state != CuisineContentState.ERROR)) {
                colors.labelPrimary
            } else cuisineTint(ink),
            if (uncertain) colors.container8 else fill, compact,
        )
    }
}

@Composable
internal fun availabilityText(model: RecipeAvailabilityUi, compact: Boolean): String {
    val status = model.availability
    val label = when {
        model.state == CuisineContentState.LOADING -> stringResource(R.string.food_checking_stock)
        model.state == CuisineContentState.ERROR -> stringResource(R.string.food_stock_unavailable)
        status is RecipeAvailability.All -> stringResource(if (compact) R.string.food_available_short else R.string.food_all_available)
        status is RecipeAvailability.Partial -> pluralStringResource(
            if (compact) R.plurals.food_partial_short else R.plurals.food_partial_ingredients, status.count, status.count,
        )
        status is RecipeAvailability.Missing -> pluralStringResource(
            if (compact) R.plurals.food_missing_short else R.plurals.food_missing_ingredients, status.count, status.count,
        )
        else -> stringResource(if (compact) R.string.food_stock_unknown_short else R.string.food_stock_unrecorded)
    }
    return if (model.state == CuisineContentState.READY && model.verification != StockVerification.VERIFIED) {
        stringResource(R.string.food_unverified_availability, label, stringResource(
            if (model.verification == StockVerification.OFFLINE) R.string.food_stock_offline else R.string.food_stock_unverified,
        ))
    } else label
}

@Composable
internal fun IngredientRow(
    ingredient: IngredientRowUi,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    pressed: Boolean = false,
    showStock: Boolean = true,
) {
    if (loading) {
        CuisineLoading(modifier.heightIn(min = 60.dp))
        return
    }
    val colors = COUITheme.colorScheme
    val portion = ingredient.portion
    val source = portion.ingredient
    val quantity = portion.display()
    val split = LocalDensity.current.fontScale <= 1.3f
    val stock = ingredient.stock
    val details = buildList {
        source.preparation?.let(::add)
        if (source.optional) add(stringResource(R.string.food_optional))
        ingredient.replaces?.let { add(stringResource(R.string.food_replaces, it)) }
        if (showStock) when (ingredient.status) {
            AvailabilityStatus.AVAILABLE -> Unit
            AvailabilityStatus.PARTIAL -> stock?.inStock?.let {
                add(stringResource(R.string.food_have_over_need, FoodQuantities.formatFr(Quantity(it.amount, it.unit)), quantity))
            }
            AvailabilityStatus.MISSING, AvailabilityStatus.OPTIONAL_MISSING -> add(stringResource(R.string.food_not_in_stock))
            AvailabilityStatus.NOT_TRACKED -> if (portion.quantity.amount != null) add(stringResource(R.string.food_not_tracked)) else Unit
            AvailabilityStatus.UNKNOWN -> add(stringResource(R.string.food_stock_unknown))
        }
        stock?.substitutionAvailable?.let { add(stringResource(R.string.food_substitution, it.name)) }
        source.substitutions.filterNot { it.name == stock?.substitutionAvailable?.name }.forEach {
            add(stringResource(R.string.food_substitution, it.name))
            if (it.note.isNotBlank()) add(it.note)
        }
    }
    val title = if (split) source.name else stringResource(R.string.food_consumed_amount, source.name, quantity)
    val titleModifier = modifier.heightIn(min = 60.dp)
    val summary = details.joinToString("\n").ifBlank { null }
    val status = when {
        !showStock -> null
        ingredient.inShoppingList -> Triple(R.string.food_in_shopping, CuisineIcons.Cart, colors.containerGreen)
        ingredient.status == AvailabilityStatus.PARTIAL -> Triple(R.string.food_ingredient_partial, CuisineIcons.Warning, colors.containerYellow)
        ingredient.status in listOf(AvailabilityStatus.MISSING, AvailabilityStatus.OPTIONAL_MISSING) ->
            Triple(R.string.food_ingredient_missing, CuisineIcons.Basket, colors.containerRed)
        else -> null
    }
    val quantityInk = when {
        !ingredient.enabled -> colors.disabledNeutral
        ingredient.checked -> colors.labelSecondary
        else -> colors.labelPrimary
    }
    val haptic = rememberCouiHaptic()
    BasicComponent(
        modifier = titleModifier.semantics { toggleableState = ToggleableState(ingredient.checked) },
        startAction = { Checkbox(ToggleableState(ingredient.checked), onClick = null, enabled = ingredient.enabled) },
        endActions = {
            if (split) Text(quantity, style = COUITheme.textStyles.headlineXS, color = quantityInk)
        },
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
        enabled = ingredient.enabled,
        holdDownState = pressed,
        role = Role.Checkbox,
        onClick = {
            haptic(CouiHapticEffect.Switch)
            onToggle(!ingredient.checked)
        },
    ) {
        val supportingInk = if (ingredient.enabled) colors.labelSecondary else colors.disabledNeutral
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = COUITheme.textStyles.headlineXS, color = quantityInk)
            if (summary != null) Text(summary, style = COUITheme.textStyles.bodyS, color = supportingInk)
            if (status != null) {
                val (label, icon, fill) = status
                val tint = when (label) {
                    R.string.food_in_shopping -> colors.green
                    R.string.food_ingredient_partial -> colors.yellow
                    else -> colors.red
                }
                CuisineBadge(stringResource(label), icon = icon,
                    ink = if (ingredient.enabled) cuisineTint(tint) else colors.disabledNeutral,
                    fill = if (ingredient.enabled) fill else colors.container8, compact = true)
            }
        }
    }
}

@Composable
internal fun IngredientChecklist(
    model: IngredientChecklistUi,
    onToggle: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onAddMissing: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    showStock: Boolean = true,
) {
    val state = if (model.state == CuisineContentState.READY && model.ingredients.isEmpty()) CuisineContentState.EMPTY else model.state
    Card(modifier.fillMaxWidth()) {
        when (state) {
            CuisineContentState.LOADING -> repeat(4) { CuisineLoading(Modifier.heightIn(min = 60.dp)) }
            CuisineContentState.EMPTY -> CuisineMessage(
                stringResource(R.string.food_no_ingredients), detail = stringResource(R.string.food_no_ingredients_detail),
                icon = CuisineIcons.Basket,
            )
            CuisineContentState.ERROR -> CuisineMessage(
                stringResource(R.string.food_ingredient_list_error), detail = stringResource(R.string.food_ingredient_list_error_detail),
                icon = CuisineIcons.Warning, isError = true, onRetry = onRetry,
            )
            CuisineContentState.READY -> {
                val colors = COUITheme.colorScheme
                val portions = portionLabel(model.portions)
                val all = model.ingredients
                val available = all.count { it.status == AvailabilityStatus.AVAILABLE }
                val partial = all.count { it.status == AvailabilityStatus.PARTIAL }
                val missing = all.count { it.status == AvailabilityStatus.MISSING }
                val tracked = available + partial + missing
                val pending = all.count {
                    !it.inShoppingList && !it.portion.ingredient.optional &&
                        it.status in listOf(AvailabilityStatus.PARTIAL, AvailabilityStatus.MISSING)
                }
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FoodSupporting(stringResource(R.string.food_for_portions, portions))
                    if (showStock && model.stockKnown && tracked > 0) {
                        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(available to colors.green, partial to colors.orange, missing to colors.red)
                                .filter { it.first > 0 }
                                .forEach { (count, color) ->
                                    LinearProgressIndicator(Modifier.weight(count.toFloat()), 1f, height = 6.dp,
                                        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color))
                                }
                        }
                        val summary = listOfNotNull(
                            stringResource(R.string.food_count_in_stock, available),
                            if (partial > 0) pluralStringResource(R.plurals.food_partial_short, partial, partial) else null,
                            if (missing > 0) pluralStringResource(R.plurals.food_missing_short, missing, missing) else null,
                        ).joinToString(" · ")
                        FoodSupporting(summary)
                    } else if (showStock && !model.stockKnown) {
                        AvailabilityChip(RecipeAvailabilityUi())
                    }
                }
                CardListGroup {
                    all.forEach { row ->
                        item { IngredientRow(row, { onToggle(row.id, it) }, showStock = showStock && model.stockKnown) }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (model.stockKnown && pending > 0 && onAddMissing != null) {
                        Button(
                            onAddMissing, Modifier.fillMaxWidth(), minHeight = 48.dp, minWidth = 48.dp,
                            colors = ButtonDefaults.buttonColors(color = colors.primary.copy(alpha = 0.15f)),
                        ) {
                            Icon(CuisineIcons.Cart, null, Modifier.size(18.dp), tint = cuisineTint(colors.primary))
                            Spacer(Modifier.width(8.dp))
                            Text(pluralStringResource(R.plurals.food_add_missing_to_shopping, pending, pending),
                                style = COUITheme.textStyles.buttonM, color = cuisineTint(colors.primary))
                        }
                    } else if (model.stockKnown && pending == 0 && partial + missing > 0) {
                        CuisineBadge(stringResource(R.string.food_missing_in_shopping), icon = COUIIcons.Ok,
                            ink = cuisineTint(colors.green), fill = colors.containerGreen)
                    }
                    FoodSupporting(stringResource(R.string.food_ingredients_checked, all.count { it.checked }, all.size))
                }
            }
        }
    }
}

@CouiComponentPreviews
@Composable
private fun RecipeCardPreview() = FoodComponentPreview {
    RecipeCard(FoodPreviewData.pasta, RecipeCardVariant.L, FoodPreviewData.missing, {})
    RecipeCard(FoodPreviewData.soup, RecipeCardVariant.L, FoodPreviewData.offline, {}, pressed = true)
}

@CouiComponentPreviews
@Composable
private fun RecipeCardVariantsPreview() = FoodComponentPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecipeCard(FoodPreviewData.omelette, RecipeCardVariant.S, FoodPreviewData.ready, {})
        RecipeCard(FoodPreviewData.pasta, RecipeCardVariant.S, FoodPreviewData.missing, {}, pressed = true)
    }
    RecipeCard(FoodPreviewData.soup, RecipeCardVariant.R, FoodPreviewData.partial, {})
    RecipeCard(FoodPreviewData.pasta, RecipeCardVariant.R, FoodPreviewData.missing, {}, pressed = true)
}

@CouiComponentPreviews
@Composable
private fun RecipeCardStatusPreview() = FoodComponentPreview {
    RecipeCard(null, RecipeCardVariant.L, FoodPreviewData.ready, {}, state = CuisineContentState.LOADING)
    RecipeCard(null, RecipeCardVariant.R, FoodPreviewData.ready, {}, state = CuisineContentState.LOADING)
    RecipeCard(null, RecipeCardVariant.L, FoodPreviewData.ready, {}, state = CuisineContentState.ERROR, onRetry = {})
    RecipeCard(null, RecipeCardVariant.L, FoodPreviewData.ready, {}, state = CuisineContentState.EMPTY)
}

@CouiStressPreviews
@CuisineNarrowFontPreview
@Composable
private fun RecipeCardStressPreview() = FoodComponentPreview {
    RecipeCard(FoodPreviewData.pasta, RecipeCardVariant.L, FoodPreviewData.offline, {})
}

@CouiStressPreviews
@CuisineNarrowFontPreview
@Composable
private fun RecipeCardRowStressPreview() = FoodComponentPreview {
    RecipeCard(FoodPreviewData.soup, RecipeCardVariant.R, FoodPreviewData.partial, {})
}

@CouiComponentPreviews
@Composable
private fun RecipeHeroPreview() = FoodComponentPreview {
    RecipeHero(FoodPreviewData.pasta, 2, false, {}, {})
    RecipeHero(FoodPreviewData.omelette, 2, true, {}, {})
}

@CouiComponentPreviews
@Composable
private fun PortionStepperPreview() = FoodComponentPreview {
    PortionStepper(2, 1..12, {})
    PortionStepper(1, 1..12, {})
    PortionStepper(12, 1..12, {})
    PortionStepper(4, 1..12, {}, enabled = false)
    PortionStepper(2, 1..12, {}, large = true)
}

@CouiComponentPreviews
@Composable
private fun IngredientRowPreview() = FoodComponentPreview {
    Card {
        CardListGroup {
            item { IngredientRow(FoodPreviewData.spaghetti, {}) }
            item { IngredientRow(FoodPreviewData.parmesan, {}) }
            item { IngredientRow(FoodPreviewData.basil, {}) }
            item { IngredientRow(FoodPreviewData.basil.copy(inShoppingList = true), {}) }
            item { IngredientRow(FoodPreviewData.oregano, {}) }
            item { IngredientRow(FoodPreviewData.spaghetti.copy(checked = true), {}) }
            item { IngredientRow(FoodPreviewData.spaghetti, {}, pressed = true) }
            item { IngredientRow(FoodPreviewData.spaghetti.copy(enabled = false), {}) }
            item { IngredientRow(FoodPreviewData.spaghetti, {}, loading = true) }
        }
    }
}

@CouiComponentPreviews
@Composable
private fun IngredientChecklistPreview() = FoodComponentPreview {
    IngredientChecklist(FoodPreviewData.checklist, { _, _ -> }, onAddMissing = {})
}

@CouiComponentPreviews
@Composable
private fun IngredientChecklistStatusPreview() = FoodComponentPreview {
    val checklist = FoodPreviewData.checklist
    IngredientChecklist(checklist.copy(state = CuisineContentState.LOADING), { _, _ -> })
    IngredientChecklist(checklist.copy(ingredients = emptyList()), { _, _ -> })
    IngredientChecklist(checklist.copy(state = CuisineContentState.ERROR), { _, _ -> }, onRetry = {})
}

@CouiComponentPreviews
@Composable
private fun AvailabilityChipPreview() = FoodComponentPreview {
    AvailabilityChip(FoodPreviewData.ready)
    AvailabilityChip(FoodPreviewData.partial)
    AvailabilityChip(FoodPreviewData.missing)
    AvailabilityChip(RecipeAvailabilityUi())
    AvailabilityChip(FoodPreviewData.offline)
    AvailabilityChip(FoodPreviewData.ready.copy(verification = StockVerification.UNVERIFIED), compact = true)
    AvailabilityChip(RecipeAvailabilityUi(state = CuisineContentState.LOADING))
    AvailabilityChip(RecipeAvailabilityUi(state = CuisineContentState.ERROR))
}

@CouiComponentPreviews
@Composable
private fun IngredientChecklistPortionsPreview() = FoodComponentPreview {
    IngredientChecklist(FoodPreviewData.checklistFourPortions, { _, _ -> }, onAddMissing = {})
}

@CouiComponentPreviews
@Composable
private fun IngredientChecklistUnknownStockPreview() = FoodComponentPreview {
    val checklist = FoodPreviewData.checklist
    IngredientChecklist(checklist.copy(
        ingredients = checklist.ingredients.map { it.copy(stock = null) },
        stockKnown = false,
    ), { _, _ -> })
}
