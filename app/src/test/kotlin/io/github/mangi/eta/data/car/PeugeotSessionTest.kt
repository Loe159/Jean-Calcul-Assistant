package io.github.mangi.eta.data.car

import io.github.mangi.eta.BuildConfig
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeugeotSessionTest {
    private val parameters = MyPeugeotParameters("example-client", "fake-example")
    private val vehicles = "https://api.groupe-psa.com/connectedcar/v4/user/vehicles?extension=branding".toHttpUrl()
    private var now = Instant.parse("2026-11-21T12:00:00Z")

    @Test
    fun callbackVerifiesStateAndUsesPkceWithBasicAndForm() = runBlocking {
        val attempt = PeugeotLogin.create(parameters, usePkce = true)
        val state = attempt.url.queryParameter("state")!!
        assertTrue(attempt.acceptsState(state))
        assertFalse(attempt.acceptsState("invalid"))
        assertEquals("openid profile", attempt.url.queryParameter("scope"))
        assertEquals("S256", attempt.url.queryParameter("code_challenge_method"))
        val store = MemoryStore()
        val calls = AtomicInteger()
        val session = PeugeotSession(transport { request ->
            calls.incrementAndGet()
            assertEquals("idpcvs.peugeot.com", request.url.host)
            assertEquals("/am/oauth2/access_token", request.url.encodedPath)
            assertEquals("POST", request.method)
            assertEquals(Credentials.basic("example-client", "fake-example"), request.header("Authorization"))
            assertEquals(
                mapOf("grant_type" to "authorization_code", "code" to "example-code",
                    "redirect_uri" to peugeotRedirectUri, "code_verifier" to attempt.verifier),
                request.form(),
            )
            request.response(200, token("access-one", "refresh-one"))
        }, store, now = { now })
        val rejected = runCatching { session.connect(attempt, "invalid", "example-code") }.exceptionOrNull()
        assertTrue(rejected is IOException)
        assertEquals(0, calls.get())
        session.connect(attempt, state, "example-code")
        assertEquals("refresh-one", store.record.refreshToken)
        assertEquals(now.plusSeconds(3600).epochSecond, store.record.expiresAt)
    }

    @Test
    fun pkce400IsReportedWithoutAutomaticDowngrade() = runBlocking {
        val attempt = PeugeotLogin.create(parameters, usePkce = true)
        val calls = AtomicInteger()
        val session = PeugeotSession(transport { request ->
            calls.incrementAndGet()
            request.response(400, """{"error":"invalid_request"}""")
        }, MemoryStore())
        val error = runCatching { session.connect(attempt, attempt.url.queryParameter("state"), "example-code") }.exceptionOrNull()
        assertTrue(error is PeugeotExchangeRejected)
        assertEquals(1, calls.get())
    }

    @Test
    fun disabledPkceKeepsStateAndOmitsChallengeAndVerifier() = runBlocking {
        val attempt = PeugeotLogin.create(parameters, usePkce = false)
        val state = attempt.url.queryParameter("state")
        assertTrue(attempt.acceptsState(state))
        assertNull(attempt.url.queryParameter("code_challenge"))
        assertNull(attempt.url.queryParameter("code_challenge_method"))
        val session = PeugeotSession(transport { request ->
            assertNull(request.form()["code_verifier"])
            request.response(200, token("access-example", "refresh-example"))
        }, MemoryStore())
        session.connect(attempt, state, "example-code")
    }

    @Test
    fun parallelReadsReuseTheRenewedTokenAndAnticipateExpiry() = runBlocking {
        val refreshes = AtomicInteger()
        val store = MemoryStore(PeugeotSessionRecord("refresh-initial", vehicleId = "example-car", fuel = CarFuel.SP98))
        val session = PeugeotSession(transport { request ->
            if (request.url.host == "idpcvs.peugeot.com") {
                val count = refreshes.incrementAndGet()
                assertEquals("refresh_token", request.form()["grant_type"])
                assertEquals(if (count == 1) "refresh-initial" else "refresh-1", request.form()["refresh_token"])
                request.response(200, token("access-$count", "refresh-$count"))
            } else {
                assertEquals("GET", request.method)
                assertEquals("fr-FR", request.url.queryParameter("locale"))
                assertEquals("example-client", request.url.queryParameter("client_id"))
                assertEquals("branding", request.url.queryParameter("extension"))
                assertEquals("clientsB2CPeugeot", request.header("x-introspect-realm"))
                assertEquals("Bearer access-${refreshes.get()}", request.header("Authorization"))
                request.response(200, "{}")
            }
        }, store, now = { now })
        val one = async { session.readVehicles() }
        val two = async { session.readVehicles() }
        assertEquals("{}", one.await())
        assertEquals("{}", two.await())
        assertEquals(1, refreshes.get())
        now = now.plusSeconds(3299)
        session.readVehicles()
        assertEquals(1, refreshes.get())
        now = now.plusSeconds(1)
        session.readVehicles()
        assertEquals(2, refreshes.get())
        assertEquals("refresh-2", store.record.refreshToken)
        assertEquals("example-car", store.record.vehicleId)
        assertEquals(CarFuel.SP98, store.record.fuel)
    }

    @Test
    fun unauthorizedGetRetriesOnceAndStopsAfterSecond401() = runBlocking {
        val attempts = AtomicInteger()
        val refreshes = AtomicInteger()
        val session = PeugeotSession(transport { request ->
            if (request.method == "POST") {
                val count = refreshes.incrementAndGet()
                request.response(200, token("access-$count"))
            } else {
                assertEquals("application/hal+json", request.header("Accept"))
                assertEquals("Bearer access-${attempts.incrementAndGet()}", request.header("Authorization"))
                request.response(401, "{}")
            }
        }, MemoryStore(PeugeotSessionRecord("refresh-initial")), now = { now })
        val error = runCatching { session.readVehicles() }.exceptionOrNull() as CarReadFailure
        assertEquals(401, error.status)
        assertEquals(CarReadStage.Vehicles, error.stage)
        assertEquals(2, refreshes.get())
        assertEquals(2, attempts.get())
    }

    @Test
    fun invalidGrantWhileRetryingClearsTokensAndRequiresReconnection() = runBlocking {
        val refreshes = AtomicInteger()
        val attempts = AtomicInteger()
        val store = MemoryStore(PeugeotSessionRecord("refresh-initial", vehicleId = "example-car"))
        val warnings = mutableListOf<String>()
        val session = PeugeotSession(transport(warn = { warnings += it }) { request ->
            if (request.method == "POST") {
                if (refreshes.incrementAndGet() == 1) request.response(200, token("access-one"))
                else request.response(400, """{"error":"invalid_grant","refresh_token":"refresh-initial"}""")
            } else {
                attempts.incrementAndGet()
                request.response(401, "{}")
            }
        }, store)
        repeat(2) {
            val error = runCatching { session.readVehicles() }.exceptionOrNull()
            assertTrue(error is PeugeotReauthRequired)
        }
        assertEquals(2, refreshes.get())
        assertEquals(1, attempts.get())
        if (BuildConfig.DEBUG) {
            assertEquals(2, warnings.size)
            assertTrue(warnings[0].contains("vehicles /connectedcar/v4/user/vehicles HTTP 401"))
            assertTrue(warnings[1].contains("token refresh /am/oauth2/access_token HTTP 400"))
            assertTrue(warnings[1].contains("invalid_grant"))
            assertFalse(warnings[1].contains("refresh-initial"))
        } else {
            assertTrue(warnings.isEmpty())
        }
        assertNull(store.record.refreshToken)
        assertNull(store.record.expiresAt)
        assertTrue(store.record.needsReauth)
        assertEquals("example-car", store.record.vehicleId)
        session.disconnect()
        assertFalse(store.record.needsReauth)
        assertNull(store.record.vehicleId)
    }

    @Test
    fun rateLimitHonorsRetryAfterAndBlocksAdditionalRequestsOnThatOrigin() = runBlocking {
        val attempts = AtomicInteger()
        val http = transport { request ->
            if (attempts.incrementAndGet() == 1) request.response(429, "{}", retry = "120")
            else request.response(200, "{}")
        }
        val request = Request.Builder().url(vehicles).build()
        assertEquals(429, http.execute(request).status)
        now = now.plusSeconds(119)
        assertEquals(429, (runCatching { http.execute(request) }.exceptionOrNull() as? CarHttpFailure)?.status)
        assertEquals(1, attempts.get())
        now = now.plusSeconds(1)
        assertEquals(200, http.execute(request).status)
    }

    private suspend fun PeugeotSession.readVehicles(): String = get(parameters, vehicles, CarReadStage.Vehicles) { it }

    private fun transport(warn: (String) -> Unit = {}, respond: (Request) -> Response): CarHttp = CarHttp(
        OkHttpClient.Builder().addInterceptor { chain -> respond(chain.request()) }.build(),
        now = { now },
        warn = warn,
    )

    private fun token(access: String, refresh: String? = null): String =
        """{"access_token":"$access","expires_in":3600""" + (refresh?.let { ""","refresh_token":"$it"""" } ?: "") + "}"

    private fun Request.form(): Map<String, String> {
        val form = body as FormBody
        return (0 until form.size).associate { form.name(it) to form.value(it) }
    }

    private fun Request.response(status: Int, json: String, retry: String? = null): Response =
        Response.Builder().request(this).protocol(Protocol.HTTP_1_1)
            .code(status).message("Synthetic response").body(json.toResponseBody())
            .apply { if (retry != null) header("Retry-After", retry) }.build()

    private class MemoryStore(var record: PeugeotSessionRecord = PeugeotSessionRecord()) : PeugeotSessionStorage {
        override fun read(): PeugeotSessionRecord = record
        override fun save(record: PeugeotSessionRecord) { this.record = record }
        override fun clear() { record = PeugeotSessionRecord() }
    }
}
