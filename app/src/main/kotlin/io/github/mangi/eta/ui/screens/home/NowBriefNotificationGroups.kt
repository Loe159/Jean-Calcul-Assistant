package io.github.mangi.eta.ui.screens.home

import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationTopics
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.ui.components.coui.CouiIcon
import io.github.mangi.eta.ui.components.coui.CouiButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlinx.coroutines.launch

/** Group by package, retaining descending post time within each group. */
internal data class BriefNotificationGroup(
    val packageName: String,
    val category: String,
    val entries: List<NotificationHistoryRepository.RecentEntry>,
)

internal fun groupBriefNotifications(
    entries: List<NotificationHistoryRepository.RecentEntry>,
): List<BriefNotificationGroup> = entries
    .sortedByDescending { it.postedAt }
    .groupBy { it.userTopic ?: it.topic }
    .map { (topic, list) -> BriefNotificationGroup(list.first().packageName, topic, list) }
    .sortedByDescending { it.entries.first().postedAt }

@Composable
internal fun BriefNotificationAppCard(
    group: BriefNotificationGroup,
    onDismiss: (List<String>) -> Unit,
    onFeedback: (NotificationHistoryRepository.RecentEntry, NotificationUserFeedback) -> Unit = { _, _ -> },
    onCategoryChange: (NotificationHistoryRepository.RecentEntry, String) -> Unit = { _, _ -> },
    onOpen: (NotificationHistoryRepository.RecentEntry) -> Unit = {},
    onTouchChange: (Boolean) -> Unit = {},
) {
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val appName = remember(group.packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(group.packageName, 0)).toString()
        }.getOrDefault(group.packageName.substringAfterLast('.'))
    }
    val title = NotificationAdaptiveSettings.title(context, group.category)
    var expanded by remember(group.category) { mutableStateOf(false) }
    var visibleCount by remember(group.category) { mutableIntStateOf(20) }
    val source = remember { MutableInteractionSource() }
    val headerMotion = rememberCouiTouchMotion(expanded)
    val removed = group.entries.count { it.state == NotificationHistoryRepository.DecisionState.DISMISSED }
    val pending = group.entries.count { it.state == NotificationHistoryRepository.DecisionState.PROPOSED }
    val postponed = group.entries.count {
        it.state == NotificationHistoryRepository.DecisionState.SNOOZED_1H ||
            it.state == NotificationHistoryRepository.DecisionState.SNOOZED_24H
    }
    val statusSummary = buildList {
        if (removed > 0) add("$removed ${if (removed == 1) "supprimée" else "supprimées"} sur Android")
        if (postponed > 0) add("$postponed ${if (postponed == 1) "reportée" else "reportées"}")
        if (pending > 0) add("$pending à vérifier")
    }.joinToString(" · ")
    BriefDismissStretch(
        identity = group.category,
        onDismiss = { onDismiss(group.entries.map { it.key }) },
        onTouchChange = onTouchChange,
        swipeEnabled = !expanded, // When expanded, only individual rows can be dismissed.
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 5.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(CouiShape(27.dp, 2f))
                .background(
                    if (dark) Color(0xFF17191C).copy(alpha = 0.86f)
                    else Color(0xFFF9F9FA).copy(alpha = 0.90f),
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    // Collapsed group deformation is on the whole card.
                    // Once expanded, its header gets the native menu motion
                    // and child notifications deform independently.
                    .couiTouchMotion(headerMotion, expanded = expanded)
                    .graphicsLayer {
                        if (expanded) {
                            translationX = headerMotion.translationX.value
                            translationY = headerMotion.translationY.value
                            scaleX = headerMotion.scaleX.value
                            scaleY = headerMotion.scaleY.value
                            transformOrigin = TransformOrigin.Center
                        }
                    }
                    .couiButtonFeedback(dark, source, enabled = true, list = true)
                    .clickable(
                        interactionSource = source, indication = null,
                        role = Role.Button,
                        onClickLabel = if (expanded) "Replier $title" else "Déplier $title",
                    ) { expanded = !expanded }
                    .padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BriefApplicationIcon(group.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CouiText(text = title, kind = CouiTextKind.ItemTitle, color = colors.title, maxLines = 1)
                    CouiText(
                        text = "${group.entries.size} notification${if (group.entries.size > 1) "s" else ""} · " +
                            appName + " · " + group.entries.first().title.ifBlank { "Activité récente" },
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (statusSummary.isNotEmpty()) {
                        CouiText(
                            text = statusSummary, kind = CouiTextKind.GroupFooter,
                            color = colors.accent, maxLines = 2,
                        )
                    }
                }
                CouiIcon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null, tint = colors.summary,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 12.dp)) {
                    group.entries.take(visibleCount).forEach { entry ->
                        val itemSource = remember(entry.key) { MutableInteractionSource() }
                        var showFullText by remember(entry.key) { mutableStateOf(false) }
                        var showCategories by remember(entry.key) { mutableStateOf(false) }
                        BriefDismissStretch(
                            identity = entry.key,
                            onDismiss = { onDismiss(listOf(entry.key)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(
                                        if (dark) Color.White.copy(alpha = 0.075f)
                                        else Color.Black.copy(alpha = 0.045f),
                                    )
                                    .couiButtonFeedback(dark, itemSource, enabled = true, list = true)
                                    .clickable(
                                        interactionSource = itemSource, indication = null,
                                        role = Role.Button, onClickLabel = "Afficher le contenu",
                                    ) { showFullText = !showFullText }
                                    .padding(start = 12.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CouiText(
                                        text = entry.title.ifBlank { appName },
                                        kind = CouiTextKind.ItemTitle, color = colors.title,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                    if (entry.text.isNotBlank()) {
                                        CouiText(
                                            text = entry.text, kind = CouiTextKind.ItemSummary,
                                            color = colors.summary, maxLines = if (showFullText) 20 else 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val decisionText = when (entry.state) {
                                        NotificationHistoryRepository.DecisionState.DISMISSED ->
                                            "Supprimée sur Android · conservée ici"
                                        NotificationHistoryRepository.DecisionState.SNOOZED_1H ->
                                            "Reportée sur Android pendant 1 heure"
                                        NotificationHistoryRepository.DecisionState.SNOOZED_24H ->
                                            "Reportée sur Android pendant 24 heures"
                                        NotificationHistoryRepository.DecisionState.PROPOSED ->
                                            "Filtrage proposé · ${entry.reason ?: "en attente de confirmation Android"}"
                                        NotificationHistoryRepository.DecisionState.KEPT ->
                                            "Conservée · ${entry.reason ?: "après analyse locale"}"
                                        NotificationHistoryRepository.DecisionState.OBSERVED ->
                                            "Analysée · mode observation"
                                        NotificationHistoryRepository.DecisionState.QUEUED ->
                                            if (System.currentTimeMillis() - entry.postedAt > 10 * 60_000L)
                                                "Analyse interrompue · relancer dans les paramètres"
                                            else "Analyse locale en attente · jusqu'à 5 par minute"
                                        NotificationHistoryRepository.DecisionState.PROTECTED ->
                                            "Notification protégée · aucune suppression automatique"
                                        NotificationHistoryRepository.DecisionState.ANALYZED_PROTECTED ->
                                            "Analysée · ${entry.reason ?: "protection ou déjà retirée"}"
                                        NotificationHistoryRepository.DecisionState.NO_CONTENT ->
                                            "Aucun texte exploitable pour l'analyse IA"
                                        NotificationHistoryRepository.DecisionState.MODEL_MISSING ->
                                            "Analyse impossible · modèle local non installé"
                                        NotificationHistoryRepository.DecisionState.BATTERY_PAUSED ->
                                            "Analyse suspendue · économie d'énergie ou batterie faible"
                                        NotificationHistoryRepository.DecisionState.LIMIT_REACHED ->
                                            "Analyse reportée · quota quotidien ou file pleine"
                                        NotificationHistoryRepository.DecisionState.EXPIRED ->
                                            "Analyse interrompue · notification absente ou service relancé"
                                        NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR ->
                                            "Analyse locale échouée · vérifier le modèle"
                                        else -> "Reçue · aucune analyse encore enregistrée"
                                    }
                                    CouiText(
                                        text = decisionText, kind = CouiTextKind.GroupFooter,
                                        color = if (entry.state == NotificationHistoryRepository.DecisionState.DISMISSED)
                                            colors.accent else colors.summary,
                                        maxLines = 2,
                                    )
                                    if (entry.category != null && entry.score != null) {
                                        val categoryLabel = when (entry.category) {
                                            NotificationFilterPolicy.AD -> "Publicité"
                                            NotificationFilterPolicy.SPAM -> "Spam ou arnaque"
                                            else -> entry.category
                                        }
                                        CouiText(
                                            text = "$categoryLabel · correspondance " +
                                                String.format(Locale.FRANCE, "%.1f %%", entry.score * 100),
                                            kind = CouiTextKind.GroupFooter, color = colors.summary,
                                        )
                                    }
                                    val whenText = remember(entry.postedAt) {
                                        DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.FRENCH)
                                            .format(Instant.ofEpochMilli(entry.postedAt)
                                                .atZone(ZoneId.systemDefault()))
                                    }
                                    CouiText(text = whenText, kind = CouiTextKind.GroupFooter, color = colors.summary)
                                    if (showFullText) {
                                        CouiText(
                                            text = "Catégorie : ${NotificationAdaptiveSettings.title(context, entry.userTopic ?: entry.topic)}" +
                                                " · intérêt : ${entry.personalInterest ?: "non appris"}",
                                            kind = CouiTextKind.GroupFooter, color = colors.summary,
                                        )
                                        CouiButton(text = if (showCategories) "Fermer les catégories" else "Changer la catégorie",
                                            onClick = { showCategories = !showCategories })
                                        if (showCategories) {
                                            NotificationTopics.titles.keys.forEach { topic ->
                                                CouiButton(text = NotificationAdaptiveSettings.title(context, topic),
                                                    modifier = Modifier.fillMaxWidth(), onClick = {
                                                        showCategories = false
                                                        onCategoryChange(entry, topic)
                                                    })
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            CouiButton(text = "Intéressante", modifier = Modifier.weight(1f), onClick = {
                                                onFeedback(entry, NotificationUserFeedback.INTERESTING)
                                            })
                                            CouiButton(text = "Indésirable", modifier = Modifier.weight(1f), onClick = {
                                                onFeedback(entry, NotificationUserFeedback.UNWANTED)
                                            })
                                        }
                                        CouiButton(text = "Ouvrir l'application", onClick = { onOpen(entry) })
                                        if (entry.state == NotificationHistoryRepository.DecisionState.DISMISSED ||
                                            entry.state == NotificationHistoryRepository.DecisionState.SNOOZED_1H ||
                                            entry.state == NotificationHistoryRepository.DecisionState.SNOOZED_24H) {
                                            CouiButton(text = "Suppression incorrecte", onClick = {
                                                onFeedback(entry, NotificationUserFeedback.WRONG_REMOVAL)
                                            })
                                        }
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button, onClickLabel = "Archiver la copie") {
                                            onDismiss(listOf(entry.key))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CouiIcon(Icons.Rounded.Close, "Archiver la copie",
                                        tint = colors.summary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    if (visibleCount < group.entries.size) {
                        CouiButton(
                            text = "Afficher les notifications suivantes",
                            onClick = { visibleCount += 20 },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    CouiButton(
                        text = "Archiver toutes les copies de $appName",
                        onClick = { onDismiss(group.entries.map { it.key }) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
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
        modifier = Modifier.size(42.dp).clip(CouiShape(14.dp, 2f))
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
            CouiIcon(Icons.Rounded.NotificationsNone, contentDescription = null,
                tint = CouiPageDefaults.themeColors(CouiTheme.isDark).summary,
                modifier = Modifier.size(22.dp))
        }
    }
}

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
