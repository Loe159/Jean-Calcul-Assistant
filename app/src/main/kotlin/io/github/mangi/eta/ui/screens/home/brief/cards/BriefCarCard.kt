package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoTrip
import io.github.mangi.eta.ui.screens.home.brief.briefHour
import io.github.mangi.eta.ui.screens.home.brief.briefNumber
import io.github.mangi.eta.ui.screens.home.brief.briefProgress
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefCarCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    onDemoAction: () -> Unit,
    trip: BriefDemoTrip,
) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefCard(BriefCardId.CAR, expanded, onToggleDensity, motionAllowed, modifier, demo.carModel) {
        Peugeot208Illustration(alert = expanded, motionAllowed = motionAllowed, modifier = Modifier.padding(vertical = 4.dp))
        BriefMetricRow { item ->
            BriefMetric("Autonomie", "${demo.rangeKm}", item(.9f), unit = "km")
            BriefMetric("Carburant", "${demo.fuelPercent}", item(.9f), unit = "%")
            BriefMetric("Compteur", briefNumber(demo.odometerKm), item(1.2f), unit = "km")
        }
        BriefGauge(
            briefProgress(demo.fuelPercent, 100), listOf(colors.red, colors.orange, colors.yellow),
            motionAllowed, Modifier.fillMaxWidth().padding(top = 12.dp).height(10.dp), "Niveau de carburant",
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Vide", "¼", "½", "¾", "Plein").forEach {
                Text(it, color = colors.labelSecondary, style = styles.bodyXS)
            }
        }
        CarTrip(trip)
        CarAlert(Icons.Rounded.LocalGasStation, colors.orange, "Plein conseillé bientôt", demo.station, onDemoAction)
        if (expanded) {
            CarAlert(COUIIcons.Report, colors.red, demo.tyreAlert, demo.tyreDetail)
            BriefSection("Prochain entretien", "Dans ${briefNumber(demo.serviceRemainingKm)} km")
            BriefGauge(
                briefProgress(demo.serviceIntervalKm - demo.serviceRemainingKm, demo.serviceIntervalKm),
                listOf(colors.azure), motionAllowed, Modifier.fillMaxWidth().height(6.dp), "Intervalle de révision parcouru",
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            ) {
                Text("Dernière · ${demo.lastService}", color = colors.labelSecondary, style = styles.bodyXS)
                Text("Prévue vers le ${demo.nextService}", color = colors.labelSecondary, style = styles.bodyXS)
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BriefDemoButton("Réserver la révision", onDemoAction, icon = Icons.Rounded.Build)
                BriefDemoButton("Itinéraire", onDemoAction, icon = Icons.Rounded.NearMe)
            }
        } else {
            Text(
                "Révision dans ${briefNumber(demo.serviceRemainingKm)} km",
                Modifier.padding(top = 9.dp, bottom = 2.dp), color = colors.labelSecondary, style = styles.bodyXS,
            )
        }
    }
}

@Composable
private fun CarTrip(trip: BriefDemoTrip) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val traffic = demo.traffic.map { it.color() }
    BriefInlineAction(
        Modifier.fillMaxWidth().padding(top = 12.dp).briefInset(18.dp).padding(12.dp),
        leading = { BriefIconTile(Icons.Rounded.NearMe, colors.azure, 38.dp) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${demo.tripKm} km · ${demo.tripMinutes} min", color = colors.labelSecondary, style = styles.bodyXS)
            Text(demo.destination, color = colors.labelPrimary, style = styles.headlineXS)
            Canvas(Modifier.fillMaxWidth().padding(top = 2.dp).height(6.dp)) {
                val gap = 2.dp.toPx()
                val width = (size.width - gap * traffic.lastIndex) / traffic.size
                if (width <= 0f) return@Canvas
                traffic.forEachIndexed { index, tint ->
                    drawRoundRect(
                        tint, Offset(index * (width + gap), 0f), Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            }
            Text(demo.tripTraffic, color = briefTintedLabel(colors.orange), style = styles.bodyXS)
        } },
        action = {
            Column(horizontalAlignment = Alignment.End) {
                Text(if (trip.tomorrow) "Demain · partir à" else "Partir à", color = colors.labelSecondary, style = styles.bodyXS)
                Text(briefHour(trip.departure), color = colors.labelPrimary, style = styles.headlineS)
            }
        },
        spacing = 10.dp,
    )
}

@Composable
private fun CarAlert(icon: ImageVector, tint: Color, title: String, detail: String, onAction: (() -> Unit)? = null) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefInlineAction(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .briefInset(16.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        leading = { Icon(icon, null, Modifier.size(19.dp), tint = briefTintedLabel(tint)) },
        text = {
            Column {
                Text(title, color = colors.labelPrimary, style = styles.headlineXS)
                Text(detail, color = colors.labelSecondary, style = styles.bodyXS)
            }
        },
        action = onAction?.let { { BriefDemoButton("Y aller", it) } },
    )
}
