package io.github.mangi.eta.agent.voice

import io.github.mangi.eta.data.model.AsrProvider
import io.github.mangi.eta.data.model.SpeechCredentials
import io.github.mangi.eta.data.model.SpeechSettings
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechCredentialsUnavailable
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class SpeechErrorCode {
    CONFIGURATION, CREDENTIALS, PERMISSION, AUDIO, NETWORK, TIMEOUT, AUTHENTICATION,
    RATE_LIMITED, SERVER, PROTOCOL, NO_SPEECH, BACKPRESSURE, STORAGE,
}

internal class SpeechFailure(val code: SpeechErrorCode, val userMessage: String) : IOException(code.name)

internal fun Throwable.speechFailure(): SpeechFailure = when (this) {
    is SpeechFailure -> this
    is SpeechCredentialsUnavailable -> SpeechFailure(SpeechErrorCode.CREDENTIALS, "Could not decrypt speech credentials. Enter and save them again.")
    is SecurityException -> SpeechFailure(SpeechErrorCode.PERMISSION, "Allow Jean-Calcul to use the microphone")
    is kotlinx.coroutines.TimeoutCancellationException -> SpeechFailure(SpeechErrorCode.TIMEOUT, "Speech service timed out. Try again.")
    is IOException -> SpeechFailure(SpeechErrorCode.NETWORK, "Could not connect to the speech service. Check your network.")
    else -> SpeechFailure(SpeechErrorCode.PROTOCOL, "Speech service returned unsupported data")
}

internal fun validateSpeechSettings(
    settings: SpeechSettings,
    secrets: SpeechCredentials,
    synthesis: Boolean,
    hasProviderDeepSeekKey: Boolean = false,
) {
    if (!synthesis && !settings.recognitionEnabled) return
    fun required(value: String, name: String) {
        if (value.isBlank()) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Configure first: $name")
    }
    if (synthesis) {
        when (settings.tts) {
            TtsProvider.NONE -> throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Configure speech synthesis in settings first")
            TtsProvider.QWEN -> {
                required(secrets.qwenTts, "Qwen speech API key")
                required(settings.qwenVoice, "Voice")
                speechBaseUrl(settings.qwenTts.endpoint())
            }
            TtsProvider.DOUBAO -> {
                required(secrets.doubaoTts, "Doubao speech credentials")
                required(settings.doubaoVoice, "Voice")
                speechBaseUrl(settings.doubaoTts.baseUrl)
                if (settings.doubaoTts.legacyAuth) required(settings.doubaoTts.appId, "Doubao app ID")
            }
            TtsProvider.GEMINI -> {
                required(secrets.geminiTts, "Gemini speech API key")
                required(settings.geminiTts.voice, "Voice")
                speechBaseUrl(settings.geminiTts.baseUrl)
                if (settings.geminiTts.style.length > 500) {
                    throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Reading style must be at most 500 characters")
                }
            }
            TtsProvider.MINIMAX -> {
                required(secrets.minimaxTts, "MiniMax speech API key")
                required(settings.minimaxTts.voice, "Voice")
                speechBaseUrl(settings.minimaxTts.baseUrl)
                if (settings.minimaxTts.speed !in 0.5f..2f) {
                    throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "MiniMax speech speed must be between 0.5 and 2")
                }
            }
            TtsProvider.MIMO -> {
                required(secrets.mimoTts, "MiMo speech API key")
                required(settings.mimoTts.voice, "Voice")
                speechBaseUrl(settings.mimoTts.baseUrl)
                if (settings.mimoTts.style.length > 500) {
                    throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Reading style must be at most 500 characters")
                }
            }
            TtsProvider.DEEPSEEK -> {
                if (!hasProviderDeepSeekKey) required(secrets.deepseekTts, "DeepSeek speech API key")
                required(settings.deepseekTts.voice, "Voice")
                speechBaseUrl(settings.deepseekTts.baseUrl)
            }
        }
    } else when (settings.asr) {
        AsrProvider.MIMO -> {
            required(secrets.mimoAsr, "MiMo recognition API key")
            speechBaseUrl(settings.mimoAsr.baseUrl)
        }
        AsrProvider.DEEPSEEK -> {
            if (!hasProviderDeepSeekKey) required(secrets.deepseekAsr, "DeepSeek recognition API key")
            speechBaseUrl(settings.deepseekAsr.baseUrl)
        }
        AsrProvider.DOUBAO -> {
            required(secrets.doubaoAsr, "Doubao recognition credentials")
            speechBaseUrl(settings.doubaoAsr.baseUrl)
            if (settings.doubaoAsr.legacyAuth) required(settings.doubaoAsr.appId, "Doubao app ID")
        }
        AsrProvider.QWEN_REALTIME,
        AsrProvider.QWEN_FLASH,
        AsrProvider.QWEN_FILE,
        -> {
            required(secrets.qwenAsr, "Qwen recognition API key")
            speechBaseUrl(settings.qwenAsr.endpoint())
            if (settings.asr == AsrProvider.QWEN_FILE) {
                required(settings.oss.bucket, "OSS Bucket")
                required(settings.oss.region, "OSS region")
                required(secrets.ossAccessKeyId, "OSS AccessKey ID")
                required(secrets.ossAccessKeySecret, "OSS AccessKey Secret")
                speechBaseUrl(settings.oss.endpoint)
                if (!Regex("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]").matches(settings.oss.bucket) ||
                    settings.oss.prefix.split('/').any { it == ".." || it == "." }
                ) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Check the OSS bucket and directory prefix")
            }
        }
    }
}

internal fun speechBaseUrl(value: String): String {
    val url = value.trim().toHttpUrlOrNull()
    if (url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
        url.query != null || url.fragment != null
    ) throw SpeechFailure(SpeechErrorCode.CONFIGURATION, "Service URL must use HTTPS without credentials or query parameters")
    return url.toString().trimEnd('/')
}
