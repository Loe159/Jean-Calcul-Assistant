package io.github.mangi.eta.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.ProgressIndicatorDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
) {
    val dark = COUITheme.isDark
    val context = LocalContext.current
    val summary by NotificationObservation.summary.collectAsState()
    val notificationsChanged by NotificationActivityFeed.changes.collectAsState()
    var activityEntries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
    var activityError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationHistoryRepository(context) }
    LaunchedEffect(visible, notificationsChanged) {
        if (visible) {
            runCatching {
                withContext(Dispatchers.IO) { repository.recent(limit = 300) }
            }.onSuccess { activityEntries = it; activityError = false }
                .onFailure { activityError = true }
        }
    }
    val notificationGroups = remember(activityEntries) { groupBriefNotifications(activityEntries) }
    val dismissActivityEntries: (List<String>) -> Unit = { keys ->
        // Removing from the briefing is deliberately distinct from cancelling
        // notifications in Android's system shade.
        activityEntries = activityEntries.filterNot { it.key in keys }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.removeFromBrief(keys) } }
                .onFailure { activityError = true }
        }
    }
    val dateText = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH),
    ).replaceFirstChar { it.uppercaseChar() }
    val cardSurface = if (dark) Color(0xFF464A51) else Color(0xFFE9E9E9)
    val listState = rememberLazyListState()
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
    var heroExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
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
                scrollOffsetPx = scrollDistance,
                revealProgress = revealProgress,
            )
        },
        modifier = modifier,
    ) {
        item(key = "date") {
            BriefReveal("date", visible, 0, visited) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dateText,
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "notifications:hero") {
            BriefReveal("hero", visible, 1, visited) {
            Card(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 28.dp).padding(bottom = 14.dp),
                insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                colors = briefCardColors(dark),
                shadow = CardDefaults.shadow(size = 12.dp),
                onClick = { heroExpanded = !heroExpanded },
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    IconBubble(Icons.Rounded.AutoAwesome, COUITheme.colorScheme.primary)
                    Text(text = "Votre tranquillité", style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
                }
                BriefAnimatedNumber(summary.filtered, visible, 36, COUITheme.colorScheme.labelPrimary)
                Text(
                    text = if (summary.filtered == 1) "notification filtrée pendant cette session"
                    else "notifications filtrées pendant cette session",
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BriefMetric("Analysées", summary.analyzed, visible, cardSurface, COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                    BriefMetric("Conservées", summary.kept, visible, cardSurface, COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                    BriefMetric("Reportées", summary.snoozed, visible, cardSurface, COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                }
                AnimatedVisibility(visible = heroExpanded) {
                    Text(
                        text = "${summary.dismissed} suppressions confirmées · ${summary.proposed} filtrages proposés",
                        style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                    )
                }
              }
            }
            }
        }

        item(key = "notifications:categories") {
            BriefReveal("categories", visible, 2, visited) {
            BriefCard(onClick = { categoryExpanded = !categoryExpanded }) {
                SectionHeader("Ce qui a été filtré", "", COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary)
                Spacer(Modifier.height(11.dp))
                val classified = summary.filtered
                CategoryBar("Publicités", summary.filteredAds, classified, visible, COUITheme.colorScheme.primary, COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary)
                Spacer(Modifier.height(11.dp))
                CategoryBar("Spams", summary.filteredSpam, classified, visible, Color(0xFFCA8358), COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary)
                AnimatedVisibility(visible = categoryExpanded) {
                    Text(
                        text = "Reportées : ${summary.snoozed} · Supprimées : ${summary.dismissed}",
                        style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                    )
                }
                if (summary.dismissed > 0) {
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = "${summary.dismissed} suppression(s) définitive(s) · irréversibles",
                        style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                    )
                }
            }
            }
        }

        item(key = "notifications:recent-title") {
            BriefReveal("recent", visible, 3, visited) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 11.dp, start = 30.dp, end = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BriefHeading("Activité récente", color = COUITheme.colorScheme.labelPrimary, size = 19)
            }
            }
        }
        item(key = "notification-feed:description") {
            Text(
                text = "Notifications reçues · 7 derniers jours · ${activityEntries.size} " +
                    (if (activityEntries.size == 1) "affichée" else "affichées")
                    + if (activityEntries.size >= 300) " (limite d'affichage)" else "",
                style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 11.dp),
            )
        }
        if (activityError) {
            item(key = "notification-feed:error") {
                Text(
                    text = "Historique indisponible. Vérifiez l'accès aux notifications.",
                    style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 8.dp),
                )
            }
        }
        if (notificationGroups.isEmpty()) {
            item(key = "notification-feed:empty") {
                BriefCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.NotificationsNone, COUITheme.colorScheme.labelSecondary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Aucune notification enregistrée",
                            style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary,
                        )
                    }
                }
            }
        } else {
            items(notificationGroups, key = { "app:" + it.packageName }) { group ->
                BriefNotificationAppCard(group, dismissActivityEntries)
            }
            item(key = "notification-feed:privacy") {
                Text(
                    text = "Glissez dans les deux sens pour retirer du briefing. " +
                        "Les notifications Android restent intactes.",
                    style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 6.dp),
                )
            }
        }

        // Additional cards are a real composable extension point, not fictional content.
        extensions.filter { it.id !in setOf("notifications", "header") }
            .sortedWith(compareBy(NowBriefCardExtension::priority, NowBriefCardExtension::id))
            .forEach { extension ->
                item(key = "extension:${extension.id}") {
                    BriefReveal(extension.id, visible, 5, visited) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                            extension.content()
                        }
                    }
                }
            }

        item(key = "future") {
            BriefReveal("future", visible, 6, visited) {
            BriefCard() {
                SectionHeader("À venir", "", COUITheme.colorScheme.labelPrimary, COUITheme.colorScheme.labelSecondary)
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutureChip(Icons.Rounded.CalendarMonth, "Agenda", COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                    FutureChip(Icons.Rounded.TaskAlt, "Tâches", COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutureChip(Icons.Rounded.Schedule, "Routines", COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                    FutureChip(Icons.Rounded.AutoAwesome, "Agents", COUITheme.colorScheme.labelSecondary, Modifier.weight(1f))
                }
            }
            }
        }
    }
}

@Composable
private fun BriefAnimatedNumber(value: Int, visible: Boolean, size: Int, color: Color) {
    val context = LocalContext.current
    val enabled = remember(context) { briefMotionAllowed(context) }
    val display by animateIntAsState(
        targetValue = if (visible || BriefMotionSession.hasOpened || !enabled) value else 0,
        animationSpec = tween(BriefMotionTiming.COUNTER_MS, easing = FastOutSlowInEasing),
        label = "brief-stat-count",
    )
    BriefHeading(display.toString(), color, size)
}

@Composable
private fun BriefHeading(text: String, color: Color, size: Int) {
    val style = COUITheme.textStyles.headlineXS
    BasicText(text, style = style.copy(color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
        fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun briefCardColors(dark: Boolean) = CardDefaults.defaultColors(
    color = if (dark) Color(0xDD17191C) else Color(0xE6F9F9FA),
    contentColor = if (dark) Color.White else Color(0xFF17191C),
    pressedColor = if (dark) Color(0xEE272A30) else Color(0xFFF0F2F5),
)

@Composable
private fun BriefCard(
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val dark = COUITheme.isDark
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 28.dp).padding(bottom = 15.dp),
        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = 17.dp, vertical = 16.dp),
        colors = briefCardColors(dark),
        shadow = CardDefaults.shadow(size = 7.dp),
        onClick = onClick,
        content = content,
    )
}

@Composable
private fun IconDecor(icon: ImageVector, color: Color, size: Int) {
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(34.dp).clip(SquircleShape(12.dp)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { IconDecor(icon, color, 19) }
}

@Composable
private fun BriefMetric(label: String, value: Int, visible: Boolean, surface: Color, title: Color, summary: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(SquircleShape(13.dp))
            .background(surface.copy(alpha = 0.46f)).padding(horizontal = 7.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BriefAnimatedNumber(value, visible, 19, title)
        Text(text = label, style = COUITheme.textStyles.bodyS, color = summary, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, color: Color, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text = title, style = COUITheme.textStyles.headlineXS, color = color)
        if (subtitle.isNotBlank()) {
            Text(text = subtitle, style = COUITheme.textStyles.bodyS, color = secondary)
        }
    }
}

@Composable
private fun CategoryBar(
    name: String, amount: Int, total: Int, visible: Boolean, accent: Color, title: Color, summary: Color,
) {
    val context = LocalContext.current
    val enabled = remember(context) { briefMotionAllowed(context) }
    val fraction by animateFloatAsState(
        targetValue = if (visible || BriefMotionSession.hasOpened || !enabled) {
            if (total > 0) (amount.toFloat() / total).coerceIn(0f, 1f) else 0f
        } else 0f,
        animationSpec = tween(BriefMotionTiming.BAR_MS, easing = FastOutSlowInEasing),
        label = "brief-bar-fill",
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = COUITheme.textStyles.bodyS, color = title, modifier = Modifier.weight(1f))
            Text(text = amount.toString(), style = COUITheme.textStyles.bodyS, color = summary)
        }
        LinearProgressIndicator(
            progress = fraction,
            height = 7.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = accent,
                backgroundColor = accent.copy(alpha = 0.12f),
            ),
        )
    }
}

@Composable
private fun FutureChip(icon: ImageVector, title: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(SquircleShape(14.dp))
            .background(color.copy(alpha = 0.09f)).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        IconDecor(icon, color, 17)
        Text(text = title, style = COUITheme.textStyles.bodyS, color = color, maxLines = 1)
    }
}
