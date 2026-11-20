package io.github.mangi.eta.data.repository

import android.app.Application
import android.content.Context
import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.model.VllmSpeechConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SpeechSettingsRepositoryTest {
    @Test fun speechSettingsRoundTripWithoutOverwritingModelOrAppearance() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        SettingsDataStore.init(context)
        val before = SettingsDataStore.settings()
        val speech = SpeechSettings(asr = AsrProvider.OPENAI, tts = TtsProvider.VLLM,
            autoSpeak = true, recognitionEnabled = false)
        SpeechSettingsRepository.save(speech)
        assertEquals(speech, SpeechSettingsRepository.settings())
        SettingsDataStore.updateSettings { it.copy(memoryEnabled = !before.memoryEnabled) }
        assertEquals(speech, SpeechSettingsRepository.settings())
        assertEquals(before.appearance, SettingsDataStore.settings().appearance)
        assertEquals(before.selectedProviderId, SettingsDataStore.settings().selectedProviderId)
    }

    @Test fun unreadableCredentialsRequireReconfiguration() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("jeancalcul_speech_secrets", Context.MODE_PRIVATE)
        prefs.edit().putString("credentials_v2", "invalid ciphertext").commit()
        try {
            SpeechSettingsRepository.credentials(context)
            fail("Invalid ciphertext must not become empty credentials")
        } catch (expected: SpeechCredentialsUnavailable) {
            assertFalse(expected.toString().contains("invalid ciphertext"))
        }
    }

    @Test fun conversationVllmProviderLendsAddressAndKey() {
        val chatGpt = OpenAiCompatibleProviderSetting(
            id = "chatgpt", name = "ChatGPT", baseUrl = "https://chatgpt.com/backend-api/codex",
            sourceType = ProviderSourceTypes.OPENAI, authMode = ProviderAuthModes.OPENAI_CHATGPT, apiKey = "",
        )
        val disabled = OpenAiCompatibleProviderSetting(
            id = "old", name = "Old", baseUrl = "http://old:8000/v1", isEnabled = false, sortOrder = 0,
        )
        val lan = OpenAiCompatibleProviderSetting(
            id = "lan", name = "LAN", baseUrl = " http://lan:8000/v1 ", apiKey = "lan-key", sortOrder = 5,
        )
        assertEquals("http://lan:8000/v1" to "lan-key", SpeechSettingsRepository.vllmConnection(listOf(chatGpt, disabled, lan)))
        assertNull(SpeechSettingsRepository.vllmConnection(listOf(chatGpt)))
    }

    @Test fun vllmFallbackOnlyFillsMissingValues() = runBlocking {
        val connection: suspend () -> Pair<String, String>? = { "http://lan:8000/v1" to "lan-key" }
        val (settings, secrets) = SpeechSettingsRepository.resolveVllmFallback(
            SpeechSettings(asr = AsrProvider.VLLM), SpeechCredentials(), connection,
        )
        assertEquals("http://lan:8000/v1", settings.vllm.baseUrl)
        assertEquals("lan-key", secrets.vllm)

        val own = SpeechSettings(asr = AsrProvider.VLLM, vllm = VllmSpeechConfig(baseUrl = "http://mine/v1"))
        assertEquals(own, SpeechSettingsRepository.resolveVllmFallback(own, SpeechCredentials(), connection).first)

        val android = SpeechSettings()
        assertEquals(android, SpeechSettingsRepository.resolveVllmFallback(android, SpeechCredentials(), connection).first)
    }
}
