package io.github.mangi.eta.ui.screens.home

import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.SwipeActionItem
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.suqi8.coui.kmp.basic.Icon
import androidx.compose.foundation.LocalIndication
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Group by package, retaining descending post time within each group. */
internal data class BriefNotificationGroup(
    val packageName: String,
    val entries: List<NotificationHistoryRepository.RecentEntry>,
)

internal fun groupBriefNotifications(
    entries: List<NotificationHistoryRepository.RecentEntry>,
): List<BriefNotificationGroup> = entries
    .sortedByDescending { it.postedAt }
    .groupBy { it.packageName }
    .map { (pkg, list) -> BriefNotificationGroup(pkg, list) }
    .sortedByDescending { it.entries.first().postedAt }

@Composable
internal fun BriefNotificationAppCard(
    group: BriefNotificationGroup,
    onDismiss: (List<String>) -> Unit,
) {
    val dark = COUITheme.isDark
    val context = LocalContext.current
    val appName = remember(group.packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(group.packageName, 0)).toString()
        }.getOrDefault(group.packageName.substringAfterLast('.'))
    }
    var expanded by remember(group.packageName) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    SwipeActionItem(
        actions = emptyList(),
        onActionClick = {},
        onDelete = { onDismiss(group.entries.map { it.key }) },
        fastDeleteEnabled = true,
        deleteContentDescription = stringResource(R.string.brief_remove_action),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 5.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = COUITheme.colorScheme.card,
                contentColor = COUITheme.colorScheme.labelPrimary,
                pressedColor = if (dark) Color(0xFF2A2C30) else Color(0xFFECEEF1),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable(
                        interactionSource = source, indication = LocalIndication.current,
                        role = Role.Button,
                        onClickLabel = if (expanded) "Replier $appName" else "Déplier $appName",
                    ) { expanded = !expanded }
                    .padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BriefApplicationIcon(group.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = appName, style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary, maxLines = 1)
                    Text(
                        text = "${group.entries.size} notification${if (group.entries.size > 1) "s" else ""} · " +
                            group.entries.first().title.ifBlank { "Activité récente" },
                        style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null, tint = COUITheme.colorScheme.labelSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 12.dp)) {
                    group.entries.forEach { entry ->
                        key(entry.key) {
                            SwipeActionItem(
                                actions = emptyList(),
                                onActionClick = {},
                                onDelete = { onDismiss(listOf(entry.key)) },
                                fastDeleteEnabled = true,
                                deleteContentDescription = stringResource(R.string.brief_remove_action),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(if (dark) Color(0xFF2A2C30) else Color(0xFFF1F2F3))
                                    .padding(start = 12.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = entry.title.ifBlank { appName },
                                        style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (entry.text.isNotBlank()) {
                                        Text(
                                            text = entry.text, style = COUITheme.textStyles.bodyS,
                                            color = COUITheme.colorScheme.labelSecondary, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val whenText = remember(entry.postedAt) {
                                        DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.FRENCH)
                                            .format(Instant.ofEpochMilli(entry.postedAt)
                                                .atZone(ZoneId.systemDefault()))
                                    }
                                    Text(text = whenText, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary)
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BriefApplicationIcon(packageName: String) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
    }
    Box(
        modifier = Modifier.size(42.dp).clip(SquircleShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(icon)
                } },
                modifier = Modifier.size(34.dp),
            )
        } else {
            Icon(Icons.Rounded.NotificationsNone, contentDescription = null,
                tint = COUITheme.colorScheme.labelSecondary,
                modifier = Modifier.size(22.dp))
        }
    }
}
