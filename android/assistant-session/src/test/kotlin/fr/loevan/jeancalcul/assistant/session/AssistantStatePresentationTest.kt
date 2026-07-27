package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.ui.ActionCardState
import fr.loevan.jeancalcul.ui.GradientOrbState
import fr.loevan.jeancalcul.ui.PrivacyIndicatorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AssistantStatePresentationTest {
    @Test
    fun `every domain state has an explicit orb representation`() {
        val expected =
            mapOf(
                AssistantState.Idle to GradientOrbState.Idle,
                AssistantState.Invoked to GradientOrbState.Invoked,
                AssistantState.Listening to GradientOrbState.Listening,
                AssistantState.Transcribing to GradientOrbState.Transcribing,
                AssistantState.Thinking to GradientOrbState.Thinking,
                AssistantState.ProposingAction("action") to GradientOrbState.ProposingAction,
                AssistantState.WaitingApproval("action") to GradientOrbState.WaitingApproval,
                AssistantState.Executing("action") to GradientOrbState.Executing,
                AssistantState.Speaking("response") to GradientOrbState.Speaking,
                AssistantState.Completed to GradientOrbState.Completed,
                AssistantState.Cancelled("cancelled") to GradientOrbState.Cancelled,
                AssistantState.Error("error") to GradientOrbState.Error,
            )

        expected.forEach { (state, orb) ->
            val presentation = VoiceSessionState(assistantState = state).presentation()
            assertEquals(orb, presentation.orbState)
            assertNotNull(presentation.title)
        }
    }

    @Test
    fun `microphone indicator is active only while listening`() {
        assertEquals(
            PrivacyIndicatorState.MicrophoneActive,
            VoiceSessionState(assistantState = AssistantState.Listening).presentation().microphoneState,
        )

        nonListeningStates.forEach { state ->
            assertEquals(
                "Unexpected microphone indicator for $state",
                PrivacyIndicatorState.MicrophoneInactive,
                VoiceSessionState(assistantState = state).presentation().microphoneState,
            )
        }
    }

    @Test
    fun `tool states expose an action card representation`() {
        assertEquals(
            ActionCardState.Proposed,
            VoiceSessionState(
                assistantState = AssistantState.ProposingAction("Set volume"),
            ).presentation().actionState,
        )
        assertEquals(
            ActionCardState.ConfirmationRequired,
            VoiceSessionState(
                assistantState = AssistantState.WaitingApproval("Set volume"),
            ).presentation().actionState,
        )
        assertEquals(
            ActionCardState.Executing,
            VoiceSessionState(
                assistantState = AssistantState.Executing("Set volume"),
            ).presentation().actionState,
        )
    }

    @Test
    fun `missing microphone permission has a dedicated recoverable representation`() {
        val presentation =
            VoiceSessionState(
                assistantState = AssistantState.Error("Permission required"),
                microphonePermissionRequired = true,
            ).presentation()

        assertEquals(GradientOrbState.Offline, presentation.orbState)
        assertEquals("Microphone requis", presentation.title)
    }

    private companion object {
        val nonListeningStates =
            listOf(
                AssistantState.Idle,
                AssistantState.Invoked,
                AssistantState.Transcribing,
                AssistantState.Thinking,
                AssistantState.ProposingAction("action"),
                AssistantState.WaitingApproval("action"),
                AssistantState.Executing("action"),
                AssistantState.Speaking("response"),
                AssistantState.Completed,
                AssistantState.Cancelled("cancelled"),
                AssistantState.Error("error"),
            )
    }
}
