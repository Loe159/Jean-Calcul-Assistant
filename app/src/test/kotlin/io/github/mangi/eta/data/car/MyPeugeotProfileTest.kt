package io.github.mangi.eta.data.car

import java.time.Instant
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyPeugeotProfileTest {
    @Test
    fun parsesSelectedVehicleAndKeepsOnlySupportedReadings() {
        val garage = parseMymGarage(
            """
            {
              "success": {
                "selected_vehicle": {
                  "vin": "example-vehicle-a",
                  "short_label": "208 de test",
                  "visual": "https://images.example.invalid/208.png",
                  "mileage": {"value": 12345.5, "timestamp": "2026-11-21T12:02:00+01:00", "source": "example"},
                  "attributes": [],
                  "eligibility": []
                },
                "vehicles": [
                  {"vin": "example-vehicle-a", "short_label": "208"},
                  {"vin": "example-vehicle-b", "short_label": "308 de test"}
                ]
              }
            }
            """.trimIndent(),
        )
        val vehicle = requireNotNull(garage.choose("old-v4-id"))
        val data = vehicle.data
        assertEquals(2, garage.vehicles.size)
        assertNotEquals("example-vehicle-a", vehicle.id)
        assertEquals("308 de test", garage.choose(garage.vehicles[1].id)?.data?.label)
        assertEquals("208 de test", data.label)
        assertEquals("https://images.example.invalid/208.png", data.imageUrl.toString())
        assertEquals(12345.5, data.status?.odometerKm)
        assertEquals(Instant.parse("2026-11-21T11:02:00Z"), data.measuredAt)
        assertNull(data.status?.fuelPercent)
        assertNull(data.status?.rangeKm)
        assertNull(data.status?.position)
        assertNull(data.maintenance)
        assertNull(data.alerts)
    }

    @Test
    fun toleratesMissingReadingsAndRejectsInsecureImageUrls() {
        val data = parseMymGarage(
            """{"success":{"selected_vehicle":{"visual":"http://images.example.invalid/208.png",
                "mileage":{"value":-1,"timestamp":"2026-11-21T11:02:00Z"}}}}""",
        ).selected?.data
        assertEquals("Peugeot", data?.label)
        assertNull(data?.imageUrl)
        assertNull(data?.status?.odometerKm)
        assertNull(data?.measuredAt)
    }

    @Test
    fun parsesSessionTokenFromTheSuccessEnvelope() {
        assertEquals(
            "synthetic-mym-token",
            parseMymAccessToken("""{"success":{"mym_access_token":"synthetic-mym-token","cat_ticket":"example"}}"""),
        )
    }

    @Test(expected = SerializationException::class)
    fun rejectsBlankSessionToken() {
        parseMymAccessToken("""{"success":{"mym_access_token":" "}}""")
    }
}
