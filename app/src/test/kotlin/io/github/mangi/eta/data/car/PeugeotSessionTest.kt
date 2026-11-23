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
    private val profileUrl = "https://microservices.mym.awsmpsa.com/me/v1/user?language=fr".toHttpUrl()
    private var now = Instant.parse("2026-11-21T12:00:00Z")

    @Test
    fun callbackVerifiesStateAndUsesPkceWithBasicAndForm() = runBlocking {
        val attempt = PeugeotLogin.create(parameters, usePkce = true)
        val state = attempt.url.queryParameter("state")!!
        assertTrue(attempt.acceptsState(state))
        assertFalse(attempt.acceptsState("invalid"))
        assertEquals("openid profile", attempt.url.queryParameter("scope"))
        assertEquals("fr-FR", attempt.url.queryParameter("locale"))
        assertEquals("S256", attempt.url.queryParameter("code_challenge_method"))
        val store = MemoryStore()
        val calls = AtomicInteger()
        val http = transport { request ->
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
        }
        val session = PeugeotSession(store, now = { now })
        val rejected = runCatching { session.connect(attempt, "invalid", "example-code", http) }.exceptionOrNull()
        assertTrue(rejected is IOException)
        assertEquals(0, calls.get())
        session.connect(attempt, state, "example-code", http)
        assertEquals("refresh-one", store.record.refreshToken)
        assertEquals(now.plusSeconds(3600).epochSecond, store.record.expiresAt)
    }

    @Test
    fun pkce400IsReportedWithoutAutomaticDowngrade() = runBlocking {
        val attempt = PeugeotLogin.create(parameters, usePkce = true)
        val calls = AtomicInteger()
        val http = transport { request ->
            calls.incrementAndGet()
            request.response(400, """{"error":"invalid_request"}""")
        }
        val session = PeugeotSession(MemoryStore())
        val error = runCatching { session.connect(attempt, attempt.url.queryParameter("state"), "example-code", http) }.exceptionOrNull()
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
        val http = transport { request ->
            assertNull(request.form()["code_verifier"])
            request.response(200, token("access-example", "refresh-example"))
        }
        PeugeotSession(MemoryStore()).connect(attempt, state, "example-code", http)
    }

    @Test
    fun parallelReadsShareBothTokensWithTheExactFrenchApiContract() = runBlocking {
        val refreshes = AtomicInteger()
        val exchanges = AtomicInteger()
        val store = MemoryStore(PeugeotSessionRecord("refresh-initial", vehicleId = "example-car", fuel = CarFuel.SP98))
        val http = transport { request ->
            if (request.url.host == "idpcvs.peugeot.com") {
                val count = refreshes.incrementAndGet()
                assertEquals("refresh_token", request.form()["grant_type"])
                assertEquals(if (count == 1) "refresh-initial" else "refresh-1", request.form()["refresh_token"])
                request.response(200, token("access-$count", "refresh-$count"))
            } else {
                assertEquals("microservices.mym.awsmpsa.com", request.url.host)
                assertEquals("GET", request.method)
                assertEquals("fr", request.url.queryParameter("language"))
                assertEquals("okhttp/4.8.0", request.header("User-Agent"))
                assertEquals("1.55.1", request.header("Version"))
                assertEquals("App-Android", request.header("Source-Agent"))
                assertNull(request.header("Authorization"))
                when (request.url.encodedPath) {
                    "/session/v2/accesstoken" -> {
                        assertEquals("site_code=AP_FR_ESP&language=fr", request.url.query)
                        assertEquals("access-${refreshes.get()}", request.headers.toMultimap()["cvs-token"]?.single())
                        assertNull(request.header("mym-access-token"))
                        request.response(200, mymToken(exchanges.incrementAndGet()))
                    }
                    "/me/v1/user" -> {
                        assertEquals(profileUrl, request.url)
                        assertEquals("mym-${exchanges.get()}", request.headers.toMultimap()["mym-access-token"]?.single())
                        assertNull(request.header("cvs-token"))
                        request.response(200, """{"success":{"selected_vehicle":{"short_label":"208 fictive"}}}""")
                    }
                    else -> error("Unexpected API path")
                }
            }
        }
        val session = PeugeotSession(store, now = { now })
        val one = async { session.profile(parameters, http) }
        val two = async { session.profile(parameters, http) }
        assertEquals("208 fictive", one.await().selected?.data?.label)
        assertEquals("208 fictive", two.await().selected?.data?.label)
        assertEquals(1, refreshes.get())
        assertEquals(1, exchanges.get())
        now = now.plusSeconds(3299)
        session.profile(parameters, http)
        assertEquals(1, refreshes.get())
        now = now.plusSeconds(1)
        session.profile(parameters, http)
        assertEquals(2, refreshes.get())
        assertEquals(2, exchanges.get())
        assertEquals("refresh-2", store.record.refreshToken)
        assertEquals("example-car", store.record.vehicleId)
        assertEquals(CarFuel.SP98, store.record.fuel)
    }

    @Test
    fun unauthorizedProfileRenewsMymTokenOnceWithoutRefreshingOAuth() = runBlocking {
        val attempts = AtomicInteger()
        val refreshes = AtomicInteger()
        val exchanges = AtomicInteger()
        val http = transport { request ->
            when {
                request.method == "POST" -> {
                    refreshes.incrementAndGet()
                    request.response(200, token("access-one"))
                }
                request.url.encodedPath == "/session/v2/accesstoken" ->
                    request.response(200, mymToken(exchanges.incrementAndGet()))
                else -> {
                    assertEquals("mym-${attempts.incrementAndGet()}", request.header("mym-access-token"))
                    request.response(401, "{}")
                }
            }
        }
        val session = PeugeotSession(MemoryStore(PeugeotSessionRecord("refresh-initial")), now = { now })
        val error = runCatching { session.profile(parameters, http) }.exceptionOrNull() as CarReadFailure
        assertEquals(401, error.status)
        assertEquals(CarReadStage.Profile, error.stage)
        assertEquals(1, refreshes.get())
        assertEquals(2, exchanges.get())
        assertEquals(2, attempts.get())
    }

    @Test
    fun invalidGrantWhileRetryingClearsTokensAndRequiresReconnection() = runBlocking {
        val refreshes = AtomicInteger()
        val attempts = AtomicInteger()
        val store = MemoryStore(PeugeotSessionRecord("refresh-initial", vehicleId = "example-car"))
        val warnings = mutableListOf<String>()
        val http = transport(warn = { warnings += it }, logDetails = false) { request ->
            if (request.method == "POST") {
                if (refreshes.incrementAndGet() == 1) request.response(200, token("access-one"))
                else request.response(400, """{"error":"invalid_grant","refresh_token":"refresh-initial"}""")
            } else {
                attempts.incrementAndGet()
                request.response(401, "{}")
            }
        }
        val session = PeugeotSession(store)
        repeat(2) {
            val error = runCatching { session.profile(parameters, http) }.exceptionOrNull()
            assertTrue(error is PeugeotReauthRequired)
        }
        assertEquals(2, refreshes.get())
        assertEquals(1, attempts.get())
        if (BuildConfig.DEBUG) {
            assertEquals(listOf("session MYM: HTTP 401", "token refresh: HTTP 400"), warnings)
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
        val request = Request.Builder().url(profileUrl).build()
        assertEquals(429, http.execute(request).status)
        now = now.plusSeconds(119)
        assertEquals(429, (runCatching { http.execute(request) }.exceptionOrNull() as? CarHttpFailure)?.status)
        assertEquals(1, attempts.get())
        now = now.plusSeconds(1)
        assertEquals(200, http.execute(request).status)
    }

    private fun transport(
        warn: (String) -> Unit = {},
        logDetails: Boolean = true,
        respond: (Request) -> Response,
    ): CarHttp = CarHttp(
        OkHttpClient.Builder().addInterceptor { chain -> respond(chain.request()) }.build(),
        now = { now },
        warn = warn,
        logDetails = logDetails,
    )

    private fun token(access: String, refresh: String? = null): String =
        """{"access_token":"$access","expires_in":3600""" + (refresh?.let { ""","refresh_token":"$it"""" } ?: "") + "}"

    private fun mymToken(id: Int): String =
        """{"success":{"mym_access_token":"mym-$id","cat_ticket":"synthetic-ticket"}}"""

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
