package io.github.mangi.eta.data.car

import io.github.mangi.eta.BuildConfig
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
    private val http: CarHttp,
    private val storage: PeugeotSessionStorage,
    private val now: () -> Instant = Instant::now,
) {
    private val mutex = Mutex()
    private class Access(val token: String, val expiry: Instant)
    private var access: Access? = null
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

    suspend fun connect(attempt: PeugeotLogin, state: String?, code: String): Unit = mutex.withLock {
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
    }

    suspend fun <T> get(
        parameters: MyPeugeotParameters,
        url: HttpUrl,
        stage: CarReadStage,
        decode: (String) -> T,
    ): T {
        require(url.scheme == "https" && url.host == "api.groupe-psa.com" && url.port == 443)
        val authorizedUrl = url.newBuilder()
            .setQueryParameter("client_id", requireNotNull(parameters.cvsClientId))
            .setQueryParameter("locale", peugeotLocale)
            .build()

        suspend fun request(token: Access): T {
            val request = Request.Builder().url(authorizedUrl).get()
                .header("Authorization", "Bearer ${token.token}")
                .header("Accept", "application/hal+json")
                .header("x-introspect-realm", "clientsB2CPeugeot")
                .build()
            val sensitive = if (BuildConfig.DEBUG) {
                val session = snapshot()
                listOf(parameters.cvsClientId, parameters.cvsSecret, token.token, session.refreshToken, session.vehicleId)
            } else {
                emptyList()
            }
            return http.read(stage, request, sensitive) { decode(it.requireSuccess()) }
        }

        val firstToken = token(parameters)
        return try {
            request(firstToken)
        } catch (failure: CarReadFailure) {
            if ((failure.error as? CarHttpFailure)?.status != 401) throw failure
            request(token(parameters, rejected = firstToken))
        }
    }

    private suspend fun token(parameters: MyPeugeotParameters, rejected: Access? = null): Access = mutex.withLock {
        val refresh = stored().refreshToken ?: throw PeugeotReauthRequired()
        val current = access
        if (current != null && current !== rejected &&
            (rejected != null || current.expiry.isAfter(now().plusSeconds(refreshLeadSeconds)))
        ) return@withLock current

        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("scope", "openid profile")
            .build()
        try {
            val request = tokenRequest(parameters, form)
            http.read(CarReadStage.TokenRefresh, request, listOf(
                parameters.cvsClientId, parameters.cvsSecret, refresh, current?.token,
                stored().vehicleId, request.header("Authorization")?.removePrefix("Basic "),
            )) { install(it.tokenBody(), existingRefreshToken = refresh) }
        } catch (failure: CarReadFailure) {
            if (failure.error !is PeugeotInvalidGrant) throw failure
            val previous = stored()
            save(PeugeotSessionRecord(vehicleId = previous.vehicleId, fuel = previous.fuel, needsReauth = true))
            access = null
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
        return Access(token, expiry).also { access = it }
    }

    private fun tokenRequest(parameters: MyPeugeotParameters, form: FormBody): Request =
        Request.Builder().url("$oauthBase/access_token")
            .header("Authorization", Credentials.basic(requireNotNull(parameters.cvsClientId), requireNotNull(parameters.cvsSecret)))
            .post(form).build()

    private fun CarHttpResponse.tokenBody(pkce: Boolean = false): PeugeotTokenBody {
        if (status == 400 && pkce) throw PeugeotExchangeRejected()
        if (status in 400..499 && status != 429) {
            val error = runCatching { carJson.decodeFromString<PeugeotTokenBody>(body).error }.getOrNull()
            if (error == "invalid_grant") throw PeugeotInvalidGrant()
        }
        return carJson.decodeFromString(requireSuccess())
    }
}
