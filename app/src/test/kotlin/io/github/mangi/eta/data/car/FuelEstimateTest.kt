package io.github.mangi.eta.data.car

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEstimateTest {
    // Données synthétiques uniquement (pas de VIN ni d'adresse réels).
    private val sampleMyp = """
        [{"vin":"TEST-VIN","trips":[
          {"end":{"mileage":1000.0,"date":"2026-01-01T10:00:00.000Z"},"fuelLevel":80,"fuelAutonomy":640},
          {"end":{"mileage":1100.0,"date":"2026-01-05T10:00:00.000Z"},"fuelLevel":60,"fuelAutonomy":480},
          {"end":{"mileage":1150.0,"date":"2026-01-06T10:00:00.000Z"},"fuelLevel":0,"fuelAutonomy":500},
          {"end":{"mileage":1160.0,"date":"2026-01-07T10:00:00.000Z"},"fuelLevel":-1,"fuelAutonomy":-1}
        ]}]
    """.trimIndent()

    @Test
    fun importPicksLatestPlausibleTripAndSkipsNoise() {
        val result = requireNotNull(parseMypAnchor(sampleMyp))
        // Le trajet du 06 (fuelLevel 0) et du 07 (-1) sont écartés -> on garde celui du 05.
        assertEquals(60.0, result.anchor.fuelPercent, 1e-6)
        assertEquals(480.0, result.anchor.rangeKm, 1e-6)
        assertEquals(1100.0, result.anchor.odometerKm, 1e-6)
        assertEquals(FuelAnchorSource.MypImport, result.anchor.source)
        assertEquals(4, result.tripsSeen)
        assertEquals("TEST-VIN", result.vin)
    }

    @Test
    fun estimateExtrapolatesFromImportedAnchor() {
        val state = FuelEstimateState().withAnchor(requireNotNull(parseMypAnchor(sampleMyp)).anchor)
        // Base plein = 480 / 0,60 = 800 km. Après 80 km : 400 km restants, soit 50 %.
        val reading = requireNotNull(state.estimate(1180.0))
        assertEquals(400.0, reading.rangeKm, 1e-6)
        assertEquals(50.0, reading.fuelPercent, 1e-6)
    }

    @Test
    fun fillUpStartsAtFullTankAndDecreasesWithDistance() {
        val fill = FuelEstimateState().withFillUp(2000.0, Instant.parse("2026-02-01T00:00:00Z"))
        val reading = requireNotNull(fill.estimate(2090.0))
        assertEquals(FuelEstimateState.DEFAULT_FULL_TANK_KM - 90.0, reading.rangeKm, 1e-6)
        assertEquals(90.0, reading.fuelPercent, 1e-6)
    }

    @Test
    fun staleOdometerNeverInflatesEstimate() {
        val fill = FuelEstimateState().withFillUp(2000.0, Instant.EPOCH)
        // Compteur cloud plus ancien que l'ancre : distance bornée à 0, reste à 100 %.
        val reading = requireNotNull(fill.estimate(1900.0))
        assertEquals(FuelEstimateState.DEFAULT_FULL_TANK_KM, reading.rangeKm, 1e-6)
        assertEquals(100.0, reading.fuelPercent, 1e-6)
    }

    @Test
    fun fillUpReusesFullTankLearnedFromImport() {
        val learned = FuelEstimateState()
            .withAnchor(requireNotNull(parseMypAnchor(sampleMyp)).anchor) // apprend 800 km à plein
            .withFillUp(3000.0, Instant.EPOCH)
        assertEquals(800.0, requireNotNull(learned.anchor).rangeKm, 1e-6)
        assertTrue(learned.fullTankKm in 799.9..800.1)
    }

    @Test
    fun emptyOrAnchorlessInputsAreSafe() {
        assertNull(parseMypAnchor("[]"))
        assertNull(FuelEstimateState().estimate(1000.0))
        assertNull(FuelEstimateState().withAnchor(FuelAnchor(0.0, 50.0, 400.0, 0L, FuelAnchorSource.MypImport)).estimate(null))
    }
}
