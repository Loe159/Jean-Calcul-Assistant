package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.home.BriefMotionTiming
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Alarm
import io.github.suqi8.coui.kmp.icon.extended.BankCards
import io.github.suqi8.coui.kmp.icon.extended.CloudFill
import io.github.suqi8.coui.kmp.icon.extended.ExpandMore
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Months
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Tasks
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUIRoundCorner
import io.github.suqi8.coui.kmp.theme.COUITheme

private val BriefCardRadius = 24.dp

internal val BriefCardId.icon: ImageVector
    get() = when (this) {
        BriefCardId.SUGGESTIONS -> Icons.Rounded.AutoAwesome
        BriefCardId.NOTIFICATIONS -> Icons.Rounded.NotificationsNone
        BriefCardId.AGENDA -> COUIIcons.Months
        BriefCardId.CAR -> Icons.Rounded.DirectionsCar
        BriefCardId.TASKS -> COUIIcons.Tasks
        BriefCardId.HEALTH -> Icons.Rounded.FavoriteBorder
        BriefCardId.HABITS -> COUIIcons.Alarm
        BriefCardId.FINANCE -> COUIIcons.BankCards
        BriefCardId.NEWS -> COUIIcons.Notes
        BriefCardId.WEATHER -> COUIIcons.CloudFill
        BriefCardId.HOME -> COUIIcons.Home
    }

@Composable
internal fun BriefCardId.tint(): Color = with(COUITheme.colorScheme) {
    when (this@tint) {
        BriefCardId.SUGGESTIONS, BriefCardId.TASKS -> violet
        BriefCardId.NOTIFICATIONS -> red
        BriefCardId.AGENDA -> blue
        BriefCardId.CAR, BriefCardId.WEATHER -> azure
        BriefCardId.HEALTH -> orange
        BriefCardId.HABITS, BriefCardId.NEWS -> purple
        BriefCardId.FINANCE -> green
        BriefCardId.HOME -> yellow
    }
}

@Composable
internal fun briefTintedLabel(tint: Color): Color =
    lerp(COUITheme.colorScheme.labelPrimary.copy(alpha = 1f), tint, if (COUITheme.isDark) .60f else .38f)

@Composable
internal fun BriefCard(
    card: BriefCardId,
    expanded: Boolean,
    onToggleDensity: () -> Unit,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    meta: String? = null,
    preview: Boolean = false,
    continuesInLazyList: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tint = card.tint()
    val tintLabel = briefTintedLabel(tint)
    val dark = COUITheme.isDark
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionAllowed) tween(400, easing = BriefMotionTiming.EaseMove) else snap(),
        label = "brief-density-chevron",
    )
    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth()
                .drawWithCache {
                    val halo = Brush.radialGradient(
                        0f to tint.copy(alpha = if (dark) .22f else .14f),
                        .74f to Color.Transparent,
                        center = Offset.Zero,
                        radius = size.width.coerceAtLeast(1f),
                    )
                    onDrawBehind { drawRect(halo) }
                }
                .then(
                    if (motionAllowed) Modifier.animateContentSize(
                        tween(BriefMotionTiming.DENSITY_MS, easing = BriefMotionTiming.EaseMove),
                    ) else Modifier,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(32.dp).squircleSurface(tint.copy(alpha = .22f), cornerRadius = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(card.icon, contentDescription = null, tint = tintLabel, modifier = Modifier.size(19.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(card.title, style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
                    if (meta != null) {
                        Text(meta, style = COUITheme.textStyles.bodyXS, color = COUITheme.colorScheme.labelSecondary)
                    }
                    if (preview) {
                        Text(
                            "Aperçu",
                            style = COUITheme.textStyles.bodyXS,
                            color = tintLabel,
                            modifier = Modifier.padding(top = 2.dp)
                                .squircleSurface(tint.copy(alpha = .12f), cornerRadius = 6.dp)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                CouiTopAppBarIconButton(
                    icon = COUIIcons.ExpandMore,
                    contentDescription = if (expanded) "Réduire la carte ${card.title}" else "Agrandir la carte ${card.title}",
                    onClick = onToggleDensity,
                    tint = COUITheme.colorScheme.labelSecondary,
                    modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = rotation },
                    iconSize = 20.dp,
                )
            }
            content()
        }
    }
    if (continuesInLazyList) {
        Box(modifier.fillMaxWidth().briefFeedCardSegment(top = true)) { body() }
    } else {
        Card(modifier.fillMaxWidth(), cornerRadius = BriefCardRadius, insideMargin = PaddingValues(0.dp)) {
            body()
        }
    }
}

/** Matches the COUI card surface while allowing a notification row to remain a lazy item. */
@Composable
internal fun Modifier.briefFeedCardSegment(top: Boolean = false, bottom: Boolean = false): Modifier {
    val color = COUITheme.colorScheme.cardBackground
    if (!top && !bottom) return background(color)
    val topRadius = if (top) BriefCardRadius else 0.dp
    val bottomRadius = if (bottom) BriefCardRadius else 0.dp
    return squircleSurface(
        color = color,
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
        weight = COUIRoundCorner.Normal.Weight,
    )
}
