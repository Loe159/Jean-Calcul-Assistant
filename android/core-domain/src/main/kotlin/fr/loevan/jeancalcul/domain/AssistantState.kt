package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Minimal shared contract for future UI and assistant components.
 *
 * The foundation intentionally defines no voice, Android service, or model behavior.
 */
@Serializable
sealed interface AssistantState {
    @Serializable
    data object Idle : AssistantState
}

/** A source of observable assistant state, independent of Android APIs. */
interface AssistantStateSource {
    val state: StateFlow<AssistantState>
}
