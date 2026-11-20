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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoAgenda
import io.github.mangi.eta.ui.screens.home.brief.BriefRibbonTicks
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
import io.github.mangi.eta.ui.screens.home.brief.briefRibbonLabelPositions
import java.time.format.DateTimeFormatter
import java.util.Locale

private val agendaDateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@Composable
internal fun BriefAgendaCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    onDemoAction: () -> Unit,
    agenda: BriefDemoAgenda,
) {
    val demo = BriefDemoData
    val countdown = agenda.countdown
    val date = agenda.date.format(agendaDateFormat).replaceFirstChar { it.uppercase(Locale.FRENCH) }
    val day = if (agenda.tomorrow) "Demain" else "Aujourd’hui"
    val next = countdown.event
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefCard(BriefCardId.AGENDA, expanded, onToggleDensity, motionAllowed, modifier, date) {
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
                    val hours = countdown.minutes / 60
                    Text(if (hours == 0) countdown.minutes.toString() else "$hours h",
                        color = colors.labelPrimary, style = styles.headlineS)
                    Text(if (hours == 0) "min" else "${countdown.minutes % 60} min",
                        color = colors.labelSecondary, style = styles.bodyXS)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (countdown.inProgress) "En cours" else "$day · dans ${briefDuration(countdown.minutes)}",
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
            Column(Modifier.weight(1f)) {
                val departure = agenda.departure
                if (departure != null) {
                    Text("Départ conseillé", color = colors.labelSecondary, style = styles.bodyXS)
                    Text(
                        if (departure.toLocalDate() == agenda.date &&
                            departure.hour * 60 + departure.minute == agenda.nowMinute) "Maintenant"
                        else briefHour(departure),
                        color = colors.labelPrimary, style = styles.headlineS,
                    )
                }
            }
            if (next == demo.project) Row(
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
        AgendaRibbon(agenda)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (agenda.departure != null) {
                BriefDemoButton("Itinéraire", onDemoAction, Modifier.weight(1f), primary = true, icon = Icons.Rounded.NearMe)
            }
            BriefDemoButton("Détails", onDemoAction, Modifier.weight(1f))
        }
        if (expanded) AgendaTimeline(agenda, date)
    }
}

@Composable
internal fun BriefDateTile(month: String, day: String, tint: Color) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Column(
        Modifier.widthIn(min = 45.dp).width(IntrinsicSize.Max).squircleSurface(tint.copy(alpha = .13f), cornerRadius = 12.dp),
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
private fun AgendaRibbon(agenda: BriefDemoAgenda) {
    val next = agenda.countdown.event
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val ribbon = remember(agenda) { briefAgendaRibbon(agenda.events, agenda.nowMinute) }
    val segmentColors = ribbon.segments.map { it.event.tone.color() }
    val ribbonDescription = agenda.events.joinToString(prefix = "Programme : ") {
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
            val inset = minOf(1.dp.toPx(), size.width / 2)
            val x = (size.width * current).coerceIn(inset, size.width - inset)
            drawLine(colors.red.copy(alpha = .22f), Offset(x, 2.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
            drawLine(colors.red, Offset(x, 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
    Layout(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        content = {
            BriefRibbonTicks.forEach { Text(briefHour(it), color = colors.labelSecondary, style = styles.bodyXS, maxLines = 1, softWrap = false) }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val labels = measurables.map { it.measure(Constraints()) }
        val positions = briefRibbonLabelPositions(width, BriefRibbonTicks, labels.map { it.width }, 8.dp.roundToPx())
        layout(width, labels.indices.filter { positions[it] != null }.maxOfOrNull { labels[it].height } ?: 0) {
            positions.forEachIndexed { index, left -> if (left != null) labels[index].place(left, 0) }
        }
    }
    val minute = agenda.nowMinute ?: return
    Row(
        Modifier.padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(colors.red, CircleShape))
        Text("Maintenant · ${briefHour(minute)}", color = colors.labelSecondary, style = styles.bodyXS)
    }
}

@Composable
private fun AgendaTimeline(agenda: BriefDemoAgenda, date: String) {
    val next = agenda.countdown.event
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val gaps = remember(agenda) { agenda.gaps.associateBy { it.end } }
    val line = colors.labelPrimary.copy(alpha = .17f)
    BriefSection(date, "${agenda.events.size} événements")
    agenda.events.forEachIndexed { index, event ->
        val tint = event.tone.color()
        gaps[event.start]?.let { gap ->
            Box(Modifier.fillMaxWidth().drawBehind {
                val x = 56.dp.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }.padding(start = 70.dp, bottom = 9.dp)) {
                Row(
                    Modifier.fillMaxWidth().briefInset(12.dp)
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
                if (index != agenda.events.lastIndex) drawLine(line, dot, Offset(x, size.height), 1.dp.toPx())
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
