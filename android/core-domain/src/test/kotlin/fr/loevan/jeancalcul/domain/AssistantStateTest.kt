package fr.loevan.jeancalcul.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantStateTest {
    @Test
    fun `every assistant state is serializable`() {
        val states =
            listOf(
                AssistantState.Idle,
                AssistantState.Invoked,
                AssistantState.Listening,
                AssistantState.Transcribing,
                AssistantState.Thinking,
                AssistantState.ProposingAction("Set volume to 30 percent"),
                AssistantState.WaitingApproval("Set volume to 30 percent"),
                AssistantState.Executing("Set volume to 30 percent"),
                AssistantState.Speaking("Done"),
                AssistantState.Completed,
                AssistantState.Cancelled("User cancelled"),
                AssistantState.Error("Provider unavailable"),
            )

        states.forEach { state ->
            val encoded = Json.encodeToString<AssistantState>(state)
            assertEquals(state, Json.decodeFromString<AssistantState>(encoded))
        }
    }
}
