package io.github.mangi.eta.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.agent.device.notifications.NotificationBriefEvent
import io.github.mangi.eta.agent.device.notifications.NotificationBriefOutcome
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.LocalNotificationModel
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterAction
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    onNotificationTouchChange: (Boolean) -> Unit = {},
    onManageFiltering: () -> Unit = {},
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val summary by NotificationObservation.summary.collectAsState()
    val notificationsChanged by NotificationActivityFeed.changes.collectAsState()
    var activityEntries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
    var activityError by remember { mutableStateOf(false) }
    var undoEntries by remember { mutableStateOf<List<NotificationHistoryRepository.RecentEntry>>(emptyList()) }
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
    val reviewedEntries = remember(activityEntries) {
        activityEntries.filter { it.state in setOf(
            NotificationHistoryRepository.DecisionState.PROPOSED,
            NotificationHistoryRepository.DecisionState.QUEUED,
            NotificationHistoryRepository.DecisionState.MODEL_MISSING,
            NotificationHistoryRepository.DecisionState.BATTERY_PAUSED,
            NotificationHistoryRepository.DecisionState.LIMIT_REACHED,
            NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR,
            NotificationHistoryRepository.DecisionState.SNOOZED_1H,
            NotificationHistoryRepository.DecisionState.SNOOZED_24H,
            NotificationHistoryRepository.DecisionState.DISMISSED,
        ) }
    }
    val recentEntries = remember(activityEntries) { activityEntries - reviewedEntries.toSet() }
    val reviewedGroups = remember(reviewedEntries) { groupBriefNotifications(reviewedEntries) }
    val notificationGroups = remember(recentEntries) { groupBriefNotifications(recentEntries) }
    val removedCount = reviewedEntries.count {
        it.state == NotificationHistoryRepository.DecisionState.DISMISSED
    }
    val proposedCount = reviewedEntries.count {
        it.state == NotificationHistoryRepository.DecisionState.PROPOSED
    }
    val snoozedCount = reviewedEntries.count {
        it.state in setOf(
            NotificationHistoryRepository.DecisionState.SNOOZED_1H,
            NotificationHistoryRepository.DecisionState.SNOOZED_24H,
        )
    }
    val pendingAnalysisCount = reviewedEntries.size - removedCount - proposedCount - snoozedCount
    val filterConfig = remember(visible) { NotificationFilterSettings.load(context) }
    val listenerReady = remember(visible) { AgentNotificationHistoryService.isEnabled(context) }
    val modelReady = remember(visible) { LocalNotificationModel.installed(context) }
    val dismissActivityEntries: (List<String>) -> Unit = { keys ->
        val removed = activityEntries.filter { it.key in keys }
        if (removed.isNotEmpty()) {
            activityEntries = activityEntries.filterNot { it.key in keys }
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.archiveFromBrief(keys)
                    removed.forEach { repository.feedback(it.key, it.postedAt, NotificationUserFeedback.ARCHIVED) }
                } }
                    .onSuccess { undoEntries = (undoEntries + removed).distinctBy { it.key } }
                    .onFailure {
                        activityEntries = (activityEntries + removed).distinctBy { it.key }
                            .sortedByDescending { it.postedAt }
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
                } }.onSuccess { NotificationActivityFeed.notifyChanged() }
                    .onFailure { activityError = true }
            }
        }
    val correctCategory: (NotificationHistoryRepository.RecentEntry, String) -> Unit =
        { entry, category ->
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    repository.feedback(entry.key, entry.postedAt, NotificationUserFeedback.CATEGORY_CORRECTED, category)
                } }.onSuccess { NotificationActivityFeed.notifyChanged() }
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
    val undoDismissal: () -> Unit = {
        val restoring = undoEntries
        undoEntries = emptyList()
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                repository.restoreToBrief(restoring)
                restoring.forEach { repository.feedback(it.key, it.postedAt, NotificationUserFeedback.RESTORED) }
            } }
                .onSuccess {
                    activityEntries = (activityEntries + restoring).distinctBy { it.key }
                        .sortedByDescending { it.postedAt }
                    activityError = false
                }
                .onFailure { undoEntries = restoring; activityError = true }
        }
    }
    val dateText = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH),
    ).replaceFirstChar { it.uppercaseChar() }
    val cardSurface = if (dark) Color(0xFF464A51) else Color(0xFFE9E9E9)
    val showSessionSummary = summary.analyzed > 0 || summary.filtered > 0 ||
        summary.kept > 0 || summary.proposed > 0
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
    val heroPress = remember { MutableInteractionSource() }
    var heroExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
    CouiLazyScaffoldPage(
        title = "Mon briefing",
        onBack = onClose,
        // Same surface as AgentAppShell's conversation scaffold, so the
        // first swipe frame has no abrupt black-to-COUI color transition.
        containerColor = MiuixTheme.colorScheme.surface,
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
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "date") {
            BriefReveal("date", visible, 0, visited) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CouiText(
                        text = dateText,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (showSessionSummary) item(key = "notifications:hero") {
            BriefReveal("hero", visible, 1, visited) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 28.dp).padding(bottom = 14.dp)
                    .menuCardSurface(dark, prominent = true)
                    .couiButtonFeedback(dark, heroPress, enabled = true)
                    .clickable(
                        interactionSource = heroPress, indication = null,
                        role = Role.Button, onClickLabel = "Détails des notifications filtrées",
                        onClick = { heroExpanded = !heroExpanded },
                    )
                    .semantics { stateDescription = if (heroExpanded) "Développé" else "Réduit" }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    IconBubble(Icons.Rounded.AutoAwesome, colors.accent)
                    CouiText(text = "Votre tranquillité", kind = CouiTextKind.ItemTitle, color = colors.title)
                }
                BriefAnimatedNumber(summary.filtered, visible, 36, colors.title)
                CouiText(
                    text = if (summary.filtered == 1) "notification filtrée pendant cette session"
                    else "notifications filtrées pendant cette session",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                if (LocalDensity.current.fontScale >= 1.25f) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BriefMetric("Analysées", summary.analyzed, visible, cardSurface, colors.title, colors.summary, Modifier.fillMaxWidth())
                        BriefMetric("Conservées", summary.kept, visible, cardSurface, colors.title, colors.summary, Modifier.fillMaxWidth())
                        BriefMetric("Reportées", summary.snoozed, visible, cardSurface, colors.title, colors.summary, Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BriefMetric("Analysées", summary.analyzed, visible, cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                        BriefMetric("Conservées", summary.kept, visible, cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                        BriefMetric("Reportées", summary.snoozed, visible, cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                    }
                }
                AnimatedVisibility(visible = heroExpanded) {
                    CouiText(
                        text = briefCount(summary.dismissed, "suppression confirmée", "suppressions confirmées") +
                            " · " + briefCount(summary.proposed, "filtrage proposé", "filtrages proposés"),
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
            }
            }
        }

        if (summary.filtered > 0) item(key = "notifications:categories") {
            BriefReveal("categories", visible, 2, visited) {
            BriefCard(onClick = { categoryExpanded = !categoryExpanded },
                onClickLabel = "Afficher ou masquer le détail des catégories") {
                SectionHeader("Ce qui a été filtré", "", colors.title, colors.summary)
                Spacer(Modifier.height(11.dp))
                val classified = summary.filtered
                CategoryBar("Publicités", summary.filteredAds, classified, visible, colors.accent, colors.title, colors.summary)
                Spacer(Modifier.height(11.dp))
                CategoryBar("Spams", summary.filteredSpam, classified, visible, Color(0xFFCA8358), colors.title, colors.summary)
                AnimatedVisibility(visible = categoryExpanded) {
                    CouiText(
                        text = "Reportées : ${summary.snoozed} · Supprimées : ${summary.dismissed}",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
                if (summary.dismissed > 0) {
                    Spacer(Modifier.height(13.dp))
                    CouiText(
                        text = briefCount(summary.dismissed, "suppression définitive", "suppressions définitives") +
                            " · irréversible sur Android",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
            }
            }
        }

        item(key = "notification-filter:settings") {
            BriefCard(onClick = { onClose(); onManageFiltering() },
                onClickLabel = "Ouvrir les paramètres du filtrage") {
                SectionHeader("Filtrage des notifications", "Paramètres", colors.title, colors.summary)
                Spacer(Modifier.height(8.dp))
                val description = when {
                    !listenerReady -> "Accès aux notifications à autoriser"
                    !modelReady -> "Modèle local à installer"
                    filterConfig.mode == NotificationFilterMode.OFF -> "Désactivé"
                    filterConfig.mode == NotificationFilterMode.OBSERVE ->
                        "Observation uniquement · aucune suppression sur Android"
                    filterConfig.mode == NotificationFilterMode.CAUTIOUS ->
                        "Prudent · publicités très certaines reportées 1 h"
                    filterConfig.action == NotificationFilterAction.DISMISS ->
                        "Suppression automatique · toutes les applications éligibles"
                    else -> "Filtrage automatique · toutes les applications éligibles"
                }
                CouiText(text = description, kind = CouiTextKind.ItemSummary, color = colors.summary)
                if (pendingAnalysisCount + proposedCount > 0) {
                    CouiText(
                        text = briefCount(pendingAnalysisCount + proposedCount,
                            "analyse à suivre", "analyses à suivre") + " · ouvrir le diagnostic",
                        kind = CouiTextKind.GroupFooter, color = colors.summary,
                    )
                }
            }
        }
        if (reviewedGroups.isNotEmpty() || notificationGroups.isNotEmpty()) {
            item(key = "notification-feed:gesture-help") {
                CouiText(
                    text = "Glisser archive la copie de ce briefing. " +
                        "Elle reste accessible dans les paramètres ; la notification Android reste intacte.",
                    kind = CouiTextKind.GroupFooter, color = colors.summary,
                    modifier = Modifier.padding(horizontal = 30.dp, vertical = 6.dp),
                )
            }
        }
        if (reviewedGroups.isNotEmpty()) {
            item(key = "notification-filter:review-title") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 9.dp, bottom = 8.dp, start = 30.dp, end = 30.dp),
                ) {
                    BriefHeading("Suivi du filtrage", color = colors.title, size = 19)
                }
            }
            item(key = "notification-filter:review-counts") {
                val counts = buildList {
                    if (removedCount > 0) add(briefCount(removedCount, "suppression définitive", "suppressions définitives"))
                    if (snoozedCount > 0) add(briefCount(snoozedCount, "report", "reports"))
                    if (proposedCount > 0) add(briefCount(proposedCount, "proposition", "propositions"))
                    if (pendingAnalysisCount > 0) add(briefCount(pendingAnalysisCount, "analyse à suivre", "analyses à suivre"))
                }
                CouiText(
                    text = counts.joinToString(" · "),
                    kind = CouiTextKind.GroupFooter, color = colors.summary,
                    modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 10.dp),
                )
            }
            items(reviewedGroups, key = { "review:" + it.category }) { group ->
                BriefNotificationAppCard(group, dismissActivityEntries, markFeedback, correctCategory,
                    openNotification, onNotificationTouchChange)
            }
        }

        if (reviewedGroups.isEmpty() || notificationGroups.isNotEmpty()) {
            item(key = "notifications:recent-title") {
                BriefReveal("recent", visible, 3, visited) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 11.dp, start = 30.dp, end = 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BriefHeading("Activité récente", color = colors.title, size = 19)
                    }
                }
            }
            item(key = "notification-feed:description") {
                CouiText(
                    text = "7 derniers jours · " +
                        briefCount(activityEntries.size, "notification reçue", "notifications reçues") +
                        " · ${recentEntries.size} dans cette section"
                        + if (activityEntries.size >= 300) " (limite d'affichage)" else "",
                    kind = CouiTextKind.GroupFooter, color = colors.summary,
                    modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 11.dp),
                )
            }
            if (activityError) {
                item(key = "notification-feed:error") {
                    CouiText(
                        text = "Historique indisponible. Vérifiez l'accès aux notifications.",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 8.dp),
                    )
                }
            }
            if (notificationGroups.isEmpty()) {
                item(key = "notification-feed:empty") {
                    BriefCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.NotificationsNone, colors.summary)
                            Spacer(Modifier.width(12.dp))
                            CouiText(
                                text = if (!listenerReady) "L’activité apparaîtra ici après autorisation"
                                    else "Aucune notification récente",
                                kind = CouiTextKind.ItemTitle, color = colors.title,
                            )
                        }
                    }
                }
            } else {
                items(notificationGroups, key = { "topic:" + it.category }) { group ->
                    BriefNotificationAppCard(group, dismissActivityEntries, markFeedback, correctCategory,
                        openNotification, onNotificationTouchChange)
                }
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

    }
    if (undoEntries.isNotEmpty()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .menuCardSurface(dark)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CouiText(
                text = briefCount(undoEntries.size, "copie archivée", "copies archivées") +
                    ". ${if (undoEntries.size == 1) "Retrouvez-la" else "Retrouvez-les"} " +
                    "dans Paramètres › Notifications intelligentes › Archives du briefing.",
                kind = CouiTextKind.ItemSummary, color = colors.title,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CouiButton(text = "Annuler", onClick = undoDismissal, modifier = Modifier.weight(1f))
                CouiButton(text = "Fermer", onClick = { undoEntries = emptyList() },
                    modifier = Modifier.weight(1f))
            }
        }
    }
    }
}
private fun briefCount(count: Int, singular: String, plural: String): String =
    "$count ${if (count == 1) singular else plural}"

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
private fun BriefNotificationArrival(
    event: NotificationBriefEvent,
    visible: Boolean,
    cardSurface: Color,
    title: Color,
    summary: Color,
    accent: Color,
) {
    val context = LocalContext.current
    val enabled = remember(context) { briefMotionAllowed(context) }
    var shown by remember(event.timestampMs) { mutableStateOf(false) }
    LaunchedEffect(event.timestampMs, visible) {
        if (visible || !enabled) shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(BriefMotionTiming.NOTIFICATION_MS)) +
            slideInVertically(
                animationSpec = tween(BriefMotionTiming.NOTIFICATION_MS, easing = FastOutSlowInEasing),
                initialOffsetY = { -(it / 8) },
            ),
        exit = fadeOut(tween(100)),
    ) {
        BriefEventRow(event, cardSurface, title, summary, accent)
    }
}

@Composable
private fun BriefHeading(text: String, color: Color, size: Int) {
    val style = rememberCouiTextStyle(CouiTextKind.ItemTitle)
    BasicText(text, style = style.copy(color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
        fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** COUI menu-inspired near-black floating surfaces without glass gradients. */
private fun Modifier.menuCardSurface(dark: Boolean, prominent: Boolean = false): Modifier {
    val shape = CouiShape(if (prominent) 30.dp else 27.dp, 2f)
    return this
        .shadow(if (prominent) 12.dp else 7.dp, shape, clip = false)
        .clip(shape)
        // Slightly translucent so the green/orange ambient halo remains visible.
        .background(
            if (dark) Color(0xFF17191C).copy(alpha = 0.86f)
            else Color(0xFFF9F9FA).copy(alpha = 0.90f),
            shape,
        )
}

@Composable
private fun BriefCard(
    onClick: (() -> Unit)? = null,
    onClickLabel: String = "Afficher les détails",
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val dark = CouiTheme.isDark
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 28.dp).padding(bottom = 15.dp)
            .menuCardSurface(dark)
            .then(
                if (onClick != null) Modifier
                    .couiButtonFeedback(dark, source, enabled = true, list = true)
                    .clickable(
                        interactionSource = source, indication = null,
                        role = Role.Button, onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                else Modifier,
            )
            .padding(horizontal = 17.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
private fun IconDecor(icon: ImageVector, color: Color, size: Int) {
    CouiIcon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(size.dp))
}

@Composable
private fun IconBubble(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(34.dp).clip(CouiShape(12.dp, 2f)).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { IconDecor(icon, color, 19) }
}

@Composable
private fun BriefMetric(label: String, value: Int, visible: Boolean, surface: Color, title: Color, summary: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(CouiShape(13.dp, 2f))
            .background(surface.copy(alpha = 0.46f)).padding(horizontal = 7.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BriefAnimatedNumber(value, visible, 19, title)
        CouiText(text = label, kind = CouiTextKind.GroupFooter, color = summary, maxLines = 2)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, color: Color, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        CouiText(text = title, kind = CouiTextKind.ItemTitle, color = color)
        if (subtitle.isNotBlank()) {
            CouiText(text = subtitle, kind = CouiTextKind.ItemSummary, color = secondary)
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
            CouiText(text = name, kind = CouiTextKind.ItemSummary, color = title, modifier = Modifier.weight(1f))
            CouiText(text = amount.toString(), kind = CouiTextKind.ItemSummary, color = summary)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(7.dp)
                .clip(CircleShape).background(accent.copy(alpha = 0.12f)),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier.fillMaxWidth(fraction)
                        .height(7.dp).background(accent, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun BriefEventRow(event: NotificationBriefEvent, surface: Color, title: Color, summary: Color, accent: Color) {
    val context = LocalContext.current
    val appName = remember(event.packageName) {
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(event.packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(event.packageName.substringAfterLast('.'))
    }
    val category = when (event.category) {
        NotificationFilterPolicy.AD -> "Publicité"
        NotificationFilterPolicy.SPAM -> "Spam"
        else -> "Autre catégorie"
    }
    val (state, actionColor) = when (event.outcome) {
        NotificationBriefOutcome.SNOOZED_1H -> "Reportée 1 h" to accent
        NotificationBriefOutcome.SNOOZED_24H -> "Reportée 24 h" to accent
        NotificationBriefOutcome.DISMISSED -> "Supprimée" to Color(0xFFCD6658)
        NotificationBriefOutcome.KEPT -> "Conservée" to summary
        NotificationBriefOutcome.OBSERVED -> "Observée" to summary
        NotificationBriefOutcome.PROPOSED -> "Filtrage proposé" to summary
    }
    val time = remember(event.timestampMs) {
        DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH).format(
            Instant.ofEpochMilli(event.timestampMs).atZone(ZoneId.systemDefault()),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            .padding(bottom = 9.dp)
            .menuCardSurface(CouiTheme.isDark)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBubble(
            if (event.outcome in setOf(NotificationBriefOutcome.SNOOZED_1H,
                    NotificationBriefOutcome.SNOOZED_24H, NotificationBriefOutcome.DISMISSED))
                Icons.Rounded.CheckCircleOutline else Icons.Rounded.NotificationsNone,
            actionColor,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CouiText(text = appName, kind = CouiTextKind.ItemTitle, color = title,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            CouiText(text = "$category · $state", kind = CouiTextKind.ItemSummary, color = summary,
                maxLines = 2)
        }
        CouiText(text = time, kind = CouiTextKind.GroupFooter, color = summary)
    }
}
