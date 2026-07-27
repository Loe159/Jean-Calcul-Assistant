package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Inputs accepted by [AssistantStateMachine]. External work reports its result as another event. */
sealed interface AssistantEvent {
    data object Invoke : AssistantEvent

    data object StartListening : AssistantEvent

    data object SpeechEnded : AssistantEvent

    data class TranscriptionCompleted(val text: String) : AssistantEvent

    data class TextSubmitted(val text: String) : AssistantEvent

    data class ActionProposed(val summary: String) : AssistantEvent

    data object ApprovalRequired : AssistantEvent

    data object ActionAutoApproved : AssistantEvent

    data object ApprovalGranted : AssistantEvent

    data class ResponseReady(val text: String) : AssistantEvent

    data class ActionCompleted(val response: String) : AssistantEvent

    data class SpeakRequested(val text: String) : AssistantEvent

    data object SpeechCompleted : AssistantEvent

    data class Cancel(val reason: String = "Interaction cancelled") : AssistantEvent

    data class Interrupt(val reason: String = "Interaction interrupted") : AssistantEvent

    data class Fail(
        val message: String,
        val recoverable: Boolean = true,
    ) : AssistantEvent

    data class Timeout(val timeout: AssistantTimeout) : AssistantEvent

    data object Recover : AssistantEvent
}

/** Work requested by a transition. The reducer never performs these effects itself. */
sealed interface AssistantEffect {
    data object StartSpeechRecognition : AssistantEffect

    data object StopSpeechRecognition : AssistantEffect

    data class RequestResponse(val input: String) : AssistantEffect

    data class PresentAction(val summary: String) : AssistantEffect

    data class RequestApproval(val summary: String) : AssistantEffect

    data class ExecuteAction(val summary: String) : AssistantEffect

    data class Speak(val text: String) : AssistantEffect

    data object CancelActiveWork : AssistantEffect

    data object InterruptActiveWork : AssistantEffect

    data class ScheduleTimeout(val timeout: AssistantTimeout) : AssistantEffect

    data object CancelTimeout : AssistantEffect
}

enum class AssistantTimeout(val durationMillis: Long) {
    LISTENING(15_000L),
    TRANSCRIPTION(5_000L),
    RESPONSE(60_000L),
    ACTION_PROPOSAL(30_000L),
    APPROVAL(30_000L),
    EXECUTION(30_000L),
    SPEAKING(60_000L),
}

data class AssistantTransition(
    val from: AssistantState,
    val event: AssistantEvent,
    val to: AssistantState,
    val effects: List<AssistantEffect>,
    val accepted: Boolean,
)

/** Thread-safe observable state holder around the pure reducer. */
class AssistantStateMachine(
    initialState: AssistantState = AssistantState.Idle,
) : AssistantStateSource {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<AssistantState> = mutableState.asStateFlow()

    @Synchronized
    fun dispatch(event: AssistantEvent): AssistantTransition =
        AssistantStateReducer.reduce(mutableState.value, event).also { transition ->
            if (transition.accepted) mutableState.value = transition.to
        }
}

/** Deterministic transition table. Android, network and tool work is represented only as effects. */
object AssistantStateReducer {
    fun reduce(
        state: AssistantState,
        event: AssistantEvent,
    ): AssistantTransition {
        val reduction = next(state, event) ?: return rejected(state, event)
        val effects = buildEffects(state, reduction.state, reduction.effects)
        return AssistantTransition(state, event, reduction.state, effects, accepted = true)
    }

    private fun next(
        state: AssistantState,
        event: AssistantEvent,
    ): Reduction? =
        when {
            event.isVoiceInputEvent() -> nextVoiceInput(state, event)
            event.isRequestEvent() -> nextRequest(state, event)
            event.isActionPlanningEvent() -> nextActionPlanning(state, event)
            event.isActionResultEvent() -> nextActionResult(state, event)
            else -> nextLifecycle(state, event)
        }

    private fun nextVoiceInput(
        state: AssistantState,
        event: AssistantEvent,
    ): Reduction? =
        when (event) {
            AssistantEvent.Invoke ->
                if (state.isStable()) Reduction(AssistantState.Invoked) else null

            AssistantEvent.StartListening ->
                if (state == AssistantState.Invoked) {
                    Reduction(AssistantState.Listening, listOf(AssistantEffect.StartSpeechRecognition))
                } else {
                    null
                }

            AssistantEvent.SpeechEnded ->
                if (state == AssistantState.Listening) {
                    Reduction(AssistantState.Transcribing, listOf(AssistantEffect.StopSpeechRecognition))
                } else {
                    null
                }

            else -> null
        }

    private fun nextRequest(
        state: AssistantState,
        event: AssistantEvent,
    ): Reduction? =
        when (event) {
            is AssistantEvent.TranscriptionCompleted ->
                if (state == AssistantState.Transcribing && event.text.isNotBlank()) {
                    Reduction(
                        AssistantState.Thinking,
                        listOf(AssistantEffect.RequestResponse(event.text.trim())),
                    )
                } else {
                    null
                }

            is AssistantEvent.TextSubmitted ->
                if (state == AssistantState.Invoked && event.text.isNotBlank()) {
                    Reduction(
                        AssistantState.Thinking,
                        listOf(AssistantEffect.RequestResponse(event.text.trim())),
                    )
                } else {
                    null
                }

            is AssistantEvent.SpeakRequested ->
                if (state == AssistantState.Invoked && event.text.isNotBlank()) {
                    Reduction(
                        AssistantState.Speaking(event.text),
                        listOf(AssistantEffect.Speak(event.text)),
                    )
                } else {
                    null
                }

            else -> null
        }

    private fun nextActionPlanning(
        state: AssistantState,
        event: AssistantEvent,
    ): Reduction? =
        when (event) {
            is AssistantEvent.ActionProposed ->
                if (state == AssistantState.Thinking && event.summary.isNotBlank()) {
                    Reduction(
                        AssistantState.ProposingAction(event.summary),
                        listOf(AssistantEffect.PresentAction(event.summary)),
                    )
                } else {
                    null
                }

            AssistantEvent.ApprovalRequired ->
                if (state is AssistantState.ProposingAction) {
                    Reduction(
                        AssistantState.WaitingApproval(state.summary),
                        listOf(AssistantEffect.RequestApproval(state.summary)),
                    )
                } else {
                    null
                }

            AssistantEvent.ActionAutoApproved ->
                if (state is AssistantState.ProposingAction) {
                    Reduction(
                        AssistantState.Executing(state.summary),
                        listOf(AssistantEffect.ExecuteAction(state.summary)),
                    )
                } else {
                    null
                }

            AssistantEvent.ApprovalGranted ->
                if (state is AssistantState.WaitingApproval) {
                    Reduction(
                        AssistantState.Executing(state.summary),
                        listOf(AssistantEffect.ExecuteAction(state.summary)),
                    )
                } else {
                    null
                }

            else -> null
        }

    private fun nextActionResult(
        state: AssistantState,
        event: AssistantEvent,
    ): Reduction? =
        when (event) {
            is AssistantEvent.ResponseReady ->
                if (state == AssistantState.Thinking && event.text.isNotBlank()) {
                    Reduction(
                        AssistantState.Speaking(event.text),
                        listOf(AssistantEffect.Speak(event.text)),
                    )
                } else {
                    null
                }

            is AssistantEvent.ActionCompleted ->
                if (state is AssistantState.Executing && event.response.isNotBlank()) {
                    Reduction(
                        AssistantState.Speaking(event.response),
                        listOf(AssistantEffect.Speak(event.response)),
                    )
                } else {
                    null
                }

            AssistantEvent.SpeechCompleted ->
                if (state is AssistantState.Speaking) Reduction(AssistantState.Completed) else null

            else -> null
        }

    private fun nextLifecycle(
        state: AssistantState,
        event: AssistantEvent,
    ): Reduction? =
        when (event) {
            is AssistantEvent.Cancel ->
                if (state.isActive()) {
                    Reduction(
                        AssistantState.Cancelled(event.reason),
                        listOf(AssistantEffect.CancelActiveWork),
                    )
                } else {
                    null
                }

            is AssistantEvent.Interrupt ->
                if (state.isInterruptible()) {
                    Reduction(AssistantState.Invoked, listOf(AssistantEffect.InterruptActiveWork))
                } else {
                    null
                }

            is AssistantEvent.Fail ->
                if (state !is AssistantState.Error) {
                    Reduction(
                        AssistantState.Error(event.message, event.recoverable),
                        listOf(AssistantEffect.CancelActiveWork),
                    )
                } else {
                    null
                }

            is AssistantEvent.Timeout ->
                if (state.timeout() == event.timeout) {
                    Reduction(
                        AssistantState.Error(event.timeout.message(), recoverable = true),
                        listOf(AssistantEffect.CancelActiveWork),
                    )
                } else {
                    null
                }

            AssistantEvent.Recover ->
                if (state is AssistantState.Cancelled || state is AssistantState.Error) {
                    Reduction(AssistantState.Idle)
                } else {
                    null
                }

            else -> null
        }

    private fun buildEffects(
        from: AssistantState,
        to: AssistantState,
        transitionEffects: List<AssistantEffect>,
    ): List<AssistantEffect> =
        buildList {
            if (from.timeout() != null) add(AssistantEffect.CancelTimeout)
            to.timeout()?.let { add(AssistantEffect.ScheduleTimeout(it)) }
            addAll(transitionEffects)
        }

    private fun rejected(
        state: AssistantState,
        event: AssistantEvent,
    ) = AssistantTransition(state, event, state, emptyList(), accepted = false)
}

private fun AssistantEvent.isVoiceInputEvent(): Boolean =
    this == AssistantEvent.Invoke ||
        this == AssistantEvent.StartListening ||
        this == AssistantEvent.SpeechEnded

private fun AssistantEvent.isRequestEvent(): Boolean =
    this is AssistantEvent.TranscriptionCompleted ||
        this is AssistantEvent.TextSubmitted ||
        this is AssistantEvent.SpeakRequested

private fun AssistantEvent.isActionPlanningEvent(): Boolean =
    this is AssistantEvent.ActionProposed ||
        this == AssistantEvent.ApprovalRequired ||
        this == AssistantEvent.ActionAutoApproved ||
        this == AssistantEvent.ApprovalGranted

private fun AssistantEvent.isActionResultEvent(): Boolean =
    this is AssistantEvent.ResponseReady ||
        this is AssistantEvent.ActionCompleted ||
        this == AssistantEvent.SpeechCompleted

private fun AssistantState.isStable(): Boolean =
    this == AssistantState.Idle ||
        this == AssistantState.Completed ||
        this is AssistantState.Cancelled ||
        this is AssistantState.Error

private fun AssistantState.isActive(): Boolean = this == AssistantState.Invoked || isInterruptible()

private fun AssistantState.isInterruptible(): Boolean =
    this == AssistantState.Listening ||
        this == AssistantState.Transcribing ||
        this == AssistantState.Thinking ||
        this is AssistantState.ProposingAction ||
        this is AssistantState.WaitingApproval ||
        this is AssistantState.Executing ||
        this is AssistantState.Speaking

private fun AssistantState.timeout(): AssistantTimeout? =
    when (this) {
        AssistantState.Listening -> AssistantTimeout.LISTENING
        AssistantState.Transcribing -> AssistantTimeout.TRANSCRIPTION
        AssistantState.Thinking -> AssistantTimeout.RESPONSE
        is AssistantState.ProposingAction -> AssistantTimeout.ACTION_PROPOSAL
        is AssistantState.WaitingApproval -> AssistantTimeout.APPROVAL
        is AssistantState.Executing -> AssistantTimeout.EXECUTION
        is AssistantState.Speaking -> AssistantTimeout.SPEAKING
        else -> null
    }

private fun AssistantTimeout.message(): String =
    when (this) {
        AssistantTimeout.LISTENING -> "No speech was detected before the listening timeout."
        AssistantTimeout.TRANSCRIPTION -> "Speech transcription timed out."
        AssistantTimeout.RESPONSE -> "The assistant response timed out."
        AssistantTimeout.ACTION_PROPOSAL -> "The action proposal expired."
        AssistantTimeout.APPROVAL -> "The approval request expired."
        AssistantTimeout.EXECUTION -> "The action execution timed out."
        AssistantTimeout.SPEAKING -> "Speech synthesis timed out."
    }

private data class Reduction(
    val state: AssistantState,
    val effects: List<AssistantEffect> = emptyList(),
)
