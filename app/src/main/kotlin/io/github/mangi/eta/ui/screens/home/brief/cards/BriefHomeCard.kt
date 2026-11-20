package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefHomeControl
import io.github.mangi.eta.ui.screens.home.brief.briefDecimal
import io.github.mangi.eta.ui.screens.home.brief.briefProgress
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun BriefHomeCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    enabledControls: Set<BriefHomeControl>,
    onControlChange: (BriefHomeControl, Boolean) -> Unit,
    target: Float,
    onTargetChange: (Float) -> Unit,
    onDemoAction: () -> Unit,
) {
    BriefCard(BriefCardId.HOME, expanded, onToggleDensity, motionAllowed, modifier, "Salon") {
        HomeOverview(target, onTargetChange, motionAllowed)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BriefHomeControl.entries.chunked(2).forEach { controls ->
                BriefMetricRow { item ->
                    controls.forEach { control ->
                        HomeControlTile(control, control in enabledControls, { onControlChange(control, it) }, item(1f))
                    }
                }
            }
        }
        OpenWindowAlert(onDemoAction)
        if (expanded) {
            HomeEnergyChart()
            HomeAppliances(motionAllowed)
        }
    }
}

@Composable
private fun HomeOverview(target: Float, onTargetChange: (Float) -> Unit, motionAllowed: Boolean) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(.8f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThermostatDial(target, motionAllowed, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CouiTopAppBarIconButton(
                    Icons.Rounded.Remove, "Baisser la température cible", { onTargetChange(target - .5f) },
                    enabled = target > demo.thermostatMin, tint = briefTintedLabel(colors.yellow),
                    modifier = Modifier.size(36.dp), iconSize = 20.dp,
                )
                CouiTopAppBarIconButton(
                    COUIIcons.Add, "Augmenter la température cible", { onTargetChange(target + .5f) },
                    enabled = target < demo.thermostatMax, tint = briefTintedLabel(colors.yellow),
                    modifier = Modifier.size(36.dp), iconSize = 20.dp,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Humidité ${demo.humidityPercent} %", color = colors.labelSecondary, style = styles.bodyXS)
            Text("${briefDecimal(demo.homeEnergyToday)} kWh aujourd’hui", color = colors.labelPrimary, style = styles.headlineXS)
            Text(
                "${briefDecimal(demo.energyVsAverage.toDouble(), decimals = 0, signed = true)} % vs moyenne",
                color = briefTintedLabel(colors.green), style = styles.bodyXS,
                modifier = Modifier.squircleSurface(colors.green.copy(alpha = .12f), cornerRadius = 8.dp)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ThermostatDial(target: Float, motionAllowed: Boolean, modifier: Modifier = Modifier) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val temperatureRange = demo.thermostatMax - demo.thermostatMin
    val currentFraction = (demo.indoorTemperature - demo.thermostatMin) / temperatureRange
    val targetFraction = (target - demo.thermostatMin) / temperatureRange
    val sweep = briefAnimatedProgress(currentFraction, motionAllowed)
    Box(modifier.sizeIn(minHeight = 120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 8.dp.toPx()
            val length = (size.minDimension - 2.5f * stroke).coerceAtLeast(0f)
            val origin = center - Offset(length / 2, length / 2)
            val bounds = Size(length, length)
            val start = 135f
            val total = 270f
            val line = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(colors.labelPrimary.copy(alpha = .11f), start, total, false, origin, bounds, style = line)
            if (sweep.value > 0f) {
                drawArc(
                    Brush.horizontalGradient(listOf(colors.orange, colors.yellow), startX = origin.x, endX = origin.x + length),
                    start, total * sweep.value, false, origin, bounds, style = line,
                )
            }
            val angle = Math.toRadians((start + total * targetFraction).toDouble())
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(
                colors.labelPrimary.copy(alpha = .75f),
                center + direction * (length / 2 - stroke * .55f),
                center + direction * (length / 2 + stroke * .55f),
                2.dp.toPx(), StrokeCap.Round,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                "${briefDecimal(demo.indoorTemperature.toDouble())}°",
                color = colors.labelPrimary, style = styles.headlineM, textAlign = TextAlign.Center,
            )
            Text("Cible ${briefDecimal(target.toDouble())}°", color = colors.labelSecondary, style = styles.bodyXS, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HomeControlTile(control: BriefHomeControl, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Column(
        modifier.squircleSurface(
            if (checked) colors.yellow.copy(alpha = .12f) else colors.labelPrimary.copy(alpha = .05f),
            cornerRadius = 15.dp,
        ).padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(control.icon(), null, Modifier.size(21.dp), tint = if (checked) briefTintedLabel(colors.yellow) else colors.labelSecondary)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = control.label },
            )
        }
        Text(control.label, color = colors.labelPrimary, style = styles.headlineXS)
        Text(if (checked) control.onLabel else control.offLabel, color = colors.labelSecondary, style = styles.bodyXS)
    }
}

private fun BriefHomeControl.icon(): ImageVector = when (this) {
    BriefHomeControl.Lights -> Icons.Rounded.Lightbulb
    BriefHomeControl.Heating -> Icons.Rounded.Thermostat
    BriefHomeControl.Shutters -> Icons.Rounded.Blinds
    BriefHomeControl.Lock -> COUIIcons.Lock
}

@Composable
private fun OpenWindowAlert(onDemoAction: () -> Unit) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefInlineAction(
        Modifier.fillMaxWidth().padding(top = 9.dp)
            .briefInset(14.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        leading = { Icon(COUIIcons.Info, null, Modifier.size(17.dp), tint = briefTintedLabel(colors.orange)) },
        text = {
            Column {
                Text("Fenêtre de la chambre ouverte", color = colors.labelPrimary, style = styles.headlineXS)
                Text("Depuis ${BriefDemoData.openWindowMinutes} min", color = colors.labelSecondary, style = styles.bodyXS)
            }
        },
        action = { BriefDemoButton("Rappel", onDemoAction) },
        spacing = 7.dp,
    )
}

@Composable
private fun HomeEnergyChart() {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val hourly = BriefDemoData.homeEnergy
    val max = hourly.max()
    BriefSection("Consommation sur 24 h", "${briefDecimal(BriefDemoData.homePowerKw)} kW")
    Canvas(
        Modifier.fillMaxWidth().height(64.dp).semantics {
            val peak = briefDecimal(max.toDouble())
            contentDescription = "Consommation horaire des dernières 24 heures en kilowattheures, pic à $peak kWh"
        },
    ) {
        val cell = size.width / hourly.size
        val barWidth = cell * .72f
        hourly.forEachIndexed { index, value ->
            val height = size.height * value / max
            val tint = if (index == hourly.lastIndex) colors.yellow else colors.yellow.copy(alpha = .28f)
            drawRoundRect(tint, Offset(cell * index + (cell - barWidth) / 2f, size.height - height), Size(barWidth, height), CornerRadius(3.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Il y a 24 h", "Il y a 12 h", "Maintenant").forEach { label ->
            Text(label, color = colors.labelSecondary, style = styles.bodyXS)
        }
    }
}

@Composable
private fun HomeAppliances(motionAllowed: Boolean) {
    val demo = BriefDemoData
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefSection("Appareils")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BriefIconTile(Icons.Rounded.LocalLaundryService, colors.azure, 32.dp)
        Column(Modifier.weight(1f)) {
            Text("Lave-linge", color = colors.labelPrimary, style = styles.headlineXS)
            Text("Fin du cycle dans ${demo.washerMinutes} min", color = colors.labelSecondary, style = styles.bodyXS)
        }
        BriefGauge(
            1f - briefProgress(demo.washerMinutes, demo.washerCycleMinutes), listOf(colors.azure),
            motionAllowed, Modifier.width(62.dp).height(6.dp), "Progression du cycle",
        )
    }
    BriefRowDivider()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BriefIconTile(Icons.Rounded.SmartToy, colors.green, 32.dp)
        Column(Modifier.weight(1f)) {
            Text("Aspirateur robot", color = colors.labelPrimary, style = styles.headlineXS)
            Text("Chargé · prêt", color = colors.labelSecondary, style = styles.bodyXS)
        }
        Text("${demo.robotPercent} %", color = briefTintedLabel(colors.green), style = styles.headlineXS)
    }
}
