package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** Platform-neutral lifecycle exposed to every assistant surface. */
@Serializable
sealed interface AssistantState {
    @Serializable
    data object Idle : AssistantState

    @Serializable
    data object Invoked : AssistantState

    @Serializable
    data object Listening : AssistantState

    @Serializable
    data object Transcribing : AssistantState

    @Serializable
    data object Thinking : AssistantState

    @Serializable
    data class ProposingAction(val summary: String) : AssistantState

    @Serializable
    data class WaitingApproval(val summary: String) : AssistantState

    @Serializable
    data class Executing(val summary: String) : AssistantState

    @Serializable
    data class Speaking(val text: String) : AssistantState

    @Serializable
    data object Completed : AssistantState

    @Serializable
    data class Cancelled(val reason: String) : AssistantState

    @Serializable
    data class Error(
        val message: String,
        val recoverable: Boolean = true,
    ) : AssistantState
}

/** A source of observable assistant state, independent of Android APIs. */
interface AssistantStateSource {
    val state: StateFlow<AssistantState>
}
