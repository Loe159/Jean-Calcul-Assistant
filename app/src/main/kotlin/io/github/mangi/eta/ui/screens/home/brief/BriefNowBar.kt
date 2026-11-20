package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.FloatingToolbar
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.Location
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefNowBar(
    moment: BriefMoment,
    onOpenCard: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val suggestion = briefBarSuggestion(moment)
    val colors = COUITheme.colorScheme
    val tint = when (moment) {
        BriefMoment.MORNING, BriefMoment.EVENING -> colors.blue
        BriefMoment.DAY -> colors.orange
        BriefMoment.NIGHT -> colors.violet
    }
    val icon = when (moment) {
        BriefMoment.DAY -> COUIIcons.Location
        BriefMoment.NIGHT -> Icons.Rounded.Bedtime
        else -> suggestion.card.icon
    }
    FloatingToolbar(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        cornerRadius = 28.dp,
        outSidePadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        showDivider = true,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .then(if (onOpenCard != null) Modifier.clickable(role = Role.Button, onClick = onOpenCard) else Modifier)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).squircleSurface(tint.copy(alpha = .22f), cornerRadius = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(38.dp)) {
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(tint.copy(alpha = .24f), -90f, 360f, useCenter = false, style = stroke)
                    drawArc(tint, -90f, 360f * suggestion.progress, useCenter = false, style = stroke)
                }
                Icon(icon, null, tint = briefTintedLabel(tint), modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(suggestion.title, style = COUITheme.textStyles.headlineXS, color = colors.labelPrimary)
                Text(
                    "Aperçu · ${suggestion.detail}",
                    style = COUITheme.textStyles.bodyXS,
                    color = colors.labelSecondary,
                )
            }
            if (onOpenCard != null) {
                Icon(
                    COUIIcons.ChevronForward, null, tint = colors.labelSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
