package io.github.mangi.eta.agent.device.notifications.remote

import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.ui.screens.home.priorityForBrief
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same fixtures as homelab-ai/tests/fixtures/notification-contract/v1 (identical copies). */
class MastraContractTest {
    private fun fixture(name: String) =
        requireNotNull(javaClass.classLoader!!.getResource("notification-contract/v1/$name")).readText()

    @Test
    fun `shared v1 fixtures decode with the Kotlin mirror`() {
        val json = MastraContract.json
        val request = json.decodeFromString(AnalyzeRequest.serializer(), fixture("analyze-request.json"))
        assertEquals(2, request.notifications.size)
        assertEquals("com.snapchat.android", request.notifications.first().packageName)
        val response = json.decodeFromString(AnalyzeResponse.serializer(), fixture("analyze-response.json"))
        assertEquals(listOf(Priority.LOW, Priority.NOW), response.decisions.map { it.priority })
        assertTrue(response.decisions.all(MastraContract::validate))
        assertEquals(ActionType.CREATE_TASK, response.decisions[1].actions.first().type)
        assertEquals("INVALID", response.errors.single().code)
        json.decodeFromString(FeedbackRequest.serializer(), fixture("feedback-request.json"))
        json.decodeFromString(FeedbackResponse.serializer(), fixture("feedback-response.json"))
        json.decodeFromString(HealthResponse.serializer(), fixture("health-response.json"))
        val action = json.decodeFromString(ActionRecord.serializer(), fixture("action-record.json"))
        assertEquals("AWAITING_APPROVAL", action.state)
    }

    @Test
    fun `encoded request round-trips the server field names`() {
        val request = MastraContract.json.decodeFromString(AnalyzeRequest.serializer(), fixture("analyze-request.json"))
        val encoded = Json.parseToJsonElement(MastraContract.json.encodeToString(AnalyzeRequest.serializer(), request))
        val first = encoded.jsonObject["notifications"]!!.let { (it as kotlinx.serialization.json.JsonArray)[0].jsonObject }
        assertEquals("com.snapchat.android", first["package"]!!.jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(fixture("analyze-request.json")), encoded)
    }

    @Test
    fun `HMAC signature matches the shared test vector`() {
        val v = Json.parseToJsonElement(fixture("signature.json")).jsonObject
        fun s(k: String) = v[k]!!.jsonPrimitive.content
        assertEquals(s("canonical"), MastraNotificationClient.canonical(s("method"), s("path"), s("timestamp"), s("nonce"), s("body")))
        assertEquals(s("signature"), MastraNotificationClient.sign(s("secret"), s("method"), s("path"), s("timestamp"), s("nonce"), s("body")))
    }

    @Test
    fun `a decision removing NOW is rejected by the phone`() {
        val ok = MastraContract.json.decodeFromString(AnalyzeResponse.serializer(), fixture("analyze-response.json")).decisions[0]
        assertTrue(MastraContract.validate(ok))
        assertFalse(MastraContract.validate(ok.copy(priority = Priority.NOW)))
        assertTrue(MastraContract.validate(ok.copy(protected = true)))
        assertFalse(MastraContract.validate(ok.copy(topic = "unknown")))
        assertFalse(MastraContract.validate(ok.copy(contentHash = "abc")))
    }

    @Test
    fun `pairing string requires HTTPS outside loopback and a 32 byte secret`() {
        val secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })
        val good = MastraConnection.parsePairing(
            "jeancalcul-mastra:v1?url=https%3A%2F%2Fhomelab.example.ts.net&device=jc_device_0001&secret=$secret")
        assertNotNull(good)
        assertEquals("https://homelab.example.ts.net", good!!.baseUrl)
        assertNull(MastraConnection.parsePairing("jeancalcul-mastra:v1?url=http%3A%2F%2F192.168.1.2&device=jc_device_0001&secret=$secret"))
        assertNotNull(MastraConnection.parsePairing("jeancalcul-mastra:v1?url=http%3A%2F%2F10.0.2.2%3A4111&device=jc_device_0001&secret=$secret"))
        assertNull(MastraConnection.parsePairing("jeancalcul-mastra:v1?url=https%3A%2F%2Fh.example&device=jc_device_0001&secret=c2hvcnQ"))
        assertNull(MastraConnection.parsePairing("https://homelab.example"))
        assertNull(MastraConnection.parsePairing("jeancalcul-mastra:v1?url=https%3A%2F%2Fh.example%2Fprefix&device=jc_device_0001&secret=$secret"))
        assertEquals("Europe/Paris", MastraNotificationSync.contractZone(java.time.ZoneId.of("Europe/Paris")))
        assertEquals("UTC", MastraNotificationSync.contractZone(java.time.ZoneId.of("GMT+01:00")))
    }

    @Test
    fun `privacy filter masks codes but keeps dates, times and words`() {
        val f = NotificationPrivacyFilter.filter("Banque", "Votre code est 482 913. Valable 10 min, le 2026-11-26 à 14:30", null)
        assertTrue(f.redacted)
        assertFalse(f.content!!.contains("482"))
        assertTrue(f.content!!.contains("2026-11-26"))
        assertTrue(f.content!!.contains("14:30"))
        val plain = NotificationPrivacyFilter.filter("Snapchat", "Suggestion d'ami : découvrez Paul", null)
        assertFalse(plain.redacted)
        assertEquals("Suggestion d'ami : découvrez Paul", plain.content)
    }

    @Test
    fun `Now Brief priority comes from Mastra or the user, never from local rules`() {
        val base = NotificationHistoryRepository.RecentEntry("k", "com.snapchat.android", "Léa", "Léa vous a envoyé un message", 1L)
        // Without a decision the entry waits in "Aujourd'hui", even if local regexes would say NOW.
        assertEquals(NotificationPriority.TODAY, priorityForBrief(base))
        assertEquals(NotificationPriority.LOW, priorityForBrief(base.copy(remotePriority = "LOW")))
        assertEquals(NotificationPriority.NOW, priorityForBrief(base.copy(remotePriority = "LOW", userPriority = "NOW")))
    }
}
