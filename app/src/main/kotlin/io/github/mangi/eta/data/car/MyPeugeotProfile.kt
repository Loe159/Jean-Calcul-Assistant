package io.github.mangi.eta.data.car

import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
private class MymSessionResponse(val success: MymSession)

@Serializable
private class MymSession(@SerialName("mym_access_token") val accessToken: String)

internal fun parseMymAccessToken(body: String): String =
    carJson.decodeFromString<MymSessionResponse>(body).success.accessToken.takeIf(String::isNotBlank)
        ?: throw SerializationException("Missing MYM access token")

@Serializable
private class MymProfileResponse(val success: MymProfile)

@Serializable
private class MymProfile(
    @SerialName("selected_vehicle") val selected: MymProfileVehicle? = null,
    val vehicles: List<MymProfileVehicle>? = null,
)

@Serializable
private class MymProfileVehicle(
    val vin: String? = null,
    @SerialName("short_label") val shortLabel: String? = null,
    val visual: String? = null,
    val mileage: MymMileage? = null,
) {
    fun toVehicle(): MymVehicle {
        // Keep the selection stable across loads without persisting the VIN.
        val id = vin?.trim()?.takeIf(String::isNotEmpty)?.let {
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8))
            Base64.getUrlEncoder().withoutPadding().encodeToString(fingerprint)
        }
        val odometer = mileage?.value?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
        return MymVehicle(
            id = id,
            data = BriefCarData(
                label = shortLabel?.trim()?.takeIf(String::isNotEmpty) ?: "Peugeot",
                status = CarStatus(
                    odometerKm = odometer,
                    odometerMeasuredAt = mileage?.timestamp?.contentOrNull?.takeIf { odometer != null }?.let(::mileageInstant),
                ),
                imageUrl = visual?.toHttpUrlOrNull()?.takeIf {
                    it.isHttps && it.encodedUsername.isEmpty() && it.encodedPassword.isEmpty()
                },
            ),
        )
    }
}

@Serializable
private class MymMileage(val value: JsonPrimitive? = null, val timestamp: JsonPrimitive? = null)

internal class MymVehicle(val id: String?, val data: BriefCarData)

internal class MymGarage(val selected: MymVehicle?, val vehicles: List<MymVehicle>) {
    fun choose(preferredId: String?): MymVehicle? =
        preferredId?.let { id -> vehicles.firstOrNull { it.id == id } } ?: selected ?: vehicles.singleOrNull()
}

internal fun parseMymGarage(body: String): MymGarage {
    val profile = carJson.decodeFromString<MymProfileResponse>(body).success
    val selected = profile.selected?.toVehicle()
    val vehicles = (listOfNotNull(selected) + profile.vehicles.orEmpty().map(MymProfileVehicle::toVehicle))
        .filter { it.id != null }.distinctBy(MymVehicle::id)
    return MymGarage(selected, vehicles)
}

private fun mileageInstant(value: String): Instant? {
    carInstant(value)?.let { return it }
    val epoch = value.toLongOrNull() ?: return null
    return when (epoch) {
        in 0..253_402_300_799L -> Instant.ofEpochSecond(epoch)
        in 1_000_000_000_000L..253_402_300_799_999L -> Instant.ofEpochMilli(epoch)
        else -> null
    }
}
