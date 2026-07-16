package fr.loevan.jeancalcul.assistant.session

/** Visual states supported before speech recognition is introduced. */
enum class AssistantSessionVisualState {
    INVOKED,
    LISTENING,
    ERROR,
}

internal enum class AssistantSessionEvent {
    PREPARED,
    SHOWN,
    RECOVERABLE_ERROR,
}

/**
 * Keeps session state transitions explicit and independent from the Android window lifecycle.
 */
internal object AssistantSessionStateReducer {
    fun reduce(event: AssistantSessionEvent): AssistantSessionVisualState =
        when (event) {
            AssistantSessionEvent.PREPARED -> AssistantSessionVisualState.INVOKED
            AssistantSessionEvent.SHOWN -> AssistantSessionVisualState.LISTENING
            AssistantSessionEvent.RECOVERABLE_ERROR -> AssistantSessionVisualState.ERROR
        }
}
