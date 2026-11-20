package io.github.mangi.eta.data.model

import io.github.mangi.eta.agent.voice.SpeechFailure
import io.github.mangi.eta.agent.voice.validateSpeechSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SpeechSettingsTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test fun defaultsWorkWithoutAnyAccount() {
        val config = json.decodeFromString<SpeechSettings>("{}")
        assertEquals(AsrProvider.ANDROID, config.asr)
        assertEquals(TtsProvider.ANDROID, config.tts)
        assertTrue(config.recognitionEnabled)
        validateSpeechSettings(config, SpeechCredentials(), synthesis = false)
        validateSpeechSettings(config, SpeechCredentials(), synthesis = true)
    }

    @Test fun legacyEtaProvidersFallBackToAndroidAndKeepUserPreferences() {
        val legacy = json.decodeFromString<SpeechSettings>(
            """{"asr":"QWEN_REALTIME","tts":"DOUBAO","autoSpeak":true,"chatAutoSpeak":true,
               |"refineTranscript":true,"autoSendSilenceMs":3000,"qwenVoice":"Cherry",
               |"oss":{"bucket":"old"}}""".trimMargin(),
        )
        assertEquals(AsrProvider.ANDROID, legacy.asr)
        assertEquals(TtsProvider.ANDROID, legacy.tts)
        assertTrue(legacy.autoSpeak)
        assertTrue(legacy.chatAutoSpeak)
        assertTrue(legacy.refineTranscript)
        assertEquals(3_000, legacy.autoSendSilenceMs)
        assertFalse(json.encodeToString(legacy).contains("Cherry"))
    }

    @Test fun openAiRequiresItsKey() {
        val asr = SpeechSettings(asr = AsrProvider.OPENAI)
        assertThrows(SpeechFailure::class.java) { validateSpeechSettings(asr, SpeechCredentials(vllm = "other"), false) }
        validateSpeechSettings(asr, SpeechCredentials(openAi = "test"), false)
        val tts = SpeechSettings(tts = TtsProvider.OPENAI)
        assertThrows(SpeechFailure::class.java) { validateSpeechSettings(tts, SpeechCredentials(), true) }
        validateSpeechSettings(tts, SpeechCredentials(openAi = "test"), true)
        assertThrows(SpeechFailure::class.java) {
            validateSpeechSettings(tts.copy(openAi = OpenAiSpeechConfig(instructions = "x".repeat(501))),
                SpeechCredentials(openAi = "test"), true)
        }
    }

    @Test fun vllmKeyIsOptionalButAddressComesFromSettingsOrConversationProvider() {
        val asr = SpeechSettings(asr = AsrProvider.VLLM)
        assertThrows(SpeechFailure::class.java) { validateSpeechSettings(asr, SpeechCredentials(), false) }
        validateSpeechSettings(asr, SpeechCredentials(), false, hasProviderVllm = true)
        validateSpeechSettings(
            asr.copy(vllm = VllmSpeechConfig(baseUrl = "http://10.0.0.2:8000/v1")),
            SpeechCredentials(),
            false,
        )
        val tts = SpeechSettings(tts = TtsProvider.VLLM, vllm = VllmSpeechConfig(baseUrl = "http://10.0.0.2:8000/v1"))
        assertThrows(SpeechFailure::class.java) { validateSpeechSettings(tts, SpeechCredentials(), true) }
        validateSpeechSettings(tts.copy(vllm = tts.vllm.copy(speechModel = "kokoro")), SpeechCredentials(), true)
    }

    @Test fun languageMustLookLikeACode() {
        validateSpeechSettings(SpeechSettings(language = "fr-FR"), SpeechCredentials(), false)
        assertThrows(SpeechFailure::class.java) {
            validateSpeechSettings(SpeechSettings(language = "français"), SpeechCredentials(), false)
        }
    }

    @Test fun disabledRecognitionSkipsValidationButKeepsConfiguration() {
        val disabled = SpeechSettings(asr = AsrProvider.OPENAI, recognitionEnabled = false, tts = TtsProvider.NONE)
        validateSpeechSettings(disabled, SpeechCredentials(), synthesis = false)
        assertThrows(SpeechFailure::class.java) { validateSpeechSettings(disabled, SpeechCredentials(), synthesis = true) }
        assertEquals(disabled, json.decodeFromString<SpeechSettings>(json.encodeToString(disabled)))
    }

    @Test fun credentialsAreRedactedAndIndependent() {
        val secrets = SpeechCredentials(openAi = "private-key").withValue(SpeechCredentialField.VLLM, "lan-key")
        assertEquals("private-key", secrets.openAi)
        assertEquals("lan-key", secrets.vllm)
        assertEquals("SpeechCredentials(redacted)", secrets.toString())
        assertFalse(json.encodeToString(SpeechSettings()).contains("private-key"))
        val legacy = json.decodeFromString<SpeechCredentials>("""{"qwenAsr":"old","openAi":"kept"}""")
        assertEquals("kept", legacy.openAi)
    }
}
