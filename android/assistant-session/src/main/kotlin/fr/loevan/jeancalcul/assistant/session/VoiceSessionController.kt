package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextError
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.observability.PerformanceTrace
import fr.loevan.jeancalcul.observability.PerformanceTraceEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordinates recognition, synthesis, cancellation and timeouts for one assistant session. */
@Suppress("TooManyFunctions")
internal class VoiceSessionController(
    private val speechToTextProvider: SpeechToTextProvider,
    private val textToSpeechProvider: TextToSpeechProvider,
    private val voiceCommandProcessor: VoiceCommandProcessor = NoOpVoiceCommandProcessor,
    private val performanceTrace: PerformanceTrace = NoOpPerformanceTrace,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(VoiceSessionState())
    private var timeoutJob: Job? = null
    private var isClosed = false

    val state: StateFlow<VoiceSessionState> = mutableState.asStateFlow()

    init {
        scope.launch { speechToTextProvider.events.collect(::handleSpeechEvent) }
        scope.launch { textToSpeechProvider.events.collect(::handleSynthesisEvent) }
    }

    fun requireMicrophonePermission() {
        clearTimeout()
        mutableState.value =
            VoiceSessionState(
                status = VoiceSessionStatus.PERMISSION_REQUIRED,
                message = "Autorisez le microphone dans l'application pour utiliser la voix.",
            )
    }

    fun startListening() {
        clearTimeout()
        if (
            mutableState.value.status == VoiceSessionStatus.LISTENING ||
            mutableState.value.status == VoiceSessionStatus.PROCESSING
        ) {
            speechToTextProvider.cancel()
        }
        mutableState.value =
            VoiceSessionState(
                status = VoiceSessionStatus.LISTENING,
                message = "Parlez maintenant.",
            )
        speechToTextProvider.startListening()
        scheduleTimeout(
            delayMillis = LISTENING_TIMEOUT_MILLIS,
            message = "Aucune parole detectee. Reessayez.",
        )
    }

    fun stopListening() {
        if (mutableState.value.status != VoiceSessionStatus.LISTENING) return

        speechToTextProvider.stopListening()
        waitForFinalResult()
    }

    fun speakTestResponse() {
        clearTimeout()
        mutableState.value =
            mutableState.value.copy(
                status = VoiceSessionStatus.SPEAKING,
                message = TEST_RESPONSE,
            )
        textToSpeechProvider.speak(TEST_RESPONSE)
    }

    fun confirmPendingCommand() {
        if (mutableState.value.status != VoiceSessionStatus.CONFIRMATION_REQUIRED) return
        handleCommandOutcome(voiceCommandProcessor.confirm())
    }

    fun updateTextFallback(text: String) {
        mutableState.value = mutableState.value.copy(partialTranscript = text)
    }

    fun submitTextFallback() {
        val text = mutableState.value.partialTranscript.trim()
        if (text.isEmpty()) return

        clearTimeout()
        speechToTextProvider.cancel()
        mutableState.value =
            mutableState.value.copy(
                status = VoiceSessionStatus.INVOKED,
                finalResult = SpeechRecognitionResult(text = text, confidence = null),
                message = "Texte utilise comme transcription.",
            )
    }

    fun cancelActiveWork() {
        clearTimeout()
        speechToTextProvider.cancel()
        textToSpeechProvider.stop()
        voiceCommandProcessor.cancelPending()
        mutableState.value =
            VoiceSessionState(
                status = VoiceSessionStatus.INVOKED,
                message = "Interaction vocale annulee.",
            )
    }

    fun close() {
        if (isClosed) return

        isClosed = true
        clearTimeout()
        speechToTextProvider.cancel()
        textToSpeechProvider.stop()
        speechToTextProvider.release()
        textToSpeechProvider.release()
        scope.cancel()
    }

    private fun handleSpeechEvent(event: SpeechToTextEvent) {
        when (event) {
            SpeechToTextEvent.Ready -> performanceTrace.mark(PerformanceTraceEvent.MICROPHONE_READY)
            SpeechToTextEvent.SpeechStarted -> performanceTrace.mark(PerformanceTraceEvent.SPEECH_STARTED)
            is SpeechToTextEvent.Partial -> {
                if (mutableState.value.status == VoiceSessionStatus.LISTENING) {
                    performanceTrace.mark(PerformanceTraceEvent.FIRST_TRANSCRIPTION)
                    mutableState.value = mutableState.value.copy(partialTranscript = event.text)
                }
            }

            SpeechToTextEvent.EndOfSpeech -> waitForFinalResult()
            is SpeechToTextEvent.Final -> {
                clearTimeout()
                performanceTrace.mark(PerformanceTraceEvent.FINAL_RESULT)
                mutableState.value =
                    mutableState.value.copy(
                        partialTranscript = event.result.text,
                        finalResult = event.result,
                    )
                handleCommandOutcome(voiceCommandProcessor.process(event.result.text))
            }

            is SpeechToTextEvent.Error -> showSpeechError(event.error)
        }
    }

    private fun handleSynthesisEvent(event: TextToSpeechEvent) {
        when (event) {
            TextToSpeechEvent.Ready -> Unit
            TextToSpeechEvent.Started -> {
                mutableState.value = mutableState.value.copy(status = VoiceSessionStatus.SPEAKING)
            }

            TextToSpeechEvent.Completed,
            TextToSpeechEvent.Stopped,
            -> {
                if (mutableState.value.status == VoiceSessionStatus.SPEAKING) {
                    mutableState.value = mutableState.value.copy(status = VoiceSessionStatus.INVOKED)
                }
            }

            is TextToSpeechEvent.Error -> {
                mutableState.value =
                    mutableState.value.copy(
                        status = VoiceSessionStatus.ERROR,
                        message = event.message,
                    )
            }
        }
    }

    private fun waitForFinalResult() {
        if (mutableState.value.status != VoiceSessionStatus.LISTENING) return

        clearTimeout()
        mutableState.value =
            mutableState.value.copy(
                status = VoiceSessionStatus.PROCESSING,
                message = "Traitement de la transcription.",
            )
        scheduleTimeout(
            delayMillis = FINAL_RESULT_TIMEOUT_MILLIS,
            message = "La transcription a expire. Reessayez.",
        )
    }

    private fun handleCommandOutcome(outcome: VoiceCommandOutcome) {
        when (outcome) {
            is VoiceCommandOutcome.Completed -> {
                mutableState.value =
                    mutableState.value.copy(
                        status = VoiceSessionStatus.SPEAKING,
                        confirmationPrompt = null,
                        message = outcome.response,
                    )
                textToSpeechProvider.speak(outcome.response)
            }

            is VoiceCommandOutcome.ConfirmationRequired -> {
                mutableState.value =
                    mutableState.value.copy(
                        status = VoiceSessionStatus.CONFIRMATION_REQUIRED,
                        confirmationPrompt = outcome.prompt,
                        message = outcome.prompt,
                    )
            }

            is VoiceCommandOutcome.Invalid -> {
                mutableState.value =
                    mutableState.value.copy(
                        status = VoiceSessionStatus.INVOKED,
                        confirmationPrompt = null,
                        message = outcome.message,
                    )
            }

            is VoiceCommandOutcome.Failure -> {
                mutableState.value =
                    mutableState.value.copy(
                        status = VoiceSessionStatus.ERROR,
                        confirmationPrompt = null,
                        message = outcome.message,
                    )
            }
        }
    }

    private fun showSpeechError(error: SpeechToTextError) {
        clearTimeout()
        mutableState.value =
            mutableState.value.copy(
                status = VoiceSessionStatus.ERROR,
                message = error.message(),
            )
    }

    private fun scheduleTimeout(
        delayMillis: Long,
        message: String,
    ) {
        timeoutJob =
            scope.launch {
                delay(delayMillis)
                if (
                    mutableState.value.status == VoiceSessionStatus.LISTENING ||
                    mutableState.value.status == VoiceSessionStatus.PROCESSING
                ) {
                    speechToTextProvider.cancel()
                    mutableState.value =
                        mutableState.value.copy(
                            status = VoiceSessionStatus.ERROR,
                            message = message,
                        )
                }
            }
    }

    private fun clearTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun SpeechToTextError.message(): String =
        when (this) {
            SpeechToTextError.UNAVAILABLE -> "La reconnaissance vocale n'est pas disponible."
            SpeechToTextError.AUDIO -> "Le microphone n'est pas disponible."
            SpeechToTextError.CLIENT -> "La reconnaissance vocale a ete interrompue."
            SpeechToTextError.NETWORK -> "La reconnaissance vocale a rencontre une erreur reseau."
            SpeechToTextError.NO_MATCH -> "Aucune parole n'a ete reconnue."
            SpeechToTextError.TIMEOUT -> "Le delai de reconnaissance a expire."
            SpeechToTextError.UNKNOWN -> "La reconnaissance vocale a echoue."
        }

    private companion object {
        const val LISTENING_TIMEOUT_MILLIS = 15_000L
        const val FINAL_RESULT_TIMEOUT_MILLIS = 5_000L
        const val TEST_RESPONSE = "La reponse vocale de test fonctionne."
    }
}

private object NoOpVoiceCommandProcessor : VoiceCommandProcessor {
    override fun process(transcript: String): VoiceCommandOutcome =
        VoiceCommandOutcome.Invalid("Aucune commande locale n'est configuree.")

    override fun confirm(): VoiceCommandOutcome =
        VoiceCommandOutcome.Invalid("Aucune action n'est en attente de confirmation.")

    override fun cancelPending() = Unit
}

internal object NoOpPerformanceTrace : PerformanceTrace {
    override fun startInvocation() = Unit

    override fun mark(event: PerformanceTraceEvent) = Unit

    override fun captureMemory(checkpoint: String) = Unit

    override fun finishInvocation(reason: String) = Unit
}
