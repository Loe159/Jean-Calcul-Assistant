package io.github.mangi.eta.agent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Reconnaissance faite par le téléphone : Jean-Calcul ne lit pas le micro lui-même sur ce chemin. */
internal interface DeviceSpeechRecognition {
    suspend fun recognize(
        language: String,
        endpointSilenceMs: Int,
        onReady: () -> Unit,
        onLevel: (Float) -> Unit,
        onPreview: (String) -> Unit,
    ): String

    /** Arrête l’écoute et demande le résultat final. */
    fun finish()
}

/**
 * Utilise d’abord le moteur hors ligne d’Android (`createOnDeviceSpeechRecognizer`), puis le service de
 * reconnaissance système d’une autre application (Google, constructeur). Jean-Calcul étant l’assistant par
 * défaut, son propre `EtaRecognitionService` est exclu pour ne pas se rappeler lui-même.
 */
internal class AndroidSpeechRecognition(context: Context) : DeviceSpeechRecognition {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var active: SpeechRecognizer? = null

    override suspend fun recognize(
        language: String,
        endpointSilenceMs: Int,
        onReady: () -> Unit,
        onLevel: (Float) -> Unit,
        onPreview: (String) -> Unit,
    ): String = withContext(Dispatchers.Main.immediate) {
        val routes = AndroidRecognizerRoute.available(context)
        if (routes.isEmpty()) {
            throw SpeechFailure(
                SpeechErrorCode.UNAVAILABLE,
                "No speech recognition engine on this phone. Choose OpenAI or vLLM in speech settings.",
            )
        }
        val intent = recognizerIntent(language.ifBlank { Locale.getDefault().toLanguageTag() }, endpointSilenceMs)
        var failure: SpeechFailure? = null
        for (route in routes) {
            when (val attempt = listen(route, intent, onReady, onLevel, onPreview)) {
                is Attempt.Done -> return@withContext attempt.text
                is Attempt.Retry -> {
                    AndroidAgentLogger.warn("Jean-Calcul device recognition fallback: route=${route.name} error=${attempt.error}")
                    failure = attempt.failure
                }
            }
        }
        throw failure ?: SpeechFailure(SpeechErrorCode.UNAVAILABLE, "Speech recognition is unavailable")
    }

    override fun finish() {
        main.post { runCatching { active?.stopListening() } }
    }

    private suspend fun listen(
        route: AndroidRecognizerRoute,
        intent: Intent,
        onReady: () -> Unit,
        onLevel: (Float) -> Unit,
        onPreview: (String) -> Unit,
    ): Attempt = suspendCancellableCoroutine { continuation ->
        val recognizer = try {
            route.create(context)
        } catch (error: RuntimeException) {
            continuation.resume(Attempt.Retry(SpeechErrorCode.UNAVAILABLE.failure(), SpeechRecognizer.ERROR_CLIENT))
            return@suspendCancellableCoroutine
        }
        active = recognizer
        var released = false
        var heard = false
        fun release() {
            if (released) return
            released = true
            if (active === recognizer) active = null
            runCatching { recognizer.destroy() }
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onReady()
            override fun onBeginningOfSpeech() { heard = true }
            override fun onRmsChanged(rmsdB: Float) = onLevel(rmsLevel(rmsdB))
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                bestResult(partialResults)?.let(onPreview)
            }

            override fun onResults(results: Bundle?) {
                release()
                if (continuation.isActive) continuation.resume(Attempt.Done(bestResult(results).orEmpty()))
            }

            override fun onError(error: Int) {
                release()
                if (!continuation.isActive) return
                val failure = recognitionFailure(error)
                if (!heard && error in RETRYABLE_ERRORS) {
                    if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && route == AndroidRecognizerRoute.ON_DEVICE) {
                        requestLanguageDownload(intent)
                    }
                    continuation.resume(Attempt.Retry(failure, error))
                } else {
                    continuation.resumeWithException(failure)
                }
            }
        })
        continuation.invokeOnCancellation {
            main.post {
                runCatching { recognizer.cancel() }
                release()
            }
        }
        try {
            recognizer.startListening(intent)
        } catch (error: RuntimeException) {
            release()
            if (continuation.isActive) {
                continuation.resume(Attempt.Retry(SpeechErrorCode.UNAVAILABLE.failure(), SpeechRecognizer.ERROR_CLIENT))
            }
        }
    }

    /** Le pack de langue hors ligne manque : Android propose de le télécharger pendant qu’on bascule de moteur. */
    private fun requestLanguageDownload(intent: Intent) {
        runCatching {
            val downloader = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            downloader.triggerModelDownload(intent)
            main.postDelayed({ runCatching { downloader.destroy() } }, 5_000)
        }
    }

    private sealed interface Attempt {
        class Done(val text: String) : Attempt
        class Retry(val failure: SpeechFailure, val error: Int) : Attempt
    }

    private companion object {
        val RETRYABLE_ERRORS = setOf(
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
        )
    }
}

internal enum class AndroidRecognizerRoute {
    ON_DEVICE,
    SYSTEM_SERVICE;

    fun create(context: Context): SpeechRecognizer = when (this) {
        ON_DEVICE -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        SYSTEM_SERVICE -> SpeechRecognizer.createSpeechRecognizer(
            context,
            SystemSpeechRecognizer.resolveExternalService(context)
                ?: throw IllegalStateException("No external recognition service"),
        )
    }

    companion object {
        fun available(context: Context): List<AndroidRecognizerRoute> = buildList {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) add(ON_DEVICE)
            if (SystemSpeechRecognizer.resolveExternalService(context) != null) add(SYSTEM_SERVICE)
        }
    }
}

internal fun recognizerIntent(language: String, endpointSilenceMs: Int): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .apply {
            if (endpointSilenceMs > 0) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, endpointSilenceMs.toLong())
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, endpointSilenceMs.toLong())
            } else {
                // Dictée et maintien du bouton : on laisse l’utilisateur marquer des pauses avant « Terminé ».
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, MANUAL_SILENCE_MS)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, MANUAL_SILENCE_MS)
            }
        }

private const val MANUAL_SILENCE_MS = 5_000L

/** `onRmsChanged` fournit environ −2 à 10 dB ; ramené sur l’échelle 0–1 du micro interne. */
internal fun rmsLevel(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)

internal fun bestResult(bundle: Bundle?): String? =
    bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)

private fun SpeechErrorCode.failure() = SpeechFailure(this, "Speech recognition is unavailable on this phone")

internal fun recognitionFailure(error: Int): SpeechFailure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
        SpeechFailure(SpeechErrorCode.NO_SPEECH, "No speech recognized. Try again.")
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        SpeechFailure(SpeechErrorCode.PERMISSION, "Allow Jean-Calcul to use the microphone")
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        SpeechFailure(SpeechErrorCode.NETWORK, "The phone recognizer needs a network or an offline language pack.")
    SpeechRecognizer.ERROR_AUDIO -> SpeechFailure(SpeechErrorCode.AUDIO, "Could not read the microphone")
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
        SpeechFailure(SpeechErrorCode.AUDIO, "The phone recognizer is busy. Try again.")
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS ->
        SpeechFailure(SpeechErrorCode.RATE_LIMITED, "Too many recognition requests. Try again later.")
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
        SpeechFailure(
            SpeechErrorCode.UNAVAILABLE,
            "This language is not installed for offline recognition. Download it in Android speech settings, or choose OpenAI or vLLM.",
        )
    else -> SpeechFailure(SpeechErrorCode.UNAVAILABLE, "Phone speech recognition failed (code $error). Try again or choose OpenAI or vLLM.")
}
