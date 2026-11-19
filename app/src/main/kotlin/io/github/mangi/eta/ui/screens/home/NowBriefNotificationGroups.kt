package io.github.mangi.eta.ui.screens.home

import android.widget.ImageView
import android.util.LruCache
import android.graphics.drawable.Drawable
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.motion.couiTouchMotion
import com.itos.coui.motion.rememberCouiTouchMotion
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationPriorityInput
import io.github.mangi.eta.agent.device.notifications.NotificationPriorityPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationPersonalization
import io.github.mangi.eta.ui.components.coui.CouiIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlinx.coroutines.launch

/** Categories remain independently collapsible. Rows themselves are virtualized by parent LazyColumn. */
internal data class BriefNotificationGroup(
    val packageName: String,
    val category: String,
    val priority: NotificationPriority,
    val entries: List<NotificationHistoryRepository.RecentEntry>,
)

internal fun priorityForBrief(entry: NotificationHistoryRepository.RecentEntry): NotificationPriority =
    NotificationPriorityPolicy.classify(NotificationPriorityInput(
        packageName = entry.packageName,
        title = entry.title,
        body = entry.text,
        channelId = entry.channelId,
        androidCategory = entry.androidCategory,
        topic = entry.userTopic ?: entry.topic,
        predictedLabel = entry.category,
        game = entry.topic == "games",
        repeatedNewsHeadline = entry.repeatedNewsHeadline,
        personalInterest = when (entry.personalInterest) {
            "WANTED" -> NotificationPersonalization.Interest.WANTED
            "UNWANTED" -> NotificationPersonalization.Interest.UNWANTED
            else -> NotificationPersonalization.Interest.NEUTRAL
        },
    ))

internal fun groupBriefNotifications(entries: List<NotificationHistoryRepository.RecentEntry>): List<BriefNotificationGroup> =
    entries.sortedWith(compareByDescending<NotificationHistoryRepository.RecentEntry> { it.postedAt }.thenByDescending { it.key })
        .groupBy { priorityForBrief(it) to (it.userTopic ?: it.topic) }
        .map { (key, list) -> BriefNotificationGroup(list.first().packageName, key.second, key.first, list) }
        .sortedWith(compareBy<BriefNotificationGroup> { it.priority.ordinal }
            .thenByDescending { it.entries.first().postedAt })

private fun categoryIcon(category: String): ImageVector = when (category) {
    "messages" -> Icons.Rounded.ChatBubbleOutline
    "security" -> Icons.Rounded.Security
    "development" -> Icons.Rounded.Code
    "learning" -> Icons.Rounded.School
    "work" -> Icons.Rounded.WorkOutline
    "calendar" -> Icons.Rounded.Event
    "tasks" -> Icons.Rounded.TaskAlt
    "finance" -> Icons.Rounded.AccountBalanceWallet
    "shopping" -> Icons.Rounded.LocalShipping
    "news" -> Icons.Rounded.Article
    "promotions" -> Icons.Rounded.Campaign
    "social" -> Icons.Rounded.Groups
    "games" -> Icons.Rounded.SportsEsports
    "system" -> Icons.Rounded.Settings
    else -> Icons.Rounded.NotificationsNone
}

@Composable
private fun BriefTopicIcon(category: String) {
    val colors = CouiPageDefaults.themeColors(CouiTheme.isDark)
    Box(Modifier.size(42.dp).clip(CouiShape(14.dp, 2f))
        .background(colors.summary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        CouiIcon(categoryIcon(category), contentDescription = null,
            tint = colors.title, modifier = Modifier.size(23.dp))
    }
}

@Composable
internal fun BriefNotificationAppCard(
    group: BriefNotificationGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: (List<String>) -> Unit,
    onTouchChange: (Boolean) -> Unit = {},
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val title = NotificationAdaptiveSettings.title(context, group.category)
    val source = remember { MutableInteractionSource() }
    val motion = rememberCouiTouchMotion(expanded)
    val unread = group.entries.count { it.state == NotificationHistoryRepository.DecisionState.PROPOSED }
    BriefDismissStretch(
        identity = "group:${group.priority.name}:${group.category}",
        onDismiss = { onDismiss(group.entries.map { it.key }) },
        swipeEnabled = !expanded,
        onTouchChange = onTouchChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(CouiShape(20.dp, 2f))
                .background(if (dark) Color(0xFF1D2024) else Color(0xFFF6F6F8))
                .couiTouchMotion(motion, expanded = expanded)
                .couiButtonFeedback(dark, source, enabled = true, list = true)
                .clickable(interactionSource = source, indication = null, role = Role.Button,
                    onClickLabel = if (expanded) "Replier $title" else "Déplier $title", onClick = onToggle)
                .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BriefTopicIcon(group.category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CouiText(text = title, kind = CouiTextKind.ItemTitle, color = colors.title,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                CouiText(text = "${group.entries.size} notification${if (group.entries.size > 1) "s" else ""}" +
                    if (unread > 0) " · $unread à vérifier" else "",
                    kind = CouiTextKind.ItemSummary, color = colors.summary, maxLines = 2)
            }
            CouiIcon(imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null, tint = colors.summary, modifier = Modifier.size(22.dp))
        }
    }
}

/** One notification = one LazyColumn item. No nested Column of 20–100 remembered rows. */
@Composable
internal fun BriefNotificationRow(
    entry: NotificationHistoryRepository.RecentEntry,
    onDismiss: (List<String>) -> Unit,
    onFeedback: (NotificationHistoryRepository.RecentEntry, NotificationUserFeedback) -> Unit,
    onCategoryChange: (NotificationHistoryRepository.RecentEntry, String) -> Unit,
    onOpen: (NotificationHistoryRepository.RecentEntry) -> Unit,
    onTouchChange: (Boolean) -> Unit = {},
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val appName = remember(entry.packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(entry.packageName, 0)).toString()
        }.getOrDefault(entry.packageName.substringAfterLast('.'))
    }
    var details by remember(entry.key) { mutableStateOf(false) }
    var showMenu by remember(entry.key) { mutableStateOf(false) }
    var chooseCategory by remember(entry.key) { mutableStateOf(false) }
    val anchor = rememberCouiMenuAnchor()
    val menuItems = if (chooseCategory) {
        listOf(CouiMenuItem(-1, "Retour")) + NotificationTopics.titles.keys.mapIndexed { index, topic ->
            CouiMenuItem(index + 10, NotificationAdaptiveSettings.title(context, topic))
        }
    } else buildList {
        add(CouiMenuItem(1, "Ouvrir l'application"))
        add(CouiMenuItem(2, "Intéressante"))
        add(CouiMenuItem(3, "Indésirable"))
        add(CouiMenuItem(4, "Changer la catégorie"))
        if (entry.state in setOf(NotificationHistoryRepository.DecisionState.DISMISSED,
                NotificationHistoryRepository.DecisionState.SNOOZED_1H,
                NotificationHistoryRepository.DecisionState.SNOOZED_24H)) {
            add(CouiMenuItem(5, "Suppression incorrecte"))
        }
        add(CouiMenuItem(6, "Archiver la copie"))
    }
    val status = when (entry.state) {
        NotificationHistoryRepository.DecisionState.DISMISSED -> "Supprimée sur Android"
        NotificationHistoryRepository.DecisionState.SNOOZED_1H -> "Reportée 1 h"
        NotificationHistoryRepository.DecisionState.SNOOZED_24H -> "Reportée 24 h"
        NotificationHistoryRepository.DecisionState.PROPOSED -> "À vérifier"
        NotificationHistoryRepository.DecisionState.QUEUED -> "Analyse en attente"
        NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR -> "Analyse échouée"
        NotificationHistoryRepository.DecisionState.BATTERY_PAUSED -> "Économie d'énergie"
        else -> ""
    }
    val date = remember(entry.postedAt) {
        DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.FRENCH)
            .format(Instant.ofEpochMilli(entry.postedAt).atZone(ZoneId.systemDefault()))
    }
    BriefDismissStretch(identity = entry.key, onDismiss = { onDismiss(listOf(entry.key)) },
        onTouchChange = onTouchChange,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 1.dp, bottom = 1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (dark) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.035f))
            .heightIn(min = 50.dp).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BriefApplicationIcon(entry.packageName, 32.dp)
            Column(Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Afficher les détails de ${entry.title}") { details = !details },
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CouiText(text = entry.title.ifBlank { appName }, kind = CouiTextKind.ItemTitle,
                    color = colors.title, maxLines = if (details) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis)
                if (entry.text.isNotBlank()) CouiText(text = entry.text,
                    kind = CouiTextKind.ItemSummary, color = colors.summary,
                    maxLines = if (details) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                CouiText(text = buildString {
                    append(appName)
                    append(" · ")
                    append(date)
                    if (status.isNotBlank()) { append(" · "); append(status) }
                }, kind = CouiTextKind.GroupFooter, color = colors.summary, maxLines = 3)
                if (details && entry.category != null && entry.score != null) {
                    CouiText(text = "${if (entry.category == NotificationFilterPolicy.AD) "Publicité" else if (entry.category == NotificationFilterPolicy.SPAM) "Spam" else entry.category} · " +
                        String.format(Locale.FRANCE, "%.1f %%", entry.score * 100),
                        kind = CouiTextKind.GroupFooter, color = colors.summary)
                }
            }
            Box {
                Box(modifier = Modifier.size(48.dp).couiMenuAnchor(anchor)
                    .clickable(role = Role.Button, onClickLabel = "Actions pour cette notification") {
                        chooseCategory = false
                        anchor.open { showMenu = true }
                    }, contentAlignment = Alignment.Center) {
                    CouiIcon(Icons.Rounded.MoreHoriz, "Actions", tint = colors.title,
                        modifier = Modifier.size(22.dp))
                }
                Menu(expanded = showMenu, onDismissRequest = { showMenu = false; chooseCategory = false },
                    items = menuItems, onItemClick = { item ->
                        showMenu = false
                        when (item.value) {
                            -1 -> { chooseCategory = false; anchor.open { showMenu = true } }
                            1 -> onOpen(entry)
                            2 -> onFeedback(entry, NotificationUserFeedback.INTERESTING)
                            3 -> onFeedback(entry, NotificationUserFeedback.UNWANTED)
                            4 -> { chooseCategory = true; anchor.open { showMenu = true } }
                            5 -> onFeedback(entry, NotificationUserFeedback.WRONG_REMOVAL)
                            6 -> onDismiss(listOf(entry.key))
                            else -> {
                                val topic = NotificationTopics.titles.keys.elementAtOrNull(item.value - 10)
                                if (topic != null) onCategoryChange(entry, topic)
                                chooseCategory = false
                            }
                        }
                    }, anchor = anchor)
            }
        }
    }
}

@Composable
private fun BriefApplicationIcon(packageName: String, size: Dp = 42.dp) {
    val context = LocalContext.current
    val icon = remember(packageName) {
        appIconCache[packageName] ?: runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()?.also { appIconCache.put(packageName, it) }
    }
    Box(
        modifier = Modifier.size(size).clip(CouiShape(10.dp, 2f))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            AndroidView(
                factory = { ctx -> ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(icon.constantState?.newDrawable()?.mutate() ?: icon)
                } },
                modifier = Modifier.size(size - 5.dp),
            )
        } else {
            CouiIcon(Icons.Rounded.NotificationsNone, contentDescription = null,
                tint = CouiPageDefaults.themeColors(CouiTheme.isDark).summary,
                modifier = Modifier.size(size - 9.dp))
        }
    }
}

private val appIconCache = LruCache<String, Drawable>(48)

/**
 * Inverse-like sticky resistance: first centimetres move slowly, then the
 * visual displacement accelerates as the finger approaches the edge.
 * Derivative is monotonic and odd (same behaviour to the left and right).
 * Input is cumulative finger travel, not previous visual offset.
 */
internal fun briefStickySwipeOffset(rawPx: Float, widthPx: Float): Float {
    if (!rawPx.isFinite() || !widthPx.isFinite() || widthPx <= 0f) return 0f
    val t = (abs(rawPx) / (widthPx * 0.78f)).coerceIn(0f, 1.75f)
    val movement = (t * 0.18f + t.pow(2.0f) * 0.82f) * widthPx
    return sign(rawPx) * movement.coerceAtMost(widthPx * 1.4f)
}

/** Notification-like bidirectional dismissal with COUI press and elastic release. */
@Composable
private fun BriefDismissStretch(
    identity: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onTouchChange: (Boolean) -> Unit = {},
    swipeEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settle = remember(identity) { Animatable(0f) }
    var fingerDeltaX by remember(identity) { mutableFloatStateOf(0f) }
    var horizontalDrag by remember(identity) { mutableStateOf(false) }
    // The same native rubber/deformation animator that COUI Menu uses.
    // It tracks finger position without stealing drags from child rows.
    val touchMotion = rememberCouiTouchMotion(swipeEnabled)
    var widthPx by remember(identity) { mutableFloatStateOf(1f) }
    var active by remember(identity) { mutableStateOf(true) }
    // If the card disappears during its release animation, the page must
    // never remain permanently locked against navigation gestures.
    DisposableEffect(identity) {
        onDispose { onTouchChange(false) }
    }
    if (!active) return

    val visualOffset = if (horizontalDrag) briefStickySwipeOffset(fingerDeltaX, widthPx)
        else settle.value
    Box(
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .couiTouchMotion(touchMotion, expanded = swipeEnabled)
            // Initial pass claims the full touch lifetime, even before the
            // horizontal slop threshold; the page-level gesture is disabled.
            .pointerInput(identity) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onTouchChange(true)
                    try {
                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        onTouchChange(false)
                    }
                }
            },
    ) {
        val swipeColor = CouiPageDefaults.themeColors(CouiTheme.isDark).summary
        Box(
            modifier = Modifier.matchParentSize()
                .graphicsLayer { alpha = (abs(visualOffset) / (widthPx * 0.5f)).coerceIn(0f, 1f) }
                .clip(CouiShape(22.dp, 2f))
                .background(swipeColor.copy(alpha = 0.16f)),
        ) {
            CouiIcon(Icons.Rounded.Close, null, tint = swipeColor,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 17.dp)
                    .size(23.dp).graphicsLayer { alpha = if (visualOffset > 0f) 1f else 0f })
            CouiIcon(Icons.Rounded.Close, null, tint = swipeColor,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 17.dp)
                    .size(23.dp).graphicsLayer { alpha = if (visualOffset < 0f) 1f else 0f })
        }
        Box(
            modifier = Modifier.fillMaxWidth()
                .pointerInput(identity, widthPx, swipeEnabled) {
                    if (swipeEnabled) detectHorizontalDragGestures(
                        onDragStart = {
                            scope.launch { settle.stop() }
                            fingerDeltaX = 0f
                            horizontalDrag = true
                        },
                        onDragEnd = {
                            val drag = fingerDeltaX
                            horizontalDrag = false
                            scope.launch {
                                settle.snapTo(briefStickySwipeOffset(drag, widthPx))
                                if (abs(drag) >= widthPx * 0.43f) {
                                    settle.animateTo(
                                        targetValue = sign(drag) * widthPx * 1.35f,
                                        animationSpec = spring(
                                            dampingRatio = 0.82f,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                    active = false
                                    onDismiss()
                                } else {
                                    settle.animateTo(0f, spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ))
                                }
                            }
                        },
                        onDragCancel = {
                            horizontalDrag = false
                            val drag = fingerDeltaX
                            scope.launch {
                                settle.snapTo(briefStickySwipeOffset(drag, widthPx))
                                settle.animateTo(0f, spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ))
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            fingerDeltaX = (fingerDeltaX + delta).coerceIn(-widthPx * 1.3f, widthPx * 1.3f)
                        },
                    )
                }
                .graphicsLayer {
                    translationX = visualOffset + touchMotion.translationX.value
                    translationY = touchMotion.translationY.value
                    val fraction = (abs(visualOffset) / widthPx).coerceIn(0f, 1f)
                    scaleX = touchMotion.scaleX.value * (1f + fraction * 0.06f)
                    scaleY = touchMotion.scaleY.value * (1f - fraction * 0.025f)
                    // COUI Menu scales around its centre; only an actual swipe
                    // anchors the stretched edge to the drag direction.
                    transformOrigin = if (abs(visualOffset) < 1f) TransformOrigin.Center
                        else TransformOrigin(if (visualOffset >= 0f) 0f else 1f, 0.5f)
                },
        ) { content() }
    }
}
