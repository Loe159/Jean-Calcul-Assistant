package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoEvent
import io.github.mangi.eta.ui.screens.home.brief.BriefRibbonTicks
import io.github.mangi.eta.ui.screens.home.brief.briefAgendaCountdown
import io.github.mangi.eta.ui.screens.home.brief.briefAgendaGaps
import io.github.mangi.eta.ui.screens.home.brief.briefAgendaRibbon
import io.github.mangi.eta.ui.screens.home.brief.briefDuration
import io.github.mangi.eta.ui.screens.home.brief.briefHour
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Pin
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.roundToInt

@Composable
internal fun BriefAgendaCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    onDemoAction: () -> Unit,
) {
    val demo = BriefDemoData
    val countdown = remember { checkNotNull(briefAgendaCountdown(demo.agenda, demo.agendaDemoNow)) }
    val next = countdown.event
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefCard(BriefCardId.AGENDA, expanded, onToggleDensity, motionAllowed, modifier, demo.agendaDate, preview = true) {
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BriefRings(
                listOf(BriefRing(countdown.progress, if (countdown.inProgress) colors.green else colors.blue)),
                84.dp, 7.dp, motionAllowed,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdown.minutes.toString(), color = colors.labelPrimary, style = styles.headlineS)
                    Text("min", color = colors.labelSecondary, style = styles.bodyXS)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (countdown.inProgress) "En cours" else "Aujourd’hui · dans ${briefDuration(countdown.minutes)}",
                    color = briefTintedLabel(colors.blue),
                    style = styles.bodyXS,
                    modifier = Modifier.squircleSurface(colors.blue.copy(alpha = .17f), cornerRadius = 12.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                Text(next.title, color = colors.labelPrimary, style = styles.headlineS)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(COUIIcons.Pin, null, Modifier.size(15.dp), tint = colors.labelSecondary)
                    Text(next.place, color = colors.labelSecondary, style = styles.bodyXS)
                }
                Text("${briefHour(next.start)} – ${briefHour(next.end)}", color = colors.labelSecondary, style = styles.bodyS)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BriefDateTile(demo.agendaMonth, demo.agendaDay, colors.blue)
            Column(Modifier.weight(1f)) {
                Text("Départ conseillé", color = colors.labelSecondary, style = styles.bodyXS)
                Text(demo.departure, color = colors.labelPrimary, style = styles.headlineS)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.semantics { contentDescription = "Participants : " + demo.attendees.joinToString { it.first } },
            ) {
                demo.attendees.forEach { (name, tone) ->
                    val tint = tone.color()
                    Box(Modifier.size(25.dp).background(tint.copy(alpha = .21f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(name.first().toString(), color = briefTintedLabel(tint), style = styles.bodyXS)
                    }
                }
            }
        }
        AgendaRibbon(next)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BriefDemoButton("Itinéraire", onDemoAction, Modifier.weight(1f), primary = true, icon = Icons.Rounded.NearMe)
            BriefDemoButton("Détails", onDemoAction, Modifier.weight(1f))
        }
        if (expanded) AgendaTimeline(next)
    }
}

@Composable
internal fun BriefDateTile(month: String, day: String, tint: Color) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Column(
        Modifier.width(45.dp).squircleSurface(tint.copy(alpha = .13f), cornerRadius = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            month,
            Modifier.fillMaxWidth().background(tint.copy(alpha = .28f)).padding(vertical = 2.dp),
            color = colors.labelPrimary,
            style = styles.bodyXS,
            textAlign = TextAlign.Center,
        )
        Text(day, Modifier.padding(vertical = 3.dp), color = colors.labelPrimary, style = styles.headlineXS)
    }
}

@Composable
private fun AgendaRibbon(next: BriefDemoEvent) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val ribbon = remember { briefAgendaRibbon(demo.agenda, demo.agendaDemoNow) }
    val segmentColors = ribbon.segments.map { it.event.tone.color() }
    val ribbonDescription = demo.agenda.joinToString(prefix = "Programme : ") {
        "${briefHour(it.start)} ${it.title}"
    }
    Canvas(
        Modifier.fillMaxWidth().padding(top = 14.dp).height(30.dp)
            .semantics { contentDescription = ribbonDescription },
    ) {
        drawRoundRect(colors.labelPrimary.copy(alpha = .055f), cornerRadius = CornerRadius(10.dp.toPx()))
        ribbon.segments.forEachIndexed { index, segment ->
            val width = (segment.end - segment.start) * size.width
            val left = size.width * segment.start
            val tint = segmentColors[index]
            val selected = segment.event == next
            if (selected) {
                drawRect(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = .5f), Color.Transparent),
                        center = Offset(left + width / 2, center.y), radius = width,
                    ),
                )
            }
            drawRoundRect(
                tint.copy(alpha = if (selected) 1f else .7f),
                Offset(left, 4.dp.toPx()), Size(width, size.height - 8.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        ribbon.now?.let { current ->
            val x = (size.width * current).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
            drawLine(colors.red.copy(alpha = .22f), Offset(x, 2.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
            drawLine(colors.red, Offset(x, 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        content = {
            BriefRibbonTicks.forEach { Text(briefHour(it), color = colors.labelSecondary, style = styles.bodyXS) }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val labels = measurables.map { it.measure(Constraints(maxWidth = width / 5)) }
        val start = BriefRibbonTicks.first()
        val duration = BriefRibbonTicks.last() - start
        layout(width, labels.maxOf { it.height }) {
            labels.forEachIndexed { index, label ->
                val center = width * (BriefRibbonTicks[index] - start).toFloat() / duration
                label.placeRelative((center - label.width / 2f).roundToInt().coerceIn(0, width - label.width), 0)
            }
        }
    }
    Row(
        Modifier.padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(colors.red, CircleShape))
        Text("Maintenant · ${briefHour(demo.agendaDemoNow)}", color = colors.labelSecondary, style = styles.bodyXS)
    }
}

@Composable
private fun AgendaTimeline(next: BriefDemoEvent) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val gaps = remember { briefAgendaGaps(demo.agenda).associateBy { it.end } }
    val line = colors.labelPrimary.copy(alpha = .17f)
    BriefSection(demo.agendaDate, "${demo.agenda.size} événements")
    demo.agenda.forEachIndexed { index, event ->
        val tint = event.tone.color()
        gaps[event.start]?.let { gap ->
            Box(Modifier.fillMaxWidth().drawBehind {
                val x = 56.dp.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }.padding(start = 70.dp, bottom = 9.dp)) {
                Row(
                    Modifier.fillMaxWidth().squircleSurface(colors.green.copy(alpha = .14f), cornerRadius = 12.dp)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp), tint = briefTintedLabel(colors.green))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Créneau libre · ${briefDuration(gap.minutes)}",
                            color = briefTintedLabel(colors.green), style = styles.headlineXS,
                        )
                        Text(
                            "${briefHour(gap.start)} – ${briefHour(gap.end)}",
                            color = colors.labelSecondary, style = styles.bodyXS,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().drawBehind {
                val x = 56.dp.toPx()
                val dot = Offset(x, 9.dp.toPx())
                if (index != 0) drawLine(line, Offset(x, 0f), dot, 1.dp.toPx())
                if (index != demo.agenda.lastIndex) drawLine(line, dot, Offset(x, size.height), 1.dp.toPx())
                drawCircle(tint.copy(alpha = .23f), 8.dp.toPx(), dot)
                drawCircle(tint, 5.dp.toPx(), dot)
            }.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(briefHour(event.start), Modifier.width(42.dp), color = colors.labelSecondary, style = styles.bodyXS, textAlign = TextAlign.End)
            Column(Modifier.weight(1f)) {
                Text(event.title, color = colors.labelPrimary, style = if (event == next) styles.headlineXS else styles.bodyS)
                Text("${event.place} · ${briefDuration(event.end - event.start)}", color = colors.labelSecondary, style = styles.bodyXS)
            }
        }
    }
}
