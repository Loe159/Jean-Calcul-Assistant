package io.github.mangi.eta.agent.device.notifications.remote

import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun briefSummaryFixture(name: String): String =
    requireNotNull(MastraBriefSummaryContractTest::class.java.getResource("/brief-summary-v1/$name.json")).readText()

class MastraBriefSummaryContractTest {
    private val contract = MastraBriefSummaryContract

    @Test
    fun `server request fixture round trips with only the allowed fields`() {
        val fixture = briefSummaryFixture("request")
        val request = contract.decodeRequest(fixture)
        assertEquals(MastraBriefMoment.MORNING, request.moment)
        assertEquals(MastraBriefCounts(2, 0, 0), request.notificationCounts)
        assertEquals("2026-11-20T09:10:00+01:00", request.deviceTime)
        assertEquals(Json.parseToJsonElement(fixture), Json.parseToJsonElement(contract.encodeRequest(request)))
    }

    @Test
    fun `request decoder requires the version and only accepts aggregate integer counts`() {
        val fixture = briefSummaryFixture("request")
        val invalid = listOf(
            fixture.replace("\"schema_version\": 1,", ""),
            fixture.replace("\"now\": 2", "\"now\": \"2\""),
            fixture.replace("\"now\": 2", "\"now\": -2"),
            fixture.replace("\"now\": 2", "\"now\": 2.5"),
            fixture.replace("\"low\": 0", "\"low\": 0, \"title\": \"synthétique\""),
            fixture.replace("\"locale\": \"fr-FR\"", "\"locale\": \"fr_FR\""),
            fixture.replace("\"time_zone\": \"Europe/Paris\"", "\"time_zone\": \"+01:00\""),
            fixture.replace("\"moment\": \"MORNING\"", "\"moment\": \"MORNING\", \"user_first_name\": null"),
        )
        invalid.forEach { text ->
            assertThrows(RuntimeException::class.java) { contract.decodeRequest(text) }
        }
    }

    @Test
    fun `server response preserves emphases and punctuation`() {
        val response = contract.decodeResponse(briefSummaryFixture("response"))
        assertEquals(
            "Tu as 2 notifications prioritaires et Dentiste à 9 h 30.",
            response.segments.joinToString("") { it.text },
        )
        assertEquals(
            listOf("2 notifications prioritaires", "Dentiste à 9 h 30"),
            response.segments.filter { it.emphasis }.map { it.text },
        )
        assertEquals(listOf(MastraBriefSource.CALENDAR, MastraBriefSource.NOTIFICATIONS), response.sources)
    }

    @Test
    fun `response decoder rejects invalid v1 data`() {
        val fixture = briefSummaryFixture("response")
        val invalid = listOf(
            fixture.replace("\"schema_version\": 1", "\"schema_version\": 2"),
            fixture.replace("\"schema_version\": 1", "\"schema_version\": \"1\""),
            fixture.replace("\"schema_version\": 1,", ""),
            fixture.replace("\"emphasis\": true", "\"emphasis\": \"true\""),
            fixture.replace("\"degraded\": false", "\"degraded\": \"false\""),
            fixture.replace("\"Tu as \"", "\"\""),
            fixture.replace("\"Dentiste à 9 h 30\"", "\" \""),
            fixture.replace("\"Dentiste à 9 h 30\"", "\"**Dentiste**\""),
            fixture.replace("\"Dentiste à 9 h 30\"", "\"Rendez-vous 😀\""),
            fixture.replace("\"Dentiste à 9 h 30\"", "\"Rendez-vous\\u202E\""),
            fixture.replace("\"Dentiste à 9 h 30\"", "\"Rendez-vous\\uD800\""),
            fixture.replace("\"Dentiste à 9 h 30\"", "\"" + "é".repeat(221) + "\""),
            fixture.replace(Regex("\"segments\": \\[.*?],", RegexOption.DOT_MATCHES_ALL), "\"segments\": [],"),
            fixture.replace("08:30:00", "08:10:00"),
            fixture.replace("08:30:00", "08:41:00"),
            fixture.replace("08:30:00.000Z", "08:30:00.000"),
            fixture.replace("9a7c03ff-cfb1-4ece-a226-a0b039dfab9e", "invalid"),
            fixture.replace("\"calendar\"", "\"notifications\""),
            fixture.replace("\"calendar\"", "\"outlook\""),
            fixture.replace("\"calendar\", \"notifications\"", ""),
            fixture.replace("\"degraded\": false", "\"degraded\": false, \"unexpected\": true"),
        )
        invalid.forEachIndexed { index, text ->
            assertThrows("invalid response #$index", RuntimeException::class.java) { contract.decodeResponse(text) }
        }
    }

    @Test
    fun `response length limit applies to concatenated segments`() {
        val response = contract.decodeResponse(briefSummaryFixture("response"))
        val exactLimit = response.copy(segments = listOf(
            MastraBriefSegment("é".repeat(100), false),
            MastraBriefSegment("é".repeat(120), true),
        ))
        contract.validate(exactLimit)
        assertThrows(IllegalArgumentException::class.java) {
            contract.validate(exactLimit.copy(segments = exactLimit.segments + MastraBriefSegment(".", false)))
        }
    }

    @Test
    fun `signed summary POST uses the shared Mastra request transport`() = runBlocking<Unit> {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
        val pairing = MastraPairing("https://homelab.example", "jc_fixture", secret)
        val timestamp = 1_795_162_200_000L
        val input = contract.decodeRequest(briefSummaryFixture("request"))
        val assertions = mutableListOf<Boolean>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { requireNotNull(request.body).writeTo(it) }.readUtf8()
            val nonce = requireNotNull(request.header("X-JC-Nonce"))
            assertions.addAll(listOf(
                request.method == "POST",
                request.url.encodedPath == contract.PATH,
                request.header("X-JC-Device") == pairing.deviceId,
                request.header("X-JC-Timestamp") == timestamp.toString(),
                Regex("[a-f0-9]{32}").matches(nonce),
                request.header("X-JC-Signature") ==
                    MastraNotificationClient.sign(secret, "POST", contract.PATH, timestamp.toString(), nonce, body),
                input == contract.decodeRequest(body),
            ))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(briefSummaryFixture("response").toResponseBody()).build()
        }.build()

        val result = MastraNotificationClient(pairing, http) { timestamp }.briefSummary(input)

        assertEquals(contract.decodeResponse(briefSummaryFixture("response")), result)
        assertEquals(7, assertions.size)
        assertTrue(assertions.all { it })
    }
}
