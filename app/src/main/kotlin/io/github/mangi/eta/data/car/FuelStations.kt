package io.github.mangi.eta.data.car

import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

internal data class FuelStation(
    val address: String,
    val point: CarPoint?,
    val distanceKm: Double?,
    val price: Double,
    val priceUpdatedAt: Instant?,
    val fuel: CarFuel,
    val hours: String?,
)

internal class FuelStations(private val http: CarHttp) {
    suspend fun nearest(center: CarPoint, fuel: CarFuel): FuelStation? {
        require(center.isValid())
        val point = "geom'POINT(${center.longitude} ${center.latitude})'"
        val price = "${fuel.field}_prix"
        val updated = "${fuel.field}_maj"
        val interruption = "${fuel.field}_rupture_type"
        val url = stationUrl.toHttpUrl().newBuilder()
            .addQueryParameter("where", "within_distance(geom, $point, 5km) AND $price IS NOT NULL AND $interruption IS NULL")
            .addQueryParameter("order_by", "distance(geom, $point) ASC")
            .addQueryParameter("select", "id,adresse,ville,geom,$price,$updated,horaires,distance(geom, $point) AS distance_m")
            .addQueryParameter("limit", "1")
            .build()
        val raw = http.execute(Request.Builder().url(url).get().build()).requireSuccess()
        val row = carJson.decodeFromString<StationResults>(raw).results?.firstOrNull() ?: return null
        val stationPrice = (row[price] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 && it.isFinite() } ?: return null
        val geo = (row["geom"] as? JsonObject)?.let {
            val lat = (it["lat"] as? JsonPrimitive)?.doubleOrNull ?: return@let null
            val lon = (it["lon"] as? JsonPrimitive)?.doubleOrNull ?: return@let null
            CarPoint(lat, lon).takeIf(CarPoint::isValid)
        }
        return FuelStation(
            address = listOfNotNull(row.string("adresse"), row.string("ville")).joinToString(" · ").ifBlank { "Station-service" },
            point = geo,
            distanceKm = (row["distance_m"] as? JsonPrimitive)?.doubleOrNull?.takeIf { it >= 0 && it.isFinite() }?.div(1_000),
            price = stationPrice,
            priceUpdatedAt = carInstant(row.string(updated)),
            fuel = fuel,
            hours = row.string("horaires"),
        )
    }
}

@Serializable
private class StationResults(val results: List<JsonObject>? = null)

private const val stationUrl =
    "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records"

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.objects(key: String): List<JsonObject> = when (val value = get(key)) {
    is JsonObject -> listOf(value)
    is JsonArray -> value.filterIsInstance<JsonObject>()
    else -> emptyList()
}

private val stationClockPattern = Regex("""(\d{1,2})[.:](\d{2})""")

private fun stationMinute(value: String?): Int? {
    val match = value?.let(stationClockPattern::matchEntire) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return (hour * 60 + minute).takeIf { (hour in 0..23 && minute in 0..59) || (hour == 24 && minute == 0) }
}

/** Returns the closing minute of an explicit window that contains the current local time. */
internal fun stationClosingMinute(hours: String?, now: ZonedDateTime): Int? {
    if (hours == null) return null
    val schedule = runCatching { carJson.decodeFromString<JsonObject>(hours) }.getOrNull() ?: return null
    val days = schedule.objects("jour")
    val currentMinute = now.hour * 60 + now.minute

    fun closingAt(day: ZonedDateTime, minute: Int): Int? {
        val record = days.firstOrNull { it.string("@id")?.toIntOrNull() == day.dayOfWeek.value } ?: return null
        if (record.string("@ferme") == "1") return null
        return record.objects("horaire").mapNotNull {
            val open = stationMinute(it.string("@ouverture")) ?: return@mapNotNull null
            val close = stationMinute(it.string("@fermeture")) ?: return@mapNotNull null
            if (open == close || open == 1440) return@mapNotNull null
            val end = if (close < open) close + 1440 else close
            end.takeIf { minute in open until end }
        }.maxOrNull()
    }

    val end = closingAt(now, currentMinute) ?: closingAt(now.minusDays(1), currentMinute + 1440)
    return end?.let { if (it > 1440) it - 1440 else it }
}
