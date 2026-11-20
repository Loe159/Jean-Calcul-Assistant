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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.couiPageScroll
import io.github.mangi.eta.ui.components.coui.rememberMaterialBackdropProvider
import io.github.mangi.eta.ui.components.coui.rememberPageBackdrop
import io.github.mangi.eta.ui.components.coui.rememberPopupMaterialBlur
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefCustomization
import io.github.mangi.eta.ui.screens.home.brief.BriefFeedItem
import io.github.mangi.eta.ui.screens.home.brief.BriefHeader
import io.github.mangi.eta.ui.screens.home.brief.BriefHeroKey
import io.github.mangi.eta.ui.screens.home.brief.BriefMinibar
import io.github.mangi.eta.ui.screens.home.brief.BriefNotificationsFooterKey
import io.github.mangi.eta.ui.screens.home.brief.BriefNowBar
import io.github.mangi.eta.ui.screens.home.brief.BriefSummaryInput
import io.github.mangi.eta.ui.screens.home.brief.BriefSummarySource
import io.github.mangi.eta.ui.screens.home.brief.BriefVisibilityPreferences
import io.github.mangi.eta.ui.screens.home.brief.LocalBriefSummary
import io.github.mangi.eta.ui.screens.home.brief.briefBarSuggestion
import io.github.mangi.eta.ui.screens.home.brief.briefDemoCard
import io.github.mangi.eta.ui.screens.home.brief.briefFeedOrder
import io.github.mangi.eta.ui.screens.home.brief.briefMoment
import io.github.mangi.eta.ui.screens.home.brief.briefNotificationCardItems
import io.github.mangi.eta.ui.screens.home.brief.localBriefSummary
import io.github.mangi.eta.ui.screens.home.brief.rememberBriefTime
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
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

private val newestFirst = compareByDescending<NotificationHistoryRepository.RecentEntry> { it.postedAt }
    .thenByDescending { it.key }

@Composable
internal fun NowBriefScreen(
    visible: Boolean,
    onClose: () -> Unit,
    onNotificationTouchChange: (Boolean) -> Unit = {},
    onManageFiltering: () -> Unit = {},
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
    summarySource: BriefSummarySource = LocalBriefSummary,
) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val active = visible && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val preferences = remember(context) { BriefVisibilityPreferences(context) }
    var hiddenCards by remember(preferences) { mutableStateOf(preferences.hiddenCards()) }
    val showNotifications = BriefCardId.NOTIFICATIONS !in hiddenCards
    var showCustomization by remember { mutableStateOf(false) }
    val expandedCards = remember { mutableStateMapOf<BriefCardId, Boolean>() }
    val now by rememberBriefTime(active)
    val moment = briefMoment(now.toLocalTime())
    val motionAllowed = rememberBriefMotionAllowed(active)
    val cards = remember(moment, hiddenCards) { briefFeedOrder(moment, hiddenCards) }

    val notificationsChanged by NotificationActivityFeed.changes.collectAsState()
    var activityEntries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
    var activityLoaded by remember { mutableStateOf(false) }
    var activityError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationHistoryRepository(context) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val expandedTopics = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val pageSize = 40
    LaunchedEffect(active, showNotifications, notificationsChanged) {
        if (active && showNotifications) {
            runCatching { withContext(Dispatchers.IO) { repository.recentPage(pageSize) } }
                .onSuccess { page ->
                    if (activityEntries.isEmpty()) hasMore = page.size == pageSize
                    // Keep already visited rows while new notifications arrive; do not reset scroll.
                    activityEntries = (page + activityEntries).distinctBy { it.key }.sortedWith(newestFirst)
                    activityLoaded = true
                    activityError = false
                }.onFailure {
                    if (it is CancellationException) throw it
                    activityError = true
                }
        }
    }
    fun loadMore() {
        if (loadingMore || !hasMore || !showNotifications) return
        loadingMore = true
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    repository.recentPage(pageSize, activityEntries.lastOrNull())
                }
                activityEntries = (activityEntries + next).distinctBy { it.key }.sortedWith(newestFirst)
                hasMore = next.size == pageSize
                activityError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                activityError = true
                hasMore = false
            } finally {
                loadingMore = false
            }
        }
    }
    // Grouping by Mastra's stored priority stays off the main thread.
    val notificationGroups by produceState<List<BriefNotificationGroup>?>(null, activityLoaded, activityEntries) {
        value = if (activityLoaded) withContext(Dispatchers.Default) { groupBriefNotifications(activityEntries) } else null
    }
    val dismissActivityEntries: (List<String>) -> Unit = { keys ->
        val removed = activityEntries.filter { it.key in keys }
        if (removed.isNotEmpty()) {
            activityEntries = activityEntries.filterNot { it.key in keys }
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.archiveFromBrief(keys)
                    removed.forEach { repository.feedback(it.key, it.postedAt, NotificationUserFeedback.ARCHIVED) }
                    MastraNotificationSync.request(context)
                } }.onFailure {
                    activityEntries = (activityEntries + removed).distinctBy { it.key }.sortedWith(newestFirst)
                    activityError = true
                }
            }
        }
    }
    val markFeedback: (NotificationHistoryRepository.RecentEntry, NotificationUserFeedback) -> Unit =
        { entry, feedback ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.feedback(entry.key, entry.postedAt, feedback)
                } }.onSuccess {
                    NotificationActivityFeed.notifyChanged()
                    MastraNotificationSync.request(context)
                }.onFailure { activityError = true }
            }
        }
    // A priority correction is an explicit preference: Mastra applies it to future decisions.
    val correctPriority: (NotificationHistoryRepository.RecentEntry, NotificationPriority) -> Unit =
        { entry, priority ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.feedback(entry.key, entry.postedAt, NotificationUserFeedback.PRIORITY_CORRECTED,
                        correctedPriority = priority.name)
                } }.onSuccess {
                    activityEntries = activityEntries.map { if (it.key == entry.key) it.copy(userPriority = priority.name) else it }
                    NotificationActivityFeed.notifyChanged()
                    MastraNotificationSync.request(context)
                }.onFailure { activityError = true }
            }
        }
    val correctCategory: (NotificationHistoryRepository.RecentEntry, String) -> Unit =
        { entry, category ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.feedback(entry.key, entry.postedAt, NotificationUserFeedback.CATEGORY_CORRECTED, category)
                } }.onSuccess {
                    NotificationActivityFeed.notifyChanged()
                    MastraNotificationSync.request(context)
                }
                    .onFailure { activityError = true }
            }
        }
    val openNotification: (NotificationHistoryRepository.RecentEntry) -> Unit = { entry ->
        val opened = runCatching {
            val active = AgentNotificationHistoryService.currentNotifications()
                ?.firstOrNull { it.key == entry.key && it.postTime == entry.postedAt }
            val pendingIntent = active?.notification?.contentIntent
            if (pendingIntent != null) pendingIntent.send()
            else {
                val intent = context.packageManager.getLaunchIntentForPackage(entry.packageName)
                    ?: error("Application indisponible")
                context.startActivity(intent)
            }
        }.isSuccess
        if (opened) markFeedback(entry, NotificationUserFeedback.OPENED)
    }
    val briefActions = rememberBriefActionController(repository, openNotification)
    val homelabEnabled = remember(notificationsChanged) { MastraConnection.enabled(context) }
    LaunchedEffect(visible) {
        if (visible) {
            BriefMotionSession.hasOpened = true
        } else {
            showCustomization = false
            if (BriefMotionSession.hasOpened) BriefMotionSession.hasClosedAfterOpening = true
        }
    }

    val groups = notificationGroups
    val urgentCount = if (showNotifications) groups?.sumOf {
        if (it.priority == NotificationPriority.NOW) it.entries.size else 0
    } else null
    val summaryInput = BriefSummaryInput(now, urgentCount, hasMore || activityError)
    val localSummary = remember(summaryInput) { localBriefSummary(summaryInput) }
    val summary by produceState(localSummary, summarySource, summaryInput, active) {
        value = localSummary
        if (active) value = summarySource.summarize(summaryInput)
    }
    val notificationItems = if (showNotifications) {
        briefNotificationCardItems(
            groups = groups,
            expandedTopics = expandedTopics,
            homelabEnabled = homelabEnabled,
            hasError = activityError,
            loadingMore = loadingMore,
            hasMore = hasMore,
            onLoadMore = ::loadMore,
            onToggleTopic = { key, expanded -> expandedTopics[key] = expanded },
            onDismiss = dismissActivityEntries,
            onFeedback = markFeedback,
            onCategoryChange = correctCategory,
            onOpen = openNotification,
            onTouchChange = onNotificationTouchChange,
            onPriorityChange = correctPriority,
            onAction = briefActions.onAction,
        )
    } else emptyList()
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
                val isNotifications = card == BriefCardId.NOTIFICATIONS
                val demo = if (isNotifications) null else briefDemoCard(card)
                val modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
                BriefReveal(card.key, active, motionAllowed, index) {
                    BriefCard(
                        card = card,
                        expanded = expanded,
                        onToggleDensity = { expandedCards[card] = !expanded },
                        motionAllowed = motionAllowed,
                        modifier = modifier,
                        meta = demo?.headline ?: groups?.sumOf { it.entries.size }?.let { "$it dans le brief" },
                        preview = !isNotifications,
                        continuesInLazyList = isNotifications,
                    ) {
                        if (expanded) {
                            Text(
                                demo?.detail ?: "Les notifications sont classées par priorité et par catégorie.",
                                style = COUITheme.textStyles.bodyS,
                                color = COUITheme.colorScheme.labelSecondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            })
            if (card == BriefCardId.NOTIFICATIONS) addAll(notificationItems)
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

    // The notifications may end well before the bottom of the dashboard.
    val footerIndex by rememberUpdatedState(rows.indexOfFirst { it.key == BriefNotificationsFooterKey } + 1)
    val loadPage by rememberUpdatedState(::loadMore)
    LaunchedEffect(active, showNotifications) {
        if (!active || !showNotifications) return@LaunchedEffect
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val footer = footerIndex
            footer.takeIf {
                it > 0 && listState.firstVisibleItemIndex <= it && lastVisible >= it - 8 && listState.isScrollInProgress
            }
        }.collect { nearFooter ->
            if (nearFooter != null && activityEntries.isNotEmpty()) loadPage()
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
                            shortcuts = shortcuts,
                            active = active,
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
            val suggestionCard = briefBarSuggestion(moment).card
            CompositionLocalProvider(LocalMaterialBackdrop provides materialBackdrop) {
                BriefNowBar(
                    moment = moment,
                    onOpenCard = if (suggestionCard in cards) ({ onScrollToCard(suggestionCard) }) else null,
                    modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { nowBarHeight = it.height },
                )
            }
        }
        briefActions.dialogs()
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
