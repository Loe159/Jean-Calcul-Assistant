package io.github.mangi.eta.data.car

import java.time.Instant
import kotlinx.serialization.Serializable

// Estimation du carburant/autonomie à partir d'une "ancre" (un relevé réel) et du delta
// du compteur lu au cloud. Deux façons de poser une ancre : un plein signalé par l'utilisateur
// (100 %) ou l'import d'un .myp (dernier relevé Bluetooth de MyPeugeot). Entre deux ancres,
// on extrapole : chaque kilomètre parcouru retranche autant à l'autonomie.

internal enum class FuelAnchorSource { FillUp, MypImport }

@Serializable
internal data class FuelAnchor(
    val odometerKm: Double,
    val fuelPercent: Double,
    val rangeKm: Double,
    val recordedAtEpochSec: Long,
    val source: FuelAnchorSource,
) {
    val recordedAt: Instant get() = Instant.ofEpochSecond(recordedAtEpochSec)

    // Autonomie théorique à 100 % impliquée par cette ancre (ex. 560 km à 54 % → ~1037 km).
    val fullTankKm: Double? get() = if (fuelPercent > 0.0) rangeKm / (fuelPercent / 100.0) else null
}

@Serializable
internal data class FuelEstimateState(
    val anchor: FuelAnchor? = null,
    val fullTankKm: Double = DEFAULT_FULL_TANK_KM,
) {
    companion object {
        // Repli tant qu'aucun .myp n'a été importé pour caler l'autonomie à plein réelle.
        const val DEFAULT_FULL_TANK_KM = 900.0
    }
}

internal data class FuelEstimate(val rangeKm: Double, val fuelPercent: Double, val recordedAt: Instant)

internal fun FuelEstimateState.estimate(odometerKm: Double?): FuelEstimate? {
    val a = anchor ?: return null
    if (odometerKm == null || !odometerKm.isFinite()) return null
    // Un compteur cloud plus ancien que l'ancre (ou aberrant) ne doit pas gonfler l'estimation.
    val distance = (odometerKm - a.odometerKm).coerceAtLeast(0.0)
    val rangeNow = (a.rangeKm - distance).coerceAtLeast(0.0)
    val basis = a.fullTankKm ?: fullTankKm
    if (basis <= 0.0) return null
    val fuelNow = (rangeNow / basis * 100.0).coerceIn(0.0, 100.0)
    return FuelEstimate(rangeNow, fuelNow, a.recordedAt)
}

internal fun FuelEstimateState.withFillUp(odometerKm: Double, now: Instant): FuelEstimateState {
    val full = anchor?.fullTankKm ?: fullTankKm
    return FuelEstimateState(
        anchor = FuelAnchor(odometerKm, 100.0, full, now.epochSecond, FuelAnchorSource.FillUp),
        fullTankKm = full,
    )
}

internal fun FuelEstimateState.withAnchor(anchor: FuelAnchor): FuelEstimateState =
    FuelEstimateState(anchor = anchor, fullTankKm = anchor.fullTankKm ?: fullTankKm)
