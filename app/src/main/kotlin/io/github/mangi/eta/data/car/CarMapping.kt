package io.github.mangi.eta.data.car

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val carJson = Json { ignoreUnknownKeys = true }

internal fun carInstant(value: String?): Instant? = try {
    value?.let { OffsetDateTime.parse(it).toInstant() }
} catch (_: DateTimeParseException) {
    null
}

@Serializable
internal class CarCollection(
    @SerialName("_embedded") val embedded: CarCollectionItems? = null,
    @SerialName("_links") val links: CarLinks? = null,
)

@Serializable
internal class CarCollectionItems(
    val vehicles: List<PeugeotVehicle>? = null,
    val alerts: List<PeugeotAlert>? = null,
)

@Serializable
internal class CarLinks(val next: CarLink? = null, val maintenance: CarLink? = null)

@Serializable
internal class CarLink(val href: String? = null)

@Serializable
internal class PeugeotVehicle(
    val id: String? = null,
    val brand: String? = null,
    val label: String? = null,
    @SerialName("_embedded") val embedded: VehicleEmbedded? = null,
    @SerialName("_links") val links: CarLinks? = null,
) {
    fun toVehicle(): CarVehicle? {
        val vehicleId = id?.takeIf(String::isNotBlank) ?: return null
        val branding = embedded?.extension?.branding
        val vehicleBrand = branding?.brand.nonBlank() ?: brand.nonBlank() ?: "Peugeot"
        val model = branding?.label.nonBlank() ?: label.nonBlank()
        val name = when {
            model == null -> vehicleBrand
            model.equals(vehicleBrand, ignoreCase = true) || model.startsWith("$vehicleBrand ", ignoreCase = true) -> model
            else -> "$vehicleBrand $model"
        }
        return CarVehicle(vehicleId, name)
    }
}

private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)

@Serializable
internal class VehicleEmbedded(val extension: VehicleExtension? = null)

@Serializable
internal class VehicleExtension(val branding: VehicleBranding? = null)

@Serializable
internal class VehicleBranding(val brand: String? = null, val label: String? = null)

@Serializable
internal class PeugeotStatus(
    val energies: List<PeugeotEnergy>? = null,
    val odometer: PeugeotOdometer? = null,
    val lastPosition: PeugeotPosition? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    fun toStatus(): CarStatus {
        val fuel = energies?.firstOrNull { it.type.equals("Fuel", ignoreCase = true) }
        return CarStatus(
            rangeKm = fuel?.autonomy?.takeIf { it.isFinite() && it >= 0 },
            fuelPercent = fuel?.level?.takeIf { it in 0.0..100.0 },
            odometerKm = odometer?.mileage?.takeIf { it.isFinite() && it >= 0 },
            fuelMeasuredAt = carInstant(fuel?.createdAt) ?: carInstant(fuel?.updatedAt),
            odometerMeasuredAt = carInstant(odometer?.createdAt),
            measuredAt = carInstant(updatedAt) ?: carInstant(createdAt),
            position = lastPosition?.toPosition(),
        )
    }
}

@Serializable
internal class PeugeotEnergy(
    val type: String? = null,
    val autonomy: Double? = null,
    val level: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal class PeugeotOdometer(val mileage: Double? = null, val createdAt: String? = null)

@Serializable
internal class PeugeotPosition(val geometry: PeugeotGeometry? = null, val properties: PeugeotPositionProperties? = null) {
    fun toPosition(): CarPosition? {
        val coordinates = geometry?.coordinates ?: return null
        if (coordinates.size < 2) return null
        val point = CarPoint(coordinates[1], coordinates[0]).takeIf(CarPoint::isValid) ?: return null
        return CarPosition(point, carInstant(properties?.createdAt), properties?.type.equals("Estimated", ignoreCase = true))
    }
}

@Serializable
internal class PeugeotGeometry(val coordinates: List<Double>? = null)

@Serializable
internal class PeugeotPositionProperties(val createdAt: String? = null, val type: String? = null)

@Serializable
internal class PeugeotMaintenance(
    val mileageBeforeMaintenance: Long? = null,
    val daysBeforeMaintenance: Int? = null,
    val updatedAt: String? = null,
    val createdAt: String? = null,
) {
    fun toMaintenance(zone: ZoneId): CarMaintenance? {
        if (mileageBeforeMaintenance == null && daysBeforeMaintenance == null) return null
        val measuredAt = carInstant(updatedAt) ?: carInstant(createdAt)
        val due = daysBeforeMaintenance?.let { measuredAt?.atZone(zone)?.toLocalDate()?.plusDays(it.toLong()) }
        return CarMaintenance(mileageBeforeMaintenance, daysBeforeMaintenance, due, measuredAt)
    }
}

@Serializable
internal class PeugeotAlert(
    val type: String? = null,
    val active: Boolean? = null,
    val severity: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val endAt: String? = null,
) {
    fun toActiveAlert(): CarAlert? {
        if (active != true) return null
        return CarAlert(
            type.nonBlank()?.let(::carAlertLabel) ?: "Alerte véhicule",
            severity, carInstant(startedAt), carInstant(updatedAt), carInstant(endAt),
        )
    }
}

private val tyrePositions = mapOf(
    "LeftFront" to "avant gauche", "RightFront" to "avant droit",
    "LeftRear" to "arrière gauche", "RightRear" to "arrière droit",
)

private val carAlertLabels = mapOf(
    "TireUnderInflationDetectionSystemFault" to "Défaut de détection du sous-gonflage",
    "OilLevelLow" to "Niveau d’huile bas",
    "EngineOilPressureFailure" to "Défaut de pression d’huile moteur",
    "AdBlueFault" to "Défaut AdBlue",
    "AdBlueFaultStartingImpossible" to "Défaut AdBlue · démarrage impossible",
    "AdBlueFaultStartingImpossibleSoon" to "Défaut AdBlue · démarrage bientôt impossible",
    "AdBlueLevelLow" to "Niveau d’AdBlue bas",
    "MaintenanceDue" to "Entretien à prévoir",
    "MaintenanceRequired" to "Entretien nécessaire",
    "ServiceDue" to "Révision à prévoir",
    "ServiceReminder" to "Révision à prévoir",
    "BrakingSystemFailure" to "Défaillance du système de freinage",
    "AbsBrakingSystemFault" to "Défaut du freinage ABS",
    "ReplaceBrakePads" to "Plaquettes de frein à remplacer",
    "ParkingBrakeOrHillStartSystemFailure" to "Défaut du frein de stationnement ou de l’aide au démarrage en côte",
    "BatteryChargeOrElectricalCircuitSystemFailure" to "Défaut de charge de la batterie ou du circuit électrique",
    "BatteryLevelLow" to "Batterie faible",
    "ReplaceTheRemoteControlBattery" to "Pile de la télécommande à remplacer",
    "FuelLevelLow" to "Niveau de carburant bas",
    "EngineFault" to "Défaut moteur",
    "EngineFailure" to "Défaillance moteur",
    "AddWasherFluid" to "Liquide lave-glace à ajouter",
)

private val alertWordBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
private val alertSeparators = Regex("[_\\-]+")

internal fun carAlertLabel(type: String): String {
    carAlertLabels[type]?.let { return it }
    for ((prefix, position) in tyrePositions) {
        when (type.removePrefix(prefix)) {
            "TireUnderInflated" -> return "Pneu $position sous-gonflé"
            "TirePuncture" -> return "Pneu $position crevé"
            "TirePressurSensorFault" -> return "Défaut du capteur de pression · pneu $position"
        }
    }
    return type.replace(alertWordBoundary, " ").replace(alertSeparators, " ")
}
