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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.UnfoldMore
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
import com.itos.coui.menu.couiMenuAnchor
import com.itos.coui.menu.rememberCouiMenuAnchor
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
    var cardStyleName by rememberSaveable {
        mutableStateOf(NowBriefStyleSettings.read(context).name)
    }
    val cardStyle = NowBriefCardStyle.parse(cardStyleName)
    val palette = nowBriefGlassPalette(cardStyle, dark)
    val heroPalette = nowBriefGlassPalette(cardStyle, dark, prominent = true)
    val styleAnchor = rememberCouiMenuAnchor()
    var styleMenuExpanded by remember { mutableStateOf(false) }
    val styleMenuItems = remember(cardStyle) {
        NowBriefCardStyle.entries.map { option ->
            CouiMenuItem(value = option, text = option.label, selected = option == cardStyle)
        }
    }
    val summary by NotificationObservation.summary.collectAsState()
    val dateText = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH),
    ).replaceFirstChar { it.uppercaseChar() }
    // The existing ambient wallpaper is intentionally shared by every variant.
    val cardSurface = palette.metricColor
    val listState = rememberLazyListState()
    val motionAllowed = remember(context, visible) { briefMotionAllowed(context) }
    val visited = BriefMotionSession.revealedCards
    LaunchedEffect(visible) {
        if (!visible) styleMenuExpanded = false
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
    CouiLazyScaffoldPage(
        title = "Mon briefing",
        onBack = onClose,
        listState = listState,
        titleMotionProgress = titleProgress,
        backgroundEffect = {
            BriefAmbientBackground(
                visible = visible,
                dark = dark,
                scrollOffsetPx = scrollDistance,
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
                    CouiText(
                        text = dateText,
                        kind = CouiTextKind.ItemSummary,
                        color = colors.summary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "card-style-switcher") {
            val source = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 4.dp)
                    .padding(bottom = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .couiMenuAnchor(styleAnchor)
                        .clip(CouiPageDefaults.shape)
                        .background(colors.card)
                        .couiButtonFeedback(
                            dark, source, enabled = true, list = true,
                            hoverLocked = styleMenuExpanded,
                        )
                        .clickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Choisir le style des cartes",
                        ) { styleAnchor.open { styleMenuExpanded = true } }
                        .semantics { contentDescription = "Choisir le style des cartes" }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CouiText(
                            text = "Style des cartes",
                            kind = CouiTextKind.ItemTitle,
                            color = colors.title,
                        )
                        CouiText(
                            text = cardStyle.label,
                            kind = CouiTextKind.ItemSummary,
                            color = colors.summary,
                        )
                    }
                    IconDecor(Icons.Rounded.UnfoldMore, colors.summary, 20)
                }
                // Reuse the exact anchored native COUI popup used by the chat + button.
                Menu(
                    expanded = styleMenuExpanded,
                    onDismissRequest = { styleMenuExpanded = false },
                    items = styleMenuItems,
                    onItemClick = { item ->
                        styleMenuExpanded = false
                        cardStyleName = item.value.name
                        NowBriefStyleSettings.save(context, item.value)
                    },
                    anchor = styleAnchor,
                )
            }
            CouiText(
                text = cardStyle.detail,
                kind = CouiTextKind.GroupFooter,
                color = colors.summary,
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, bottom = 12.dp),
            )
        }

        item(key = "notifications:hero") {
            BriefReveal("hero", visible, 1, visited) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 28.dp).padding(bottom = 14.dp)
                    .clip(CouiShape(heroPalette.cornerDp.dp, 2f))
                    .briefGlass(CouiShape(heroPalette.cornerDp.dp, 2f), heroPalette)
                    .couiButtonFeedback(dark, heroPress, enabled = true)
                    .clickable(
                        interactionSource = heroPress, indication = null,
                        role = Role.Button, onClickLabel = "Détails des notifications filtrées",
                        onClick = { heroExpanded = !heroExpanded },
                    )
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            BriefReveal("categories", visible, 2, visited) {
            BriefCard(style = cardStyle, onClick = { categoryExpanded = !categoryExpanded }) {
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
                        text = "${summary.dismissed} suppression(s) définitive(s) · irréversibles",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
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
                BriefHeading("Activité récente", color = colors.title, size = 19)
            }
            }
        }
        if (summary.events.isEmpty()) {
            item(key = "notifications:empty") {
                BriefReveal("empty", visible, 4, visited) {
                BriefCard(style = cardStyle) {
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
                    event = event, visible = visible,
                    cardSurface = cardSurface, style = cardStyle, title = colors.title,
                    summary = colors.summary, accent = colors.accent,
                )
            }
            if (summary.events.size > 5) {
                item(key = "notifications:more") {
                    val remaining = summary.events.size - 5
                    CouiText(text = "Et $remaining autres événements en mémoire", kind = CouiTextKind.ItemSummary,
                        color = colors.summary, modifier = Modifier.padding(horizontal = 30.dp))
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

        item(key = "future") {
            BriefReveal("future", visible, 6, visited) {
            BriefCard(style = cardStyle) {
                SectionHeader("À venir", "", colors.title, colors.summary)
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FutureChip(Icons.Rounded.CalendarMonth, "Agenda", colors.summary, Modifier.weight(1f))
                    FutureChip(Icons.Rounded.TaskAlt, "Tâches", colors.summary, Modifier.weight(1f))
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    style: NowBriefCardStyle,
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
        BriefEventRow(event, cardSurface, style, title, summary, accent)
    }
}

@Composable
private fun BriefHeading(text: String, color: Color, size: Int) {
    val style = rememberCouiTextStyle(CouiTextKind.ItemTitle)
    BasicText(text, style = style.copy(color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
        fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/**
 * COUI/ETA glass surfaces: three interchangeable palettes sharing the same
 * animated background, native COUI squircle and low-cost gradient renderer.
 */
private fun Modifier.briefGlass(
    shape: Shape,
    palette: NowBriefGlassPalette,
): Modifier = this
    .background(Brush.linearGradient(palette.stops), shape)
    .border(palette.rimWidthDp.dp, Brush.linearGradient(palette.rimStops), shape)

@Composable
private fun BriefCard(
    style: NowBriefCardStyle,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val dark = CouiTheme.isDark
    val palette = nowBriefGlassPalette(style, dark)
    val interaction = remember { MutableInteractionSource() }
    val shape = CouiShape(palette.cornerDp.dp, 2f)
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 28.dp).padding(bottom = 15.dp)
            .clip(shape)
            .briefGlass(shape, palette)
            .then(
                if (onClick != null) Modifier
                    .couiButtonFeedback(dark, interaction, enabled = true, list = true)
                    .clickable(
                        interactionSource = interaction, indication = null,
                        role = Role.Button, onClickLabel = "Afficher les détails",
                        onClick = onClick,
                    )
                else Modifier,
            )
            .padding(
                horizontal = if (style == NowBriefCardStyle.DASHBOARD) 15.dp else 17.dp,
                vertical = if (style == NowBriefCardStyle.DASHBOARD) 13.dp else 16.dp,
            ),
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
private fun BriefEventRow(event: NotificationBriefEvent, surface: Color, style: NowBriefCardStyle, title: Color, summary: Color, accent: Color) {
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
            .padding(bottom = 9.dp).clip(CouiShape(nowBriefGlassPalette(style, CouiTheme.isDark).cornerDp.dp, 2f))
            .briefGlass(
                CouiShape(nowBriefGlassPalette(style, CouiTheme.isDark).cornerDp.dp, 2f),
                nowBriefGlassPalette(style, CouiTheme.isDark),
            )
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

@Composable
private fun FutureChip(icon: ImageVector, title: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(CouiShape(14.dp, 2f))
            .background(color.copy(alpha = 0.09f)).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        IconDecor(icon, color, 17)
        CouiText(text = title, kind = CouiTextKind.ItemSummary, color = color, maxLines = 1)
    }
}
