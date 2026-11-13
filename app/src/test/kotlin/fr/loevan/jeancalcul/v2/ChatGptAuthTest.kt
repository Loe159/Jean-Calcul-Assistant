package fr.loevan.jeancalcul.v2

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.Socket
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

internal class ChatGptFixture : java.io.Closeable {
    val server = MockWebServer()
    val key = RSAKeyGenerator(2048).keyID("fixture-key").generate()
    var signingKey = key
    val tokenCalls = AtomicInteger()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    var claims: (JWTClaimsSet.Builder) -> JWTClaimsSet.Builder = { it }
    var tokenError: Int? = null
    var tokenErrorCode = "invalid_grant"
    var scope = "openid email offline_access resource.invoke $PLAN_SCOPE"
    var revokeStatus = 200
    var responseBody = ""
    var clientId = "oaiapp_fixture"
    var subject = "fixture-subject"
    var nonce = "nonce"
    val oauth: ChatGptOAuth
    init {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverTls.sslSocketFactory(), false); server.start()
        val issuer = server.url("/").toString().trimEnd('/')
        oauth = ChatGptOAuth(ChatGptEndpoints(issuer, "$issuer/v1"), OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).followRedirects(false).build())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val value: JSONObject = when (request.path) {
                    "/.well-known/openid-configuration" -> JSONObject().put("issuer", issuer)
                        .put("jwks_uri", "$issuer/jwks").put("revocation_endpoint", "$issuer/revoke")
                    "/jwks" -> JSONObject().put("keys", JSONArray().put(JSONObject(key.toPublicJWK().toJSONString())))
                    "/api/accounts/oauth/token" -> {
                        val count = tokenCalls.incrementAndGet()
                        tokenError?.let { return MockResponse().setResponseCode(it).setBody(JSONObject().put("error", tokenErrorCode).toString()) }
                        val builder = JWTClaimsSet.Builder().issuer(issuer).audience(clientId).subject(subject)
                            .issueTime(Date()).expirationTime(Date(System.currentTimeMillis() + 3_600_000))
                            .claim("nonce", nonce).claim("email", "fixture@example.invalid")
                        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims(builder).build())
                        jwt.sign(RSASSASigner(signingKey))
                        JSONObject().put("access_token", "synthetic-access-$count").put("refresh_token", "synthetic-refresh-$count")
                            .put("id_token", jwt.serialize()).put("expires_in", 3600).put("scope", scope).put("token_type", "Bearer")
                    }
                    "/v1/models" -> JSONObject().put("models", JSONArray()
                        .put(JSONObject().put("slug", "fixture-model").put("display_name", "Fixture Model").put("visibility", "list"))
                        .put(JSONObject().put("slug", "hidden").put("display_name", "Hidden").put("visibility", "hidden")))
                    "/revoke" -> return MockResponse().setResponseCode(revokeStatus)
                    "/v1/responses" -> return MockResponse().setHeader("Content-Type", "text/event-stream").setBody(responseBody)
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())
            }
        }
    }
    fun attempt() = OAuthAttempt("state", nonce, "verifier", "http://127.0.0.1:1455/auth/callback", "urn:uuid:test")
    fun exchange() = oauth.exchange(attempt(), OAuthCode("synthetic-code", clientId))
    override fun close() { server.close() }
}
internal class ChatGptMemoryStore(var value: ChatGptRegistration = ChatGptRegistration()) : ChatGptStore {
    override fun load() = value
    override fun save(value: ChatGptRegistration) { this.value = value }
}

class ChatGptAuthTest {
    @Test fun pkceAndDynamicRegistrationUseDocumentedPublicClientParameters() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val attempt = OAuthAttempt("state", "nonce", verifier, "http://127.0.0.1:3210/auth/callback", "urn:uuid:test")
        val url = attempt.authorizationUrl(ChatGptEndpoints()).toHttpUrl()
        assertEquals("dynamic_agent_client", url.queryParameter("client_id"))
        assertEquals("Jean-Calcul", url.queryParameter("agent_name_hint"))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", url.queryParameter("code_challenge"))
        assertEquals(attempt.redirect, url.queryParameter("redirect_uri"))
        assertTrue(url.queryParameter("scope")!!.contains(PLAN_SCOPE))
        assertFalse(attempt.toString().contains(verifier))
        assertEquals("oaiapp_new", attempt.validateCallback((attempt.redirect + "?code=c&state=state&client_id=oaiapp_new").toHttpUrl()).clientId)
    }
    @Test fun callbackRejectsForgedExpiredDuplicateOrWrongClientValues() {
        val attempt = OAuthAttempt("state", "nonce", "verifier", "http://127.0.0.1:1455/auth/callback", "urn:uuid:test")
        for (query in listOf("code=c&state=wrong&client_id=oaiapp_x", "code=c&state=state", "code=c&code=d&state=state&client_id=oaiapp_x",
            "error=access_denied&state=state", "code=c&state=state&client_id=dynamic_agent_client")) {
            expect(RunError.CHATGPT_AUTH) { attempt.validateCallback((attempt.redirect + "?" + query).toHttpUrl()) }
        }
        expect(RunError.CHATGPT_AUTH) { attempt.validateCallback((attempt.redirect + "?code=c&state=state&client_id=oaiapp_x").toHttpUrl(), attempt.expiresAt) }
        val returning = attempt.copy(clientId = "oaiapp_saved")
        expect(RunError.CHATGPT_AUTH) { returning.validateCallback((attempt.redirect + "?code=c&state=state&client_id=oaiapp_other").toHttpUrl()) }
        val returningUrl = returning.authorizationUrl(ChatGptEndpoints()).toHttpUrl()
        assertNull(returningUrl.queryParameter("agent_name_hint"))
        assertEquals("oaiapp_saved", returning.validateCallback((attempt.redirect + "?code=c&state=state").toHttpUrl()).clientId)
    }
    @Test fun signaturesIssuerAudienceExpiryNonceAndIdentityAreRequired() = ChatGptFixture().use { fixture ->
        val valid = fixture.exchange()
        assertEquals("fixture-subject", valid.subject)
        assertEquals(setOf(PLAN_SCOPE), valid.tokens!!.scopes.intersect(setOf(PLAN_SCOPE)))
        for (change in listOf<(JWTClaimsSet.Builder) -> JWTClaimsSet.Builder>(
            { it.issuer("https://attacker.invalid") }, { it.audience("oaiapp_other") },
            { it.expirationTime(Date(1)) }, { it.claim("nonce", "wrong") }, { it.subject("") })) {
            fixture.claims = change
            expect(RunError.CHATGPT_AUTH) { fixture.exchange() }
        }
        fixture.claims = { it }
        fixture.signingKey = RSAKeyGenerator(2048).keyID(fixture.key.keyID).generate()
        expect(RunError.CHATGPT_AUTH) { fixture.exchange() }
        fixture.signingKey = fixture.key
        expect(RunError.CHATGPT_AUTH) { fixture.oauth.exchange(fixture.attempt().copy(expectedSubject = "other"), OAuthCode("code", fixture.clientId)) }
        val request = fixture.requests.first { it.path == "/api/accounts/oauth/token" }.body.clone().readUtf8()
        assertTrue(request.contains("client_id=oaiapp_fixture")); assertTrue(request.contains("code_verifier=verifier"))
        assertFalse(request.contains("client_secret"))
    }
    @Test fun registrationsStaySeparateAndNoModelIsInvented() = runBlocking {
        ChatGptFixture().use { fixture ->
            val store = ChatGptMemoryStore(); val manager = ChatGptAccounts(store, fixture.oauth)
            manager.complete(fixture.attempt(), OAuthCode("code", fixture.clientId))
            assertEquals("", manager.ui.value.model)
            assertEquals(listOf("fixture-model"), manager.ui.value.models.map { it.id })
            manager.selectModel("fixture-model")
            val first = store.value.accounts.single()
            fixture.clientId = "oaiapp_second"; fixture.subject = "second-subject"
            manager.complete(fixture.attempt(), OAuthCode("code", fixture.clientId))
            assertEquals(2, manager.ui.value.accounts.size)
            assertEquals(first, store.value.accounts.first())
            assertEquals("oaiapp_second", store.value.active)
            val host = store.value.hostId
            assertEquals(host, manager.attempt("http://127.0.0.1:1234/auth/callback", first.clientId).hostId)
            assertEquals(first.clientId, manager.attempt("http://127.0.0.1:1234/auth/callback", first.clientId).clientId)
            assertEquals(store.value, ChatGptRegistration.parse(store.value.json()))
        }
    }
    @Test fun identityWithoutPlanPermissionCannotRunInference() = runBlocking {
        ChatGptFixture().use { fixture ->
            fixture.scope = "openid email"
            val store = ChatGptMemoryStore(); val manager = ChatGptAccounts(store, fixture.oauth)
            try { manager.complete(fixture.attempt(), OAuthCode("code", fixture.clientId)); fail() }
            catch (failure: RunFailure) { assertEquals(RunError.CHATGPT_PERMISSION, failure.kind) }
            assertFalse(manager.ui.value.authorized)
            assertNotNull(store.value.accounts.single().tokens)
            assertEquals("consent", manager.attempt(fixture.attempt().redirect, fixture.clientId)
                .authorizationUrl(fixture.oauth.endpoints).toHttpUrl().queryParameter("prompt"))
            assertTrue(fixture.requests.none { it.path == "/v1/models" || it.path == "/v1/responses" })
        }
    }
    @Test fun rotatingRefreshIsSerializedAndSavedAtomically() = runBlocking {
        ChatGptFixture().use { fixture ->
            val account = fixture.exchange().let { it.copy(model = "fixture-model", tokens = it.tokens!!.copy(expiresAt = 1)) }
            val store = ChatGptMemoryStore(ChatGptRegistration(accounts = listOf(account), active = account.clientId, enabled = true))
            val manager = ChatGptAccounts(store, fixture.oauth)
            val first = async(Dispatchers.Default) { manager.credentials(account.clientId) }
            val second = async(Dispatchers.Default) { manager.credentials(account.clientId) }
            assertEquals(first.await().tokens, second.await().tokens)
            assertEquals(2, fixture.tokenCalls.get()) // one initial code exchange, one refresh
            assertEquals("synthetic-refresh-2", store.value.accounts.single().tokens!!.refresh)
        }
    }
    @Test fun onlyTerminalRefreshFailureClearsTokensAndSignOutRetainsRegistration() = runBlocking {
        for ((status, code, clears) in listOf(Triple(400, "invalid_grant", true), Triple(503, "temporary", false), Triple(400, "invalid_client", false))) {
            ChatGptFixture().use { fixture ->
                val account = fixture.exchange().let { it.copy(model = "fixture-model", tokens = it.tokens!!.copy(expiresAt = 1)) }
                val store = ChatGptMemoryStore(ChatGptRegistration(accounts = listOf(account), active = account.clientId, enabled = true))
                val manager = ChatGptAccounts(store, fixture.oauth)
                fixture.tokenError = status; fixture.tokenErrorCode = code
                try { manager.credentials(account.clientId); fail() } catch (_: RunFailure) { }
                assertEquals(clears, store.value.accounts.single().tokens == null)
                fixture.tokenError = null
                assertTrue(manager.signOut())
                assertEquals(account.clientId, store.value.accounts.single().clientId)
                assertNull(store.value.accounts.single().tokens)
                assertEquals(account.clientId, manager.attempt(fixture.attempt().redirect, account.clientId).clientId)
            }
        }
    }
    @Test fun failedRevocationClearsLocalTokensAndReportsUnconfirmedRemoteResult() = runBlocking {
        ChatGptFixture().use { fixture ->
            val account = fixture.exchange()
            val store = ChatGptMemoryStore(ChatGptRegistration(accounts = listOf(account), active = account.clientId, enabled = true))
            fixture.revokeStatus = 503
            assertFalse(ChatGptAccounts(store, fixture.oauth).signOut())
            assertNull(store.value.accounts.single().tokens)
            assertEquals(2, fixture.requests.count { it.path == "/revoke" })
        }
    }
    @Test fun realLoopbackListenerConsumesCallbackOnceWithoutEchoingSecrets() = runBlocking {
        LoopbackOAuth().use { listener ->
            val attempt = OAuthAttempt.create(listener.redirect, "urn:uuid:test")
            val awaiting = async(Dispatchers.IO) { listener.await(attempt) }
            val address = listener.redirect.toHttpUrl()
            val html = Socket("127.0.0.1", address.port).use { socket ->
                socket.soTimeout = 5000
                val path = "/auth/callback?state=${attempt.state}&code=synthetic-code&client_id=oaiapp_new"
                socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                socket.getInputStream().bufferedReader().readText()
            }
            assertEquals("oaiapp_new", awaiting.await().clientId)
            assertFalse(html.contains("synthetic-code")); assertFalse(html.contains(attempt.state))
            assertTrue(runCatching { Socket("127.0.0.1", address.port).close() }.isFailure)
        }
    }
    private fun expect(error: RunError, action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (failure: RunFailure) { assertEquals(error, failure.kind) }
    }
}
