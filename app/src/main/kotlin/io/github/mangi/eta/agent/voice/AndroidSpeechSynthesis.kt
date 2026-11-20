package io.github.mangi.eta.agent.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Lecture par le moteur de synthèse du téléphone (Google, Samsung, constructeur…). Aucune clé, aucun serveur ;
 * les segments sont mis en file dans le moteur, qui gère lui-même la sortie audio.
 */
internal suspend fun speakOnDevice(
    context: Context,
    language: String,
    chunks: List<String>,
    onStart: () -> Unit,
) {
    val engine = openEngine(context.applicationContext)
    try {
        val locale = Locale.forLanguageTag(speechLanguageTag(language))
        when (engine.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> throw SpeechFailure(
                SpeechErrorCode.UNAVAILABLE,
                "The phone speech engine has no voice for this language. Install one in Android text-to-speech settings.",
            )
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        val ids = chunks.map { UUID.randomUUID().toString() }
        val started = CompletableDeferred<Unit>()
        suspendCancellableCoroutine { continuation ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (started.complete(Unit)) onStart()
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == ids.last() && continuation.isActive) continuation.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            SpeechFailure(SpeechErrorCode.AUDIO, "The phone speech engine failed (code $errorCode)"),
                        )
                    }
                }
            })
            continuation.invokeOnCancellation { runCatching { engine.stop() } }
            chunks.forEachIndexed { index, chunk ->
                val result = engine.speak(chunk, TextToSpeech.QUEUE_ADD, Bundle(), ids[index])
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resumeWithException(
                        SpeechFailure(SpeechErrorCode.AUDIO, "The phone speech engine rejected the text"),
                    )
                    return@suspendCancellableCoroutine
                }
            }
        }
    } finally {
        runCatching { engine.stop() }
        engine.shutdown()
    }
}

private suspend fun openEngine(context: Context): TextToSpeech {
    val ready = CompletableDeferred<Int>()
    val engine = TextToSpeech(context) { status -> ready.complete(status) }
    val status = try {
        ready.await()
    } catch (error: Throwable) {
        engine.shutdown()
        throw error
    }
    if (status != TextToSpeech.SUCCESS) {
        engine.shutdown()
        throw SpeechFailure(
            SpeechErrorCode.UNAVAILABLE,
            "No text-to-speech engine is available on this phone. Choose OpenAI or vLLM for spoken answers.",
        )
    }
    return engine
}
