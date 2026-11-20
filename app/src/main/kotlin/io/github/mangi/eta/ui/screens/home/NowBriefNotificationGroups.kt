package io.github.mangi.eta.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.remote.ActionProposal
import io.github.mangi.eta.agent.device.notifications.remote.ActionType
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.ExpandableGroupArrow
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.effect.LocalCOUIMaterialEffectEnabled
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.More
import io.github.suqi8.coui.kmp.menu.OverlayIconDropdownMenu
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Categories remain independently collapsible. Rows themselves are virtualized by parent LazyColumn. */
internal data class BriefNotificationGroup(
    val packageName: String,
    val category: String,
    val priority: NotificationPriority,
    val entries: List<NotificationHistoryRepository.RecentEntry>,
    val applicationCount: Int? = null,
) {
    val id: String get() = if (applicationCount == null) "${priority.name}:$category" else "app:$packageName"
}

internal fun groupBriefNotifications(entries: List<NotificationHistoryRepository.RecentEntry>): List<BriefNotificationGroup> =
    entries.sortedWith(compareByDescending<NotificationHistoryRepository.RecentEntry> { it.postedAt }.thenByDescending { it.key })
        .groupBy { it.effectivePriority to (it.userTopic ?: it.topic) }
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

/**
 * One segment of a COUI card that spans several LazyColumn items. It paints the same surface as
 * [io.github.suqi8.coui.kmp.basic.Card] (card background, Normal.XL smooth corners), rounding only
 * the outer edges of the group, so a long notification list stays virtualized.
 */
@Composable
internal fun Modifier.briefCardSegment(position: CardListPosition): Modifier {
    // A middle segment has no rounded corner: a plain fill avoids the squircle's offscreen layer.
    if (position == CardListPosition.Middle) return background(COUITheme.colorScheme.cardBackground)
    val radius = CardDefaults.CornerRadius
    val top = if (position == CardListPosition.Head || position == CardListPosition.Full) radius else 0.dp
    val bottom = if (position == CardListPosition.Tail || position == CardListPosition.Full) radius else 0.dp
    return squircleSurface(
        color = COUITheme.colorScheme.cardBackground,
        topStart = top,
        topEnd = top,
        bottomEnd = bottom,
        bottomStart = bottom,
        weight = COUIRoundCorner.Normal.Weight,
    )
}

/** Horizontal inset of brief cards, the same as settings cards. */
internal val BriefCardInset = 16.dp

@Composable
private fun BriefTopicIcon(category: String) {
    Icon(
        imageVector = categoryIcon(category),
        contentDescription = null,
        tint = COUITheme.colorScheme.labelPrimary,
        modifier = Modifier.size(24.dp),
    )
}

/** The header row of a category card; the card continues with one item per notification. */
@Composable
internal fun BriefNotificationAppCard(
    group: BriefNotificationGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: (List<NotificationHistoryRepository.RecentEntry>) -> Job,
    onTouchChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val byApp = group.applicationCount != null
    val title = if (byApp) briefApplicationName(context, group.packageName)
        else NotificationAdaptiveSettings.title(context, group.category)
    val count = group.applicationCount ?: group.entries.size
    val unread = group.entries.count { it.state == NotificationHistoryRepository.DecisionState.PROPOSED }
    val position = if (expanded) CardListPosition.Head else CardListPosition.Full
    BriefDismissStretch(
        identity = "group:${group.id}",
        onDismiss = { onDismiss(group.entries) },
        swipeEnabled = !expanded && !byApp,
        onTouchChange = onTouchChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = BriefCardInset)
            .padding(top = 8.dp),
        position = position,
    ) {
        BasicComponent(
            modifier = Modifier.briefCardSegment(position),
            title = title,
            summary = "$count notification${if (count > 1) "s" else ""}" +
                if (unread > 0) " · $unread à vérifier" else "",
            startAction = {
                if (byApp) BriefApplicationIcon(group.packageName, 32.dp) else BriefTopicIcon(group.category)
            },
            endActions = { ExpandableGroupArrow(expanded = expanded) },
            cardListPosition = position,
            onClick = onToggle,
            onClickLabel = if (expanded) "Replier $title" else "Déplier $title",
        )
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.FRENCH)

/** One notification = one LazyColumn item, drawn as a middle or tail segment of its category card. */
@Composable
internal fun BriefNotificationRow(
    entry: NotificationHistoryRepository.RecentEntry,
    last: Boolean,
    actions: BriefNotificationActions,
    onTouchChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appName = remember(entry.packageName) { briefApplicationName(context, entry.packageName) }
    var details by remember(entry.key) { mutableStateOf(false) }
    val status = when {
        entry.androidActionState == NotificationHistoryRepository.AndroidActionState.CONFIRMED ->
            "Retirée des notifications Android"
        entry.androidActionState == NotificationHistoryRepository.AndroidActionState.REQUESTED -> "Retrait demandé"
        entry.syncState == NotificationHistoryRepository.SyncState.QUEUED ||
            entry.syncState == NotificationHistoryRepository.SyncState.SENT -> "Analyse en attente"
        entry.syncState == NotificationHistoryRepository.SyncState.ERROR -> "Analyse en attente · homelab indisponible"
        entry.syncState == NotificationHistoryRepository.SyncState.EXPIRED -> "Non analysée"
        entry.remoteProtected -> "Protégée"
        else -> legacyStatus(entry.state)
    }
    val proposals = rememberBriefNotificationProposals(entry)
    val meta = remember(entry.postedAt, appName, status) {
        buildString {
            append(appName)
            append(" · ")
            append(dateFormat.format(Instant.ofEpochMilli(entry.postedAt).atZone(ZoneId.systemDefault())))
            if (status.isNotBlank()) { append(" · "); append(status) }
        }
    }
    val position = if (last) CardListPosition.Tail else CardListPosition.Middle
    BriefDismissStretch(
        identity = entry.key,
        onDismiss = { actions.archiveEntries(listOf(entry)) },
        onTouchChange = onTouchChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = BriefCardInset),
        position = position,
    ) {
        Column(Modifier.briefCardSegment(position)) {
            HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp))
            BasicComponent(
                startAction = { BriefApplicationIcon(entry.packageName, 32.dp) },
                endActions = {
                    BriefNotificationMenu(entry, actions, proposals) { actions.archiveEntries(listOf(entry)) }
                },
                cardListPosition = position,
                onClick = { details = !details },
                onClickLabel = "Afficher les détails de ${entry.title}",
            ) {
                val lines = if (details) Int.MAX_VALUE else 2
                Text(
                    text = entry.title.ifBlank { appName },
                    style = COUITheme.textStyles.headlineXS,
                    color = COUITheme.colorScheme.labelPrimary,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.text.isNotBlank()) Text(
                    text = entry.text,
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = meta,
                    style = COUITheme.textStyles.bodyXS,
                    color = COUITheme.colorScheme.labelTertiary,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (details) {
                    // Decision explanation from Mastra; nothing is recomputed on the phone.
                    val explanation = buildList {
                        add(NotificationAdaptiveSettings.title(context, entry.userTopic ?: entry.topic) + " · " +
                            priorityTitle(entry.effectivePriority))
                        entry.reason?.takeIf { it.isNotBlank() }?.let(::add)
                        if (proposals.isNotEmpty()) add("Suggestions : " + proposals.joinToString(" · ") { it.label })
                        if (entry.remoteUncertainty == "HIGH") add("Décision incertaine : conservée par prudence")
                    }.joinToString("\n")
                    Text(
                        text = explanation,
                        style = COUITheme.textStyles.bodyXS,
                        color = COUITheme.colorScheme.labelTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberBriefNotificationProposals(entry: NotificationHistoryRepository.RecentEntry): List<ActionProposal> =
    remember(entry.actionsJson) {
        MastraNotificationSync.decodeActions(entry.actionsJson).filter { it.type != ActionType.OPEN_SOURCE_APP }
    }

@Composable
internal fun BriefNotificationMenu(
    entry: NotificationHistoryRepository.RecentEntry,
    callbacks: BriefNotificationActions,
    proposals: List<ActionProposal>,
    onArchive: () -> Unit,
) {
    val context = LocalContext.current
    val removable = entry.state in setOf(
        NotificationHistoryRepository.DecisionState.DISMISSED,
        NotificationHistoryRepository.DecisionState.SNOOZED_1H,
        NotificationHistoryRepository.DecisionState.SNOOZED_24H,
    ) || entry.androidActionState == NotificationHistoryRepository.AndroidActionState.CONFIRMED
    val topics = NotificationTopics.titles.keys
    val currentPriority = entry.effectivePriority
    val menu = remember(entry, callbacks, proposals, onArchive) {
        listOf(
            DropdownEntry(items = buildList {
                proposals.forEach { action -> add(DropdownItem(text = action.label, onClick = { callbacks.onAction(entry, action) })) }
                add(DropdownItem(text = "Ouvrir l'application", onClick = { callbacks.open(entry) }))
            }),
            DropdownEntry(items = buildList {
                add(DropdownItem(text = "Intéressante",
                    onClick = { callbacks.markFeedback(entry, NotificationUserFeedback.INTERESTING) }))
                add(DropdownItem(text = "Indésirable",
                    onClick = { callbacks.markFeedback(entry, NotificationUserFeedback.UNWANTED) }))
                add(DropdownItem(text = "Corriger la priorité", children = NotificationPriority.entries.map { p ->
                    DropdownItem(text = priorityTitle(p), selected = p == currentPriority,
                        onClick = { callbacks.correctPriority(entry, p) })
                }))
                add(DropdownItem(text = "Changer la catégorie", children = topics.map { topic ->
                    DropdownItem(
                        text = NotificationAdaptiveSettings.title(context, topic),
                        selected = topic == (entry.userTopic ?: entry.topic),
                        onClick = { callbacks.correctCategory(entry, topic) },
                    )
                }))
                if (removable) add(DropdownItem(text = "Suppression incorrecte",
                    onClick = { callbacks.markFeedback(entry, NotificationUserFeedback.WRONG_REMOVAL) }))
            }),
            DropdownEntry(items = listOf(
                DropdownItem(text = "Archiver la copie", alert = true,
                    onClick = onArchive),
            )),
        )
    }
    // A list row's "more" action is a bare icon in COUI; without this the
    // icon button draws a frosted material disc on every notification.
    CompositionLocalProvider(LocalCOUIMaterialEffectEnabled provides false) {
        OverlayIconDropdownMenu(entries = menu, minWidth = 36.dp, minHeight = 36.dp,
            backgroundColor = Color.Transparent) {
            Icon(
                imageVector = COUIIcons.Basic.More,
                contentDescription = "Actions pour cette notification",
                tint = COUITheme.colorScheme.labelSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun priorityTitle(priority: NotificationPriority): String = when (priority) {
    NotificationPriority.NOW -> "À traiter maintenant"
    NotificationPriority.TODAY -> "À voir aujourd'hui"
    NotificationPriority.LOW -> "Sans importance"
}

private fun legacyStatus(state: NotificationHistoryRepository.DecisionState): String = when (state) {
    NotificationHistoryRepository.DecisionState.DISMISSED -> "Retirée des notifications Android"
    NotificationHistoryRepository.DecisionState.SNOOZED_1H -> "Reportée 1 h"
    NotificationHistoryRepository.DecisionState.SNOOZED_24H -> "Reportée 24 h"
    NotificationHistoryRepository.DecisionState.QUEUED -> "Analyse en attente"
    NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR -> "Analyse en attente · homelab indisponible"
    else -> ""
}

internal fun briefApplicationName(context: android.content.Context, packageName: String): String =
    appNameCache.get(packageName) ?: runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName.substringAfterLast('.')).also { appNameCache.put(packageName, it) }

/**
 * Application icons are rasterized once off the main thread and cached; a scrolling list then
 * draws plain bitmaps instead of inflating an Android ImageView per row.
 */
@Composable
internal fun BriefApplicationIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val cacheKey = "$packageName@$px"
    val bitmap by produceState(appIconCache.get(cacheKey), cacheKey) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val source = context.packageManager.getApplicationIcon(packageName)
                    val drawable = source.constantState?.newDrawable()?.mutate() ?: source
                    val raster = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, px, px)
                    drawable.draw(Canvas(raster))
                    raster.asImageBitmap()
                }.getOrNull()?.also { appIconCache.put(cacheKey, it) }
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Icon(
            imageVector = Icons.Rounded.NotificationsNone,
            contentDescription = null,
            tint = COUITheme.colorScheme.labelSecondary,
            modifier = Modifier.size(size).padding(4.dp),
        )
    }
}

private val appIconCache = LruCache<String, ImageBitmap>(64)
private val appNameCache = LruCache<String, String>(128)

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

/**
 * Notification-like bidirectional dismissal with an elastic release. The revealed background
 * takes the shape of the card segment underneath, so a swiped row never shows square corners.
 */
@Composable
private fun BriefDismissStretch(
    identity: String,
    onDismiss: () -> Job,
    position: CardListPosition,
    modifier: Modifier = Modifier,
    onTouchChange: (Boolean) -> Unit = {},
    swipeEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settle = remember(identity) { Animatable(0f) }
    var fingerDeltaX by remember(identity) { mutableFloatStateOf(0f) }
    var horizontalDrag by remember(identity) { mutableStateOf(false) }
    var widthPx by remember(identity) { mutableFloatStateOf(1f) }
    // If the card disappears during its release animation, the page must
    // never remain permanently locked against navigation gestures.
    DisposableEffect(identity) {
        onDispose { onTouchChange(false) }
    }

    fun visualOffset(): Float = if (horizontalDrag) briefStickySwipeOffset(fingerDeltaX, widthPx) else settle.value
    Box(
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
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
        val swipeColor = COUITheme.colorScheme.labelSecondary
        Box(
            modifier = Modifier.matchParentSize()
                .graphicsLayer { alpha = (abs(visualOffset()) / (widthPx * 0.5f)).coerceIn(0f, 1f) }
                .briefCardSegment(position),
        ) {
            Icon(Icons.Rounded.Close, null, tint = swipeColor,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 17.dp)
                    .size(23.dp).graphicsLayer { alpha = if (visualOffset() > 0f) 1f else 0f })
            Icon(Icons.Rounded.Close, null, tint = swipeColor,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 17.dp)
                    .size(23.dp).graphicsLayer { alpha = if (visualOffset() < 0f) 1f else 0f })
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
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium),
                                    )
                                    onDismiss().join()
                                    // A failed or stale archive leaves this row in the history.
                                    settle.animateTo(0f)
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
                // Read the offset in the draw phase only: dragging never recomposes the row.
                .graphicsLayer {
                    val offset = visualOffset()
                    translationX = offset
                    val fraction = (abs(offset) / widthPx).coerceIn(0f, 1f)
                    scaleX = 1f + fraction * 0.06f
                    scaleY = 1f - fraction * 0.025f
                    transformOrigin = if (abs(offset) < 1f) TransformOrigin.Center
                        else TransformOrigin(if (offset >= 0f) 0f else 1f, 0.5f)
                },
        ) { content() }
    }
}
