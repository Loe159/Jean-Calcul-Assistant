package io.github.mangi.eta.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.remote.MastraBriefCounts
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.couiPageScroll
import io.github.mangi.eta.ui.components.coui.rememberMaterialBackdropProvider
import io.github.mangi.eta.ui.components.coui.rememberPageBackdrop
import io.github.mangi.eta.ui.components.coui.rememberPopupMaterialBlur
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefCustomization
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefFeedItem
import io.github.mangi.eta.ui.screens.home.brief.BriefHeader
import io.github.mangi.eta.ui.screens.home.brief.BriefHeroKey
import io.github.mangi.eta.ui.screens.home.brief.BriefMinibar
import io.github.mangi.eta.ui.screens.home.brief.BriefNowBar
import io.github.mangi.eta.ui.screens.home.brief.BriefSummaryInput
import io.github.mangi.eta.ui.screens.home.brief.BriefSummarySource
import io.github.mangi.eta.ui.screens.home.brief.BriefVisibilityPreferences
import io.github.mangi.eta.ui.screens.home.brief.MastraBriefSummarySource
import io.github.mangi.eta.ui.screens.home.brief.briefBarSuggestion
import io.github.mangi.eta.ui.screens.home.brief.briefDemoAgenda
import io.github.mangi.eta.ui.screens.home.brief.briefDemoSuggestions
import io.github.mangi.eta.ui.screens.home.brief.briefDemoTrip
import io.github.mangi.eta.ui.screens.home.brief.briefFeedOrder
import io.github.mangi.eta.ui.screens.home.brief.briefMoment
import io.github.mangi.eta.ui.screens.home.brief.rememberBriefSummary
import io.github.mangi.eta.ui.screens.home.brief.rememberBriefSummaryReveal
import io.github.mangi.eta.ui.screens.home.brief.rememberBriefTime
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefAgendaCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefCarCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefFinanceCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefHabitsCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefHealthCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefHomeCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefNewsCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefNotificationsCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefSuggestionsCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefTasksCard
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefWeatherCard
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extensibility slot for independent dashboard cards (agenda, tasks, automations, A2UI).
 * Extensions must be permission-checked and backed by real data; the shell never runs an LLM.
 * Stable IDs enable future persistence of user ordering / pinning.
 */
internal data class NowBriefCardExtension(
    val id: String,
    val priority: Int = 100,
    val content: @Composable () -> Unit,
)

@Composable
internal fun NowBriefScreen(
    visible: Boolean,
    onClose: () -> Unit,
    notificationPage: NotificationFilter?,
    onOpenNotifications: (NotificationFilter) -> Unit,
    onCloseNotifications: () -> Unit,
    onNotificationTouchChange: (Boolean) -> Unit = {},
    onManageFiltering: () -> Unit = {},
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
    summarySource: BriefSummarySource? = null,
) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = visible && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val preferences = remember(context) { BriefVisibilityPreferences(context) }
    var hiddenCards by remember(preferences) { mutableStateOf(preferences.hiddenCards()) }
    val showNotifications = BriefCardId.NOTIFICATIONS !in hiddenCards
    var showCustomization by remember { mutableStateOf(false) }
    val expandedCards = remember { mutableStateMapOf<BriefCardId, Boolean>() }
    var checkedSuggestions by remember { mutableStateOf(emptySet<String>()) }
    var deferredSuggestions by remember { mutableStateOf(emptySet<String>()) }
    var checkedTasks by remember {
        mutableStateOf(BriefDemoData.tasks.filter { it.done }.map { it.title }.toSet())
    }
    val habitCounts = remember { mutableStateMapOf<String, Int>() }
    var homeControls by remember { mutableStateOf(BriefDemoData.homeEnabledControls) }
    var thermostatTarget by remember { mutableFloatStateOf(BriefDemoData.thermostatTarget) }
    val atDashboard = active && notificationPage == null
    val deviceTime by rememberBriefTime(atDashboard)
    val now = deviceTime.toLocalDateTime()
    val moment = briefMoment(now.toLocalTime())
    val motionAllowed = rememberBriefMotionAllowed(active)
    val cards = remember(moment, hiddenCards) { briefFeedOrder(moment, hiddenCards) }

    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationHistoryRepository(context) }
    val listState = rememberLazyListState()
    val weatherInViewport by remember(listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == BriefCardId.WEATHER.key } }
    }
    val snackbars = remember { SnackbarHostState() }
    val showDemoAction: () -> Unit = { scope.launch { snackbars.showSnackbar("Bientôt disponible") } }
    val (notificationActions, actionDialogs) = rememberBriefNotificationActions(repository, snackbars)
    var overview by remember(repository) { mutableStateOf<NotificationHistoryRepository.BriefOverview?>(null) }
    var activityError by remember { mutableStateOf(false) }
    var overviewReady by remember(atDashboard, showNotifications) { mutableStateOf(false) }
    LaunchedEffect(atDashboard, showNotifications, repository) {
        if (atDashboard) NotificationActivityFeed.changes.collect {
            try {
                overview = withContext(Dispatchers.IO) {
                    if (showNotifications) repository.briefOverview()
                    else NotificationHistoryRepository.BriefOverview(repository.briefCounts(), emptyList())
                }
                activityError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                activityError = true
            }
            overviewReady = true
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            BriefMotionSession.hasOpened = true
        } else {
            showCustomization = false
            if (BriefMotionSession.hasOpened) BriefMotionSession.hasClosedAfterOpening = true
        }
    }

    val source = summarySource ?: remember(context) { MastraBriefSummarySource(context.applicationContext) }
    val pairingRevision by MastraConnection.pairingChanges.collectAsState()
    val locale = LocalLocale.current.toLanguageTag()
    val summaryInput = BriefSummaryInput(
        now = deviceTime,
        locale = locale,
        counts = overview?.counts?.takeUnless { activityError }?.let { MastraBriefCounts(it.now, it.today, it.low) },
        showNotificationCount = showNotifications,
    )
    val summary = rememberBriefSummary(source, summaryInput, atDashboard && overviewReady, pairingRevision)
    val summaryElapsed = rememberBriefSummaryReveal(summary.text, atDashboard, motionAllowed)

    if (notificationPage != null) {
        BriefNotificationsPage(
            active = active,
            initialFilter = notificationPage,
            repository = repository,
            actions = notificationActions,
            dialogs = actionDialogs,
            snackbars = snackbars,
            homelabEnabled = remember(context) { MastraConnection.enabled(context) },
            onBack = onCloseNotifications,
            onNotificationTouchChange = onNotificationTouchChange,
            modifier = modifier,
        )
        return
    }

    val nowMillis = deviceTime.toInstant().toEpochMilli()
    val agenda = remember(deviceTime) { briefDemoAgenda(deviceTime) }
    val trip = remember(deviceTime, agenda) { briefDemoTrip(deviceTime, agenda) }
    val suggestions = remember(deviceTime, agenda) { briefDemoSuggestions(deviceTime, agenda) }
    val barSuggestion = remember(deviceTime, agenda, trip) { briefBarSuggestion(deviceTime, agenda, trip) }
    val extensionsInOrder = remember(extensions) {
        val reserved = BriefCardId.entries.map { it.key } + "header"
        extensions.filter { it.id !in reserved }
            .sortedWith(compareBy(NowBriefCardExtension::priority, NowBriefCardExtension::id))
    }
    val rows = buildList {
        val topCards = cards.takeWhile { it != BriefCardId.WEATHER && it != BriefCardId.HOME }
        fun addCard(card: BriefCardId, index: Int) {
            add(BriefFeedItem(card.key, "brief-card") {
                val expanded = expandedCards[card] ?: false
                val cardModifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                val toggleDensity = { expandedCards[card] = !expanded }
                val animateCard = motionAllowed && atDashboard
                BriefReveal(card.key, atDashboard, motionAllowed, index) {
                    when (card) {
                        BriefCardId.NOTIFICATIONS -> BriefNotificationsCard(
                            overview = overview,
                            hasError = activityError,
                            expanded = expanded,
                            nowMillis = nowMillis,
                            motionAllowed = motionAllowed,
                            onToggleDensity = toggleDensity,
                            onOpenNotifications = onOpenNotifications,
                            actions = notificationActions,
                            onNotificationTouchChange = onNotificationTouchChange,
                            modifier = cardModifier,
                        )
                        BriefCardId.SUGGESTIONS -> BriefSuggestionsCard(
                            expanded, animateCard, toggleDensity, cardModifier,
                            checked = checkedSuggestions,
                            deferred = deferredSuggestions,
                            onCheck = { id, checked ->
                                checkedSuggestions = if (checked) checkedSuggestions + id else checkedSuggestions - id
                            },
                            onLater = { id -> deferredSuggestions += id },
                            onDemoAction = showDemoAction,
                            suggestions = suggestions,
                        )
                        BriefCardId.AGENDA -> BriefAgendaCard(expanded, animateCard, toggleDensity, cardModifier, showDemoAction, agenda)
                        BriefCardId.CAR -> BriefCarCard(expanded, animateCard, toggleDensity, cardModifier, showDemoAction, trip)
                        BriefCardId.TASKS -> BriefTasksCard(
                            expanded, animateCard, toggleDensity, cardModifier,
                            checked = checkedTasks,
                            onCheck = { id, checked -> checkedTasks = if (checked) checkedTasks + id else checkedTasks - id },
                            onDemoAction = showDemoAction,
                        )
                        BriefCardId.HEALTH -> BriefHealthCard(expanded, animateCard, toggleDensity, cardModifier, showDemoAction)
                        BriefCardId.HABITS -> BriefHabitsCard(
                            expanded, animateCard, toggleDensity, cardModifier,
                            counts = habitCounts,
                            onCountChange = { id, count -> habitCounts[id] = count },
                            onDemoAction = showDemoAction,
                        )
                        BriefCardId.FINANCE -> BriefFinanceCard(expanded, animateCard, toggleDensity, cardModifier)
                        BriefCardId.NEWS -> BriefNewsCard(expanded, animateCard, toggleDensity, cardModifier)
                        BriefCardId.WEATHER -> BriefWeatherCard(
                            expanded, animateCard, toggleDensity, cardModifier, now.hour,
                            inViewport = { weatherInViewport },
                        )
                        BriefCardId.HOME -> BriefHomeCard(
                            expanded, animateCard, toggleDensity, cardModifier,
                            enabledControls = homeControls,
                            onControlChange = { control, checked ->
                                homeControls = if (checked) homeControls + control else homeControls - control
                            },
                            target = thermostatTarget,
                            onTargetChange = { thermostatTarget = it.coerceIn(BriefDemoData.thermostatMin, BriefDemoData.thermostatMax) },
                            onDemoAction = showDemoAction,
                        )
                    }
                }
            })
        }
        topCards.forEachIndexed { index, card -> addCard(card, index) }
        extensionsInOrder.forEachIndexed { index, extension ->
            add(BriefFeedItem("extension:${extension.id}", "brief-extension") {
                BriefReveal("extension:${extension.id}", active, motionAllowed, topCards.size + index) {
                    Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) { extension.content() }
                }
            })
        }
        cards.drop(topCards.size).forEachIndexed { index, card ->
            addCard(card, topCards.size + extensionsInOrder.size + index)
        }
    }

    val density = LocalDensity.current
    val topInsets = WindowInsets.safeDrawing.getTop(density)
    val minibarSpace = topInsets + with(density) { 72.dp.roundToPx() }
    var minibarHeight by remember { mutableIntStateOf(0) }
    var nowBarHeight by remember { mutableIntStateOf(0) }
    val showMinibar by remember(listState, minibarSpace) {
        derivedStateOf {
            val info = listState.layoutInfo
            val hero = info.visibleItemsInfo.firstOrNull { it.key == BriefHeroKey }
            if (hero == null) listState.firstVisibleItemIndex > 0
            else hero.offset + hero.size <= maxOf(minibarHeight, minibarSpace)
        }
    }
    val allowSkyMotion by remember(listState, minibarSpace) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && !showMinibar }
    }
    val onScrollToCard: (BriefCardId) -> Unit = { card ->
        val index = rows.indexOfFirst { it.key == card.key }
        if (index >= 0) scope.launch {
            val targetOffset = -maxOf(minibarHeight, minibarSpace)
            if (motionAllowed) listState.animateScrollToItem(index + 1, targetOffset)
            else listState.scrollToItem(index + 1, targetOffset)
        }
    }
    val shortcuts = remember(cards) { cards.sortedBy { it != BriefCardId.NOTIFICATIONS }.take(3) }
    val onCustomize = { showCustomization = true }

    val background = COUITheme.colorScheme.backgroundWithCard
    val backdrop = rememberPageBackdrop(background)
    val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
    val popupBlur = rememberPopupMaterialBlur(backdrop)
    // The Brief is a sibling of the chat scaffold and needs its own COUI overlay host.
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            CompositionLocalProvider(LocalPopupMaterialBlur provides popupBlur) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().couiPageScroll(null)
                        .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                    state = listState,
                    overscrollEffect = null,
                    contentPadding = PaddingValues(bottom = with(density) { nowBarHeight.toDp() } + 16.dp),
                ) {
                    item(key = BriefHeroKey, contentType = "brief-hero") {
                        BriefHeader(
                            now = now,
                            moment = moment,
                            summary = summary,
                            summaryElapsed = summaryElapsed,
                            shortcuts = shortcuts,
                            animateSky = active && allowSkyMotion,
                            motionAllowed = motionAllowed,
                            scrollOffsetPx = { listState.firstVisibleItemScrollOffset.toFloat() },
                            revealProgress = revealProgress,
                            onClose = onClose,
                            onCustomize = onCustomize,
                            onShortcut = onScrollToCard,
                        )
                    }
                    items(rows, key = { it.key }, contentType = { it.contentType }) { it.content() }
                }
            }
            if (showMinibar) {
                BriefMinibar(now, moment, onClose, onCustomize, Modifier.onSizeChanged { minibarHeight = it.height })
            }
            val suggestionCard = barSuggestion.card
            CompositionLocalProvider(LocalMaterialBackdrop provides materialBackdrop) {
                BriefNowBar(
                    moment = moment,
                    suggestion = barSuggestion,
                    onOpenCard = if (suggestionCard in cards) ({ onScrollToCard(suggestionCard) }) else null,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { nowBarHeight = it.height },
                )
            }
            SnackbarHost(
                snackbars,
                Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { nowBarHeight.toDp() }),
            )
        }
        actionDialogs()
        BriefCustomization(
            show = visible && showCustomization,
            hiddenCards = hiddenCards,
            onVisibilityChange = { card, show ->
                preferences.setVisible(card, show)
                hiddenCards = if (show) hiddenCards - card else hiddenCards + card
            },
            onDismiss = { showCustomization = false },
            onManageFiltering = {
                showCustomization = false
                onManageFiltering()
            },
        )
    }
}
