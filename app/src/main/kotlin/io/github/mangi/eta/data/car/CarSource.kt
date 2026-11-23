package io.github.mangi.eta.data.car

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl

internal interface CarSource {
    val settings: StateFlow<CarSettings>
    suspend fun load(): BriefCarState
}

internal enum class CarConnection {
    Checking, MyPeugeotMissing, Disconnected, Connected, NeedsReauth,
}

internal data class CarSettings(
    val connection: CarConnection = CarConnection.Checking,
    val hasSession: Boolean = false,
    val vehicles: List<CarVehicle> = emptyList(),
    val vehicleId: String? = null,
    val fuel: CarFuel = CarFuel.E10,
)

internal sealed interface BriefCarState {
    data object MyPeugeotMissing : BriefCarState
    data object Disconnected : BriefCarState
    data object Loading : BriefCarState
    data class Ready(val data: BriefCarData, val partial: Boolean) : BriefCarState
    data object NeedsReauth : BriefCarState
    data class Error(val message: String) : BriefCarState
}

internal enum class CarFuel(val field: String, val label: String) {
    Gazole("gazole", "Gazole"),
    SP95("sp95", "SP95"),
    E10("e10", "E10"),
    SP98("sp98", "SP98"),
    E85("e85", "E85"),
    GPLc("gplc", "GPLc"),
}

internal class CarVehicle(val id: String, val label: String)
internal data class CarPoint(val latitude: Double, val longitude: Double) {
    fun isValid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0
}

internal enum class CarPositionQuality { Available, Missing, Estimated, Stale }

internal data class CarPosition(val point: CarPoint, val measuredAt: Instant?, val estimated: Boolean) {
    fun quality(now: Instant): CarPositionQuality = when {
        estimated -> CarPositionQuality.Estimated
        measuredAt == null -> CarPositionQuality.Missing
        measuredAt > now || Duration.between(measuredAt, now) >= Duration.ofHours(24) -> CarPositionQuality.Stale
        else -> CarPositionQuality.Available
    }
}

internal data class CarStatus(
    val rangeKm: Double? = null,
    val fuelPercent: Double? = null,
    val odometerKm: Double? = null,
    val fuelMeasuredAt: Instant? = null,
    val odometerMeasuredAt: Instant? = null,
    val measuredAt: Instant? = null,
    val position: CarPosition? = null,
    // Jauge/autonomie déduites du compteur + dernier plein/relevé, pas lues du véhicule.
    val estimated: Boolean = false,
)

internal data class CarMaintenance(
    val remainingKm: Long?,
    val remainingDays: Int?,
    val dueDate: LocalDate?,
    val measuredAt: Instant?,
)

internal data class CarAlert(
    val label: String,
    val severity: String?,
    val startedAt: Instant?,
    val updatedAt: Instant?,
    val endAt: Instant?,
)

internal data class BriefCarData(
    val label: String,
    val status: CarStatus? = null,
    val maintenance: CarMaintenance? = null,
    val alerts: List<CarAlert>? = null,
    val station: FuelStation? = null,
    val stationFromPhone: Boolean = false,
    val imageUrl: HttpUrl? = null,
    val useDemoIllustration: Boolean = false,
) {
    val measuredAt: Instant?
        get() = status?.measuredAt
            ?: listOfNotNull(status?.fuelMeasuredAt, status?.odometerMeasuredAt, status?.position?.measuredAt,
                maintenance?.measuredAt, alerts?.mapNotNull { it.updatedAt ?: it.startedAt }?.maxOrNull()).maxOrNull()
}

internal const val carInstallMessage = "Installe MyPeugeot pour connecter ta voiture"
internal const val carReauthMessage = "Reconnexion nécessaire"
