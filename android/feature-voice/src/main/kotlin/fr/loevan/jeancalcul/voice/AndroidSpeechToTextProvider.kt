package fr.loevan.jeancalcul.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextError
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.SpeechToTextRequest
import fr.loevan.jeancalcul.domain.VoiceProviderDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Android [SpeechRecognizer] adapter owned by the visible assistant session. */
@Suppress("TooManyFunctions")
class AndroidSpeechToTextProvider internal constructor(
    private val context: Context,
    private val amplitudeSource: AndroidAudioAmplitudeSource,
) : SpeechToTextProvider {
    private val mutableEvents =
        MutableSharedFlow<SpeechToTextEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private var recognizer: SpeechRecognizer? = null
    private var released = false

    override val descriptor =
        VoiceProviderDescriptor(
            id = ID,
            displayName = "Reconnaissance vocale Android",
            supportsPartialResults = true,
            isOnDevice = true,
        )
    override val events: Flow<SpeechToTextEvent> = mutableEvents.asSharedFlow()

    override fun isAvailable(): Boolean = !released && SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening(request: SpeechToTextRequest) {
        if (!isAvailable()) {
            emit(SpeechToTextEvent.Error(SpeechToTextError.UNAVAILABLE))
            return
        }

        cancelRecognizer()
        try {
            amplitudeSource.start()
            recognizerOrCreate().startListening(recognitionIntent(request))
        } catch (_: SecurityException) {
            fail(SpeechToTextError.PERMISSION_DENIED)
        } catch (_: IllegalStateException) {
            fail(SpeechToTextError.CLIENT)
        }
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        cancelRecognizer()
        amplitudeSource.stop()
    }

    override fun release() {
        if (released) return
        released = true
        cancel()
    }

    private fun recognizerOrCreate(): SpeechRecognizer =
        recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { createdRecognizer ->
            recognizer = createdRecognizer
            createdRecognizer.setRecognitionListener(listener)
        }

    private val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                emit(SpeechToTextEvent.Ready)
            }

            override fun onBeginningOfSpeech() {
                emit(SpeechToTextEvent.SpeechStarted)
            }

            override fun onRmsChanged(rmsdB: Float) {
                amplitudeSource.updateRms(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                amplitudeSource.stop()
                emit(SpeechToTextEvent.EndOfSpeech)
            }

            override fun onError(error: Int) {
                fail(error.toDomainError())
            }

            override fun onResults(results: Bundle?) {
                val result = resultFrom(results)
                if (result == null) {
                    fail(SpeechToTextError.NO_MATCH)
                } else {
                    emit(SpeechToTextEvent.Final(result))
                    releaseRecognizer()
                    amplitudeSource.stop()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                transcriptFrom(partialResults)?.let { emit(SpeechToTextEvent.Partial(it)) }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }

    private fun recognitionIntent(request: SpeechToTextRequest): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.locale.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, request.partialResults)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                request.completeSilenceMillis,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                request.possibleSilenceMillis,
            )
        }

    private fun resultFrom(results: Bundle?): SpeechRecognitionResult? {
        val text = transcriptFrom(results) ?: return null
        val confidence =
            results
                ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                ?.firstOrNull()
                ?.takeIf { it >= 0f }
        return SpeechRecognitionResult(text = text, confidence = confidence)
    }

    private fun transcriptFrom(results: Bundle?): String? =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf(String::isNotBlank)

    private fun fail(error: SpeechToTextError) {
        emit(SpeechToTextEvent.Error(error))
        cancelRecognizer()
        amplitudeSource.stop()
    }

    private fun cancelRecognizer() {
        recognizer?.cancel()
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun Int.toDomainError(): SpeechToTextError =
        when (this) {
            SpeechRecognizer.ERROR_AUDIO -> SpeechToTextError.AUDIO
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechToTextError.PERMISSION_DENIED
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            -> SpeechToTextError.BUSY

            SpeechRecognizer.ERROR_CLIENT -> SpeechToTextError.CLIENT
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            -> SpeechToTextError.NETWORK

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> SpeechToTextError.LANGUAGE_UNSUPPORTED

            SpeechRecognizer.ERROR_NO_MATCH -> SpeechToTextError.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechToTextError.TIMEOUT
            else -> SpeechToTextError.UNKNOWN
        }

    private fun emit(event: SpeechToTextEvent) {
        mutableEvents.tryEmit(event)
    }

    companion object {
        const val ID = "android.speech_recognizer"
        private const val EVENT_BUFFER_CAPACITY = 16
    }
}
