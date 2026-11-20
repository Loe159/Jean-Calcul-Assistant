package io.github.mangi.eta.agent.device.notifications.remote

import android.content.Context
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.SyncState
import java.io.IOException
import java.time.ZoneId
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** End-to-end phone-side pipeline against a fake homelab (OkHttp interceptor). Synthetic data only. */
@RunWith(RobolectricTestRunner::class)
class MastraNotificationSyncTest {
    private lateinit var context: Context
    private lateinit var repository: NotificationHistoryRepository
    private val secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (it * 7).toByte() })
    private val pairing = MastraPairing("https://homelab.test", "jc_test_device_01", secret)
    private val requests = mutableListOf<Pair<String, JSONObject>>()
    private val dismissed = mutableListOf<NotificationHistoryRepository.RemoteTarget>()
    private var responder: (String, JSONObject) -> Pair<Int, String> = { _, _ -> 500 to "{}" }
    private var offline = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("eta_notification_history.db")
        repository = NotificationHistoryRepository(context)
    }

    @After
    fun tearDown() { context.deleteDatabase("eta_notification_history.db") }

    private fun sync(consent: Boolean = true): MastraNotificationSync {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (offline) throw IOException("offline")
            val req = chain.request()
            val raw = Buffer().also { req.body?.writeTo(it) }.readUtf8()
            // Every request is signed exactly like the server verifies it.
            val expected = MastraNotificationClient.sign(secret, req.method, req.url.encodedPath,
                req.header("X-JC-Timestamp")!!, req.header("X-JC-Nonce")!!, raw)
            assertEquals(expected, req.header("X-JC-Signature"))
            val body = if (raw.isEmpty()) JSONObject() else JSONObject(raw)
            requests += req.url.encodedPath to body
            val (code, text) = responder(req.url.encodedPath, body)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(text.toResponseBody("application/json".toMediaType())).build()
        }).build()
        return MastraNotificationSync(context, repository, client = { MastraNotificationClient(it, http) },
            dismisser = { dismissed += it }, zone = ZoneId.of("Europe/Paris"),
            pairingProvider = { pairing }, learningConsent = { consent }, status = {})
    }

    private fun record(key: String, text: String, postedAt: Long = System.currentTimeMillis(), fingerprint: String = "fp-$key-$text") =
        repository.record(key, "com.snapchat.android", "Snapchat", text, null, postedAt, "suggestions", "msg",
            fingerprint, NotificationHistoryRepository.StructuralMeta(appLabel = "Snapchat"), queueForRemote = true)

    /** Echoes a decision for every received notification, computed by [decide]. */
    private fun decideAll(decide: (JSONObject) -> JSONObject) { responder = { path, body ->
        if (path.endsWith("/analyze")) {
            val notifications = body.getJSONArray("notifications")
            val decisions = JSONArray()
            for (i in 0 until notifications.length()) decisions.put(decide(notifications.getJSONObject(i)))
            200 to JSONObject().put("schemaVersion", 1).put("batchId", body.getString("batchId"))
                .put("decisions", decisions).put("errors", JSONArray()).toString()
        } else if (path.endsWith("/feedback")) {
            val events = body.getJSONArray("events")
            200 to JSONObject().put("schemaVersion", 1).put("batchId", body.getString("batchId"))
                .put("accepted", JSONArray((0 until events.length()).map { events.getJSONObject(it).getString("eventId") }))
                .put("duplicates", JSONArray()).put("discarded", JSONArray()).put("errors", JSONArray())
                .put("policyVersion", "rules+p1").toString()
        } else 404 to "{}"
    } }

    private fun decision(n: JSONObject, priority: String, action: String) = JSONObject()
        .put("id", n.getString("id")).put("revision", n.getInt("revision")).put("contentHash", n.getString("contentHash"))
        .put("topic", "social").put("subtype", "friend_suggestion").put("priority", priority).put("interest", "UNWANTED")
        .put("recommendedAndroidAction", action).put("protected", false).put("reason", "Suggestion sociale")
        .put("uncertainty", "LOW").put("source", "RULE").put("ruleId", "social.suggestion").put("actions", JSONArray())
        .put("policyVersion", "rules+p0").put("modelVersion", JSONObject.NULL).put("decidedAt", "2026-11-19T18:30:06+01:00")

    @Test
    fun `homelab unreachable keeps history and never removes anything`() {
        record("a", "Suggestion d'ami : découvrez Paul")
        offline = true
        assertEquals(MastraNotificationSync.Outcome.RETRY, sync().run())
        val entry = repository.recent().single()
        assertEquals(SyncState.ERROR, entry.syncState)
        assertEquals(NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR, entry.state)
        assertTrue(dismissed.isEmpty())
        // Back online: the same event is retried and decided.
        offline = false
        decideAll { decision(it, "LOW", "DISMISS") }
        assertEquals(MastraNotificationSync.Outcome.DONE, sync().run())
        assertEquals("LOW", repository.recent().single().remotePriority)
        assertEquals(1, dismissed.size)
    }

    @Test
    fun `privacy filter is applied before sending and identifiers are opaque`() {
        record("otp", "Votre code de vérification est 482913")
        decideAll { decision(it, "NOW", "KEEP") }
        sync().run()
        val sent = requests.single { it.first.endsWith("/analyze") }.second.getJSONArray("notifications").getJSONObject(0)
        assertTrue(!sent.toString().contains("482913"))
        assertEquals("NUMERIC_CODE", sent.getJSONArray("redactions").getString(0))
        assertTrue(!sent.toString().contains("\"otp\"")) // the Android key never leaves the phone
        assertTrue(sent.getString("id").startsWith("n_"))
        assertTrue(dismissed.isEmpty())
    }

    @Test
    fun `late decision for replaced content never triggers a removal`() {
        val t = System.currentTimeMillis()
        record("k", "Suggestion d'ami : découvrez Paul", t)
        decideAll { n ->
            if (n.getInt("revision") == 1) {
                // The notification is updated while the homelab is deciding on the old content.
                record("k", "Léa vous a envoyé un message", t + 1000, fingerprint = "fp-new")
                decision(n, "LOW", "DISMISS")
            } else decision(n, "NOW", "KEEP")
        }
        sync().run()
        // The DISMISS for revision 1 is ignored; revision 2 is analysed on its own.
        assertTrue(dismissed.isEmpty())
        val current = repository.recent().single()
        assertEquals("Léa vous a envoyé un message", current.text)
        assertEquals("NOW", current.remotePriority)
    }

    @Test
    fun `inconsistent NOW plus DISMISS decision is rejected on the phone`() {
        record("x", "Suggestion d'ami : découvrez Paul")
        decideAll { decision(it, "NOW", "DISMISS") }
        sync().run()
        assertTrue(dismissed.isEmpty())
        assertEquals(SyncState.REJECTED, repository.recent().single().syncState)
    }

    @Test
    fun `same notification replayed by Android is not sent twice`() {
        val t = System.currentTimeMillis()
        record("r", "Suggestion d'ami : découvrez Paul", t)
        decideAll { decision(it, "LOW", "KEEP") }
        sync().run()
        assertEquals(false, record("r", "Suggestion d'ami : découvrez Paul", t))
        sync().run()
        assertEquals(1, requests.count { it.first.endsWith("/analyze") })
        assertEquals("LOW", repository.recent().single().remotePriority)
    }

    @Test
    fun `feedback reaches the homelab once, with consent flag and correction`() {
        val t = System.currentTimeMillis()
        record("f", "Suggestion d'ami : découvrez Paul", t)
        decideAll { decision(it, "LOW", "KEEP") }
        sync(consent = true).run()
        assertTrue(repository.feedback("f", t, NotificationUserFeedback.PRIORITY_CORRECTED, correctedPriority = "TODAY"))
        assertEquals(false, repository.feedback("f", t, NotificationUserFeedback.PRIORITY_CORRECTED, correctedPriority = "TODAY"))
        sync(consent = true).run()
        val fb = requests.filter { it.first.endsWith("/feedback") }
        assertEquals(1, fb.size)
        assertEquals(true, fb.single().second.getBoolean("learningConsent"))
        val event = fb.single().second.getJSONArray("events").getJSONObject(0)
        assertEquals("PRIORITY_CORRECTION", event.getString("type"))
        assertEquals("TODAY", event.getString("correctedPriority"))
        assertEquals("TODAY", repository.recent().single().userPriority)
        assertTrue(repository.pendingFeedback().isEmpty())
    }

    @Test
    fun `Android removal state is recorded only from the listener callback`() {
        val t = System.currentTimeMillis()
        record("d", "Suggestion d'ami : découvrez Paul", t)
        decideAll { decision(it, "LOW", "DISMISS") }
        sync().run()
        val before = repository.recent().single()
        assertEquals(NotificationHistoryRepository.DecisionState.PROPOSED, before.state)
        assertEquals(NotificationHistoryRepository.AndroidActionState.NONE, before.androidActionState)
        repository.setAndroidActionState("d", t, NotificationHistoryRepository.AndroidActionState.CONFIRMED)
        assertEquals(NotificationHistoryRepository.DecisionState.DISMISSED, repository.recent().single().state)
    }
}
