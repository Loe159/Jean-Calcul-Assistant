package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantEffect
import fr.loevan.jeancalcul.domain.AssistantEvent
import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.domain.AssistantStateMachine
import fr.loevan.jeancalcul.domain.AssistantTimeout
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

/**
 * Adapts voice, local command and synthesis work to the platform-neutral assistant state machine.
 * All external work is started from reducer effects, never from transition logic.
 */
@Suppress("TooManyFunctions")
internal class VoiceSessionController(
    private val speechToTextProvider: SpeechToTextProvider,
    private val textToSpeechProvider: TextToSpeechProvider,
    private val voiceCommandProcessor: VoiceCommandProcessor = NoOpVoiceCommandProcessor,
    private val performanceTrace: PerformanceTrace = NoOpPerformanceTrace,
    private val stateMachine: AssistantStateMachine = AssistantStateMachine(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(VoiceSessionState())
    private var timeoutJob: Job? = null
    private var isClosed = false

    val state: StateFlow<VoiceSessionState> = mutableState.asStateFlow()
    val assistantState: StateFlow<AssistantState> = stateMachine.state

    init {
        scope.launch { speechToTextProvider.events.collect(::handleSpeechEvent) }
        scope.launch { textToSpeechProvider.events.collect(::handleSynthesisEvent) }
    }

    fun invoke() {
        prepareStableState()
        dispatch(AssistantEvent.Invoke)
    }

    fun requireMicrophonePermission() {
        dispatch(
            AssistantEvent.Fail(
                message = "Autorisez le microphone dans l'application pour utiliser la voix.",
                recoverable = true,
            ),
        )
        mutableState.value =
            mutableState.value.copy(
                microphonePermissionRequired = true,
                message = "Autorisez le microphone dans l'application pour utiliser la voix.",
            )
    }

    fun startListening() {
        prepareForInteraction()
        mutableState.value =
            mutableState.value.copy(
                partialTranscript = "",
                finalResult = null,
                confirmationPrompt = null,
                microphonePermissionRequired = false,
            )
        dispatch(AssistantEvent.StartListening)
    }

    fun stopListening() {
        dispatch(AssistantEvent.SpeechEnded)
    }

    fun speakTestResponse() {
        prepareForInteraction()
        dispatch(AssistantEvent.SpeakRequested(TEST_RESPONSE))
    }

    fun confirmPendingCommand() {
        dispatch(AssistantEvent.ApprovalGranted)
    }

    fun updateTextFallback(text: String) {
        mutableState.value = mutableState.value.copy(partialTranscript = text)
    }

    fun submitTextFallback() {
        val text = mutableState.value.partialTranscript.trim()
        if (text.isEmpty()) return

        prepareForInteraction()
        mutableState.value =
            mutableState.value.copy(
                finalResult = SpeechRecognitionResult(text = text, confidence = null),
                confirmationPrompt = null,
                microphonePermissionRequired = false,
            )
        dispatch(AssistantEvent.TextSubmitted(text))
    }

    fun cancelActiveWork() {
        dispatch(AssistantEvent.Cancel("Interaction annulee par l'utilisateur."))
    }

    fun interruptActiveWork() {
        dispatch(AssistantEvent.Interrupt("Interaction interrompue par l'utilisateur."))
    }

    fun reportRecoverableError(message: String) {
        dispatch(AssistantEvent.Fail(message, recoverable = true))
    }

    fun close() {
        if (isClosed) return

        isClosed = true
        clearTimeout()
        cancelProvidersAndPending()
        speechToTextProvider.release()
        textToSpeechProvider.release()
        scope.cancel()
    }

    private fun handleSpeechEvent(event: SpeechToTextEvent) {
        when (event) {
            SpeechToTextEvent.Ready -> performanceTrace.mark(PerformanceTraceEvent.MICROPHONE_READY)
            SpeechToTextEvent.SpeechStarted -> performanceTrace.mark(PerformanceTraceEvent.SPEECH_STARTED)
            is SpeechToTextEvent.Partial -> {
                if (assistantState.value == AssistantState.Listening) {
                    performanceTrace.mark(PerformanceTraceEvent.FIRST_TRANSCRIPTION)
                    mutableState.value = mutableState.value.copy(partialTranscript = event.text)
                }
            }

            SpeechToTextEvent.EndOfSpeech -> dispatch(AssistantEvent.SpeechEnded)
            is SpeechToTextEvent.Final -> handleFinalTranscription(event.result)
            is SpeechToTextEvent.Error -> showSpeechError(event.error)
        }
    }

    private fun handleFinalTranscription(result: SpeechRecognitionResult) {
        if (assistantState.value == AssistantState.Listening) {
            dispatch(AssistantEvent.SpeechEnded)
        }
        if (assistantState.value != AssistantState.Transcribing) return

        performanceTrace.mark(PerformanceTraceEvent.FINAL_RESULT)
        mutableState.value =
            mutableState.value.copy(
                partialTranscript = result.text,
                finalResult = result,
            )
        dispatch(AssistantEvent.TranscriptionCompleted(result.text))
    }

    private fun handleSynthesisEvent(event: TextToSpeechEvent) {
        when (event) {
            TextToSpeechEvent.Ready,
            TextToSpeechEvent.Started,
            -> Unit

            TextToSpeechEvent.Completed -> dispatch(AssistantEvent.SpeechCompleted)
            TextToSpeechEvent.Stopped -> Unit
            is TextToSpeechEvent.Error -> dispatch(AssistantEvent.Fail(event.message))
        }
    }

    private fun dispatch(event: AssistantEvent) {
        val transition = stateMachine.dispatch(event)
        if (!transition.accepted) return

        mutableState.value =
            mutableState.value.copy(
                assistantState = transition.to,
                message = transition.to.defaultMessage(),
            )
        transition.effects.forEach(::handleEffect)
    }

    private fun handleEffect(effect: AssistantEffect) {
        when (effect) {
            AssistantEffect.StartSpeechRecognition -> speechToTextProvider.startListening()
            AssistantEffect.StopSpeechRecognition -> speechToTextProvider.stopListening()
            is AssistantEffect.RequestResponse -> handleCommandOutcome(voiceCommandProcessor.process(effect.input))
            is AssistantEffect.PresentAction -> Unit
            is AssistantEffect.RequestApproval -> {
                mutableState.value = mutableState.value.copy(confirmationPrompt = effect.summary)
            }

            is AssistantEffect.ExecuteAction -> handleCommandOutcome(voiceCommandProcessor.confirm())
            is AssistantEffect.Speak -> textToSpeechProvider.speak(effect.text)
            AssistantEffect.CancelActiveWork,
            AssistantEffect.InterruptActiveWork,
            -> cancelProvidersAndPending()

            is AssistantEffect.ScheduleTimeout -> scheduleTimeout(effect.timeout)
            AssistantEffect.CancelTimeout -> clearTimeout()
        }
    }

    private fun handleCommandOutcome(outcome: VoiceCommandOutcome) {
        when (outcome) {
            is VoiceCommandOutcome.Completed -> {
                val event =
                    if (assistantState.value is AssistantState.Executing) {
                        AssistantEvent.ActionCompleted(outcome.response)
                    } else {
                        AssistantEvent.ResponseReady(outcome.response)
                    }
                mutableState.value = mutableState.value.copy(confirmationPrompt = null)
                dispatch(event)
            }

            is VoiceCommandOutcome.ConfirmationRequired -> {
                dispatch(AssistantEvent.ActionProposed(outcome.prompt))
                dispatch(AssistantEvent.ApprovalRequired)
            }

            is VoiceCommandOutcome.Invalid -> dispatch(AssistantEvent.ResponseReady(outcome.message))
            is VoiceCommandOutcome.Failure -> dispatch(AssistantEvent.Fail(outcome.message))
        }
    }

    private fun showSpeechError(error: SpeechToTextError) {
        dispatch(AssistantEvent.Fail(error.message()))
    }

    private fun scheduleTimeout(timeout: AssistantTimeout) {
        clearTimeout()
        timeoutJob =
            scope.launch {
                delay(timeout.durationMillis)
                dispatch(AssistantEvent.Timeout(timeout))
            }
    }

    private fun clearTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun cancelProvidersAndPending() {
        speechToTextProvider.cancel()
        textToSpeechProvider.stop()
        voiceCommandProcessor.cancelPending()
    }

    private fun prepareForInteraction() {
        when (assistantState.value) {
            AssistantState.Idle,
            AssistantState.Completed,
            -> dispatch(AssistantEvent.Invoke)

            is AssistantState.Cancelled,
            is AssistantState.Error,
            -> {
                dispatch(AssistantEvent.Recover)
                dispatch(AssistantEvent.Invoke)
            }

            AssistantState.Invoked -> Unit
            else -> dispatch(AssistantEvent.Interrupt())
        }
    }

    private fun prepareStableState() {
        if (assistantState.value is AssistantState.Cancelled || assistantState.value is AssistantState.Error) {
            dispatch(AssistantEvent.Recover)
        }
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

    private fun AssistantState.defaultMessage(): String =
        when (this) {
            AssistantState.Idle -> "Assistant pret."
            AssistantState.Invoked -> "Preparation de l'interaction."
            AssistantState.Listening -> "Parlez maintenant."
            AssistantState.Transcribing -> "Traitement de la transcription."
            AssistantState.Thinking -> "Preparation de la reponse."
            is AssistantState.ProposingAction -> summary
            is AssistantState.WaitingApproval -> summary
            is AssistantState.Executing -> "Execution de l'action : $summary"
            is AssistantState.Speaking -> text
            AssistantState.Completed -> "Interaction terminee."
            is AssistantState.Cancelled -> reason
            is AssistantState.Error -> message
        }

    private companion object {
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
