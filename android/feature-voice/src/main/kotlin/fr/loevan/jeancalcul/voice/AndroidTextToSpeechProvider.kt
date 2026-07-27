package fr.loevan.jeancalcul.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import fr.loevan.jeancalcul.domain.TextToSpeechError
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.domain.TextToSpeechRequest
import fr.loevan.jeancalcul.domain.VoiceProviderDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale
import java.util.UUID

/** Android [TextToSpeech] adapter with lazy initialization and explicit shutdown ownership. */
class AndroidTextToSpeechProvider(
    context: Context,
) : TextToSpeechProvider {
    private val applicationContext = context.applicationContext
    private val mutableEvents = MutableSharedFlow<TextToSpeechEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private var synthesizer: TextToSpeech? = null
    private var pendingRequest: TextToSpeechRequest? = null
    private var ready = false
    private var released = false

    override val descriptor =
        VoiceProviderDescriptor(
            id = ID,
            displayName = "Synthese vocale Android",
            supportsPartialResults = false,
            isOnDevice = true,
        )
    override val events: Flow<TextToSpeechEvent> = mutableEvents.asSharedFlow()

    override fun isAvailable(): Boolean = !released

    override fun speak(request: TextToSpeechRequest) {
        if (released) {
            emit(TextToSpeechEvent.Error(TextToSpeechError.UNAVAILABLE))
            return
        }
        pendingRequest = request
        val current = synthesizer
        if (current == null) {
            synthesizer = TextToSpeech(applicationContext, ::onInitialized)
        } else if (ready) {
            speakPending(current)
        }
    }

    override fun stop() {
        pendingRequest = null
        synthesizer?.stop()
        emit(TextToSpeechEvent.Stopped)
    }

    override fun release() {
        if (released) return
        released = true
        pendingRequest = null
        synthesizer?.stop()
        synthesizer?.shutdown()
        synthesizer = null
        ready = false
    }

    private fun onInitialized(status: Int) {
        if (released) return
        ready = status == TextToSpeech.SUCCESS
        val current = synthesizer
        if (!ready || current == null) {
            emit(TextToSpeechEvent.Error(TextToSpeechError.UNAVAILABLE))
            return
        }
        emit(TextToSpeechEvent.Ready)
        speakPending(current)
    }

    private fun speakPending(current: TextToSpeech) {
        val request = pendingRequest ?: return
        val locale = Locale.forLanguageTag(request.locale.languageTag)
        val availability = current.isLanguageAvailable(locale)
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            pendingRequest = null
            emit(TextToSpeechEvent.Error(TextToSpeechError.LANGUAGE_UNSUPPORTED))
            return
        }
        current.language = locale
        current.setOnUtteranceProgressListener(progressListener)
        pendingRequest = null
        val result = current.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        if (result == TextToSpeech.ERROR) {
            emit(TextToSpeechEvent.Error(TextToSpeechError.SYNTHESIS_FAILED))
        }
    }

    private val progressListener =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                emit(TextToSpeechEvent.Started)
            }

            override fun onDone(utteranceId: String?) {
                emit(TextToSpeechEvent.Completed)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                emit(TextToSpeechEvent.Error(TextToSpeechError.SYNTHESIS_FAILED))
            }

            override fun onError(
                utteranceId: String?,
                errorCode: Int,
            ) {
                emit(TextToSpeechEvent.Error(TextToSpeechError.SYNTHESIS_FAILED))
            }

            override fun onStop(
                utteranceId: String?,
                interrupted: Boolean,
            ) {
                emit(TextToSpeechEvent.Stopped)
            }
        }

    private fun emit(event: TextToSpeechEvent) {
        mutableEvents.tryEmit(event)
    }

    companion object {
        const val ID = "android.text_to_speech"
        private const val EVENT_BUFFER_CAPACITY = 8
    }
}
