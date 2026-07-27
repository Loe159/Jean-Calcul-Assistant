package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationTest {
    @Test
    fun `direct model session cannot contain an agent backend reference`() {
        assertThrows(IllegalArgumentException::class.java) {
            AssistantSession(
                id = "session",
                conversationId = "conversation",
                kind = AssistantSessionKind.MODEL,
                modelProfileId = "model-profile",
                agentBackendSessionId = "remote-session",
                createdAtEpochMillis = 1,
            )
        }
    }

    @Test
    fun `agent session keeps remote reference outside messages`() {
        val session =
            AssistantSession(
                id = "session",
                conversationId = "conversation",
                kind = AssistantSessionKind.AGENT,
                agentProfileId = "agent-profile",
                agentBackendSessionId = "remote-session",
                lastAgentEventSequence = 4,
                createdAtEpochMillis = 1,
            )

        val message =
            Message(
                id = "message",
                conversationId = "conversation",
                assistantSessionId = session.id,
                role = MessageRole.ASSISTANT,
                text = "Termine",
                status = MessageStatus.COMPLETED,
                sequence = 1,
                createdAtEpochMillis = 2,
            )

        assertEquals("remote-session", session.agentBackendSessionId)
        assertEquals(session.id, message.assistantSessionId)
    }

    @Test
    fun `only pending or streaming messages may have empty text`() {
        assertThrows(IllegalArgumentException::class.java) {
            Message(
                id = "message",
                conversationId = "conversation",
                role = MessageRole.ASSISTANT,
                text = "",
                status = MessageStatus.COMPLETED,
                sequence = 1,
                createdAtEpochMillis = 1,
            )
        }
    }
}
