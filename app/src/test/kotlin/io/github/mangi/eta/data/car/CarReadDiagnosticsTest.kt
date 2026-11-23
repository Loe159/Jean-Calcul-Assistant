package io.github.mangi.eta.data.car

import java.io.IOException
import kotlinx.serialization.SerializationException
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarReadDiagnosticsTest {
    @Test
    fun errorBodyMasksSensitiveFieldsBeforeTakingTheFirst300Characters() {
        val body = """{"access\u005ftoken":"${"a".repeat(500)}","REFRESH_TOKEN":"refresh-example",
            "id_token":"id-example","client_id":"client-example","client_secret":"secret-example",
            "vin":"VIN-FICTIF","code":40400,"detail":{"coordinates":[2.123,48.456,10],
            "lat":12,"lon":34},"message":"Véhicule introuvable","extra":"${"x".repeat(300)}"}"""
        val preview = carErrorBodyPreview(body)
        assertEquals(300, preview.length)
        assertTrue(preview.startsWith("""{"access_token":"[masqué]","REFRESH_TOKEN":"[masqué]""""))
        assertTrue(preview.contains("Véhicule introuvable"))
        for (value in listOf("aaaa", "refresh-example", "id-example", "client-example", "secret-example",
            "VIN-FICTIF", "40400", "2.123", "48.456", ":12", ":34",
        )) {
            assertFalse(preview.contains(value))
        }
    }

    @Test
    fun errorBodyMasksPositionAndCredentialsInText() {
        val body = """{"error":"Invalid grant","description":"Bearer example-token for VF3EXAMPLE1234567",
            "detail":["POINT(2 48)","48.123 / 2.654","https://example.test/?code=example-code"],
            "lastPosition":{"street":"Exemple"}}"""
        val preview = carErrorBodyPreview(body)
        assertTrue(preview.contains("Invalid grant"))
        for (value in listOf("example-token", "VF3EXAMPLE1234567", "POINT", "48.123", "2.654", "example-code", "Exemple")) {
            assertFalse(preview.contains(value))
        }
        assertEquals("[masqué]", carErrorBodyPreview("""{"access_token":"truncated-example"""))
    }

    @Test
    fun pathOmitsQueryAndTemplatesVehicleSegmentEvenWhenEncoded() {
        val api = "https://api.groupe-psa.com/connectedcar/v4/user"
        assertEquals(
            "/connectedcar/v4/user/vehicles/{id}/maintenance",
            carDiagnosticPath("$api/vehicles/vehicle%2Fexample/maintenance?client_id=fake#fragment".toHttpUrl()),
        )
        assertEquals("/connectedcar/v4/user/vehicles", carDiagnosticPath("$api/vehicles?page=fake".toHttpUrl()))
        assertEquals("/am/oauth2/access_token", carDiagnosticPath("https://example.test/am/oauth2/access_token".toHttpUrl()))
    }

    @Test
    fun parseDiagnosticReportsFieldAndTopLevelKeysWithoutResponseValues() {
        val body = """{"odometer":{"mileage":"bad-value'suffix"},"lastPosition":{"coordinates":[2.123,48.456]}}"""
        val error = runCatching { carJson.decodeFromString<PeugeotStatus>(body) }.exceptionOrNull() as SerializationException
        val diagnostic = carReadDiagnostic(
            CarReadFailure(CarReadStage.Status, 200, error),
            "https://example.test/vehicles/example-id/status?client_id=private-example".toHttpUrl(),
            Headers.Builder().add("X-RateLimit-Remaining-day", "89").add("Set-Cookie", "cookie-example").build(),
            body, emptyList(),
        )
        assertTrue(diagnostic.contains("status /vehicles/{id}/status HTTP 200"))
        assertTrue(diagnostic.contains("X-RateLimit-Remaining-day=89"))
        assertTrue(diagnostic.contains("JsonDecodingException"))
        assertTrue(diagnostic.contains("$.odometer.mileage"))
        assertTrue(diagnostic.contains("keys=[odometer, lastPosition]"))
        for (value in listOf("bad-value", "suffix", "coordinates", "2.123", "48.456", "example-id",
            "private-example", "cookie-example", "JSON input:",
        )) {
            assertFalse(diagnostic.contains(value))
        }
    }

    @Test
    fun fetchMessagesDistinguishHttpParsingAndNetworkFailures() {
        fun status(code: Int) = CarReadFailure(CarReadStage.Status, code, CarHttpFailure(code))
        val notFound = "Véhicule introuvable côté Peugeot (services connectés ou partage des données désactivés ?)"
        assertEquals(notFound, carFetchMessage(status(404), debug = false))
        assertEquals("$notFound (status · HTTP 404)", carFetchMessage(status(404), debug = true))
        assertEquals("Serveurs Peugeot indisponibles. Réessaie plus tard.", carFetchMessage(status(503), debug = false))
        assertEquals("Vérifie l’accès aux données dans MyPeugeot.", carFetchMessage(status(403), debug = false))
        assertEquals("MyPeugeot limite les requêtes. Réessaie plus tard.", carFetchMessage(status(429), debug = false))
        assertEquals("Service Voiture indisponible.", carFetchMessage(status(400), debug = false))
        val parse = CarReadFailure(CarReadStage.Maintenance, 200, SerializationException("synthetic parse failure"))
        assertEquals("Réponse Peugeot inattendue. (maintenance · HTTP 200)", carFetchMessage(parse, debug = true))
        val network = CarReadFailure(CarReadStage.TokenRefresh, null, IOException("synthetic network failure"))
        assertEquals("Pas de connexion au service Peugeot.", carFetchMessage(network, debug = false))
        assertEquals("Pas de connexion au service Peugeot. (token refresh)", carFetchMessage(network, debug = true))
    }
}
