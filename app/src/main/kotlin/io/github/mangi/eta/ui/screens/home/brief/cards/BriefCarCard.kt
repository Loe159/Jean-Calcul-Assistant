package io.github.mangi.eta.ui.screens.home.brief.cards

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.car.BriefCarData
import io.github.mangi.eta.data.car.BriefCarState
import io.github.mangi.eta.data.car.CarMaintenance
import io.github.mangi.eta.data.car.CarPoint
import io.github.mangi.eta.data.car.CarPositionQuality
import io.github.mangi.eta.data.car.FuelStation
import io.github.mangi.eta.data.car.carInstallMessage
import io.github.mangi.eta.data.car.carReauthMessage
import io.github.mangi.eta.data.car.stationClosingMinute
import io.github.mangi.eta.ui.screens.home.brief.BriefCard
import io.github.mangi.eta.ui.screens.home.brief.BriefCardId
import io.github.mangi.eta.ui.screens.home.brief.briefDecimal
import io.github.mangi.eta.ui.screens.home.brief.briefHour
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Report
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
internal fun BriefCarCard(
    state: BriefCarState,
    now: ZonedDateTime,
    expanded: Boolean,
    motionAllowed: Boolean,
    onToggleDensity: () -> Unit,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
    onMapUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is BriefCarState.Ready) {
        BriefCard(
            BriefCardId.CAR, expanded, onToggleDensity, motionAllowed, modifier,
            title = state.data.label,
        ) {
            CarReadings(state, now, expanded, motionAllowed, onMapUnavailable)
        }
        return
    }
    val message = when (state) {
        BriefCarState.MyPeugeotMissing -> carInstallMessage
        BriefCarState.Disconnected -> "Connecte ton compte MyPeugeot pour afficher ta voiture."
        BriefCarState.NeedsReauth -> carReauthMessage
        BriefCarState.Loading -> "Chargement des données de la voiture…"
        is BriefCarState.Error -> state.message
        is BriefCarState.Ready -> return
    }
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voiture", style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
            CarDetail(message)
            when (state) {
                BriefCarState.MyPeugeotMissing, BriefCarState.Disconnected, BriefCarState.NeedsReauth ->
                    CarButton("Connecter MyPeugeot", onConnect)
                is BriefCarState.Error -> CarButton("Réglages Voiture", onSettings)
                else -> Unit
            }
        }
    }
}

@Composable
private fun CarReadings(
    state: BriefCarState.Ready,
    now: ZonedDateTime,
    expanded: Boolean,
    motionAllowed: Boolean,
    onMapUnavailable: () -> Unit,
) {
    val data = state.data
    val colors = COUITheme.colorScheme
    val status = data.status
    val range = status?.rangeKm
    val fuel = status?.fuelPercent
    val odometer = status?.odometerKm
    Peugeot208Illustration(
        alert = !data.alerts.isNullOrEmpty(),
        motionAllowed = motionAllowed,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    if (range != null || fuel != null || odometer != null) {
        BriefMetricRow { item ->
            range?.let { BriefMetric("Autonomie", briefDecimal(it, 0), item(.9f), unit = "km") }
            fuel?.let { BriefMetric("Carburant", briefDecimal(it, if (it % 1 == 0.0) 0 else 1), item(.9f), unit = "%") }
            odometer?.let { BriefMetric("Compteur", briefDecimal(it, 0), item(1.2f), unit = "km") }
        }
    }
    fuel?.let {
        BriefGauge(
            it.toFloat() / 100f, listOf(colors.red, colors.orange, colors.yellow),
            motionAllowed, Modifier.fillMaxWidth().padding(vertical = 8.dp).height(10.dp), "Niveau de carburant",
        )
    }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (data.alerts == null) {
            CarDetail("Alertes indisponibles")
        } else {
            data.alerts.forEach { alert ->
                CarAlert(alert.label, alert.severity, expanded)
            }
        }
        data.station?.let {
            val refuelSoon = (fuel != null && fuel < 25) || (range != null && range < 100)
            Station(it, now, data.stationFromPhone, refuelSoon, onMapUnavailable)
        }
        data.maintenance?.let { maintenance ->
            val km = maintenance.remainingKm
            if (!expanded && km != null && km >= 0 && (maintenance.remainingDays ?: 0) >= 0) {
                CarDetail("Révision dans ${briefDecimal(km.toDouble(), 0)} km")
            } else {
                CarSection("Prochain entretien", maintenanceText(maintenance))
            }
        }
        if (expanded && status != null) {
            val quality = status.position?.quality(now.toInstant().plusSeconds(59)) ?: CarPositionQuality.Missing
            when (quality) {
                CarPositionQuality.Missing -> CarDetail("Position du véhicule indisponible")
                CarPositionQuality.Estimated -> CarDetail("Position du véhicule estimée")
                CarPositionQuality.Stale -> CarDetail("Dernière position du véhicule trop ancienne")
                CarPositionQuality.Available -> Unit
            }
        }
        if (expanded) {
            val readings = listOf(
                "Carburant" to status?.fuelMeasuredAt,
                "Compteur" to status?.odometerMeasuredAt,
                "Entretien" to data.maintenance?.measuredAt,
            )
            readings.forEach { (label, at) ->
                at?.let { CarDetail("$label · ${it.atZone(now.zone).format(readingDateFormat)}") }
            }
        }
        CarDetail(freshnessText(data, state.partial, now))
    }
}

@Composable
private fun Station(
    station: FuelStation,
    now: ZonedDateTime,
    fromPhone: Boolean,
    refuelSoon: Boolean,
    onMapUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val intent = remember(station.point, station.address) { station.point?.let { stationIntent(it, station.address) } }

    val local = now.withZoneSameInstant(stationTimeZone)
    val closing = stationClosingMinute(station.hours, local)
    val details = listOfNotNull(
        station.distanceKm?.let { "${briefDecimal(it)} km à vol d’oiseau" },
        "${station.fuel.label} · ${briefDecimal(station.price, 3)} €/L",
        closing?.let { "ouverte jusqu’à ${briefHour(it)}" },
    ).joinToString(" · ")
    val onDirections: (() -> Unit)? = if (intent != null && intent.resolveActivity(context.packageManager) != null) {
        { if (!openStation(context, intent)) onMapUnavailable() }
    } else {
        null
    }
    CarInset(
        icon = Icons.Rounded.LocalGasStation,
        tint = COUITheme.colorScheme.orange,
        title = if (refuelSoon) "Plein conseillé bientôt" else "Station la plus proche",
        detail = "${station.address}\n$details",
        action = onDirections?.let { { BriefDemoButton("Y aller", it) } },
    )
    val priceDate = station.priceUpdatedAt?.atZone(stationTimeZone)?.format(readingDateFormat)
    CarDetail(listOfNotNull(
        "Ministère de l’Économie", priceDate?.let { "prix du $it" }, "distance depuis le ${if (fromPhone) "téléphone" else "véhicule"}",
    ).joinToString(" · "))
}

private fun stationIntent(point: CarPoint, title: String): Intent {
    val coordinates = "${point.latitude},${point.longitude}"
    return Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=$coordinates(${Uri.encode(title)})"))
}

private fun openStation(context: Context, intent: Intent): Boolean = try {
    if (intent.resolveActivity(context.packageManager) == null) false else {
        context.startActivity(intent)
        true
    }
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Composable
private fun CarAlert(title: String, severity: String?, expanded: Boolean) {
    val level = severity?.lowercase()
    val label = when (level) {
        "critical" -> "Critique"
        "high" -> "Élevée"
        "warning" -> "Avertissement"
        "info", "information" -> "Information"
        else -> null
    }
    val colors = COUITheme.colorScheme
    CarInset(
        icon = COUIIcons.Report,
        tint = if (level == "high" || level == "critical") colors.red else colors.orange,
        title = title,
        detail = label.takeIf { expanded },
    )
}

@Composable
private fun CarInset(
    icon: ImageVector,
    tint: Color,
    title: String,
    detail: String?,
    action: (@Composable () -> Unit)? = null,
) {
    BriefInlineAction(
        Modifier.fillMaxWidth().briefInset().padding(horizontal = 10.dp, vertical = 8.dp),
        leading = { Icon(icon, null, Modifier.size(19.dp), tint = briefTintedLabel(tint)) },
        text = { CarSection(title, detail) },
        action = action,
    )
}

@Composable
private fun CarSection(title: String, detail: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
        detail?.let { CarDetail(it) }
    }
}

@Composable
private fun CarDetail(text: String) {
    Text(text, color = COUITheme.colorScheme.labelSecondary, style = COUITheme.textStyles.bodyXS)
}

@Composable
private fun CarButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick) { Text(text, style = COUITheme.textStyles.buttonM) }
}

internal fun maintenanceText(maintenance: CarMaintenance): String {
    val km = maintenance.remainingKm
    val days = maintenance.remainingDays
    val distanceOverdue = km?.takeIf { it < 0 }
    val daysOverdue = days?.takeIf { it < 0 }
    val date = maintenance.dueDate?.format(carDateFormat)
    return if (distanceOverdue != null || daysOverdue != null) {
        val delay = listOfNotNull(
            distanceOverdue?.let { "${briefDecimal(it.toDouble().absoluteValue, 0)} km" },
            daysOverdue?.let { "${it.toLong().absoluteValue} ${if (it == -1) "jour" else "jours"}" },
        ).joinToString(" et ")
        "En retard de $delay"
    } else {
        listOfNotNull(
            km?.let { "Dans ${briefDecimal(it.toDouble(), 0)} km" }
                ?: days?.takeIf { date == null }?.let { "Dans $it ${if (it == 1) "jour" else "jours"}" },
            date?.let { if (km == null) "Vers le $it" else "vers le $it" },
        ).joinToString(" · ")
    }
}

private fun freshnessText(data: BriefCarData, partial: Boolean, now: ZonedDateTime): String {
    val measuredAt = data.measuredAt
    val age = measuredAt?.let { Duration.between(it, now.toInstant()).coerceAtLeast(Duration.ZERO) }
    val update = when {
        age == null -> "Date du relevé indisponible"
        age.toDays() > 0 -> "Mis à jour il y a ${age.toDays()} j"
        age.toHours() > 0 -> "Mis à jour il y a ${age.toHours()} h"
        age.toMinutes() > 0 -> "Mis à jour il y a ${age.toMinutes()} min"
        else -> "Mis à jour il y a moins d’une minute"
    }
    return if (partial) "$update · Données partielles" else update
}

private val carDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)
private val readingDateFormat = DateTimeFormatter.ofPattern("d MMM à HH:mm", Locale.FRANCE)
private val stationTimeZone = java.time.ZoneId.of("Europe/Paris")
