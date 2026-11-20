package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.remote.ActionProposal
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.RecentEntry
import io.github.mangi.eta.ui.screens.home.BriefNotificationAppCard
import io.github.mangi.eta.ui.screens.home.BriefNotificationGroup
import io.github.mangi.eta.ui.screens.home.BriefNotificationRow
import io.github.mangi.eta.ui.screens.home.priorityTitle
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

internal data class BriefFeedItem(
    val key: String,
    val contentType: String,
    val content: @Composable () -> Unit,
)

internal const val BriefNotificationsFooterKey = "notifications:footer"

/** One segment per history row keeps everything in the outer lazy list and preserves swipe isolation. */
internal fun briefNotificationCardItems(
    groups: List<BriefNotificationGroup>?,
    expandedTopics: Map<String, Boolean>,
    homelabEnabled: Boolean,
    hasError: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onToggleTopic: (String, Boolean) -> Unit,
    onDismiss: (List<String>) -> Unit,
    onFeedback: (RecentEntry, NotificationUserFeedback) -> Unit,
    onCategoryChange: (RecentEntry, String) -> Unit,
    onOpen: (RecentEntry) -> Unit,
    onTouchChange: (Boolean) -> Unit,
    onPriorityChange: (RecentEntry, NotificationPriority) -> Unit,
    onAction: (RecentEntry, ActionProposal) -> Unit,
): List<BriefFeedItem> = buildList {
    if (!homelabEnabled) {
        add(BriefFeedItem("notifications:homelab", "notification-status") {
            NotificationSegment {
                NotificationStatus("Homelab non configuré : notifications conservées sans analyse ni retrait automatique")
            }
        })
    }
    if (hasError) {
        add(BriefFeedItem("notifications:error", "notification-status") {
            NotificationSegment {
                NotificationStatus("Historique indisponible · réessayez en rouvrant le briefing")
            }
        })
    }
    if (groups == null && !hasError || groups != null && groups.isEmpty()) {
        add(BriefFeedItem("notifications:initial", "notification-status") {
            NotificationSegment {
                NotificationStatus(if (groups == null) "Chargement…" else "Aucune notification récente")
            }
        })
    }
    groups.orEmpty().groupBy { it.priority }.forEach { (tier, tierGroups) ->
        val count = tierGroups.sumOf { it.entries.size }
        add(BriefFeedItem("notifications:priority:${tier.name}", "notification-priority") {
            NotificationSegment {
                val subtitle = when (tier) {
                    NotificationPriority.NOW -> "À garder dans les notifications Android"
                    NotificationPriority.TODAY -> "Pour plus tard · disponible dans le briefing"
                    NotificationPriority.LOW -> "Publicités et rappels inutiles · repliés"
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(priorityTitle(tier), style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
                    Text(
                        "$count notification${if (count > 1) "s" else ""} · $subtitle",
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                    )
                }
            }
        })
        tierGroups.forEach { group ->
            val key = "${tier.name}:${group.category}"
            val expanded = expandedTopics[key] ?: (tier != NotificationPriority.LOW)
            add(BriefFeedItem("notifications:topic:$key", "notification-topic") {
                NotificationSegment {
                    BriefNotificationAppCard(
                        group = group,
                        expanded = expanded,
                        onToggle = { onToggleTopic(key, !expanded) },
                        onDismiss = onDismiss,
                        onTouchChange = onTouchChange,
                    )
                }
            })
            if (expanded) group.entries.forEachIndexed { index, entry ->
                add(BriefFeedItem("notifications:entry:$key:${entry.key}", "notification-entry") {
                    NotificationSegment {
                        BriefNotificationRow(
                            entry = entry,
                            last = index == group.entries.lastIndex,
                            onDismiss = onDismiss,
                            onFeedback = onFeedback,
                            onCategoryChange = onCategoryChange,
                            onOpen = onOpen,
                            onTouchChange = onTouchChange,
                            onPriorityChange = onPriorityChange,
                            onAction = onAction,
                        )
                    }
                })
            }
        }
    }
    if (!groups.isNullOrEmpty() && (loadingMore || hasMore)) {
        add(BriefFeedItem("notifications:pagination", "notification-pagination") {
            NotificationSegment {
                if (loadingMore) {
                    NotificationStatus("Chargement…")
                } else {
                    BasicComponent(
                        title = "Afficher les notifications précédentes",
                        titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.primary),
                        onClick = onLoadMore,
                    )
                }
            }
        })
    }
    add(BriefFeedItem(BriefNotificationsFooterKey, "notification-footer") {
        NotificationSegment(bottom = true) { Spacer(Modifier.height(12.dp)) }
    })
}

@Composable
private fun NotificationSegment(bottom: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).briefFeedCardSegment(bottom = bottom),
    ) { content() }
}

@Composable
private fun NotificationStatus(text: String) {
    Text(
        text, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
