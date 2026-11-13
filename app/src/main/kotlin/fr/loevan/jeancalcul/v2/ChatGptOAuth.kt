package fr.loevan.jeancalcul.v2

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import java.security.cert.CertificateException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
internal const val DYNAMIC_CLIENT = "dynamic_agent_client"
internal const val OPENAI_RESOURCE = "https://api.openai.com/v1"

/** Safe classifications. Never include token values, URLs or exception messages in the UI. */
internal enum class OAuthNetworkStage(val label: String) {
    TOKEN("échange du code"), DISCOVERY("configuration OpenAI"),
    JWKS("clés de vérification"), MODELS("liste des modèles")
}
internal enum class OAuthNetworkIssue(val label: String) {
    DNS("nom de domaine introuvable (DNS)"),
    CERTIFICATE("certificat TLS non reconnu"),
    TLS("négociation HTTPS/TLS impossible"),
    TIMEOUT("délai réseau dépassé"),
    CONNECT("connexion au serveur impossible"),
    IO("connexion interrompue ou réponse réseau invalide")
}
internal class OAuthNetworkFailure(
    val stage: OAuthNetworkStage, val issue: OAuthNetworkIssue,
) : IOException() {
    val safeMessage get() = "${stage.label} : ${issue.label}"
    override fun toString(): String = "OAuthNetworkFailure(${stage.name}, ${issue.name})"
}
internal fun classifyOAuthNetwork(error: IOException): OAuthNetworkIssue {
    // Inspect types, not exception.message, which may contain request details.
    val chain = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
    return when {
        chain.any { it is CertificateException || it is SSLPeerUnverifiedException } -> OAuthNetworkIssue.CERTIFICATE
        error is SSLHandshakeException -> OAuthNetworkIssue.TLS
        error is SSLException -> OAuthNetworkIssue.TLS
        error is UnknownHostException -> OAuthNetworkIssue.DNS
        error is SocketTimeoutException || error is InterruptedIOException -> OAuthNetworkIssue.TIMEOUT
        error is ConnectException || error is NoRouteToHostException || error is SocketException -> OAuthNetworkIssue.CONNECT
        else -> OAuthNetworkIssue.IO
    }
}
internal val chatGptHttp = OkHttpClient.Builder().dns(openAiDns()).followRedirects(false).followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
    .callTimeout(45, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

/** Constructor overrides are for HTTPS fixtures, never user-editable OAuth destinations. */
internal data class ChatGptEndpoints(
    val issuer: String = "https://auth.openai.com",
    val api: String = OPENAI_RESOURCE,
) {
    val authorize get() = "$issuer/api/accounts/authorize"
    val token get() = "$issuer/api/accounts/oauth/token"
}
internal data class OAuthAttempt(
    val state: String, val nonce: String, val verifier: String, val redirect: String,
    val hostId: String, val clientId: String = DYNAMIC_CLIENT,
    val expectedSubject: String? = null, val consent: Boolean = false, val expiresAt: Long = System.currentTimeMillis() + 300_000,
) {
    override fun toString() = "OAuthAttempt(redacted)"
    fun authorizationUrl(endpoints: ChatGptEndpoints): String = endpoints.authorize.toHttpUrl().newBuilder()
        .addQueryParameter("client_id", clientId)
        .apply { if (clientId == DYNAMIC_CLIENT) addQueryParameter("agent_name_hint", "Jean-Calcul") }
        .addQueryParameter("ext_agent_host_id", hostId).addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", redirect)
        .addQueryParameter("scope", "openid profile email offline_access resource.invoke $PLAN_SCOPE")
        .addQueryParameter("resource", endpoints.api)
        .apply { if (consent) addQueryParameter("prompt", "consent") }
        .addQueryParameter("state", state).addQueryParameter("nonce", nonce)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("code_challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))))
        .build().toString()
    fun validateCallback(url: HttpUrl, now: Long = System.currentTimeMillis()): OAuthCode {
        val expected = redirect.toHttpUrl()
        if (url.scheme != expected.scheme || url.host != expected.host || url.port != expected.port ||
            url.encodedPath != expected.encodedPath || now >= expiresAt ||
            url.queryParameterValues("state").size != 1 ||
            !MessageDigest.isEqual(url.queryParameter("state").orEmpty().toByteArray(), state.toByteArray())) authFailed()
        if (url.queryParameter("error") != null) authFailed()
        val code = url.queryParameter("code").orEmpty()
        val issued = url.queryParameter("client_id") ?: clientId
        if (code.isBlank() || url.queryParameterValues("code").size != 1 ||
            url.queryParameterValues("client_id").size > 1 || issued == DYNAMIC_CLIENT ||
            !issued.startsWith("oaiapp_") || (clientId != DYNAMIC_CLIENT && issued != clientId)) authFailed()
        return OAuthCode(code, issued)
    }
    companion object {
        private fun random() = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        fun create(redirect: String, hostId: String, account: ChatGptAccount? = null) = OAuthAttempt(
            random(), random(), random(), redirect, hostId, account?.clientId ?: DYNAMIC_CLIENT, account?.subject, account?.tokens?.let { PLAN_SCOPE !in it.scopes } == true)
    }
}
internal data class OAuthCode(val code: String, val clientId: String) {
    override fun toString() = "OAuthCode(redacted)"
}
internal fun authFailed(): Nothing = throw RunFailure(RunError.CHATGPT_AUTH)

internal data class ChatGptTokens(
    val access: String, val refresh: String, val idToken: String, val expiresAt: Long,
    val scopes: Set<String>,
) { override fun toString() = "ChatGptTokens(redacted)" }
internal data class ChatGptAccount(
    val clientId: String, val subject: String, val email: String,
    val tokens: ChatGptTokens? = null, val model: String = "", val welcomed: Boolean = false,
) {
    override fun toString() = "ChatGptAccount(redacted)"
    fun json() = JSONObject().put("clientId", clientId).put("subject", subject).put("email", email)
        .put("model", model).put("welcomed", welcomed).apply {
            tokens?.let { put("tokens", JSONObject().put("access", it.access).put("refresh", it.refresh)
                .put("id", it.idToken).put("expiresAt", it.expiresAt).put("scopes", JSONArray(it.scopes.toList()))) }
        }
    companion object {
        fun parse(json: JSONObject): ChatGptAccount {
            val tokens = json.optJSONObject("tokens")?.let {
                val scopes = it.getJSONArray("scopes")
                ChatGptTokens(it.getString("access"), it.getString("refresh"), it.getString("id"),
                    it.getLong("expiresAt"), (0 until scopes.length()).map { i -> scopes.getString(i) }.toSet())
            }
            return ChatGptAccount(json.getString("clientId"), json.getString("subject"), json.getString("email"),
                tokens, json.optString("model", ""), json.optBoolean("welcomed", false))
        }
    }
}
internal data class ChatGptRegistration(
    val hostId: String = "urn:uuid:${UUID.randomUUID()}",
    val accounts: List<ChatGptAccount> = emptyList(), val active: String = "", val enabled: Boolean = true,
) {
    override fun toString() = "ChatGptRegistration(redacted)"
    fun json() = JSONObject().put("hostId", hostId).put("accounts", JSONArray().apply { accounts.forEach { put(it.json()) } })
        .put("active", active).put("enabled", enabled).toString()
    companion object {
        fun parse(value: String): ChatGptRegistration {
            val json = JSONObject(value); val accounts = json.getJSONArray("accounts")
            return ChatGptRegistration(json.getString("hostId"), (0 until accounts.length()).map {
                ChatGptAccount.parse(accounts.getJSONObject(it)) }, json.optString("active", ""), json.optBoolean("enabled", false))
        }
    }
}
internal interface ChatGptStore { fun load(): ChatGptRegistration; fun save(value: ChatGptRegistration) }
internal data class ChatGptModel(val slug: String, val name: String)

internal class ChatGptOAuth(
    val endpoints: ChatGptEndpoints = ChatGptEndpoints(),
    val http: OkHttpClient = chatGptHttp,
) {
    private fun json(request: Request, stage: OAuthNetworkStage): JSONObject = try {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val code = runCatching { JSONObject(response.peekBody(8192).string()).optString("error") }.getOrDefault("")
                val terminal = code in setOf("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")
                throw RunFailure(when (response.code) {
                    400, 401, 403 -> RunError.CHATGPT_AUTH
                    429 -> RunError.RATE_LIMIT
                    else -> RunError.PROVIDER
                }, terminalAuth = terminal)
            }
            val source = response.body?.source() ?: authFailed()
            if (source.request(1_048_577)) throw RunFailure(RunError.PROTOCOL)
            try { JSONObject(source.readUtf8()) } catch (error: IOException) { throw error }
            catch (_: Exception) { throw RunFailure(RunError.PROTOCOL) }
        }
    } catch (error: IOException) {
        throw OAuthNetworkFailure(stage, classifyOAuthNetwork(error))
    }
    private fun discovery(): JSONObject {
        val value = json(Request.Builder().url("${endpoints.issuer}/.well-known/openid-configuration").build(), OAuthNetworkStage.DISCOVERY)
        if (value.getString("issuer") != endpoints.issuer) authFailed()
        return value
    }
    private fun trustedAuthUrl(value: String): HttpUrl {
        val url = value.toHttpUrl(); val issuer = endpoints.issuer.toHttpUrl()
        if (!url.isHttps || url.host != issuer.host || url.port != issuer.port ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null || url.query != null) authFailed()
        return url
    }
    fun exchange(attempt: OAuthAttempt, code: OAuthCode): ChatGptAccount {
        if (System.currentTimeMillis() >= attempt.expiresAt) authFailed()
        val body = FormBody.Builder().add("grant_type", "authorization_code").add("client_id", code.clientId)
            .add("code", code.code).add("code_verifier", attempt.verifier).add("redirect_uri", attempt.redirect)
            .add("resource", endpoints.api).build()
        val response = json(Request.Builder().url(endpoints.token).post(body).build(), OAuthNetworkStage.TOKEN)
        return account(response, code.clientId, attempt.nonce, attempt.expectedSubject)
    }
    fun refresh(old: ChatGptAccount): ChatGptAccount {
        val tokens = old.tokens ?: authFailed()
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", old.clientId)
            .add("refresh_token", tokens.refresh).add("resource", endpoints.api).build()
        val response = json(Request.Builder().url(endpoints.token).post(body).build(), OAuthNetworkStage.TOKEN)
        return account(response, old.clientId, null, old.subject).copy(model = old.model, welcomed = old.welcomed)
    }
    private fun account(json: JSONObject, clientId: String, nonce: String?, subject: String?): ChatGptAccount {
        val identity = json.getString("id_token")
        val jwt = try { SignedJWT.parse(identity) } catch (_: Exception) { authFailed() }
        val discovery = discovery()
        val keys = this.json(Request.Builder().url(trustedAuthUrl(discovery.getString("jwks_uri"))).build(), OAuthNetworkStage.JWKS)
        val jwks = JWKSet.parse(keys.toString())
        if (jwt.header.algorithm != JWSAlgorithm.RS256 || jwt.header.keyID.isNullOrBlank()) authFailed()
        val matching = jwks.keys.filterIsInstance<RSAKey>().filter { it.keyID == jwt.header.keyID && !it.isPrivate }
        if (matching.size != 1 || !jwt.verify(RSASSAVerifier(matching.single().toRSAPublicKey()))) authFailed()
        val claims = jwt.jwtClaimsSet
        val now = System.currentTimeMillis()
        if (claims.issuer != endpoints.issuer || clientId !in claims.audience ||
            (claims.audience.size > 1 && claims.getStringClaim("azp") != clientId) ||
            claims.expirationTime == null || claims.expirationTime.time <= now ||
            claims.issueTime == null || claims.issueTime.time > now + 60_000 ||
            (claims.notBeforeTime != null && claims.notBeforeTime.time > now + 60_000) ||
            claims.subject.isNullOrBlank() || (nonce != null && claims.getStringClaim("nonce") != nonce) ||
            (subject != null && claims.subject != subject)) authFailed()
        val access = json.getString("access_token"); val refresh = json.getString("refresh_token")
        val seconds = json.getLong("expires_in")
        if (access.isBlank() || refresh.isBlank() || seconds !in 1..86_400 ||
            !json.getString("token_type").equals("Bearer", ignoreCase = true)) authFailed()
        val scopes = json.optString("scope", "").split(' ').filter { it.isNotBlank() }.toSet()
        return ChatGptAccount(clientId, claims.subject, claims.getStringClaim("email").orEmpty(),
            ChatGptTokens(access, refresh, identity, now + seconds * 1000, scopes))
    }
    fun models(account: ChatGptAccount): List<ChatGptModel> {
        val token = account.tokens ?: authFailed()
        if (PLAN_SCOPE !in token.scopes) throw RunFailure(RunError.CHATGPT_PERMISSION)
        val response = json(Request.Builder().url("${endpoints.api}/models")
            .header("Authorization", "Bearer ${token.access}").build(), OAuthNetworkStage.MODELS).getJSONArray("models")
        return (0 until response.length()).map { response.getJSONObject(it) }
            .filter { it.optString("visibility") == "list" }
            .map { ChatGptModel(it.getString("slug"), it.getString("display_name")) }.distinctBy { it.slug }
    }
    fun revoke(account: ChatGptAccount): Boolean {
        val token = account.tokens ?: return true
        val url = trustedAuthUrl(discovery().getString("revocation_endpoint"))
        val body = FormBody.Builder().add("token", token.refresh).add("token_type_hint", "refresh_token")
            .add("client_id", account.clientId).build()
        return http.newCall(Request.Builder().url(url).post(body).build()).execute().use { it.code == 200 }
    }
}
