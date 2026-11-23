package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.screens.food.ExpiringInventoryFilter
import io.github.mangi.eta.ui.screens.food.activeTimers
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.mangi.eta.ui.screens.food.progress
import io.github.mangi.eta.ui.screens.home.NowBriefCardExtension
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
                ActiveCookingCard(current, active) { onOpen(AppRoute.CookingSession(current.id)) }
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
private fun ActiveCookingCard(session: CookingSession, active: Boolean, onOpen: () -> Unit) {
    FoodSection {
        FoodHeading(stringResource(R.string.food_cooking_in_progress))
        FoodHeading(session.recipeSnapshot.title)
        CookingProgress(session.progress())
        if (active) TimerStack(session.activeTimers())
        FoodAction(R.string.food_brief_resume, primary = true, onClick = onOpen)
    }
}

@Composable
private fun FoodServerCard(card: BriefCard, onOpen: () -> Unit, onDismiss: () -> Unit) {
    FoodSection {
        val kind = when (card) {
            is BriefCard.RecipeRecommendation -> R.string.food_brief_recommendation
            is BriefCard.ExpiringProducts -> R.string.food_brief_expiring
        }
        FoodHeading(stringResource(kind))
        FoodHeading(card.title)
        when (card) {
            is BriefCard.RecipeRecommendation -> {
                RecipePicture(card.image)
                FoodSupporting(stringResource(R.string.food_minutes, card.totalMinutes))
                FoodSupporting(stringResource(R.string.food_brief_stock_ratio, (card.availableRatio * 100).roundToInt()))
                FoodAction(R.string.food_brief_open, primary = true, onClick = onOpen)
            }
            is BriefCard.ExpiringProducts -> {
                FoodSupporting(card.products.joinToString { it.name })
                FoodAction(R.string.food_filter_expiring, primary = true, onClick = onOpen)
            }
        }
        FoodAction(R.string.food_brief_dismiss, onClick = onDismiss)
    }
}
