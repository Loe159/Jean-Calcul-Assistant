package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.OpenAiSpeechConfig
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.model.VllmSpeechConfig
import java.nio.ByteBuffer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpeechProtocolTest {
    @Test fun openAiTranscriptionPostsWavModelAndIsoLanguage() {
        val settings = SpeechSettings(asr = AsrProvider.OPENAI, language = "fr-FR")
        val request = transcriptionRequest(settings, SpeechCredentials(openAi = "test-key"), pcmToWav(byteArrayOf(0, 1)))
        assertEquals("https://api.openai.com/v1/audio/transcriptions", request.url.toString())
        assertEquals("Bearer test-key", request.header("Authorization"))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("name=\"model\"") && body.contains(OpenAiSpeechConfig.DEFAULT_TRANSCRIPTION_MODEL))
        assertTrue(Regex("name=\"language\"[\\s\\S]*?\r\n\r\nfr\r\n").containsMatchIn(body))
        assertTrue(body.contains("filename=\"speech.wav\""))
    }

    @Test fun vllmTranscriptionAllowsLanHttpAndOmitsEmptyKey() {
        val settings = SpeechSettings(
            asr = AsrProvider.VLLM,
            vllm = VllmSpeechConfig(baseUrl = "http://192.168.1.20:8000/v1/", transcriptionModel = "openai/whisper-large-v3"),
        )
        val request = transcriptionRequest(settings, SpeechCredentials(), pcmToWav(byteArrayOf(0, 1)))
        assertEquals("http://192.168.1.20:8000/v1/audio/transcriptions", request.url.toString())
        assertNull(request.header("Authorization"))
    }

    @Test fun transcriptionResponseRequiresText() {
        assertEquals("Bonjour", transcriptionText(JSONObject("""{"text":" Bonjour "}""")))
        assertEquals(SpeechErrorCode.SERVER, assertThrows(SpeechFailure::class.java) {
            transcriptionText(JSONObject("""{"error":{"message":"private"}}"""))
        }.code)
        assertEquals(SpeechErrorCode.PROTOCOL, assertThrows(SpeechFailure::class.java) {
            transcriptionText(JSONObject("{}"))
        }.code)
    }

    @Test fun speechRequestAsksForPcmWithVoiceAndInstructions() {
        val settings = SpeechSettings(
            tts = TtsProvider.OPENAI,
            openAi = OpenAiSpeechConfig(voice = "coral", instructions = "Calme"),
        )
        val request = speechRequest(settings, SpeechCredentials(openAi = "test-key"), "Salut")
        assertEquals("https://api.openai.com/v1/audio/speech", request.url.toString())
        val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertEquals("pcm", body.getString("response_format"))
        assertEquals("coral", body.getString("voice"))
        assertEquals("Calme", body.getString("instructions"))
        assertEquals("Salut", body.getString("input"))
    }

    @Test fun vllmSpeechRequestSkipsBlankVoiceAndInstructions() {
        val settings = SpeechSettings(
            tts = TtsProvider.VLLM,
            vllm = VllmSpeechConfig(baseUrl = "http://tts.lan:8000/v1", speechModel = "kokoro"),
            openAi = OpenAiSpeechConfig(instructions = "ignored"),
        )
        val body = JSONObject(Buffer().also { speechRequest(settings, SpeechCredentials(vllm = "k"), "x").body!!.writeTo(it) }.readUtf8())
        assertEquals("kokoro", body.getString("model"))
        assertFalse(body.has("voice"))
        assertFalse(body.has("instructions"))
    }

    @Test fun vllmAddressRejectsCredentialsAndParameters() {
        for (address in listOf("http://user:pass@host/v1", "http://host/v1?x=1", "http://host/v1#frag", "not a url")) {
            assertEquals(SpeechErrorCode.CONFIGURATION, assertThrows(SpeechFailure::class.java) { vllmBaseUrl(address) }.code)
        }
        assertEquals("https://host.example/v1", vllmBaseUrl(" https://host.example/v1/ "))
    }

    @Test fun languageCodeIsReducedForWhisper() {
        assertEquals("fr", SpeechSettings(language = "fr-FR").transcriptionLanguage())
        assertEquals("en", SpeechSettings(language = "EN").transcriptionLanguage())
        assertNull(SpeechSettings(language = "").transcriptionLanguage { null })
        assertEquals("fr", SpeechSettings(language = "").transcriptionLanguage { "fr-FR" })
        assertEquals("en", SpeechSettings(language = "en-US").transcriptionLanguage { "fr-FR" })
    }

    @Test fun voiceFollowsPhoneLanguageNotAppLocale() {
        assertEquals("fr-FR", speechLanguageTag("", system = { "fr-FR" }))
        assertEquals("de-DE", speechLanguageTag(" de-DE ", system = { "fr-FR" }))
    }

    @Test fun wavHeaderDescribesRecordedPcm() {
        val wav = pcmToWav(byteArrayOf(0, 1, 2, 3))
        val header = ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", wav.take(4).toByteArray().toString(Charsets.US_ASCII))
        assertEquals(40, header.getInt(4))
        assertEquals(16000, header.getInt(24))
        assertEquals(4, header.getInt(40))
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), wav.copyOfRange(44, 48))
    }

    @Test fun recordingCollectionRejectsEmptyAudio() {
        val frames = Channel<ByteArray>(1).apply { close() }
        val error = assertThrows(SpeechFailure::class.java) {
            runBlocking { collectSpeechAudio(frames) }
        }
        assertEquals(SpeechErrorCode.NO_SPEECH, error.code)
    }

    @Test fun recordingCollectionAcceptsItsLimitButRejectsAdditionalFrames() {
        val pcm = ByteArray(SpeechRecorder.MAX_BYTES)
        val complete = Channel<ByteArray>(1).apply { trySend(pcm); close() }
        val wav = runBlocking { collectSpeechAudio(complete) }
        assertEquals(pcm.size + 44, wav.size)

        val oversized = Channel<ByteArray>(2).apply {
            trySend(pcm)
            trySend(byteArrayOf(0, 0))
            close()
        }
        val error = assertThrows(SpeechFailure::class.java) {
            runBlocking { collectSpeechAudio(oversized) }
        }
        assertEquals(SpeechErrorCode.AUDIO, error.code)
    }

    @Test fun endpointDetectorWaitsForSpeechThenSilence() {
        val detector = SpeechEndpointDetector(silenceMs = 1_000, frameMs = 200)
        repeat(3) { assertEquals(SpeechEndpointDetector.Decision.CONTINUE, detector.accept(0.01f)) }
        repeat(4) { assertEquals(SpeechEndpointDetector.Decision.CONTINUE, detector.accept(0.4f)) }
        repeat(4) { assertEquals(SpeechEndpointDetector.Decision.CONTINUE, detector.accept(0.01f)) }
        assertEquals(SpeechEndpointDetector.Decision.END, detector.accept(0.01f))
        assertFalse(detector.silent)
    }

    @Test fun endpointDetectorStopsWhenNobodySpeaks() {
        val detector = SpeechEndpointDetector(silenceMs = 1_000, frameMs = 200)
        repeat(39) { assertEquals(SpeechEndpointDetector.Decision.CONTINUE, detector.accept(0.02f)) }
        assertEquals(SpeechEndpointDetector.Decision.NO_SPEECH, detector.accept(0.02f))
        assertTrue(detector.silent)
    }

    @Test fun endpointDetectorIgnoresSteadyBackgroundNoise() {
        val detector = SpeechEndpointDetector(silenceMs = 600, frameMs = 200)
        repeat(5) { detector.accept(0.08f) }
        repeat(3) { detector.accept(0.5f) }
        repeat(2) { assertEquals(SpeechEndpointDetector.Decision.CONTINUE, detector.accept(0.08f)) }
        assertEquals(SpeechEndpointDetector.Decision.END, detector.accept(0.08f))
    }

    @Test fun manualDictationNeverEndsOnSilence() {
        val detector = SpeechEndpointDetector(silenceMs = 0)
        repeat(100) { assertEquals(SpeechEndpointDetector.Decision.CONTINUE, detector.accept(0f)) }
    }
}
