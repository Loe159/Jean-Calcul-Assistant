package fr.loevan.jeancalcul.network.codex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.loevan.jeancalcul.security.SecretId
import fr.loevan.jeancalcul.security.SecretStore
import fr.loevan.jeancalcul.security.SecretStoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal const val CHATGPT_PLAN_BACKEND_ID = "openai-chatgpt-plan"
internal const val CHATGPT_PLAN_PROVIDER_ID = "chatgpt-plan"
internal const val CHATGPT_PLAN_AGENT_ID = "chatgpt-plan-agent"
internal const val CHATGPT_PLAN_API_BASE_URL = "https://api.openai.com/v1"
internal const val CHATGPT_PLAN_SECRET_ID = "provider.chatgpt.plan.oauth"
internal const val CHATGPT_PLAN_DEFAULT_MODEL = "gpt-6.1-sol"

data class ChatGptPlanAccount(
    val secretId: String,
    val email: String?,
)

internal interface ChatGptPlanTokenProvider {
    suspend fun accessToken(secretId: String): String
}

@Singleton
class ChatGptPlanAuthManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: OkHttpClient,
        private val secretStore: SecretStore,
    ) : ChatGptPlanTokenProvider {
        private val json = Json { ignoreUnknownKeys = true }
        private val secureRandom = SecureRandom()
        private val refreshMutex = Mutex()
        private val preferences =
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        suspend fun signIn(): ChatGptPlanAccount =
            withContext(Dispatchers.IO) {
                val existing = loadCredentialsOrNull(CHATGPT_PLAN_SECRET_ID)
                val hostId =
                    preferences.getString(HOST_ID_KEY, null)
                        ?: "urn:uuid:${UUID.randomUUID()}".also {
                            preferences.edit().putString(HOST_ID_KEY, it).apply()
                        }
                val storedClientId = preferences.getString(CLIENT_ID_KEY, null) ?: existing?.clientId
                val verifier = randomUrlSafe(64)
                val challenge = sha256UrlSafe(verifier)
                val state = randomUrlSafe(32)
                val nonce = randomUrlSafe(32)

                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { callbackServer ->
                    callbackServer.soTimeout = CALLBACK_TIMEOUT_MILLIS
                    val redirectUri = "http://127.0.0.1:${callbackServer.localPort}/auth/callback"
                    val dynamicRegistration = storedClientId == null
                    val requestClientId = storedClientId ?: DYNAMIC_CLIENT_ID
                    val authorizationUrl =
                        AUTHORIZATION_URL.toHttpUrl().newBuilder()
                            .addQueryParameter("response_type", "code")
                            .addQueryParameter("client_id", requestClientId)
                            .addQueryParameter("redirect_uri", redirectUri)
                            .addQueryParameter("scope", REQUIRED_SCOPES)
                            .addQueryParameter("resource", OPENAI_RESOURCE)
                            .addQueryParameter("state", state)
                            .addQueryParameter("nonce", nonce)
                            .addQueryParameter("code_challenge", challenge)
                            .addQueryParameter("code_challenge_method", "S256")
                            .addQueryParameter("ext_agent_host_id", hostId)
                            .apply {
                                if (dynamicRegistration) {
                                    addQueryParameter("agent_name_hint", "Jean Calcul")
                                } else {
                                    existing?.idToken?.takeIf(String::isNotBlank)?.let {
                                        addQueryParameter("id_token_hint", it)
                                    }
                                    existing?.email?.takeIf(String::isNotBlank)?.let {
                                        addQueryParameter("login_hint", it)
                                    }
                                }
                            }
                            .build()

                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl.toString()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )

                    val callback = receiveCallback(callbackServer)
                    if (callback.state != state) {
                        throw ChatGptPlanAuthException("La réponse OAuth ne correspond pas à cette tentative de connexion.")
                    }
                    callback.error?.let {
                        throw ChatGptPlanAuthException(
                            callback.errorDescription ?: "Connexion ChatGPT refusée : $it",
                        )
                    }
                    val code =
                        callback.code
                            ?: throw ChatGptPlanAuthException("OpenAI n'a pas renvoyé de code d'autorisation.")
                    val issuedClientId =
                        when {
                            dynamicRegistration ->
                                callback.clientId
                                    ?: throw ChatGptPlanAuthException("OpenAI n'a pas renvoyé le client OAuth créé.")
                            callback.clientId != null && callback.clientId != storedClientId ->
                                throw ChatGptPlanAuthException("Le client OAuth retourné par OpenAI est inattendu.")
                            else -> requireNotNull(storedClientId)
                        }

                    val tokens = exchangeAuthorizationCode(code, verifier, redirectUri, issuedClientId)
                    requireDirectScope(tokens.scope)
                    val identity = verifyIdToken(tokens.idToken, issuedClientId, nonce)
                    existing?.subject?.let { previousSubject ->
                        if (previousSubject != identity.subject) {
                            throw ChatGptPlanAuthException("Le compte ChatGPT connecté ne correspond pas au compte existant.")
                        }
                    }

                    val credentials =
                        ChatGptPlanCredentials(
                            email = identity.email,
                            subject = identity.subject,
                            clientId = issuedClientId,
                            hostId = hostId,
                            idToken = tokens.idToken,
                            accessToken = tokens.accessToken,
                            refreshToken = tokens.refreshToken,
                            tokenType = tokens.tokenType ?: "Bearer",
                            expiresAtEpochSeconds = epochSeconds() + tokens.expiresIn,
                            scopes = tokens.scope.split(' ').filter(String::isNotBlank).toSet(),
                        )
                    storeCredentials(CHATGPT_PLAN_SECRET_ID, credentials)
                    preferences.edit()
                        .putString(CLIENT_ID_KEY, issuedClientId)
                        .putString(HOST_ID_KEY, hostId)
                        .apply()
                    ChatGptPlanAccount(CHATGPT_PLAN_SECRET_ID, identity.email)
                }
            }

        override suspend fun accessToken(secretId: String): String =
            refreshMutex.withLock {
                val credentials =
                    loadCredentialsOrNull(secretId)
                        ?: throw ChatGptPlanAuthException("La connexion ChatGPT n'est plus disponible.")
                if (credentials.expiresAtEpochSeconds > epochSeconds() + TOKEN_EXPIRY_SAFETY_SECONDS) {
                    return@withLock credentials.accessToken
                }
                val refreshed = refresh(credentials)
                storeCredentials(secretId, refreshed)
                refreshed.accessToken
            }

        private fun receiveCallback(server: ServerSocket): OAuthCallback =
            server.accept().use { socket ->
                socket.soTimeout = SOCKET_READ_TIMEOUT_MILLIS
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                val requestLine =
                    reader.readLine()
                        ?: throw ChatGptPlanAuthException("Réponse OAuth locale vide.")
                val target = requestLine.split(' ').getOrNull(1)
                    ?: throw ChatGptPlanAuthException("Réponse OAuth locale invalide.")
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val uri = Uri.parse("http://127.0.0.1$target")
                val callback =
                    OAuthCallback(
                        code = uri.getQueryParameter("code"),
                        state = uri.getQueryParameter("state"),
                        clientId = uri.getQueryParameter("client_id"),
                        error = uri.getQueryParameter("error"),
                        errorDescription = uri.getQueryParameter("error_description"),
                    )
                val html =
                    "<!doctype html><html><body><h2>Connexion terminée</h2>" +
                        "<p>Vous pouvez revenir dans Jean Calcul.</p></body></html>"
                val body = html.toByteArray(StandardCharsets.UTF_8)
                socket.getOutputStream().apply {
                    write(
                        (
                            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                        ).toByteArray(StandardCharsets.US_ASCII),
                    )
                    write(body)
                    flush()
                }
                callback
            }

        private fun exchangeAuthorizationCode(
            code: String,
            verifier: String,
            redirectUri: String,
            clientId: String,
        ): OAuthTokenResponse {
            val form =
                FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", clientId)
                    .add("code", code)
                    .add("code_verifier", verifier)
                    .add("redirect_uri", redirectUri)
                    .add("resource", OPENAI_RESOURCE)
                    .build()
            return executeTokenRequest(form)
        }

        private fun refresh(credentials: ChatGptPlanCredentials): ChatGptPlanCredentials {
            val form =
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("client_id", credentials.clientId)
                    .add("refresh_token", credentials.refreshToken)
                    .add("resource", OPENAI_RESOURCE)
                    .build()
            val tokens = executeTokenRequest(form)
            val scopes =
                tokens.scope.takeIf(String::isNotBlank)
                    ?.split(' ')
                    ?.filter(String::isNotBlank)
                    ?.toSet()
                    ?: credentials.scopes
            if (DIRECT_SCOPE !in scopes) {
                throw ChatGptPlanAuthException("Le jeton renouvelé n'autorise plus l'utilisation directe du plan ChatGPT.")
            }
            return credentials.copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                tokenType = tokens.tokenType ?: credentials.tokenType,
                expiresAtEpochSeconds = epochSeconds() + tokens.expiresIn,
                scopes = scopes,
            )
        }

        private fun executeTokenRequest(body: FormBody): OAuthTokenResponse {
            val request =
                Request.Builder()
                    .url(TOKEN_URL)
                    .post(body)
                    .header("Accept", "application/json")
                    .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ChatGptPlanAuthException("OpenAI a refusé l'échange OAuth (HTTP ${response.code}).")
                }
                return runCatching { json.decodeFromString(OAuthTokenResponse.serializer(), raw) }
                    .getOrElse { throw ChatGptPlanAuthException("Réponse OAuth OpenAI invalide.", it) }
            }
        }

        private fun verifyIdToken(
            token: String,
            expectedClientId: String,
            expectedNonce: String,
        ): OpenAiIdentity {
            val parts = token.split('.')
            if (parts.size != 3) throw ChatGptPlanAuthException("ID token OpenAI invalide.")
            val header =
                runCatching {
                    json.parseToJsonElement(String(decodeBase64Url(parts[0]), StandardCharsets.UTF_8)) as JsonObject
                }.getOrElse { throw ChatGptPlanAuthException("En-tête JWT OpenAI invalide.", it) }
            if (header["alg"]?.jsonPrimitive?.content != "RS256") {
                throw ChatGptPlanAuthException("Algorithme de signature OpenAI inattendu.")
            }
            val keyId =
                header["kid"]?.jsonPrimitive?.content
                    ?: throw ChatGptPlanAuthException("Clé de signature OpenAI absente.")
            val jwk = fetchJwk(keyId)
            val modulus = BigInteger(1, decodeBase64Url(jwk.n))
            val exponent = BigInteger(1, decodeBase64Url(jwk.e))
            val publicKey =
                KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
            val verifier =
                Signature.getInstance("SHA256withRSA").apply {
                    initVerify(publicKey)
                    update("${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII))
                }
            if (!verifier.verify(decodeBase64Url(parts[2]))) {
                throw ChatGptPlanAuthException("Signature du compte OpenAI invalide.")
            }

            val claims =
                runCatching {
                    json.parseToJsonElement(String(decodeBase64Url(parts[1]), StandardCharsets.UTF_8)) as JsonObject
                }.getOrElse { throw ChatGptPlanAuthException("Contenu du compte OpenAI invalide.", it) }
            if (claims["iss"]?.jsonPrimitive?.content != OPENAI_ISSUER) {
                throw ChatGptPlanAuthException("Émetteur OAuth OpenAI inattendu.")
            }
            val audience = claims["aud"]
            val audienceMatches =
                when (audience) {
                    is JsonArray -> audience.any { it.jsonPrimitive.content == expectedClientId }
                    else -> audience?.jsonPrimitive?.content == expectedClientId
                }
            if (!audienceMatches) throw ChatGptPlanAuthException("Audience OAuth OpenAI invalide.")
            val expiry =
                claims["exp"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: throw ChatGptPlanAuthException("Expiration OAuth OpenAI absente.")
            if (expiry <= epochSeconds() - CLOCK_SKEW_SECONDS) {
                throw ChatGptPlanAuthException("ID token OpenAI expiré.")
            }
            if (claims["nonce"]?.jsonPrimitive?.content != expectedNonce) {
                throw ChatGptPlanAuthException("Nonce OAuth OpenAI invalide.")
            }
            val subject =
                claims["sub"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
                    ?: throw ChatGptPlanAuthException("Identité OpenAI absente.")
            return OpenAiIdentity(subject, claims["email"]?.jsonPrimitive?.content)
        }

        private fun fetchJwk(keyId: String): JsonWebKey {
            val request = Request.Builder().url(JWKS_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ChatGptPlanAuthException("Impossible de vérifier la signature OpenAI.")
                }
                val body = response.body?.string().orEmpty()
                val root =
                    runCatching { json.parseToJsonElement(body) as JsonObject }
                        .getOrElse { throw ChatGptPlanAuthException("Jeu de clés OpenAI invalide.", it) }
                val keys = root["keys"]?.jsonArray ?: throw ChatGptPlanAuthException("Clés OpenAI absentes.")
                val key =
                    keys.mapNotNull { it as? JsonObject }
                        .firstOrNull { it["kid"]?.jsonPrimitive?.content == keyId }
                        ?: throw ChatGptPlanAuthException("Clé de signature OpenAI inconnue.")
                if (key["kty"]?.jsonPrimitive?.content != "RSA") {
                    throw ChatGptPlanAuthException("Type de clé OpenAI inattendu.")
                }
                return JsonWebKey(
                    n = key["n"]?.jsonPrimitive?.content
                        ?: throw ChatGptPlanAuthException("Module RSA OpenAI absent."),
                    e = key["e"]?.jsonPrimitive?.content
                        ?: throw ChatGptPlanAuthException("Exposant RSA OpenAI absent."),
                )
            }
        }

        private fun requireDirectScope(scope: String) {
            val granted = scope.split(' ').filter(String::isNotBlank).toSet()
            if (DIRECT_SCOPE !in granted) {
                throw ChatGptPlanAuthException("OpenAI n'a pas accordé l'accès direct au plan ChatGPT.")
            }
        }

        private suspend fun storeCredentials(
            secretId: String,
            credentials: ChatGptPlanCredentials,
        ) {
            val chars = json.encodeToString(ChatGptPlanCredentials.serializer(), credentials).toCharArray()
            val result =
                try {
                    secretStore.put(SecretId(secretId), chars)
                } finally {
                    chars.fill(NULL_CHARACTER)
                }
            if (result is SecretStoreResult.Failure) {
                throw ChatGptPlanAuthException(result.error.userMessage)
            }
        }

        private suspend fun loadCredentialsOrNull(secretId: String): ChatGptPlanCredentials? =
            when (val result = secretStore.get(SecretId(secretId))) {
                is SecretStoreResult.Failure -> throw ChatGptPlanAuthException(result.error.userMessage)
                is SecretStoreResult.Success ->
                    result.value?.use { secret ->
                        secret.useChars { chars ->
                            runCatching {
                                json.decodeFromString(ChatGptPlanCredentials.serializer(), chars.concatToString())
                            }.getOrElse { throw ChatGptPlanAuthException("Identifiants ChatGPT locaux invalides.", it) }
                        }
                    }
            }

        private fun randomUrlSafe(byteCount: Int): String =
            ByteArray(byteCount).also(secureRandom::nextBytes).let(::encodeBase64Url)

        private fun sha256UrlSafe(value: String): String =
            encodeBase64Url(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.US_ASCII)))

        private fun encodeBase64Url(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        private fun decodeBase64Url(value: String): ByteArray =
            Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        private fun epochSeconds(): Long = System.currentTimeMillis() / 1_000L

        private companion object {
            const val AUTHORIZATION_URL = "https://auth.openai.com/api/accounts/authorize"
            const val TOKEN_URL = "https://auth.openai.com/api/accounts/oauth/token"
            const val JWKS_URL = "https://auth.openai.com/.well-known/jwks.json"
            const val OPENAI_ISSUER = "https://auth.openai.com"
            const val OPENAI_RESOURCE = "https://api.openai.com/v1"
            const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
            const val DIRECT_SCOPE = "chatgpt.tokens.use.direct"
            const val REQUIRED_SCOPES =
                "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
            const val PREFERENCES_NAME = "chatgpt_plan_oauth"
            const val CLIENT_ID_KEY = "issued_client_id"
            const val HOST_ID_KEY = "ext_agent_host_id"
            const val CALLBACK_TIMEOUT_MILLIS = 300_000
            const val SOCKET_READ_TIMEOUT_MILLIS = 5_000
            const val TOKEN_EXPIRY_SAFETY_SECONDS = 60L
            const val CLOCK_SKEW_SECONDS = 30L
            const val NULL_CHARACTER = '\u0000'
        }
    }

class ChatGptPlanAuthException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class ChatGptPlanCredentials(
    val email: String? = null,
    val subject: String,
    val clientId: String,
    val hostId: String,
    val idToken: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresAtEpochSeconds: Long,
    val scopes: Set<String>,
)

@Serializable
private data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String = "",
)

private data class OAuthCallback(
    val code: String?,
    val state: String?,
    val clientId: String?,
    val error: String?,
    val errorDescription: String?,
)

private data class OpenAiIdentity(
    val subject: String,
    val email: String?,
)

private data class JsonWebKey(
    val n: String,
    val e: String,
)
