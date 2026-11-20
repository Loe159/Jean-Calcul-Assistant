package io.github.mangi.eta.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal object SpeechSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun settingsFlow(): Flow<SpeechSettings> = SettingsDataStore.speechSettingsFlow()
    suspend fun settings(): SpeechSettings = SettingsDataStore.speechSettings()
    suspend fun save(settings: SpeechSettings) = SettingsDataStore.setSpeechSettings(settings)

    /** Serveur vLLM de conversation réutilisable pour la voix : adresse renseignée, connexion par clé API. */
    suspend fun vllmProviderConnection(): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            vllmConnection(ProviderRepository.allProviders())
        }.getOrNull()
    }

    internal fun vllmConnection(providers: List<ProviderSetting>): Pair<String, String>? = providers
        .filter {
            it.authMode == ProviderAuthModes.API_KEY &&
                it.sourceType in setOf("", ProviderSourceTypes.CUSTOM) &&
                it.baseUrl.isNotBlank()
        }
        .sortedWith(compareByDescending<ProviderSetting> { it.isEnabled }.thenBy { it.sortOrder })
        .firstOrNull()
        ?.let { it.baseUrl.trim() to it.apiKey.trim() }

    /** Adresse vLLM vocale vide : on reprend l’adresse et la clé du fournisseur vLLM de conversation. */
    suspend fun resolveVllmFallback(
        settings: SpeechSettings,
        credentials: SpeechCredentials,
        connection: suspend () -> Pair<String, String>? = ::vllmProviderConnection,
    ): Pair<SpeechSettings, SpeechCredentials> {
        val usesVllm = settings.asr == AsrProvider.VLLM || settings.tts == TtsProvider.VLLM
        if (!usesVllm || settings.vllm.baseUrl.isNotBlank()) return settings to credentials
        val (baseUrl, apiKey) = connection() ?: return settings to credentials
        return settings.copy(vllm = settings.vllm.copy(baseUrl = baseUrl)) to
            credentials.withVllm(credentials.vllm.ifBlank { apiKey })
    }

    suspend fun credentials(context: Context): SpeechCredentials = withContext(Dispatchers.IO) {
        val encoded = context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE)
            .getString(CREDENTIALS_KEY, null) ?: return@withContext SpeechCredentials()
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            require(bytes.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, 12))
            json.decodeFromString<SpeechCredentials>(
                cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8),
            )
        } catch (_: Exception) {
            throw SpeechCredentialsUnavailable()
        }
    }

    suspend fun saveCredentials(context: Context, credentials: SpeechCredentials) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(json.encodeToString(credentials).toByteArray(Charsets.UTF_8))
        check(context.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE).edit()
            .putString(CREDENTIALS_KEY, Base64.getEncoder().encodeToString(encrypted)).commit()) {
            "Could not save speech credentials"
        }
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    private const val SECRETS_FILE = "jeancalcul_speech_secrets"
    private const val CREDENTIALS_KEY = "credentials_v2"
    private const val KEY_ALIAS = "eta_speech_credentials_v1"
}

internal class SpeechCredentialsUnavailable : Exception("Could not decrypt speech credentials. Enter and save them again.")
