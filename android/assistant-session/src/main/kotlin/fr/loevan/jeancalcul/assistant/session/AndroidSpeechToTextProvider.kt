package fr.loevan.jeancalcul.assistant.session

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale

/** Android [SpeechRecognizer] adapter used only while an assistant session is visible. */
internal class AndroidSpeechToTextProvider(
    private val context: Context,
) : SpeechToTextProvider {
    private val mutableEvents =
        MutableSharedFlow<SpeechToTextEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private var recognizer: SpeechRecognizer? = null

    override val events: Flow<SpeechToTextEvent> = mutableEvents.asSharedFlow()

    override fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            emit(SpeechToTextEvent.Error(SpeechToTextError.UNAVAILABLE))
            return
        }

        try {
            recognizerOrCreate().startListening(recognitionIntent())
        } catch (_: SecurityException) {
            emit(SpeechToTextEvent.Error(SpeechToTextError.AUDIO))
        } catch (_: IllegalStateException) {
            emit(SpeechToTextEvent.Error(SpeechToTextError.CLIENT))
        }
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognizerOrCreate(): SpeechRecognizer =
        recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { createdRecognizer ->
            createdRecognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        emit(SpeechToTextEvent.EndOfSpeech)
                    }

                    override fun onError(error: Int) {
                        emit(SpeechToTextEvent.Error(error.toDomainError()))
                    }

                    override fun onResults(results: Bundle?) {
                        resultFrom(results)?.let { result ->
                            emit(SpeechToTextEvent.Final(result))
                        } ?: emit(SpeechToTextEvent.Error(SpeechToTextError.NO_MATCH))
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        transcriptFrom(partialResults)?.let { transcript ->
                            emit(SpeechToTextEvent.Partial(transcript))
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?,
                    ) = Unit
                },
            )
        }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MILLIS)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLE_SILENCE_MILLIS,
            )
        }

    private fun resultFrom(results: Bundle?): SpeechRecognitionResult? {
        val text = transcriptFrom(results) ?: return null
        val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
        return SpeechRecognitionResult(text = text, confidence = confidence)
    }

    private fun transcriptFrom(results: Bundle?): String? =
        results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf(String::isNotBlank)

    private fun Int.toDomainError(): SpeechToTextError =
        when (this) {
            SpeechRecognizer.ERROR_AUDIO -> SpeechToTextError.AUDIO
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            -> SpeechToTextError.CLIENT

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            -> SpeechToTextError.NETWORK

            SpeechRecognizer.ERROR_NO_MATCH -> SpeechToTextError.NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechToTextError.TIMEOUT
            else -> SpeechToTextError.UNKNOWN
        }

    private fun emit(event: SpeechToTextEvent) {
        mutableEvents.tryEmit(event)
    }

    private companion object {
        const val COMPLETE_SILENCE_MILLIS = 1_500L
        const val POSSIBLE_SILENCE_MILLIS = 750L
        const val EVENT_BUFFER_CAPACITY = 8
    }
}
