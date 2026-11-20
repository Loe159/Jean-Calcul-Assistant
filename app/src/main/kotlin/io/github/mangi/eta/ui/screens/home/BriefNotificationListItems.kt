package io.github.mangi.eta.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.ui.screens.home.brief.BriefFeedItem
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** One lazy item per group header, individual notification or status. */
internal fun briefNotificationListItems(
    groups: List<BriefNotificationGroup>?,
    groupByApp: Boolean,
    priority: NotificationPriority?,
    expandedGroups: Map<String, Boolean>,
    homelabEnabled: Boolean,
    hasError: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onToggleGroup: (String, Boolean) -> Unit,
    actions: BriefNotificationActions,
    onNotificationTouchChange: (Boolean) -> Unit,
): List<BriefFeedItem> = buildList {
    if (!homelabEnabled) {
        add(BriefFeedItem("notifications:homelab", "notification-status") {
            NotificationStatus("Homelab non configuré · analyse des notifications indisponible")
        })
    }
    if (hasError) {
        add(BriefFeedItem("notifications:error", "notification-status") {
            BasicComponent(title = "Historique indisponible", summary = "Réessayer", onClick = onRetry)
        })
    } else if (groups.isNullOrEmpty()) {
        add(BriefFeedItem("notifications:initial", "notification-status") {
            NotificationStatus(if (groups == null) "Chargement…" else "Aucune notification récente")
        })
    }

    fun addGroup(group: BriefNotificationGroup) {
        val id = group.id
        val expanded = expandedGroups[id] ?: if (groupByApp) false
            else priority != null || group.priority != NotificationPriority.LOW
        add(BriefFeedItem("notifications:group:$id", "notification-topic") {
            BriefNotificationAppCard(
                group, expanded, { onToggleGroup(id, !expanded) }, actions::archiveEntries, onNotificationTouchChange,
            )
        })
        if (expanded) group.entries.forEachIndexed { index, entry ->
            add(BriefFeedItem("notifications:entry:${entry.key}", "notification-entry") {
                BriefNotificationRow(entry, index == group.entries.lastIndex, actions, onNotificationTouchChange)
            })
        }
    }

    if (groupByApp) groups.orEmpty().forEach(::addGroup)
    else groups.orEmpty().groupBy { it.priority }.forEach { (tier, tierGroups) ->
        val count = tierGroups.sumOf { it.entries.size }
        add(BriefFeedItem("notifications:priority:${tier.name}", "notification-priority") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(priorityTitle(tier), style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
                Text(
                    "$count notification${if (count > 1) "s" else ""}",
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
            }
        })
        tierGroups.forEach(::addGroup)
    }
    if (loadingMore || hasMore && !hasError) {
        add(BriefFeedItem("notifications:pagination", "notification-pagination") {
            if (loadingMore) NotificationStatus("Chargement…")
            else BasicComponent(
                title = "Afficher les notifications précédentes",
                titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.primary),
                onClick = onLoadMore,
            )
        })
    }
}

@Composable
private fun NotificationStatus(text: String) {
    Text(
        text, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
