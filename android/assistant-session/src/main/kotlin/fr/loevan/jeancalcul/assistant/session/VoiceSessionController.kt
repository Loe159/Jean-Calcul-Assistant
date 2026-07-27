package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantEffect
import fr.loevan.jeancalcul.domain.AssistantEvent
import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.domain.AssistantStateMachine
import fr.loevan.jeancalcul.domain.AssistantTimeout
import fr.loevan.jeancalcul.domain.AudioAmplitudeSource
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextError
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.SpeechToTextRequest
import fr.loevan.jeancalcul.domain.TextToSpeechError
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.domain.TextToSpeechRequest
import fr.loevan.jeancalcul.domain.VoiceActivity
import fr.loevan.jeancalcul.domain.VoiceActivityDetector
import fr.loevan.jeancalcul.domain.VoiceAudioFocusController
import fr.loevan.jeancalcul.domain.VoiceAudioInterruption
import fr.loevan.jeancalcul.domain.VoiceAudioRoute
import fr.loevan.jeancalcul.domain.VoiceAudioRouteSource
import fr.loevan.jeancalcul.domain.VoiceAudioUse
import fr.loevan.jeancalcul.domain.VoiceLocale
import fr.loevan.jeancalcul.feature.conversation.VoiceConversationRecorder
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Adapts voice, local command and synthesis work to the platform-neutral assistant state machine.
 * All external work is started from reducer effects, never from transition logic.
 */
@Suppress("TooManyFunctions", "LongParameterList")
internal class VoiceSessionController(
    private val speechToTextProvider: SpeechToTextProvider,
    private val textToSpeechProvider: TextToSpeechProvider,
    private val amplitudeSource: AudioAmplitudeSource = NoOpAudioAmplitudeSource,
    private val activityDetector: VoiceActivityDetector = NoOpVoiceActivityDetector,
    private val audioFocusController: VoiceAudioFocusController = NoOpVoiceAudioFocusController,
    private val audioRouteSource: VoiceAudioRouteSource = NoOpVoiceAudioRouteSource,
    private val voiceCommandProcessor: VoiceCommandProcessor = NoOpVoiceCommandProcessor,
    private val performanceTrace: PerformanceTrace = NoOpPerformanceTrace,
    private val conversationRecorder: VoiceConversationRecorder = NoOpVoiceConversationRecorder,
    private val stateMachine: AssistantStateMachine = AssistantStateMachine(),
    initialLocaleTag: String = Locale.getDefault().toLanguageTag(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow(VoiceSessionState(localeTag = initialLocaleTag))
    private var timeoutJob: Job? = null
    private var resumeListeningAfterInterruption = false
    private var isClosed = false

    val state: StateFlow<VoiceSessionState> = mutableState.asStateFlow()
    val assistantState: StateFlow<AssistantState> = stateMachine.state

    init {
        scope.launch { speechToTextProvider.events.collect(::handleSpeechEvent) }
        scope.launch { textToSpeechProvider.events.collect(::handleSynthesisEvent) }
        scope.launch {
            amplitudeSource.amplitude.collect { amplitude ->
                mutableState.value = mutableState.value.copy(microphoneAmplitude = amplitude.coerceIn(0f, 1f))
            }
        }
        scope.launch {
            activityDetector.activity.collect { activity ->
                mutableState.value = mutableState.value.copy(voiceActivity = activity)
            }
        }
        scope.launch {
            audioRouteSource.route.collect { route ->
                mutableState.value = mutableState.value.copy(audioRoute = route)
            }
        }
        scope.launch { audioFocusController.interruptions.collect(::handleAudioInterruption) }
    }

    fun invoke() {
        prepareStableState()
        scope.launch { conversationRecorder.beginSession() }
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
        if (!speechToTextProvider.isAvailable()) {
            mutableState.value = mutableState.value.copy(voiceInputAvailable = false)
            dispatch(AssistantEvent.Fail("La reconnaissance vocale n'est pas disponible. Utilisez la saisie texte."))
            return
        }
        mutableState.value =
            mutableState.value.copy(
                partialTranscript = "",
                finalResult = null,
                confirmationPrompt = null,
                pendingPolicyDecision = null,
                microphonePermissionRequired = false,
                voiceInputAvailable = true,
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

    fun updateLocale(languageTag: String) {
        if (languageTag.isNotBlank()) mutableState.value = mutableState.value.copy(localeTag = languageTag)
    }

    fun submitTextFallback() {
        val text = mutableState.value.partialTranscript.trim()
        if (text.isEmpty()) return

        prepareForInteraction()
        mutableState.value =
            mutableState.value.copy(
                finalResult = SpeechRecognitionResult(text = text, confidence = null),
                confirmationPrompt = null,
                pendingPolicyDecision = null,
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
        resumeListeningAfterInterruption = false
        clearTimeout()
        cancelProvidersAndPending()
        speechToTextProvider.release()
        textToSpeechProvider.release()
        activityDetector.release()
        amplitudeSource.release()
        audioRouteSource.release()
        audioFocusController.release()
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

            TextToSpeechEvent.Completed -> {
                audioFocusController.abandon()
                dispatch(AssistantEvent.SpeechCompleted)
            }
            TextToSpeechEvent.Stopped -> Unit
            is TextToSpeechEvent.Error -> {
                audioFocusController.abandon()
                dispatch(AssistantEvent.Fail(event.error.message()))
            }
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
            AssistantEffect.StartSpeechRecognition -> startSpeechRecognition()
            AssistantEffect.StopSpeechRecognition -> stopSpeechRecognition()
            is AssistantEffect.RequestResponse -> {
                scope.launch { conversationRecorder.recordUserMessage(effect.input) }
                handleCommandOutcome(voiceCommandProcessor.process(effect.input))
            }
            is AssistantEffect.PresentAction -> Unit
            is AssistantEffect.RequestApproval -> {
                mutableState.value = mutableState.value.copy(confirmationPrompt = effect.summary)
            }

            is AssistantEffect.ExecuteAction -> handleCommandOutcome(voiceCommandProcessor.confirm())
            is AssistantEffect.Speak -> {
                scope.launch { conversationRecorder.recordAssistantMessage(effect.text) }
                speak(effect.text)
            }
            AssistantEffect.CancelActiveWork,
            AssistantEffect.InterruptActiveWork,
            -> {
                scope.launch { conversationRecorder.recordInterruption() }
                cancelProvidersAndPending()
            }

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
                mutableState.value =
                    mutableState.value.copy(
                        confirmationPrompt = null,
                        pendingPolicyDecision = null,
                    )
                dispatch(event)
            }

            is VoiceCommandOutcome.ApprovalRequired -> {
                val summary = outcome.decision.exactSummary()
                mutableState.value = mutableState.value.copy(pendingPolicyDecision = outcome.decision)
                dispatch(AssistantEvent.ActionProposed(summary))
                dispatch(AssistantEvent.ApprovalRequired)
            }

            is VoiceCommandOutcome.PermissionRequired -> {
                val summary = outcome.decision.exactSummary()
                mutableState.value = mutableState.value.copy(pendingPolicyDecision = outcome.decision)
                dispatch(AssistantEvent.ActionProposed(summary))
                dispatch(AssistantEvent.ApprovalRequired)
            }

            is VoiceCommandOutcome.Invalid -> dispatch(AssistantEvent.ResponseReady(outcome.message))
            is VoiceCommandOutcome.Failure -> dispatch(AssistantEvent.Fail(outcome.message))
        }
    }

    private fun showSpeechError(error: SpeechToTextError) {
        if (error == SpeechToTextError.UNAVAILABLE) {
            mutableState.value = mutableState.value.copy(voiceInputAvailable = false)
        }
        dispatch(AssistantEvent.Fail(error.message()))
    }

    private fun startSpeechRecognition() {
        if (!audioFocusController.request(VoiceAudioUse.RECOGNITION)) {
            dispatch(AssistantEvent.Fail("Le microphone est utilise par une autre application."))
            return
        }
        audioRouteSource.start()
        amplitudeSource.start()
        activityDetector.start()
        speechToTextProvider.startListening(
            SpeechToTextRequest(locale = VoiceLocale(mutableState.value.localeTag)),
        )
    }

    private fun stopSpeechRecognition() {
        speechToTextProvider.stopListening()
        activityDetector.stop()
        amplitudeSource.stop()
        audioRouteSource.stop()
        audioFocusController.abandon()
    }

    private fun speak(text: String) {
        if (!textToSpeechProvider.isAvailable()) {
            dispatch(AssistantEvent.Fail("La synthese vocale Android est indisponible."))
            return
        }
        if (!audioFocusController.request(VoiceAudioUse.SYNTHESIS)) {
            dispatch(AssistantEvent.Fail("La sortie audio est utilisee par une autre application."))
            return
        }
        textToSpeechProvider.speak(
            TextToSpeechRequest(text = text, locale = VoiceLocale(mutableState.value.localeTag)),
        )
    }

    private fun handleAudioInterruption(interruption: VoiceAudioInterruption) {
        when (interruption) {
            VoiceAudioInterruption.LOST_TRANSIENT -> {
                resumeListeningAfterInterruption = assistantState.value == AssistantState.Listening
                if (assistantState.value.isInterruptible()) {
                    dispatch(AssistantEvent.Interrupt("Audio interrompu temporairement."))
                }
            }

            VoiceAudioInterruption.LOST_PERMANENT -> {
                resumeListeningAfterInterruption = false
                if (assistantState.value.isInterruptible()) {
                    dispatch(AssistantEvent.Fail("L'audio a ete interrompu par le systeme."))
                }
            }

            VoiceAudioInterruption.GAINED -> {
                if (resumeListeningAfterInterruption && assistantState.value == AssistantState.Invoked) {
                    resumeListeningAfterInterruption = false
                    startListening()
                }
            }
        }
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
        activityDetector.stop()
        amplitudeSource.stop()
        audioRouteSource.stop()
        audioFocusController.abandon()
        voiceCommandProcessor.cancelPending()
        mutableState.value = mutableState.value.copy(pendingPolicyDecision = null, confirmationPrompt = null)
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
            SpeechToTextError.PERMISSION_DENIED -> "La permission microphone est refusee."
            SpeechToTextError.AUDIO -> "Le microphone n'est pas disponible."
            SpeechToTextError.BUSY -> "La reconnaissance vocale est deja utilisee."
            SpeechToTextError.CLIENT -> "La reconnaissance vocale a ete interrompue."
            SpeechToTextError.NETWORK -> "La reconnaissance vocale a rencontre une erreur reseau."
            SpeechToTextError.LANGUAGE_UNSUPPORTED -> "La langue selectionnee n'est pas prise en charge."
            SpeechToTextError.NO_MATCH -> "Aucune parole n'a ete reconnue."
            SpeechToTextError.TIMEOUT -> "Le delai de reconnaissance a expire."
            SpeechToTextError.UNKNOWN -> "La reconnaissance vocale a echoue."
        }

    private fun TextToSpeechError.message(): String =
        when (this) {
            TextToSpeechError.UNAVAILABLE -> "La synthese vocale Android est indisponible."
            TextToSpeechError.NOT_READY -> "La synthese vocale n'est pas prete."
            TextToSpeechError.LANGUAGE_UNSUPPORTED -> "La langue selectionnee n'est pas disponible pour la voix."
            TextToSpeechError.SYNTHESIS_FAILED -> "La synthese vocale a echoue."
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

private object NoOpAudioAmplitudeSource : AudioAmplitudeSource {
    override val amplitude: StateFlow<Float> = MutableStateFlow(0f)

    override fun start() = Unit

    override fun stop() = Unit

    override fun release() = Unit
}

private object NoOpVoiceActivityDetector : VoiceActivityDetector {
    override val activity: StateFlow<VoiceActivity> = MutableStateFlow(VoiceActivity.SILENCE)

    override fun start() = Unit

    override fun stop() = Unit

    override fun release() = Unit
}

private object NoOpVoiceAudioFocusController : VoiceAudioFocusController {
    override val interruptions = emptyFlow<VoiceAudioInterruption>()

    override fun request(audioUse: VoiceAudioUse) = true

    override fun abandon() = Unit

    override fun release() = Unit
}

private object NoOpVoiceAudioRouteSource : VoiceAudioRouteSource {
    override val route: StateFlow<VoiceAudioRoute> = MutableStateFlow(VoiceAudioRoute.UNKNOWN)

    override fun start() = Unit

    override fun stop() = Unit

    override fun release() = Unit
}

private object NoOpVoiceCommandProcessor : VoiceCommandProcessor {
    override fun process(transcript: String): VoiceCommandOutcome =
        VoiceCommandOutcome.Invalid("Aucune commande locale n'est configuree.")

    override fun confirm(): VoiceCommandOutcome =
        VoiceCommandOutcome.Invalid("Aucune action n'est en attente de confirmation.")

    override fun cancelPending() = Unit
}

private object NoOpVoiceConversationRecorder : VoiceConversationRecorder {
    override suspend fun beginSession() = Unit

    override suspend fun recordUserMessage(text: String) = Unit

    override suspend fun recordAssistantMessage(text: String) = Unit

    override suspend fun recordInterruption() = Unit
}

private fun fr.loevan.jeancalcul.domain.PolicyDecision.exactSummary(): String =
    buildString {
        append(summary.description)
        if (summary.parameters.isNotEmpty()) {
            append(" (")
            append(summary.parameters.joinToString { "${it.name}=${it.exactValue}" })
            append(")")
        }
    }

internal object NoOpPerformanceTrace : PerformanceTrace {
    override fun startInvocation() = Unit

    override fun mark(event: PerformanceTraceEvent) = Unit

    override fun captureMemory(checkpoint: String) = Unit

    override fun finishInvocation(reason: String) = Unit
}
