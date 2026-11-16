package io.github.mangi.eta.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import com.itos.coui.effect.couiButtonFeedback
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
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
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.LocalNotificationModel
import io.github.mangi.eta.agent.device.notifications.NotificationBriefEvent
import io.github.mangi.eta.agent.device.notifications.NotificationBriefOutcome
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.ui.components.coui.CouiIcon
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
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    extensions: List<NowBriefCardExtension> = emptyList(),
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val summary by NotificationObservation.summary.collectAsState()
    val config = remember(visible) { NotificationFilterSettings.load(context) }
    val ready = remember(visible) {
        LocalNotificationModel.installed(context) &&
            AgentNotificationHistoryService.isEnabled(context)
    }
    val dateText = LocalDate.now().format(
        DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH),
    ).replaceFirstChar { it.uppercaseChar() }
    val statusLabel = when {
        !ready || config.mode == NotificationFilterMode.OFF -> "En pause"
        config.mode == NotificationFilterMode.OBSERVE -> "Mode observation"
        config.mode == NotificationFilterMode.CAUTIOUS -> "Filtrage prudent"
        else -> "Mode personnalisé"
    }

    val cardSurface = if (dark) Color(0xFF24262C) else Color.White
    LazyColumn(
        modifier = modifier.fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BriefIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Fermer le briefing",
                        onClick = onClose,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier.clip(CircleShape)
                            .background(colors.accent.copy(alpha = if (dark) 0.19f else 0.10f))
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconDecor(Icons.Rounded.Lock, colors.accent, 14)
                        CouiText(text = "Sur cet appareil", kind = CouiTextKind.ItemSummary, color = colors.accent)
                    }
                }
                CouiText(text = "MON BRIEFING", kind = CouiTextKind.ItemSummary, color = colors.accent)
                BriefHeading(dateText, color = colors.title, size = 32)
                CouiText(
                    text = "L'essentiel de votre journée, sans le bruit.",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
            }
        }

        item(key = "notifications:hero") {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                BriefHeading(summary.filtered.toString(), color = colors.title, size = 48)
                CouiText(
                    text = if (summary.filtered == 1) "notification filtrée pendant cette session"
                    else "notifications filtrées pendant cette session",
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefMetric("Analysées", summary.analyzed.toString(), cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                    BriefMetric("Conservées", summary.kept.toString(), cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                    BriefMetric("Reportées", summary.snoozed.toString(), cardSurface, colors.title, colors.summary, Modifier.weight(1f))
                }
            }
        }

        item(key = "notifications:controls") {
            BriefCard(cardSurface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Shield, colors.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        CouiText(text = "Notifications intelligentes", kind = CouiTextKind.ItemTitle, color = colors.title)
                        CouiText(text = statusLabel, kind = CouiTextKind.ItemSummary, color = colors.summary)
                    }
                    BriefIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = "Configurer le filtrage",
                        onClick = onOpenNotificationSettings,
                    )
                }
                Spacer(Modifier.height(12.dp))
                CouiText(
                    text = when {
                        !ready -> "Installez le modèle local et autorisez l'accès aux notifications pour commencer."
                        config.mode == NotificationFilterMode.OFF -> "Le filtre est désactivé. Vos notifications restent inchangées."
                        config.mode == NotificationFilterMode.OBSERVE -> "Les notifications sont classées localement, sans action automatique."
                        config.mode == NotificationFilterMode.CAUTIOUS -> "Seules les publicités très certaines sont reportées pendant une heure."
                        else -> "Seules les applications et les catégories autorisées peuvent être filtrées."
                    },
                    kind = CouiTextKind.ItemSummary,
                    color = colors.summary,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(CouiShape(14.dp, 2f))
                        .background(colors.accent.copy(alpha = if (dark) 0.16f else 0.07f))
                        .clickable(onClick = onOpenNotificationSettings)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CouiText(text = "Gérer mes règles et protections", kind = CouiTextKind.ItemTitle, color = colors.accent,
                        modifier = Modifier.weight(1f))
                    IconDecor(Icons.Rounded.ChevronRight, colors.accent, 18)
                }
            }
        }

        item(key = "notifications:categories") {
            BriefCard(cardSurface) {
                SectionHeader("Ce qui a été filtré", "Actions confirmées par Android", colors.title, colors.summary)
                Spacer(Modifier.height(17.dp))
                val classified = summary.filtered
                CategoryBar("Publicités", summary.filteredAds, classified, colors.accent, colors.title, colors.summary)
                Spacer(Modifier.height(15.dp))
                CategoryBar("Spams", summary.filteredSpam, classified, Color(0xFFCA8358), colors.title, colors.summary)
                if (summary.dismissed > 0) {
                    Spacer(Modifier.height(13.dp))
                    CouiText(
                        text = "${summary.dismissed} suppression(s) définitive(s) · irréversibles",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
                if (classified == 0) {
                    Spacer(Modifier.height(14.dp))
                    CouiText(
                        text = "Aucun masquage confirmé pour l'instant. Les notifications protégées ne sont jamais supprimées automatiquement par cette vue.",
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                    )
                }
            }
        }

        item(key = "notifications:recent-title") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BriefHeading("Activité récente", color = colors.title, size = 21)
                Spacer(Modifier.weight(1f))
                CouiText(text = "50 max.", kind = CouiTextKind.ItemSummary, color = colors.summary)
            }
        }
        if (summary.events.isEmpty()) {
            item(key = "notifications:empty") {
                BriefCard(cardSurface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.NotificationsNone, colors.summary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            CouiText(text = "Rien à signaler", kind = CouiTextKind.ItemTitle, color = colors.title)
                            CouiText(text = "Les analyses apparaîtront ici au fil de la journée.",
                                kind = CouiTextKind.ItemSummary, color = colors.summary)
                        }
                    }
                }
            }
        } else {
            items(summary.events.take(5), key = { it.timestampMs.toString() + ":" + it.packageName + ":" + it.outcome.name }) { event ->
                BriefEventRow(event, cardSurface, colors.title, colors.summary, colors.accent)
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
                item(key = "extension:${extension.id}") { extension.content() }
            }

        item(key = "future") {
            BriefCard(cardSurface) {
                SectionHeader("Un espace qui évolue", "De nouvelles cartes rejoindront votre briefing",
                    colors.title, colors.summary)
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
        item(key = "footer") {
            CouiText(
                text = "Récapitulatif local, sans texte de notification. Historique temporaire en mémoire vive, réinitialisé à l'arrêt du processus. Aucune analyse n'est lancée par l'ouverture du briefing.",
                kind = CouiTextKind.GroupFooter,
                color = colors.summary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Standalone action, not a CouiTopAppBarButton: those require a Coui toolbar effect scope.
 * This dashboard is a LazyColumn inside the home side-pane, not a toolbar.
 */
@Composable
private fun BriefIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(44.dp)
            .clip(CouiShape(22.dp, 2f))
            .background(colors.title.copy(alpha = if (dark) 0.12f else 0.06f))
            .couiButtonFeedback(dark, source, enabled = true)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CouiIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.title,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun BriefHeading(text: String, color: Color, size: Int) {
    val style = rememberCouiTextStyle(CouiTextKind.ItemTitle)
    BasicText(text, style = style.copy(color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
        fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun BriefCard(color: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(CouiShape(24.dp, 2f))
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
private fun BriefMetric(label: String, value: String, surface: Color, title: Color, summary: Color, modifier: Modifier) {
    Column(
        modifier = modifier.clip(CouiShape(17.dp, 2f))
            .background(surface.copy(alpha = 0.82f)).padding(horizontal = 11.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        BriefHeading(value, title, 22)
        CouiText(text = label, kind = CouiTextKind.GroupFooter, color = summary, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, color: Color, secondary: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        CouiText(text = title, kind = CouiTextKind.ItemTitle, color = color)
        CouiText(text = subtitle, kind = CouiTextKind.ItemSummary, color = secondary)
    }
}

@Composable
private fun CategoryBar(
    name: String, amount: Int, total: Int, accent: Color, title: Color, summary: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CouiText(text = name, kind = CouiTextKind.ItemSummary, color = title, modifier = Modifier.weight(1f))
            CouiText(text = amount.toString(), kind = CouiTextKind.ItemSummary, color = summary)
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(7.dp)
                .clip(CircleShape).background(accent.copy(alpha = 0.12f)),
        ) {
            if (amount > 0 && total > 0) {
                Box(
                    modifier = Modifier.fillMaxWidth((amount.toFloat() / total).coerceIn(0f, 1f))
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
        modifier = Modifier.fillMaxWidth().clip(CouiShape(18.dp, 2f))
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
