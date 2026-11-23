package io.github.mangi.eta.data.car

import io.github.mangi.eta.ui.screens.home.brief.cards.maintenanceText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CarMappingTest {
    @get:Rule val files = TemporaryFolder()
    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-11-21T12:00:00Z")

    @Test
    fun absentAndNullFieldsRemainAbsentAndFuelZeroIsMeasured() {
        val blank = carJson.decodeFromString<PeugeotStatus>("""{"energies":null,"unrecognized":7}""").toStatus()
        assertEquals(CarStatus(), blank)
        val status = carJson.decodeFromString<PeugeotStatus>(
            """{
                "energies":[{"type":"Electric","level":80},{"type":"Fuel","level":0,"autonomy":0,"createdAt":"2026-11-21T11:00:00Z"}],
                "odometer":{"mileage":1234.5,"createdAt":"2026-11-21T10:30:00Z"},
                "updatedAt":"2026-11-21T11:02:00Z"
            }""",
        ).toStatus()
        assertEquals(0.0, status.rangeKm)
        assertEquals(0.0, status.fuelPercent)
        assertEquals(1234.5, status.odometerKm)
        assertEquals(Instant.parse("2026-11-21T11:00:00Z"), status.fuelMeasuredAt)
        assertEquals(Instant.parse("2026-11-21T10:30:00Z"), status.odometerMeasuredAt)
        assertEquals(Instant.parse("2026-11-21T11:02:00Z"), BriefCarData("Exemple", status).measuredAt)
    }

    @Test
    fun brandingOverridesLegacyAndKeepsOnePrefix() {
        for (model in listOf("208", "Peugeot 208")) {
            val response = carJson.decodeFromString<CarCollection>(
                """{"_embedded":{"vehicles":[
                    {"id":"vehicule-fictif","label":"Ancien","_embedded":{"extension":{"branding":{"brand":"Peugeot","label":"$model"}}}},
                    {"id":"autre-vehicule-fictif","brand":"Peugeot","label":"308"},
                    {"label":"sans identifiant"}
                ]}}""",
            )
            val labels = response.embedded?.vehicles.orEmpty().mapNotNull { it.toVehicle()?.label }
            assertEquals(listOf("Peugeot 208", "Peugeot 308"), labels)
        }
    }

    @Test
    fun maintenanceDateUsesLocalMeasurementAndSignedValues() {
        val late = carJson.decodeFromString<PeugeotMaintenance>(
            """{"updatedAt":"2026-11-20T23:30:00Z","createdAt":"2026-11-18T00:00:00Z","mileageBeforeMaintenance":-280,"daysBeforeMaintenance":-2}""",
        ).toMaintenance(zone)!!
        assertEquals(LocalDate.of(2026, 11, 19), late.dueDate)
        assertEquals("En retard de 280 km et 2 jours", maintenanceText(late))

        val upcoming = carJson.decodeFromString<PeugeotMaintenance>(
            """{"createdAt":"2026-11-20T23:30:00Z","mileageBeforeMaintenance":90,"daysBeforeMaintenance":3}""",
        ).toMaintenance(zone)!!
        assertEquals("Dans 90 km · vers le 24 nov. 2026", maintenanceText(upcoming))
        assertNull(carJson.decodeFromString<PeugeotMaintenance>("{}").toMaintenance(zone))
    }

    @Test
    fun geoJsonPositionUsesLongitudeFirstAndRequiresFreshMeasuredData() {
        fun parse(date: String?, type: String = "Acquire"): CarPosition =
            PeugeotPosition(PeugeotGeometry(listOf(1.0, 2.0, 3.0)), PeugeotPositionProperties(date, type)).toPosition()!!
        val fresh = parse("2026-11-20T12:00:01Z")
        assertEquals(CarPoint(latitude = 2.0, longitude = 1.0), fresh.point)
        assertEquals(CarPositionQuality.Available, fresh.quality(now))
        assertEquals(CarPositionQuality.Stale, parse("2026-11-20T12:00:00Z").quality(now))
        assertEquals(CarPositionQuality.Estimated, parse("2026-11-21T11:00:00Z", "Estimated").quality(now))
        assertEquals(CarPositionQuality.Missing, parse(null).quality(now))
        assertNull(PeugeotPosition(PeugeotGeometry(listOf(1.0))).toPosition())
    }

    @Test
    fun alertsFilterInactiveAndTranslateCommonTypes() {
        val alerts = carJson.decodeFromString<CarCollection>(
            """{"_embedded":{"alerts":[{"active":true,"type":"LeftFrontTireUnderInflated","severity":"Warning"},
                {"active":false,"type":"FuelLevelLow"},{"type":"EngineFault"}]}}""",
        ).embedded?.alerts.orEmpty().mapNotNull(PeugeotAlert::toActiveAlert)
        assertEquals(listOf("Pneu avant gauche sous-gonflé"), alerts.map(CarAlert::label))
        assertEquals("Warning", alerts.single().severity)
        assertEquals("Niveau d’huile bas", carAlertLabel("OilLevelLow"))
        assertEquals("Défaut AdBlue", carAlertLabel("AdBlueFault"))
        assertEquals("Entretien à prévoir", carAlertLabel("MaintenanceDue"))
        assertEquals("Plaquettes de frein à remplacer", carAlertLabel("ReplaceBrakePads"))
        assertEquals("Pile de la télécommande à remplacer", carAlertLabel("ReplaceTheRemoteControlBattery"))
        assertEquals("New ABS Message", carAlertLabel("NewABSMessage"))
    }

    @Test
    fun closingTimeRequiresAnActiveExplicitInterval() {
        val hours = """{"@automate-24-24":"1","jour":[
            {"@id":"5","@ferme":"","horaire":[{"@ouverture":"08.15","@fermeture":"12.00"},{"@ouverture":"14:00","@fermeture":"21:30"}]},
            {"@id":"6","@ferme":"1","horaire":{"@ouverture":"08.00","@fermeture":"20.00"}},
            {"@id":"7","@ferme":"0","horaire":{"@ouverture":"22.00","@fermeture":"02.00"}}
        ]}"""
        val friday = ZonedDateTime.parse("2026-11-20T11:30:00+01:00[Europe/Paris]")
        assertEquals(12 * 60, stationClosingMinute(hours, friday))
        assertNull(stationClosingMinute(hours, friday.plusHours(1)))
        assertEquals(21 * 60 + 30, stationClosingMinute(hours, friday.plusHours(4)))
        assertNull(stationClosingMinute(hours, friday.plusDays(1)))
        assertEquals(120, stationClosingMinute(hours, friday.plusDays(2).withHour(23)))
        assertEquals(120, stationClosingMinute(hours, friday.plusDays(3).withHour(1)))
        assertNull(stationClosingMinute("""{"@automate-24-24":"1"}""", friday))
        assertNull(stationClosingMinute("incomplet", friday))
    }

    @Test
    fun onlyTheExpectedFrenchZipEntryContainsParameters() {
        val file = files.newFile("synthetic.apk")
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("res/raw/parameters.json"))
            it.write("""{"cvsClientId":"wrong-locale-example","cvsSecret":"fake"}""".toByteArray())
            it.closeEntry()
        }
        assertNull(readParametersZip(file.path))
        ZipOutputStream(file.outputStream()).use {
            it.putNextEntry(ZipEntry("res/raw-fr-rFR/parameters.json"))
            it.write("""{"cvsClientId":"example-client","cvsSecret":"fake-example","ignored":true}""".toByteArray())
            it.closeEntry()
        }
        val parameters = readParametersZip(file.path)!!
        assertEquals("example-client", parameters.cvsClientId)
        assertEquals("fake-example", parameters.cvsSecret)
    }

    @Test
    fun halLinksStayOnThePeugeotOrigin() {
        val base = "https://api.groupe-psa.com/connectedcar/v4/user/vehicles".toHttpUrl()
        assertEquals("p2", peugeotLink(base, "?pageToken=p2")?.queryParameter("pageToken"))
        assertNull(peugeotLink(base, "https://example.invalid/vehicles"))
        assertNull(peugeotLink(base, "http://api.groupe-psa.com/vehicles"))
        assertNull(peugeotLink(base, "https://api.groupe-psa.com:444/vehicles"))
        assertTrue(peugeotLink(base, "https://api.groupe-psa.com/connectedcar/v4/user/vehicles") == base)
    }
}
