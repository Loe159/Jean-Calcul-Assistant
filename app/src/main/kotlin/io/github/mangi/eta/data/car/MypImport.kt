package io.github.mangi.eta.data.car

import kotlinx.serialization.Serializable

// Parseur du fichier d'export MyPeugeot (.myp) : un tableau de véhicules, chacun avec ses trajets.
// On ne garde que le dernier trajet dont le relevé est plausible pour en faire une ancre d'estimation.
// Tolérant : clés inconnues ignorées (carJson), champs absents acceptés, relevés aberrants filtrés.

@Serializable
private class MypVehicle(val vin: String? = null, val trips: List<MypTrip> = emptyList())

@Serializable
private class MypTrip(
    val end: MypPoint? = null,
    val fuelLevel: Double? = null,
    val fuelAutonomy: Double? = null,
)

@Serializable
private class MypPoint(val mileage: Double? = null, val date: String? = null)

internal class MypImportResult(val anchor: FuelAnchor, val tripsSeen: Int, val vin: String?)

internal fun parseMypAnchor(text: String): MypImportResult? {
    val vehicles = carJson.decodeFromString<List<MypVehicle>>(text)
    var seen = 0
    var best: MypImportResult? = null
    for (vehicle in vehicles) {
        for (trip in vehicle.trips) {
            seen++
            val fuel = trip.fuelLevel ?: continue
            val range = trip.fuelAutonomy ?: continue
            val mileage = trip.end?.mileage ?: continue
            val recordedAt = carInstant(trip.end.date) ?: continue
            // Écarte les relevés manifestement bruités : fuelLevel 0 ou -1, autonomie <= 0, compteur négatif.
            if (fuel < 1.0 || fuel > 100.0 || range <= 0.0 || mileage < 0.0) continue
            val current = best
            if (current == null || recordedAt.isAfter(current.anchor.recordedAt)) {
                best = MypImportResult(
                    anchor = FuelAnchor(mileage, fuel, range, recordedAt.epochSecond, FuelAnchorSource.MypImport),
                    tripsSeen = seen,
                    vin = vehicle.vin,
                )
            }
        }
    }
    // tripsSeen reflète le total parcouru, pas l'index du meilleur.
    return best?.let { MypImportResult(it.anchor, seen, it.vin) }
}
