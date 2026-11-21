package io.github.mangi.eta.ui.screens.home.brief

import io.github.mangi.eta.data.car.BriefCarData
import io.github.mangi.eta.data.car.BriefCarState
import io.github.mangi.eta.data.car.CarAlert
import io.github.mangi.eta.data.car.CarConnection
import io.github.mangi.eta.data.car.CarMaintenance
import io.github.mangi.eta.data.car.CarSettings
import io.github.mangi.eta.data.car.CarSource
import io.github.mangi.eta.data.car.CarStatus
import io.github.mangi.eta.data.car.carAlertLabel
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A synthetic vehicle for injected previews and tests. */
internal class DemoCarSource(
    private val measuredAt: Instant = Instant.parse("2026-11-20T17:12:00Z"),
) : CarSource {
    override val settings = MutableStateFlow(CarSettings(connection = CarConnection.Connected)).asStateFlow()

    override suspend fun load(): BriefCarState = BriefCarState.Ready(
        BriefCarData(
            label = BriefDemoData.carModel,
            status = CarStatus(
                rangeKm = BriefDemoData.rangeKm.toDouble(),
                fuelPercent = BriefDemoData.fuelPercent.toDouble(),
                odometerKm = BriefDemoData.odometerKm.toDouble(),
                measuredAt = measuredAt,
            ),
            maintenance = CarMaintenance(
                BriefDemoData.serviceRemainingKm.toLong(), 45,
                measuredAt.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(45), measuredAt,
            ),
            alerts = listOf(CarAlert(carAlertLabel("LeftRearTireUnderInflated"), "Warning", measuredAt, measuredAt, null)),
        ),
        partial = false,
    )
}
