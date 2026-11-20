package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.BriefOverview
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.BriefThread
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.home.BriefApplicationIcon
import io.github.mangi.eta.ui.screens.home.BriefMotionTiming
import io.github.mangi.eta.ui.screens.home.BriefNotificationActions
import io.github.mangi.eta.ui.screens.home.BriefNotificationMenu
import io.github.mangi.eta.ui.screens.home.NotificationFilter
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.mangi.eta.ui.screens.home.briefApplicationName
import io.github.mangi.eta.ui.screens.home.priorityTitle
import io.github.mangi.eta.ui.screens.home.rememberBriefNotificationProposals
import io.github.suqi8.coui.kmp.basic.ChipDefaults
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SuggestionChip
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.effect.LocalCOUIMaterialEffectEnabled
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

internal fun selectBriefThreads(threads: List<BriefThread>, expanded: Boolean): List<BriefThread> {
    val limit = if (expanded) NotificationHistoryRepository.BRIEF_THREAD_LIMIT else 5
    val urgent = threads.filter { it.priority == NotificationPriority.NOW }.take(if (expanded) limit else 3)
    return urgent + threads.filter { it.priority == NotificationPriority.TODAY }.take(limit - urgent.size)
}

internal fun briefThreadExtraLabel(count: Int): String? =
    if (count > 1) "+${count - 1}" else null

@Composable
internal fun BriefNotificationsCard(
    overview: BriefOverview?,
    hasError: Boolean,
    expanded: Boolean,
    nowMillis: Long,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    onOpenNotifications: (NotificationFilter) -> Unit,
    actions: BriefNotificationActions,
    modifier: Modifier = Modifier,
) {
    BriefCard(
        card = BriefCardId.NOTIFICATIONS,
        expanded = expanded,
        onToggleDensity = onToggleDensity,
        motionAllowed = motionAllowed,
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasError || overview == null) {
                Text(
                    if (hasError) "Historique indisponible" else "Chargement…",
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
            }
            if (overview != null) {
                val counts = overview.counts
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NotificationCounter(counts.now, NotificationFilter.NOW, onOpenNotifications)
                    NotificationCounter(counts.today, NotificationFilter.TODAY, onOpenNotifications)
                    NotificationCounter(counts.low, NotificationFilter.LOW, onOpenNotifications)
                }
                val threads = remember(overview.threads, expanded) {
                    selectBriefThreads(overview.threads, expanded)
                }
                if (threads.isEmpty()) NotificationEmpty(counts.total == 0, motionAllowed)
                else Column {
                    threads.forEachIndexed { index, thread ->
                        key(thread.id) {
                            if (expanded && threads.getOrNull(index - 1)?.priority != thread.priority) {
                                ThreadSectionTitle(thread.priority)
                            } else if (index > 0) {
                                HorizontalDivider(Modifier.padding(start = 42.dp))
                            }
                            NotificationThreadRow(thread, nowMillis, actions)
                        }
                    }
                }
                if (counts.low > 0) NotificationShield(counts.low)
            }
            TextButton(
                text = overview?.let { "Tout voir (${it.counts.total})" } ?: "Tout voir",
                onClick = { onOpenNotifications(NotificationFilter.ALL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NotificationCounter(
    count: Int,
    filter: NotificationFilter,
    onOpen: (NotificationFilter) -> Unit,
) {
    val tint = priorityTint(requireNotNull(filter.priority))
    SuggestionChip(
        label = "$count ${filter.label}",
        onClick = { onOpen(filter) },
        minWidth = 0.dp,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ChipDefaults.chipColors(containerColor = tint.copy(alpha = .18f), contentColor = briefTintedLabel(tint)),
    )
}

@Composable
private fun priorityTint(priority: NotificationPriority): Color = with(COUITheme.colorScheme) {
    when (priority) {
        NotificationPriority.NOW -> red
        NotificationPriority.TODAY -> orange
        NotificationPriority.LOW -> azure
    }
}

@Composable
private fun ThreadSectionTitle(priority: NotificationPriority) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(priorityTint(priority), CircleShape))
        Text(priorityTitle(priority), style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary)
    }
}

@Composable
private fun NotificationThreadRow(thread: BriefThread, nowMillis: Long, actions: BriefNotificationActions) {
    val context = LocalContext.current
    val entry = thread.latest
    val label = thread.id.title.ifBlank {
        remember(entry.packageName) { briefApplicationName(context, entry.packageName) }
    }
    val ago = remember(entry.postedAt, nowMillis) { briefRelativeTime(entry.postedAt, nowMillis) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BriefApplicationIcon(entry.packageName, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                label, style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (entry.text.isNotBlank()) {
                Text(
                    entry.text, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            val extra = briefThreadExtraLabel(thread.count)
            Text(
                if (extra == null) ago else "$ago · $extra",
                style = COUITheme.textStyles.bodyXS, color = COUITheme.colorScheme.labelSecondary,
            )
        }
        CompositionLocalProvider(LocalCOUIMaterialEffectEnabled provides false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CouiTopAppBarIconButton(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Ouvrir la notification",
                    onClick = { actions.open(entry) },
                    iconSize = 19.dp,
                    tint = COUITheme.colorScheme.labelSecondary,
                )
                CouiTopAppBarIconButton(
                    icon = Icons.Outlined.Archive,
                    contentDescription = "Archiver le fil",
                    onClick = { actions.archiveThread(thread.id) },
                    iconSize = 19.dp,
                    tint = COUITheme.colorScheme.labelSecondary,
                )
                BriefNotificationMenu(entry, actions, rememberBriefNotificationProposals(entry)) {
                    actions.archiveEntries(listOf(entry))
                }
            }
        }
    }
}

private fun briefRelativeTime(postedAt: Long, now: Long): String {
    val minutes = (now - postedAt).coerceAtLeast(0L) / 60_000
    return when {
        minutes < 1 -> "À l’instant"
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h"
        else -> "${minutes / (24 * 60)} j"
    }
}

@Composable
private fun NotificationShield(count: Int) {
    val green = COUITheme.colorScheme.green
    Row(
        Modifier.fillMaxWidth().squircleSurface(green.copy(alpha = .10f), cornerRadius = 16.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = briefTintedLabel(green), modifier = Modifier.size(20.dp))
        Text(
            if (count == 1) "1 notification sans importance filtrée" else "$count notifications sans importance filtrées",
            style = COUITheme.textStyles.bodyS,
            color = COUITheme.colorScheme.labelPrimary,
        )
    }
}

@Composable
private fun NotificationEmpty(emptyInbox: Boolean, motionAllowed: Boolean) {
    val scale = remember { Animatable(if (motionAllowed) .65f else 1f) }
    LaunchedEffect(motionAllowed) {
        if (motionAllowed) scale.animateTo(1f, tween(400, easing = BriefMotionTiming.EaseMove))
        else scale.snapTo(1f)
    }
    val green = COUITheme.colorScheme.green
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
                .squircleSurface(green.copy(alpha = .16f), cornerRadius = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(COUIIcons.Ok, contentDescription = null, tint = briefTintedLabel(green), modifier = Modifier.size(24.dp))
        }
        Text(
            if (emptyInbox) "Boîte vide" else "Rien à traiter",
            style = COUITheme.textStyles.headlineXS,
            color = COUITheme.colorScheme.labelPrimary,
        )
    }
}
