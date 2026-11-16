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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircleOutline
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.text.rememberCouiTextStyle
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.agent.device.notifications.NotificationBriefEvent
import io.github.mangi.eta.agent.device.notifications.NotificationBriefOutcome
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val summary by NotificationObservation.summary.collectAsState()
    val dateText = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH),
    ).replaceFirstChar { it.uppercaseChar() }
    val cardSurface = if (dark) Color(0xFF24262C) else Color.White
    val listState = rememberLazyListState()
    val motionAllowed = remember(context, visible) { briefMotionAllowed(context) }
    var openCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) { if (visible) openCount++ }
    val ambienceIndex = briefAmbienceIndex(openCount)
    val visited = remember(openCount) { mutableStateMapOf<String, Boolean>() }
    val scrollDistance by remember(listState) {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 240f + listState.firstVisibleItemScrollOffset)
                .coerceIn(0f, 800f)
        }
    }
    val titleProgress by animateFloatAsState(
        targetValue = if (visible || !motionAllowed) 1f else 0f,
        animationSpec = tween(530, easing = FastOutSlowInEasing),
        label = "brief-title-reveal",
    )
    val heroPress = remember { MutableInteractionSource() }
    val pressed by heroPress.collectIsPressedAsState()
    val heroScale by animateFloatAsState(
        targetValue = if (pressed && motionAllowed) 0.978f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 450f), label = "brief-card-press",
    )
    var heroExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    CouiLazyScaffoldPage(
        title = "Mon briefing",
        onBack = onClose,
        listState = listState,
        titleMotionProgress = titleProgress,
        backgroundEffect = {
            BriefAmbientBackground(
                visible = visible,
                variant = ambienceIndex,
                accent = colors.accent,
                dark = dark,
                scrollOffsetPx = scrollDistance,
            )
        },
        modifier = modifier,
    ) {
        item(key = "date") {
            BriefReveal("date", visible, openCount, 0, visited) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CouiText(
                        text = dateText,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        modifier = Modifier.weight(1f),
                    )
                    CouiText(
                        text = "Ambiance ${ambienceIndex + 1}/3 · ${briefAmbienceName(ambienceIndex)}",
                        kind = CouiTextKind.GroupFooter,
                        color = colors.summary,
                    )
                }
            }
        }

        item(key = "notifications:hero") {
            BriefReveal("hero", visible, openCount, 1, visited) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    .graphicsLayer { scaleX = heroScale; scaleY = heroScale }
                    .clickable(
                        interactionSource = heroPress, indication = null,
                        role = Role.Button, onClickLabel = "Détails des notifications filtrées",
                        onClick = { heroExpanded = !heroExpanded },
                    )
                    .clip(CouiShape(28.dp, 2f))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.accent.copy(alpha = if (dark) 0.23f else 0.16f),
                                cardSurface,
                                cardSurface,
                            ),
                        ),
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    IconBubble(Icons.Rounded.AutoAwesome, colors.accent)
                    CouiText(text = "Votre tranquillité", kind = CouiTextKind.ItemTitle, color = colors.title)
                }
                BriefAnimatedNumber(summary.filtered, visible, 48, colors.title)
                CouiText(
                    text = if (summary.filtered == 1) "notification filtrée pendant cette session"
                    else "notifications filtrées pendant cette session",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefMetric("Analysées", summary.analyzed, visible, cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                    BriefMetric("Conservées", summary.kept, visible, cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                    BriefMetric("Reportées", summary.snoozed, visible, cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                }
                AnimatedVisibility(visible = heroExpanded) {
                    CouiText(
                        text = "${summary.dismissed} suppressions confirmées · ${summary.proposed} filtrages proposés",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
            }
            }
        }

        item(key = "notifications:categories") {
            BriefReveal("categories", visible, openCount, 2, visited) {
            BriefCard(cardSurface, onClick = { categoryExpanded = !categoryExpanded }) {
                SectionHeader("Ce qui a été filtré", "", colors.title, colors.summary)
                Spacer(Modifier.height(17.dp))
                val classified = summary.filtered
                CategoryBar("Publicités", summary.filteredAds, classified, visible, colors.accent, colors.title, colors.summary)
                Spacer(Modifier.height(15.dp))
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
                        text = "${summary.dismissed} suppression(s) définitive(s) · irréversibles",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
            }
            }
        }

        item(key = "notifications:recent-title") {
            BriefReveal("recent", visible, openCount, 3, visited) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BriefHeading("Activité récente", color = colors.title, size = 21)
            }
            }
        }
        if (summary.events.isEmpty()) {
            item(key = "notifications:empty") {
                BriefReveal("empty", visible, openCount, 4, visited) {
                BriefCard(cardSurface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.NotificationsNone, colors.summary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            CouiText(text = "Rien à signaler", kind = CouiTextKind.ItemTitle, color = colors.title)
                        }
                    }
                }
                }
            }
        } else {
            items(summary.events.take(5), key = { it.timestampMs.toString() + ":" + it.packageName + ":" + it.outcome.name }) { event ->
                BriefNotificationArrival(
                    event = event, visible = visible, session = openCount,
                    cardSurface = cardSurface, title = colors.title,
                    summary = colors.summary, accent = colors.accent,
                )
            }
            if (summary.events.size > 5) {
                item(key = "notifications:more") {
                    val remaining = summary.events.size - 5
                    CouiText(text = "Et $remaining autres événements en mémoire", kind = CouiTextKind.ItemSummary,
                        color = colors.summary, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }

        // Additional cards are a real composable extension point, not fictional content.
        extensions.filter { it.id !in setOf("notifications", "header") }
            .sortedWith(compareBy(NowBriefCardExtension::priority, NowBriefCardExtension::id))
            .forEach { extension ->
                item(key = "extension:${extension.id}") {
                    BriefReveal(extension.id, visible, openCount, 5, visited) {
                        extension.content()
                    }
                }
            }

        item(key = "future") {
            BriefReveal("future", visible, openCount, 6, visited) {
            BriefCard(cardSurface) {
                SectionHeader("À venir", "", colors.title, colors.summary)
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FutureChip(Icons.Rounded.CalendarMonth, "Agenda", colors.summary, Modifier.weight(1f))
                    FutureChip(Icons.Rounded.TaskAlt, "Tâches", colors.summary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    FutureChip(Icons.Rounded.Schedule, "Routines", colors.summary, Modifier.weight(1f))
                    FutureChip(Icons.Rounded.AutoAwesome, "Agents", colors.summary, Modifier.weight(1f))
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
        targetValue = if (visible || !enabled) value else 0,
        animationSpec = tween(850, easing = FastOutSlowInEasing),
        label = "brief-stat-count",
    )
    BriefHeading(display.toString(), color, size)
}

@Composable
private fun BriefNotificationArrival(
    event: NotificationBriefEvent,
    visible: Boolean,
    session: Int,
    cardSurface: Color,
    title: Color,
    summary: Color,
    accent: Color,
) {
    val context = LocalContext.current
    val enabled = remember(context) { briefMotionAllowed(context) }
    var shown by remember(event.timestampMs, session) { mutableStateOf(false) }
    LaunchedEffect(event.timestampMs, session, visible) {
        shown = visible || !enabled
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(260)) +
            slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing),
                initialOffsetY = { -(it / 3) }),
        exit = fadeOut(tween(120)),
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

@Composable
private fun BriefCard(
    color: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.978f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 450f),
        label = "brief-section-press",
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CouiShape(24.dp, 2f))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = null,
                    role = Role.Button, onClickLabel = "Afficher les détails",
                    onClick = onClick,
                ) else Modifier,
            )
            .background(color).padding(19.dp),
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
        modifier = Modifier.size(42.dp).clip(CouiShape(14.dp, 2f)).background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) { IconDecor(icon, color, 23) }
}

@Composable
private fun BriefMetric(label: String, value: Int, visible: Boolean, surface: Color, title: Color, summary: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(CouiShape(17.dp, 2f))
            .background(surface.copy(alpha = 0.82f)).padding(horizontal = 11.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BriefAnimatedNumber(value, visible, 22, title)
        CouiText(text = label, kind = CouiTextKind.GroupFooter, color = summary, maxLines = 1)
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
        targetValue = if (visible || !enabled) {
            if (total > 0) (amount.toFloat() / total).coerceIn(0f, 1f) else 0f
        } else 0f,
        animationSpec = tween(780, easing = FastOutSlowInEasing),
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
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(CouiShape(18.dp, 2f))
            .background(surface).padding(15.dp),
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

@Composable
private fun FutureChip(icon: ImageVector, title: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CouiShape(14.dp, 2f))
            .background(color.copy(alpha = 0.08f)).padding(horizontal = 11.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        IconDecor(icon, color, 17)
        CouiText(text = title, kind = CouiTextKind.ItemSummary, color = color, maxLines = 1)
    }
}
