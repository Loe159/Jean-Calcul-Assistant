package io.github.mangi.eta.data.car

import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

// Switch to false for a fresh authorization only if the French IdP rejects the PKCE exchange (HTTP 400).
private const val usePeugeotPkce = true
internal const val peugeotRedirectUri = "mymap://oauth2redirect/fr"
private const val oauthBase = "https://idpcvs.peugeot.com/am/oauth2"
private const val mymBase = "https://microservices.mym.awsmpsa.com"
private const val mymLanguage = "fr"
private const val refreshLeadSeconds = 300L

internal class PeugeotLogin private constructor(
    val url: HttpUrl,
    private val state: String,
    internal val verifier: String?,
    internal val parameters: MyPeugeotParameters,
) {
    fun acceptsState(received: String?): Boolean =
        received != null && MessageDigest.isEqual(state.toByteArray(), received.toByteArray())

    companion object {
        private val random = SecureRandom()
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        private fun nonce(): String = encoder.encodeToString(ByteArray(32).also(random::nextBytes))

        fun create(parameters: MyPeugeotParameters, usePkce: Boolean = usePeugeotPkce): PeugeotLogin {
            val state = nonce()
            val verifier = if (usePkce) nonce() else null
            val url = "$oauthBase/authorize".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", requireNotNull(parameters.cvsClientId))
                .addQueryParameter("response_type", "code")
                .addQueryParameter("redirect_uri", peugeotRedirectUri)
                .addQueryParameter("scope", "openid profile")
                .addQueryParameter("locale", peugeotLocale)
                .addQueryParameter("state", state)
                .apply {
                    if (verifier != null) {
                        val challenge = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
                        addQueryParameter("code_challenge", encoder.encodeToString(challenge))
                        addQueryParameter("code_challenge_method", "S256")
                    }
                }.build()
            return PeugeotLogin(url, state, verifier, parameters)
        }
    }
}

@Serializable
internal class PeugeotSessionRecord(
    val refreshToken: String? = null,
    val expiresAt: Long? = null,
    val vehicleId: String? = null,
    val fuel: CarFuel = CarFuel.E10,
    val needsReauth: Boolean = false,
)

internal interface PeugeotSessionStorage {
    fun read(): PeugeotSessionRecord
    fun save(record: PeugeotSessionRecord)
    fun clear()
}

internal class PeugeotReauthRequired : IOException("Peugeot reauthorization required")
private class PeugeotInvalidGrant : IOException("Peugeot OAuth grant expired")

internal class PeugeotExchangeRejected : IOException("Peugeot rejected the PKCE exchange")

@Serializable
private class PeugeotTokenBody(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val error: String? = null,
)

internal class PeugeotSession(
    private val storage: PeugeotSessionStorage,
    private val now: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()
    private class Access(val token: String, val expiry: Instant)
    private var access: Access? = null
    private var mymAccess: String? = null
    private var record: PeugeotSessionRecord? = null

    private fun stored(): PeugeotSessionRecord = record ?: storage.read().also { record = it }

    private fun save(value: PeugeotSessionRecord) {
        storage.save(value)
        record = value
    }

    suspend fun snapshot(): PeugeotSessionRecord = mutex.withLock { stored() }

    suspend fun select(vehicleId: String?, fuel: CarFuel) = mutex.withLock {
        val session = stored()
        save(PeugeotSessionRecord(session.refreshToken, session.expiresAt, vehicleId, fuel, session.needsReauth))
    }

    suspend fun connect(attempt: PeugeotLogin, state: String?, code: String, http: CarHttp): Unit = mutex.withLock {
        if (!attempt.acceptsState(state) || code.isBlank()) throw IOException("Invalid Peugeot OAuth callback")
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", peugeotRedirectUri)
        attempt.verifier?.let { form.add("code_verifier", it) }
        val response = http.execute(tokenRequest(attempt.parameters, form.build())).tokenBody(pkce = attempt.verifier != null)
        install(response, existingRefreshToken = null)
    }

    suspend fun disconnect() = mutex.withLock {
        storage.clear()
        record = PeugeotSessionRecord()
        access = null
        mymAccess = null
    }

    suspend fun profile(parameters: MyPeugeotParameters, http: CarHttp): MymGarage = mutex.withLock {
        val oauth = token(parameters, http)
        try {
            readProfile(oauth, http)
        } catch (failure: CarReadFailure) {
            if (failure.status != 401) throw failure
            val retry = if (failure.stage == CarReadStage.CvsSession) token(parameters, http, forceRefresh = true) else oauth
            readProfile(retry, http)
        }
    }

    private suspend fun readProfile(oauth: Access, http: CarHttp): MymGarage {
        val token = mymAccess ?: http.read(
            CarReadStage.CvsSession,
            mymRequest("/session/v2/accesstoken", "cvs-token", oauth.token, siteCode = "AP_FR_ESP"),
        ) { parseMymAccessToken(it.requireSuccess()) }.also { mymAccess = it }
        return try {
            http.read(CarReadStage.Profile, mymRequest("/me/v1/user", "mym-access-token", token)) {
                parseMymGarage(it.requireSuccess())
            }
        } catch (failure: CarReadFailure) {
            if (failure.status == 401) mymAccess = null
            throw failure
        }
    }

    private suspend fun token(parameters: MyPeugeotParameters, http: CarHttp, forceRefresh: Boolean = false): Access {
        val refresh = stored().refreshToken ?: throw PeugeotReauthRequired()
        val current = access
        if (!forceRefresh && current != null && current.expiry.isAfter(now().plusSeconds(refreshLeadSeconds))) {
            return current
        }

        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("scope", "openid profile")
            .build()
        return try {
            http.read(CarReadStage.TokenRefresh, tokenRequest(parameters, form)) {
                install(it.tokenBody(), existingRefreshToken = refresh)
            }
        } catch (failure: CarReadFailure) {
            if (failure.error !is PeugeotInvalidGrant) throw failure
            val previous = stored()
            save(PeugeotSessionRecord(vehicleId = previous.vehicleId, fuel = previous.fuel, needsReauth = true))
            access = null
            mymAccess = null
            throw PeugeotReauthRequired()
        }
    }

    private fun install(body: PeugeotTokenBody, existingRefreshToken: String?): Access {
        val token = body.accessToken?.takeIf(String::isNotBlank) ?: throw SerializationException("Missing access token")
        val refresh = body.refreshToken?.takeIf(String::isNotBlank) ?: existingRefreshToken
            ?: throw SerializationException("Missing refresh token")
        val ttl = body.expiresIn?.takeIf { it in 1..253_402_300_799L } ?: throw SerializationException("Missing token lifetime")
        val expiry = now().plusSeconds(ttl)
        val previous = stored()
        // Save a rotated refresh token and its expiry in the same encrypted record.
        save(PeugeotSessionRecord(refresh, expiry.epochSecond, previous.vehicleId, previous.fuel))
        mymAccess = null
        return Access(token, expiry).also { access = it }
    }

    private fun tokenRequest(parameters: MyPeugeotParameters, form: FormBody): Request =
        Request.Builder().url("$oauthBase/access_token")
            .header("Authorization", Credentials.basic(requireNotNull(parameters.cvsClientId), requireNotNull(parameters.cvsSecret)))
            .post(form).build()

    private fun mymRequest(path: String, tokenHeader: String, token: String, siteCode: String? = null): Request {
        val url = "$mymBase$path".toHttpUrl().newBuilder().apply {
            siteCode?.let { addQueryParameter("site_code", it) }
            addQueryParameter("language", mymLanguage)
        }.build()
        return Request.Builder().url(url).get()
            .header(tokenHeader, token)
            .header("User-Agent", "okhttp/4.8.0")
            .header("Version", "1.55.1")
            .header("Source-Agent", "App-Android")
            .build()
    }

    private fun CarHttpResponse.tokenBody(pkce: Boolean = false): PeugeotTokenBody {
        if (status == 400 && pkce) throw PeugeotExchangeRejected()
        if (status in 400..499 && status != 429) {
            val error = runCatching { carJson.decodeFromString<PeugeotTokenBody>(body).error }.getOrNull()
            if (error == "invalid_grant") throw PeugeotInvalidGrant()
        }
        return carJson.decodeFromString(requireSuccess())
    }
}
