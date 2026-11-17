package io.github.mangi.eta.ui.screens.home

import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.shape.CouiShape
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.Text as CouiText
import com.itos.coui.theme.CouiTheme
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.components.coui.CouiIcon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

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
    val dark = CouiTheme.isDark
    val colors = CouiPageDefaults.themeColors(dark)
    val context = LocalContext.current
    val appName = remember(group.packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(group.packageName, 0)).toString()
        }.getOrDefault(group.packageName.substringAfterLast('.'))
    }
    var expanded by remember(group.packageName) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    BriefDismissStretch(
        identity = group.packageName,
        onDismiss = { onDismiss(group.entries.map { it.key }) },
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
                    .couiButtonFeedback(dark, source, enabled = true, list = true)
                    .clickable(
                        interactionSource = source, indication = null,
                        role = Role.Button,
                        onClickLabel = if (expanded) "Replier $appName" else "Déplier $appName",
                    ) { expanded = !expanded }
                    .padding(horizontal = 15.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BriefApplicationIcon(group.packageName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CouiText(text = appName, kind = CouiTextKind.ItemTitle, color = colors.title, maxLines = 1)
                    CouiText(
                        text = "${group.entries.size} notification${if (group.entries.size > 1) "s" else ""} · " +
                            group.entries.first().title.ifBlank { "Activité récente" },
                        kind = CouiTextKind.ItemSummary, color = colors.summary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                CouiIcon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null, tint = colors.summary,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 12.dp)) {
                    group.entries.forEach { entry ->
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
                                            color = colors.summary, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    val whenText = remember(entry.postedAt) {
                                        DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale.FRENCH)
                                            .format(Instant.ofEpochMilli(entry.postedAt)
                                                .atZone(ZoneId.systemDefault()))
                                    }
                                    CouiText(text = whenText, kind = CouiTextKind.GroupFooter, color = colors.summary)
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier.size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(role = Role.Button, onClickLabel = "Retirer du briefing") {
                                            onDismiss(listOf(entry.key))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CouiIcon(Icons.Rounded.Close, "Retirer du briefing",
                                        tint = colors.summary, modifier = Modifier.size(18.dp))
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
 * Native-notification-like swipe in both directions with ETA-style elastic
 * horizontal stretching. Dismissal occurs only after the threshold and spring,
 * never on incidental short drags. It removes the dashboard record only.
 */
@Composable
private fun BriefDismissStretch(
    identity: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember(identity) { Animatable(0f) }
    var widthPx by remember(identity) { mutableFloatStateOf(1f) }
    var active by remember(identity) { mutableStateOf(true) }
    if (!active) return
    Box(
        modifier = modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // Same restrained destructive feedback in either swipe direction.
        val shade = Color(0xFFC65A53)
        Box(
            modifier = Modifier.matchParentSize()
                .graphicsLayer { alpha = (abs(offset.value) / (widthPx * 0.5f)).coerceIn(0f, 1f) }
                .clip(CouiShape(22.dp, 2f))
                .background(shade.copy(alpha = 0.24f)),
        ) {
            CouiIcon(Icons.Rounded.DeleteOutline, null, tint = shade,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 17.dp)
                    .size(23.dp).graphicsLayer { alpha = if (offset.value > 0f) 1f else 0f })
            CouiIcon(Icons.Rounded.DeleteOutline, null, tint = shade,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 17.dp)
                    .size(23.dp).graphicsLayer { alpha = if (offset.value < 0f) 1f else 0f })
        }
        Box(
            modifier = Modifier.fillMaxWidth()
            .pointerInput(identity, widthPx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offset.value) >= widthPx * 0.33f) {
                                offset.animateTo(
                                    targetValue = sign(offset.value) * widthPx * 1.2f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                )
                                active = false
                                onDismiss()
                            } else {
                                offset.animateTo(0f, spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(0f, spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        )) }
                    },
                    onHorizontalDrag = { change, drag ->
                        change.consume()
                        scope.launch {
                            offset.snapTo((offset.value + drag).coerceIn(-widthPx * 1.3f, widthPx * 1.3f))
                        }
                    },
                )
            }
            .graphicsLayer {
                translationX = offset.value
                val fraction = (abs(offset.value) / widthPx).coerceIn(0f, 1f)
                scaleX = 1f + fraction * 0.06f
                scaleY = 1f - fraction * 0.025f
                transformOrigin = TransformOrigin(if (offset.value >= 0f) 0f else 1f, 0.5f)
            },
        ) { content() }
    }
}
