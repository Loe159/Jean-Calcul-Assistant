package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.BriefMotionTiming
import io.github.mangi.eta.ui.screens.home.brief.cards.BriefSuggestionOrb
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Alarm
import io.github.suqi8.coui.kmp.icon.extended.BankCards
import io.github.suqi8.coui.kmp.icon.extended.CloudFill
import io.github.suqi8.coui.kmp.icon.extended.Home
import io.github.suqi8.coui.kmp.icon.extended.Months
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Tasks
import io.github.suqi8.coui.kmp.squircle.squircleSurface
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
        BriefCardId.NOTIFICATIONS -> primary
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
    lerp(COUITheme.colorScheme.labelPrimary.copy(alpha = 1f), tint, if (COUITheme.isDark) .60f else .30f)

/**
 * Neutral COUI card. Colour lives only in the small icon tile and in the data inside the card,
 * so the feed reads as one surface instead of one hue per card.
 * The whole header toggles density, giving the chevron a full-width touch target.
 */
@Composable
internal fun BriefCard(
    card: BriefCardId,
    expanded: Boolean,
    onToggleDensity: () -> Unit,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    meta: String? = null,
    backgroundDecoration: Modifier = Modifier,
    title: String = card.title,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tint = card.tint()
    val colors = COUITheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (motionAllowed) tween(400, easing = BriefMotionTiming.EaseMove) else snap(),
        label = "brief-density-chevron",
    )
    Card(modifier.fillMaxWidth(), cornerRadius = BriefCardRadius, insideMargin = PaddingValues(0.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .then(backgroundDecoration)
                .then(
                    if (motionAllowed) Modifier.animateContentSize(
                        tween(BriefMotionTiming.DENSITY_MS, easing = BriefMotionTiming.EaseMove),
                    ) else Modifier,
                )
                .padding(bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Réduire la carte ${card.title}" else "Agrandir la carte ${card.title}",
                        onClick = onToggleDensity,
                    )
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card == BriefCardId.SUGGESTIONS) {
                    BriefSuggestionOrb(motionAllowed)
                } else {
                    Box(
                        Modifier.size(32.dp).squircleSurface(tint.copy(alpha = .16f), cornerRadius = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(card.icon, contentDescription = null, tint = briefTintedLabel(tint), modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = COUITheme.textStyles.headlineXS, color = colors.labelPrimary)
                    if (meta != null) {
                        Text(meta, style = COUITheme.textStyles.bodyXS, color = colors.labelSecondary)
                    }
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.labelSecondary,
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() }
        }
    }
}
