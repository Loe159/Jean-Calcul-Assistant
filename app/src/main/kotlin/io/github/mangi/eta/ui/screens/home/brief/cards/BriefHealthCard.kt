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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.briefDuration
import io.github.mangi.eta.ui.screens.home.brief.briefHour
import io.github.mangi.eta.ui.screens.home.brief.briefNumber
import io.github.mangi.eta.ui.screens.home.brief.briefPercent
import io.github.mangi.eta.ui.screens.home.brief.briefProgress
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefHealthCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    onDemoAction: () -> Unit,
) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val steps = briefProgress(demo.steps, demo.stepGoal)
    BriefCard(BriefCardId.HEALTH, expanded, onToggleDensity, motionAllowed, modifier, "${briefPercent(steps)} % de l’objectif de pas", preview = true) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BriefRings(
                listOf(
                    BriefRing(briefProgress(demo.calories, demo.calorieGoal), colors.orange),
                    BriefRing(briefProgress(demo.activeMinutes, demo.minuteGoal), colors.green),
                    BriefRing(steps, colors.azure),
                ),
                124.dp, 9.dp, motionAllowed,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(briefNumber(demo.steps), color = colors.labelPrimary, style = styles.headlineXS)
                    Text("pas", color = colors.labelSecondary, style = styles.bodyXS)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthStat("Pas", "${briefNumber(demo.steps)} / ${briefNumber(demo.stepGoal)}", colors.azure)
                HealthStat("Calories", "${demo.calories} / ${demo.calorieGoal} kcal", colors.orange)
                HealthStat("Minutes actives", "${demo.activeMinutes} / ${demo.minuteGoal} min", colors.green)
            }
        }
        StepsWeek(motionAllowed)
        if (expanded) {
            SleepPhases()
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BriefMetric("Score sommeil", "${demo.sleepScore}/100", Modifier.weight(1f))
                BriefMetric("Récupération", "${demo.recoveryPercent} %", Modifier.weight(1f))
                BriefMetric("VFC", "${demo.hrv} ms", Modifier.weight(1f))
            }
            val session = demo.nextWorkout
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .squircleSurface(colors.labelPrimary.copy(alpha = .045f), cornerRadius = 18.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BriefIconTile(Icons.AutoMirrored.Rounded.DirectionsRun, colors.green, 38.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Prochaine séance · ${briefHour(session.start)}", color = colors.labelSecondary, style = styles.bodyXS)
                    Text(session.title, color = colors.labelPrimary, style = styles.headlineXS)
                }
                BriefDemoButton("Détails", onDemoAction)
            }
        }
    }
}

@Composable
private fun HealthStat(label: String, value: String, tint: Color) {
    val styles = COUITheme.textStyles
    val colors = COUITheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(7.dp).height(27.dp).background(tint, RoundedCornerShape(5.dp)))
        Column {
            Text(value, color = colors.labelPrimary, style = styles.headlineXS)
            Text(label, color = colors.labelSecondary, style = styles.bodyXS)
        }
    }
}

@Composable
private fun StepsWeek(motionAllowed: Boolean) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefSection("Cette semaine", "pas par jour")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        demo.weekDays.forEachIndexed { index, day ->
            val steps = demo.weeklySteps[index]
            val today = demo.today == index
            val progress = briefAnimatedProgress(briefProgress(steps, maxOf(demo.stepGoal, demo.weeklySteps.max())), motionAllowed)
            val tint = when {
                today -> colors.azure
                steps >= demo.stepGoal -> colors.azure.copy(alpha = .54f)
                else -> colors.labelPrimary.copy(alpha = .12f)
            }
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = if (index > demo.today) day else "$day, ${briefNumber(steps)} pas"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Canvas(Modifier.fillMaxWidth().height(62.dp)) {
                    val barHeight = (size.height * progress.value).coerceAtLeast(6.dp.toPx())
                    val top = Offset(0f, size.height - barHeight)
                    val barSize = Size(size.width, barHeight)
                    val radii = CornerRadius(7.dp.toPx().coerceAtMost(barHeight / 2f))
                    if (today) {
                        val brush = Brush.verticalGradient(listOf(tint, lerp(tint, colors.red, .32f)), startY = top.y, endY = size.height)
                        drawRoundRect(brush, top, barSize, radii)
                    } else drawRoundRect(tint, top, barSize, radii)
                }
                Text(
                    day.first().toString(), color = if (today) colors.labelPrimary else colors.labelSecondary,
                    style = styles.bodyXS, fontWeight = if (today) FontWeight.SemiBold else null,
                )
            }
        }
    }
}

@Composable
private fun SleepPhases() {
    val sleep = BriefDemoData.sleep
    val total = sleep.sumOf { it.minutes }
    val stages = sleep.map { it.tone.color() }
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefSection("Sommeil de cette nuit", briefDuration(total))
    Canvas(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp))) {
        val gap = 2.dp.toPx()
        val usableWidth = (size.width - gap * sleep.lastIndex).coerceAtLeast(0f)
        var offset = 0f
        sleep.forEachIndexed { index, phase ->
            val width = usableWidth * briefProgress(phase.minutes, total)
            drawRect(stages[index].copy(alpha = .90f), Offset(offset, 0f), Size(width, size.height))
            offset += width + gap
        }
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sleep.forEachIndexed { index, phase ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(stages[index], RoundedCornerShape(3.dp)))
                Text(
                    phase.label,
                    Modifier.clearAndSetSemantics { contentDescription = "${phase.label}, ${briefDuration(phase.minutes)}" },
                    color = colors.labelSecondary, style = styles.bodyXS,
                )
            }
        }
    }
}
