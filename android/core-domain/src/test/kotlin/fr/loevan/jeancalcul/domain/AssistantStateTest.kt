package fr.loevan.jeancalcul.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantStateTest {
    @Test
    fun `idle state is serializable`() {
        val encoded = Json.encodeToString<AssistantState>(AssistantState.Idle)

        assertEquals(AssistantState.Idle, Json.decodeFromString<AssistantState>(encoded))
    }
}
