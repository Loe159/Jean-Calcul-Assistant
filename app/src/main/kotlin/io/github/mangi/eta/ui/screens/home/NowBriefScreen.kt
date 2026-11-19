package io.github.mangi.eta.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    @Suppress("UNUSED_PARAMETER") onManageFiltering: () -> Unit = {},
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
) {
    val dark = COUITheme.isDark
    val context = LocalContext.current
    val notificationsChanged by NotificationActivityFeed.changes.collectAsState()
    var activityEntries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
    var activityError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationHistoryRepository(context) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val expandedTopics = remember { mutableStateMapOf<String, Boolean>() }
    val listState = rememberLazyListState()
    val pageSize = 40
    LaunchedEffect(visible, notificationsChanged) {
        if (visible) {
            runCatching { withContext(Dispatchers.IO) { repository.recentPage(pageSize) } }
                .onSuccess { page ->
                    if (activityEntries.isEmpty()) hasMore = page.size == pageSize
                    // Keep already visited rows while new notifications arrive; do not reset scroll.
                    activityEntries = (page + activityEntries).distinctBy { it.key }.sortedWith(newestFirst)
                    activityError = false
                }.onFailure { activityError = true }
        }
    }
    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    repository.recentPage(pageSize, activityEntries.lastOrNull())
                }
                activityEntries = (activityEntries + next).distinctBy { it.key }.sortedWith(newestFirst)
                hasMore = next.size == pageSize
                activityError = false
            } catch (_: Exception) {
                activityError = true
                hasMore = false
            } finally {
                loadingMore = false
            }
        }
    }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.totalItemsCount - (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) to
                listState.isScrollInProgress
        }.collect { (remaining, scrolling) ->
            if (scrolling && remaining <= 8 && activityEntries.isNotEmpty()) loadMore()
        }
    }
    // Grouping by Mastra's stored priority; kept off the main thread. Null = not computed yet.
    val notificationGroups by produceState<List<BriefNotificationGroup>?>(null, activityEntries) {
        value = withContext(Dispatchers.Default) { groupBriefNotifications(activityEntries) }
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
    val dateText = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
            .replaceFirstChar { it.uppercaseChar() }
    }
    val motionAllowed = remember(context, visible) { briefMotionAllowed(context) }
    val visited = BriefMotionSession.revealedCards
    LaunchedEffect(visible) {
        if (visible) {
            BriefMotionSession.hasOpened = true
        } else if (BriefMotionSession.hasOpened) {
            BriefMotionSession.hasClosedAfterOpening = true
        }
    }
    val scrollDistance by remember(listState) {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 240f + listState.firstVisibleItemScrollOffset)
                .coerceIn(0f, 800f)
        }
    }
    val titleProgress by animateFloatAsState(
        targetValue = if (visible || BriefMotionSession.hasOpened || !motionAllowed) 1f else 0f,
        animationSpec = tween(BriefMotionTiming.TITLE_MS, easing = FastOutSlowInEasing),
        label = "brief-title-reveal",
    )
    val secondaryText = Modifier.fillMaxWidth()
        .padding(horizontal = PreferenceCategoryDefaults.TitleMarginStartLarge)
    CouiLazyScaffoldPage(
        title = "Mon briefing",
        onBack = onClose,
        containerColor = COUITheme.colorScheme.backgroundWithCard,
        listState = listState,
        titleMotionProgress = titleProgress,
        backgroundEffect = {
            BriefAmbientBackground(
                visible = visible,
                dark = dark,
                scrollOffsetPx = { scrollDistance },
                revealProgress = revealProgress,
            )
        },
        modifier = modifier.fillMaxSize(),
        // Action dialogs live in the page overlay slot (COUI overlay host).
        overlay = { briefActions.dialogs() },
    ) {
        item(key = "date") {
            BriefReveal("date", visible, 0, visited) {
                Text(
                    text = dateText,
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                    modifier = secondaryText.padding(bottom = 4.dp),
                )
            }
        }
        if (!homelabEnabled) item(key = "notification-feed:homelab") {
            Text(
                text = "Homelab non configuré : notifications conservées sans analyse ni retrait automatique",
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                modifier = secondaryText.padding(vertical = 4.dp),
            )
        }
        if (activityError) item(key = "notification-feed:error") {
            Text(
                text = "Historique indisponible · réessayez en rouvrant le briefing",
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                modifier = secondaryText.padding(vertical = 8.dp),
            )
        }
        val groups = notificationGroups
        if (groups != null && groups.isEmpty()) {
            item(key = "notification-feed:empty") {
                BasicComponent(
                    modifier = Modifier.padding(horizontal = BriefCardInset).padding(top = 12.dp)
                        .briefCardSegment(CardListPosition.Full),
                    title = "Aucune notification récente",
                    startAction = {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsNone,
                            contentDescription = null,
                            tint = COUITheme.colorScheme.labelSecondary,
                        )
                    },
                    cardListPosition = CardListPosition.Full,
                )
            }
        } else if (groups != null) {
            NotificationPriority.entries.forEach { tier ->
                val tierGroups = groups.filter { it.priority == tier }
                if (tierGroups.isEmpty()) return@forEach
                val label = when (tier) {
                    NotificationPriority.NOW -> "À traiter maintenant"
                    NotificationPriority.TODAY -> "À voir aujourd'hui"
                    NotificationPriority.LOW -> "Sans importance"
                }
                val subtitle = when (tier) {
                    NotificationPriority.NOW -> "À garder dans les notifications Android"
                    NotificationPriority.TODAY -> "Pour plus tard · disponible dans le briefing"
                    NotificationPriority.LOW -> "Publicités et rappels inutiles · repliés"
                }
                val count = tierGroups.sumOf { it.entries.size }
                item(key = "priority:${tier.name}") {
                    Box(Modifier.padding(top = 8.dp)) {
                        PreferenceCategory(title = label)
                    }
                    Text(
                        text = "$count notification${if (count > 1) "s" else ""} · $subtitle",
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                        modifier = secondaryText,
                    )
                }
                tierGroups.forEach { group ->
                    val key = "${tier.name}:${group.category}"
                    val expanded = expandedTopics[key] ?: (tier != NotificationPriority.LOW)
                    item(key = "topic:$key") {
                        BriefNotificationAppCard(
                            group = group,
                            expanded = expanded,
                            onToggle = { expandedTopics[key] = !expanded },
                            onDismiss = dismissActivityEntries,
                            onTouchChange = onNotificationTouchChange,
                        )
                    }
                    if (expanded) {
                        itemsIndexed(group.entries, key = { _, entry -> "brief:$key:${entry.key}" }) { index, entry ->
                            BriefNotificationRow(
                                entry = entry,
                                last = index == group.entries.lastIndex,
                                onDismiss = dismissActivityEntries,
                                onFeedback = markFeedback,
                                onCategoryChange = correctCategory,
                                onOpen = openNotification,
                                onTouchChange = onNotificationTouchChange,
                                onPriorityChange = correctPriority,
                                onAction = briefActions.onAction,
                            )
                        }
                    }
                }
            }
            if (loadingMore) item(key = "notification-feed:loading") {
                Text(
                    text = "Chargement…",
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                    modifier = secondaryText.padding(vertical = 16.dp),
                )
            }
            if (hasMore && !loadingMore) item(key = "notification-feed:load-more") {
                BasicComponent(
                    modifier = Modifier.padding(horizontal = BriefCardInset).padding(top = 16.dp)
                        .briefCardSegment(CardListPosition.Full),
                    title = "Afficher les notifications précédentes",
                    titleColor = io.github.suqi8.coui.kmp.basic.BasicComponentDefaults.titleColor(
                        color = COUITheme.colorScheme.primary,
                    ),
                    cardListPosition = CardListPosition.Full,
                    onClick = ::loadMore,
                )
            }
        }

        // Additional cards are a real composable extension point, not fictional content.
        extensions.filter { it.id !in setOf("notifications", "header") }
            .sortedWith(compareBy(NowBriefCardExtension::priority, NowBriefCardExtension::id))
            .forEach { extension ->
                item(key = "extension:${extension.id}") {
                    BriefReveal(extension.id, visible, 5, visited) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = BriefCardInset)) {
                            extension.content()
                        }
                    }
                }
            }
    }
}
