package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoSuggestion
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Merge
import io.github.suqi8.coui.kmp.icon.extended.Messages
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefSuggestionOrb(motionAllowed: Boolean) {
    val colors = COUITheme.colorScheme
    val rotation = briefAmbientCycle(motionAllowed, 9000, "brief-suggestion-orb")
    val palette = listOf(colors.violet, colors.azure, colors.green, colors.yellow, colors.orange, colors.purple, colors.violet)
    val core = colors.cardBackground.compositeOver(colors.backgroundWithCard.copy(alpha = 1f))
    Canvas(Modifier.size(28.dp).drawWithCache {
        val sweep = Brush.sweepGradient(palette, Offset(size.width / 2f, size.height / 2f))
        onDrawBehind {
            rotate(20f + 360f * rotation.value) { drawCircle(sweep) }
            drawCircle(core, size.minDimension * .35f)
            drawCircle(palette.first().copy(alpha = .15f), size.minDimension * .35f)
        }
    }) {}
}

private val BriefDemoSuggestion.icon: ImageVector
    get() = when (id) {
        "reply" -> COUIIcons.Messages
        "sport" -> Icons.Rounded.FitnessCenter
        else -> COUIIcons.Merge
    }

@Composable
internal fun BriefSuggestionsCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    checked: Set<String>,
    deferred: Set<String>,
    onCheck: (String, Boolean) -> Unit,
    onLater: (String) -> Unit,
    onDemoAction: () -> Unit,
    suggestions: List<BriefDemoSuggestion>,
) {
    val pending = suggestions.filter { it.id !in checked && it.id !in deferred }
    val current = pending.firstOrNull()
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefCard(
        card = BriefCardId.SUGGESTIONS,
        expanded = expanded,
        onToggleDensity = onToggleDensity,
        motionAllowed = motionAllowed,
        modifier = modifier,
        meta = when (pending.size) {
            0 -> "À jour"
            1 -> "1 suggestion pour maintenant"
            else -> "${pending.size} suggestions pour maintenant"
        },
    ) {
        if (current == null) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(50.dp).background(colors.green.copy(alpha = .20f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(26.dp), tint = briefTintedLabel(colors.green))
                }
                Text("Rien à signaler", color = colors.labelPrimary, style = styles.headlineS)
            }
        } else {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BriefIconTile(current.icon, current.tone.color(), 38.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.title, color = colors.labelPrimary, style = styles.headlineS)
                    Text(current.detail, color = colors.labelSecondary, style = styles.bodyS)
                }
            }
            FlowRow(
                Modifier.padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BriefDemoButton(current.action, onDemoAction, primary = true)
                BriefDemoButton("Plus tard", { onLater(current.id) })
            }
        }
        val following = if (expanded) {
            suggestions.filter { it.id != current?.id && it.id !in deferred }
        } else pending.drop(1).take(2)
        following.forEach { suggestion ->
            BriefRowDivider()
            val done = suggestion.id in checked
            BriefCheckRow(done, { onCheck(suggestion.id, it) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(suggestion.icon, null, Modifier.size(18.dp), tint = briefTintedLabel(suggestion.tone.color()))
                    Text(
                        suggestion.title,
                        color = if (done) colors.labelTertiary else colors.labelPrimary,
                        style = styles.bodyS,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
        if (expanded && current != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                suggestions.forEach { suggestion ->
                    val selected = suggestion.id == current.id
                    Box(Modifier.width(if (selected) 16.dp else 6.dp).height(6.dp)
                        .background(colors.labelPrimary.copy(alpha = if (selected) .9f else .16f), CircleShape))
                }
            }
        }
    }
}
