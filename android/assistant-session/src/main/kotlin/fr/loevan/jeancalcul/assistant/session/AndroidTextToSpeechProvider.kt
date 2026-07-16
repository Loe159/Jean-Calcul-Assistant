package fr.loevan.jeancalcul.assistant.session

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/** Android [TextToSpeech] adapter with explicit stop and shutdown ownership. */
internal class AndroidTextToSpeechProvider(
    context: Context,
) : TextToSpeechProvider {
    private val mutableEvents = MutableSharedFlow<TextToSpeechEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    private var isReady = false
    private val synthesizer: TextToSpeech = TextToSpeech(context.applicationContext, ::onInitialized)

    override val events: Flow<TextToSpeechEvent> = mutableEvents.asSharedFlow()

    init {
        synthesizer.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    emit(TextToSpeechEvent.Started)
                }

                override fun onDone(utteranceId: String?) {
                    emit(TextToSpeechEvent.Completed)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    emit(TextToSpeechEvent.Error("La synthese vocale a echoue."))
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int,
                ) {
                    emit(TextToSpeechEvent.Error("La synthese vocale a echoue."))
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean,
                ) {
                    emit(TextToSpeechEvent.Stopped)
                }
            },
        )
    }

    override fun speak(text: String) {
        if (!isReady) {
            emit(TextToSpeechEvent.Error("La synthese vocale n'est pas prete."))
            return
        }

        val result =
            synthesizer.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                UUID.randomUUID().toString(),
            )
        if (result == TextToSpeech.ERROR) {
            emit(TextToSpeechEvent.Error("La synthese vocale ne peut pas lire cette reponse."))
        }
    }

    override fun stop() {
        if (isReady) {
            synthesizer.stop()
        }
        emit(TextToSpeechEvent.Stopped)
    }

    override fun release() {
        synthesizer.stop()
        synthesizer.shutdown()
        isReady = false
    }

    private fun onInitialized(status: Int) {
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            emit(TextToSpeechEvent.Ready)
        } else {
            emit(TextToSpeechEvent.Error("La synthese vocale Android est indisponible."))
        }
    }

    private fun emit(event: TextToSpeechEvent) {
        mutableEvents.tryEmit(event)
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 8
    }
}
