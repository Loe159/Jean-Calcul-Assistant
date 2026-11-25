package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.food.ExpiringInventoryFilter
import io.github.mangi.eta.ui.screens.food.activeTimers
import io.github.mangi.eta.ui.screens.food.components.FoodAction
import io.github.mangi.eta.ui.screens.food.components.RecipePicture
import io.github.mangi.eta.ui.screens.food.components.TimerStack
import io.github.mangi.eta.ui.screens.food.components.foodRuntime
import io.github.mangi.eta.ui.screens.food.progress
import io.github.mangi.eta.ui.screens.food.toActiveCookingUi
import io.github.mangi.eta.ui.screens.home.NowBriefCardExtension
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.icon.extended.Months
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Timer
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberFoodBriefExtensions(
    active: Boolean,
    showRecommendations: Boolean,
    motionAllowed: Boolean,
    onOpen: (AppRoute) -> Unit,
): List<NowBriefCardExtension> {
    val runtime = foodRuntime()
    val repo = runtime.repository
    val session by remember(repo) {
        repo.activeSessions().map { it.firstOrNull()?.id }.distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else repo.session(id) }
    }.collectAsState(initial = null)
    val cache by remember(repo) { repo.briefCards() }.collectAsState(initial = null)
    var dismissed by remember(cache?.value?.generatedAt) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(repo, active, showRecommendations, cache?.value?.validUntil) {
        if (active && showRecommendations && cache == null) {
            // Allow the Room flow to emit its cached value before requesting a refresh.
            delay(300)
            repo.refreshBriefCards()
        }
    }

    fun feedback(cardId: String, action: BriefFeedbackAction) {
        runtime.launch { repo.briefFeedback(BriefFeedbackRequest(UUID.randomUUID().toString(), cardId, action)) }
    }
    return buildList {
        session?.takeIf { it.status == CookingStatus.ACTIVE }?.let { current ->
            add(NowBriefCardExtension("food-active", priority = 0, beforeRealCards = true) {
                ActiveCookingCard(current, active, motionAllowed) { onOpen(AppRoute.CookingSession(current.id)) }
            })
        }
        foodBriefServerCards(cache?.value, dismissed, showRecommendations).forEachIndexed { index, card ->
            add(NowBriefCardExtension("food:${card.id}", priority = 40 + index) {
                FoodServerCard(card, onOpen = {
                    feedback(card.id, BriefFeedbackAction.OPENED)
                    onOpen(when (card) {
                        is BriefCard.RecipeRecommendation -> AppRoute.RecipeDetail(card.recipeId, briefCardId = card.id)
                        is BriefCard.ExpiringProducts -> AppRoute.Inventory(ExpiringInventoryFilter)
                    })
                }, onDismiss = {
                    dismissed = dismissed + card.id
                    feedback(card.id, BriefFeedbackAction.DISMISSED)
                })
            })
        }
    }
}

internal fun foodBriefServerCards(cache: BriefCardsResponse?, dismissed: Set<String>, visible: Boolean): List<BriefCard> =
    if (!visible) emptyList() else cache?.cards.orEmpty().sortedByDescending { it.priority }
        .distinctBy { it::class }.take(2).filterNot { it.id in dismissed }

@Composable
private fun ActiveCookingCard(session: CookingSession, active: Boolean, motionAllowed: Boolean, onOpen: () -> Unit) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val progress = session.progress()
    val cooking = session.toActiveCookingUi(progress)
    FoodBriefCard(R.string.food_cooking_in_progress, COUIIcons.Timer, colors.orange) {
        FoodBriefTitle(cooking.title)
        Text(
            stringResource(R.string.food_step_position, cooking.index + 1, cooking.count),
            style = styles.bodyM,
            color = colors.labelPrimary,
        )
        BriefGauge(
            progress.ratio, listOf(colors.orange), motionAllowed, Modifier.fillMaxWidth().height(6.dp),
            stringResource(R.string.food_cooking_progress),
        )
        Text(
            stringResource(R.string.food_remaining, progress.remainingMinutes),
            style = styles.bodyXS,
            color = colors.labelSecondary,
        )
        if (active) TimerStack(session.activeTimers())
        FoodAction(R.string.food_brief_resume, primary = true, onClick = onOpen)
    }
}

@Composable
private fun FoodServerCard(card: BriefCard, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = COUITheme.colorScheme
    when (card) {
        is BriefCard.RecipeRecommendation ->
            FoodBriefCard(R.string.food_brief_recommendation, COUIIcons.Notes, colors.green, onDismiss) {
                FoodBriefTitle(card.title)
                RecipePicture(card.image)
                BriefMetricRow { item ->
                    BriefMetric(
                        stringResource(R.string.food_brief_total_time),
                        stringResource(R.string.food_minutes, card.totalMinutes),
                        item(1f),
                    )
                    BriefMetric(
                        stringResource(R.string.food_brief_in_stock),
                        stringResource(R.string.food_brief_stock_ratio, (card.availableRatio * 100).roundToInt()),
                        item(1f),
                    )
                }
                Text(
                    stringResource(R.string.food_brief_stock_snapshot),
                    style = COUITheme.textStyles.bodyXS,
                    color = colors.labelSecondary,
                )
                FoodAction(R.string.food_brief_open, primary = true, onClick = onOpen)
            }
        is BriefCard.ExpiringProducts ->
            FoodBriefCard(R.string.food_brief_expiring, COUIIcons.Months, colors.orange, onDismiss) {
                FoodBriefTitle(card.title)
                if (card.products.isNotEmpty()) ExpiringProductList(card.products)
                FoodAction(R.string.food_filter_expiring, primary = true, onClick = onOpen)
            }
    }
}

@Composable
private fun FoodBriefCard(
    @StringRes kind: Int,
    icon: ImageVector,
    tint: Color,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BriefIconTile(icon, tint, size = 32.dp)
                Text(stringResource(kind), Modifier.weight(1f), style = COUITheme.textStyles.bodyS, color = briefTintedLabel(tint))
                if (onDismiss != null) {
                    CouiTopAppBarIconButton(
                        COUIIcons.Basic.Close, stringResource(R.string.food_brief_dismiss), onDismiss, Modifier.size(48.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun FoodBriefTitle(title: String) {
    Text(
        title,
        modifier = Modifier.semantics { heading() },
        style = COUITheme.textStyles.headlineS,
        color = COUITheme.colorScheme.labelPrimary,
    )
}

@Composable
private fun ExpiringProductList(products: List<ExpiringProduct>) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Column(Modifier.fillMaxWidth().briefInset().padding(horizontal = 12.dp)) {
        products.forEachIndexed { index, product ->
            if (index > 0) HorizontalDivider()
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(product.name, style = styles.bodyM, color = colors.labelPrimary)
                Text(
                    stringResource(R.string.food_brief_best_before, briefBestBeforeDate(product.bestBeforeDate)),
                    style = styles.bodyXS,
                    color = colors.labelSecondary,
                )
            }
        }
    }
}

private val briefExpiryDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.FRANCE)

private fun briefBestBeforeDate(date: String): String = try {
    LocalDate.parse(date).format(briefExpiryDateFormat)
} catch (_: DateTimeParseException) {
    date
}
