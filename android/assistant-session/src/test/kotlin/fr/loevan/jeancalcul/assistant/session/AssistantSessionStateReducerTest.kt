package fr.loevan.jeancalcul.assistant.session

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSessionStateReducerTest {
    @Test
    fun `preparing a session shows the invoked state`() {
        assertEquals(
            AssistantSessionVisualState.INVOKED,
            AssistantSessionStateReducer.reduce(AssistantSessionEvent.PREPARED),
        )
    }

    @Test
    fun `showing a session shows the listening state`() {
        assertEquals(
            AssistantSessionVisualState.LISTENING,
            AssistantSessionStateReducer.reduce(AssistantSessionEvent.SHOWN),
        )
    }

    @Test
    fun `recoverable errors show the error state`() {
        assertEquals(
            AssistantSessionVisualState.ERROR,
            AssistantSessionStateReducer.reduce(AssistantSessionEvent.RECOVERABLE_ERROR),
        )
    }
}
