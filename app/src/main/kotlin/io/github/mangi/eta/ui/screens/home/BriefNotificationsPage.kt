package io.github.mangi.eta.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.RecentEntry
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiScaffold
import io.github.mangi.eta.ui.components.coui.couiPageScroll
import io.github.suqi8.coui.kmp.basic.SnackbarHost
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val PageSize = 40

internal enum class NotificationFilter(val label: String, val priority: NotificationPriority? = null) {
    ALL("Tout"),
    NOW("Maintenant", NotificationPriority.NOW),
    TODAY("Aujourd’hui", NotificationPriority.TODAY),
    LOW("Sans importance", NotificationPriority.LOW),
}

@Composable
internal fun BriefNotificationsPage(
    active: Boolean,
    initialFilter: NotificationFilter,
    repository: NotificationHistoryRepository,
    actions: BriefNotificationActions,
    dialogs: @Composable () -> Unit,
    snackbars: SnackbarHostState,
    homelabEnabled: Boolean,
    onBack: () -> Unit,
    onNotificationTouchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable(initialFilter) { mutableStateOf(initialFilter) }
    var groupByApp by rememberSaveable { mutableStateOf(false) }
    CouiScaffold(title = "Notifications", onBack = onBack, modifier = modifier, compactTitle = true) { padding, _, _, _ ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                CouiChipTabs(
                    items = remember { NotificationFilter.entries.map { it to it.label } },
                    selectedKey = filter,
                    onSelectedKeyChange = { filter = it },
                )
                SwitchPreference(
                    title = "Regrouper par application",
                    checked = groupByApp,
                    onCheckedChange = { groupByApp = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = BriefCardInset),
                )
                key(filter) {
                    BriefNotificationsList(
                        active, repository, actions, filter.priority, groupByApp, homelabEnabled, onNotificationTouchChange,
                    )
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
            dialogs()
        }
    }
}

@Composable
private fun BriefNotificationsList(
    active: Boolean,
    repository: NotificationHistoryRepository,
    actions: BriefNotificationActions,
    priority: NotificationPriority?,
    groupByApp: Boolean,
    homelabEnabled: Boolean,
    onNotificationTouchChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pager = remember(repository, priority) { BriefNotificationPager(repository, priority) }
    val listState = rememberLazyListState()
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(active, pager) {
        if (active) NotificationActivityFeed.changes.collect { pager.refresh() }
    }
    val appCounts by produceState<Map<String, Int>>(emptyMap(), active, groupByApp, priority, repository) {
        if (active && groupByApp) NotificationActivityFeed.changes.collect {
            try {
                value = withContext(Dispatchers.IO) { repository.briefAppCounts(priority) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                value = emptyMap()
            }
        }
    }
    val entries = pager.entries
    val groups by produceState<List<BriefNotificationGroup>?>(null, entries, groupByApp, appCounts) {
        value = entries?.let { rows ->
            withContext(Dispatchers.Default) {
                if (!groupByApp) groupBriefNotifications(rows)
                else rows.groupBy { it.packageName }.map { (packageName, appRows) ->
                    BriefNotificationGroup(
                        packageName, "", appRows.first().effectivePriority, appRows,
                        appCounts[packageName] ?: appRows.size,
                    )
                }
            }
        }
    }
    val loadMore: () -> Unit = remember(pager, scope) {
        { scope.launch { pager.loadMore() } }
    }
    val rows by remember(groups, groupByApp, priority, homelabEnabled, pager, actions, loadMore, onNotificationTouchChange) {
        derivedStateOf {
            briefNotificationListItems(
                groups = groups,
                groupByApp = groupByApp,
                priority = priority,
                expandedGroups = expandedGroups,
                homelabEnabled = homelabEnabled,
                hasError = pager.hasError,
                loadingMore = pager.loadingMore,
                hasMore = pager.hasMore,
                onLoadMore = loadMore,
                onRetry = { scope.launch { pager.refresh() } },
                onToggleGroup = { id, expanded -> expandedGroups[id] = expanded },
                actions = actions,
                onNotificationTouchChange = onNotificationTouchChange,
            )
        }
    }
    LaunchedEffect(listState, pager, active) {
        if (!active) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= 0 && last >= layout.totalItemsCount - 9 && listState.isScrollInProgress
        }.collect { nearEnd ->
            if (nearEnd && !pager.hasError) pager.loadMore()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().couiPageScroll(null),
        state = listState,
        overscrollEffect = null,
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        items(rows, key = { it.key }, contentType = { it.contentType }) { it.content() }
    }
}

private class BriefNotificationPager(
    private val repository: NotificationHistoryRepository,
    private val priority: NotificationPriority?,
) {
    var entries by mutableStateOf<List<RecentEntry>?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var hasError by mutableStateOf(false)
        private set
    var loadingMore by mutableStateOf(false)
        private set

    private val lock = Mutex()
    private var pages = 1

    suspend fun refresh() = lock.withLock {
        try {
            val (rows, more) = withContext(Dispatchers.IO) {
                val rows = mutableListOf<RecentEntry>()
                var more = true
                // Reconcile only the visited window so archived rows and corrected priorities disappear.
                repeat(pages) {
                    if (more) {
                        val next = repository.recentPage(PageSize, rows.lastOrNull(), priority)
                        rows.addAll(next)
                        more = next.size == PageSize
                    }
                }
                rows.toList() to more
            }
            entries = rows
            hasMore = more
            hasError = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            hasError = true
        }
    }

    suspend fun loadMore() {
        if (!hasMore || !lock.tryLock()) return
        loadingMore = true
        try {
            val loaded = entries.orEmpty()
            val next = withContext(Dispatchers.IO) { repository.recentPage(PageSize, loaded.lastOrNull(), priority) }
            entries = (loaded + next).distinctBy { it.key }
            hasMore = next.size == PageSize
            pages++
            hasError = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            hasError = true
        } finally {
            loadingMore = false
            lock.unlock()
        }
    }
}
