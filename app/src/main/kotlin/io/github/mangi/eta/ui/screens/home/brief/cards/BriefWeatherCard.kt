package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoData
import io.github.mangi.eta.ui.screens.home.brief.BriefWeatherCondition
import io.github.mangi.eta.ui.screens.home.brief.BriefWeatherHour
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.mangi.eta.ui.screens.home.brief.briefWeatherForecast
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.CloudFill
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefWeatherCard(
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    modifier: Modifier,
    hour: Int,
    inViewport: () -> Boolean,
) {
    val forecast = remember(hour) { briefWeatherForecast(hour) }
    val current = forecast.first()
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    BriefCard(
        BriefCardId.WEATHER, expanded, onToggleDensity, motionAllowed, modifier,
        BriefDemoData.weatherLocation,
        backgroundDecoration = Modifier.weatherBackground(current.condition, motionAllowed && inViewport()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${current.temperature}°", style = styles.display, color = colors.labelPrimary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(current.condition.label, style = styles.headlineXS, color = colors.labelPrimary)
                Text(
                    "Ressenti ${current.temperature - BriefDemoData.feelsLikeOffset}° · Vent ${BriefDemoData.windKmh} km/h",
                    style = styles.bodyXS, color = colors.labelSecondary,
                )
                Text("Pluie · ${current.rainPercent} %", style = styles.bodyXS, color = colors.labelSecondary)
            }
            Icon(current.condition.icon(), null, Modifier.size(36.dp), tint = briefTintedLabel(colors.azure))
        }
        HourlyForecast(forecast)
        if (expanded) {
            BriefSection("Température et pluie", "6 prochaines heures")
            BriefSparkline(
                forecast.map { it.temperature.toFloat() },
                colors.labelPrimary,
                "Température de ${current.temperature}° à ${forecast.last().temperature}° sur les six prochaines heures. " +
                    "Les barres représentent le risque de pluie.",
                Modifier.fillMaxWidth().height(90.dp),
                bars = forecast.map { it.rainPercent / 100f },
                barTint = colors.azure.copy(alpha = .48f),
            )
        }
        WeatherAdvice(forecast)
    }
}

@Composable
private fun HourlyForecast(forecast: List<BriefWeatherHour>) {
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        forecast.forEachIndexed { index, hour ->
            val label = if (index == 0) "Maint." else "${hour.hour} h"
            Column(
                Modifier.widthIn(min = 44.dp)
                    .squircleSurface(colors.labelPrimary.copy(alpha = if (index == 0) .10f else .045f), cornerRadius = 11.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .clearAndSetSemantics {
                        val time = if (index == 0) "Maintenant" else label
                        contentDescription = "$time, ${hour.condition.label}, ${hour.temperature} degrés, ${hour.rainPercent} % de pluie"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = styles.bodyXS, color = colors.labelSecondary)
                Icon(hour.condition.icon(), null, Modifier.size(17.dp), tint = briefTintedLabel(colors.azure))
                Text("${hour.temperature}°", style = styles.headlineXS, color = colors.labelPrimary)
                BriefGauge(hour.rainPercent / 100f, listOf(colors.azure), false, Modifier.size(22.dp, 3.dp))
                Text("${hour.rainPercent} %", style = styles.bodyXS, color = colors.labelSecondary)
            }
        }
    }
}

@Composable
private fun WeatherAdvice(forecast: List<BriefWeatherHour>) {
    val firstRain = forecast.firstOrNull { it.condition == BriefWeatherCondition.Rain }
    val advice = when {
        firstRain == forecast.first() -> "Prends un parapluie pour ta prochaine sortie."
        firstRain != null -> "Averses vers ${firstRain.hour} h. Prends un parapluie si tu sors."
        else -> "Pas d’averse prévue dans les six prochaines heures."
    }
    val colors = COUITheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp)
            .squircleSurface(colors.labelPrimary.copy(alpha = .07f), cornerRadius = 14.dp).padding(9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (firstRain == null) COUIIcons.Info else Icons.Rounded.Umbrella,
            null, Modifier.size(18.dp), tint = briefTintedLabel(colors.azure),
        )
        Text(advice, Modifier.weight(1f), style = COUITheme.textStyles.bodyS, color = colors.labelPrimary)
    }
}

private fun BriefWeatherCondition.icon(): ImageVector = when (this) {
    BriefWeatherCondition.Rain -> Icons.Rounded.Grain
    BriefWeatherCondition.Cloudy -> COUIIcons.CloudFill
    BriefWeatherCondition.ClearDay -> Icons.Rounded.WbSunny
    BriefWeatherCondition.ClearNight -> Icons.Rounded.NightlightRound
}

@Composable
private fun Modifier.weatherBackground(condition: BriefWeatherCondition, animated: Boolean): Modifier {
    val colors = COUITheme.colorScheme
    val dark = COUITheme.isDark
    val rain = condition == BriefWeatherCondition.Rain
    val travel = briefAmbientCycle(rain && animated, 4_200, "brief-weather-rain")
    val tone = when (condition) {
        BriefWeatherCondition.Rain -> lerp(colors.azure, colors.blue, .55f)
        BriefWeatherCondition.Cloudy -> lerp(colors.azure, colors.labelSecondary, .42f)
        BriefWeatherCondition.ClearDay -> colors.azure
        BriefWeatherCondition.ClearNight -> colors.violet
    }
    return drawWithCache {
        val gradient = Brush.linearGradient(
            0f to lerp(colors.card, tone, if (dark) .30f else .20f),
            .5f to lerp(colors.card, tone, if (dark) .16f else .11f),
            1f to colors.card,
            end = Offset(size.width * .64f, size.height),
        )
        onDrawBehind {
            drawRect(gradient)
            if (rain) {
                repeat(22) { index ->
                    val x = size.width * ((index * 37 % 101) / 101f)
                    val height = size.height * .72f
                    val phase = (index * .37f + travel.value * (3 + index % 3)) % 1f
                    val y = phase * height
                    val opacity = minOf(phase * 6, (1f - phase) * 6, 1f)
                    drawLine(
                        colors.labelPrimary.copy(alpha = opacity * if (dark) .12f else .08f),
                        Offset(x, y), Offset(x - 3.dp.toPx(), y + (7 + index % 5).dp.toPx()),
                        strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}
