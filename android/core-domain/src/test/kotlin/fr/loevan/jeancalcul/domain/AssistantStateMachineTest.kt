package fr.loevan.jeancalcul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStateMachineTest {
    @Test
    fun `voice path reaches completion through every voice state`() {
        val machine = AssistantStateMachine()

        assertTransition(machine, AssistantEvent.Invoke, AssistantState.Invoked)
        assertTransition(machine, AssistantEvent.StartListening, AssistantState.Listening)
        assertTransition(machine, AssistantEvent.SpeechEnded, AssistantState.Transcribing)
        val thinking = machine.dispatch(AssistantEvent.TranscriptionCompleted("Hello Jean"))
        assertEquals(AssistantState.Thinking, thinking.to)
        assertTrue(thinking.effects.contains(AssistantEffect.RequestResponse("Hello Jean")))
        val speaking = machine.dispatch(AssistantEvent.ResponseReady("Hello"))
        assertEquals(AssistantState.Speaking("Hello"), speaking.to)
        assertTrue(speaking.effects.contains(AssistantEffect.Speak("Hello")))
        assertTransition(machine, AssistantEvent.SpeechCompleted, AssistantState.Completed)
    }

    @Test
    fun `text path skips microphone and requests a response`() {
        val machine = AssistantStateMachine()
        machine.dispatch(AssistantEvent.Invoke)

        val transition = machine.dispatch(AssistantEvent.TextSubmitted("  typed request  "))

        assertEquals(AssistantState.Thinking, transition.to)
        assertTrue(transition.effects.contains(AssistantEffect.RequestResponse("typed request")))
    }

    @Test
    fun `tool path proposes approves executes and speaks result`() {
        val machine = thinkingMachine()

        assertTransition(
            machine,
            AssistantEvent.ActionProposed("Set media volume to 30 percent"),
            AssistantState.ProposingAction("Set media volume to 30 percent"),
        )
        assertTransition(
            machine,
            AssistantEvent.ApprovalRequired,
            AssistantState.WaitingApproval("Set media volume to 30 percent"),
        )
        val executing = machine.dispatch(AssistantEvent.ApprovalGranted)
        assertEquals(AssistantState.Executing("Set media volume to 30 percent"), executing.to)
        assertTrue(executing.effects.contains(AssistantEffect.ExecuteAction("Set media volume to 30 percent")))
        assertTransition(
            machine,
            AssistantEvent.ActionCompleted("Volume set"),
            AssistantState.Speaking("Volume set"),
        )
    }

    @Test
    fun `invalid transitions are refused without state or effects`() {
        val machine = AssistantStateMachine()

        val transition = machine.dispatch(AssistantEvent.ApprovalGranted)

        assertFalse(transition.accepted)
        assertEquals(AssistantState.Idle, transition.to)
        assertTrue(transition.effects.isEmpty())
        assertEquals(AssistantState.Idle, machine.state.value)
    }

    @Test
    fun `only invoked can enter listening`() {
        allStatesExcept(AssistantState.Invoked).forEach { state ->
            val transition = AssistantStateReducer.reduce(state, AssistantEvent.StartListening)
            assertFalse("Unexpected transition from $state", transition.accepted)
        }
    }

    @Test
    fun `every timed state accepts only its matching timeout`() {
        timedStates.forEach { (state, expectedTimeout) ->
            AssistantTimeout.entries.forEach { timeout ->
                val transition = AssistantStateReducer.reduce(state, AssistantEvent.Timeout(timeout))
                assertEquals("$state with $timeout", timeout == expectedTimeout, transition.accepted)
                if (timeout == expectedTimeout) assertTrue(transition.to is AssistantState.Error)
            }
        }
    }

    @Test
    fun `cancellation and errors recover to idle`() {
        val cancelled = AssistantStateMachine(AssistantState.Listening)
        val cancelTransition = cancelled.dispatch(AssistantEvent.Cancel("User cancelled"))
        assertEquals(AssistantState.Cancelled("User cancelled"), cancelTransition.to)
        assertTrue(cancelTransition.effects.contains(AssistantEffect.CancelActiveWork))
        assertTransition(cancelled, AssistantEvent.Recover, AssistantState.Idle)

        val failed = AssistantStateMachine(AssistantState.Thinking)
        assertTrue(failed.dispatch(AssistantEvent.Fail("Network unavailable")).to is AssistantState.Error)
        assertTransition(failed, AssistantEvent.Recover, AssistantState.Idle)
    }

    @Test
    fun `all interruptible states return to invoked`() {
        interruptibleStates.forEach { state ->
            val transition = AssistantStateReducer.reduce(state, AssistantEvent.Interrupt())
            assertTrue("Expected interruption from $state", transition.accepted)
            assertEquals(AssistantState.Invoked, transition.to)
            assertTrue(transition.effects.contains(AssistantEffect.InterruptActiveWork))
        }
    }

    @Test
    fun `state flow exposes accepted transitions`() {
        val machine = AssistantStateMachine()

        machine.dispatch(AssistantEvent.Invoke)
        machine.dispatch(AssistantEvent.StartListening)

        assertEquals(AssistantState.Listening, machine.state.value)
    }

    private fun thinkingMachine(): AssistantStateMachine =
        AssistantStateMachine().apply {
            dispatch(AssistantEvent.Invoke)
            dispatch(AssistantEvent.TextSubmitted("Change the volume"))
        }

    private fun assertTransition(
        machine: AssistantStateMachine,
        event: AssistantEvent,
        expected: AssistantState,
    ) {
        val transition = machine.dispatch(event)
        assertTrue(transition.accepted)
        assertEquals(expected, transition.to)
        assertEquals(expected, machine.state.value)
    }

    private fun allStatesExcept(excluded: AssistantState): List<AssistantState> = allStates.filterNot { it == excluded }

    private companion object {
        val allStates =
            listOf(
                AssistantState.Idle,
                AssistantState.Invoked,
                AssistantState.Listening,
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

        val interruptibleStates =
            listOf(
                AssistantState.Listening,
                AssistantState.Transcribing,
                AssistantState.Thinking,
                AssistantState.ProposingAction("action"),
                AssistantState.WaitingApproval("action"),
                AssistantState.Executing("action"),
                AssistantState.Speaking("response"),
            )

        val timedStates =
            mapOf(
                AssistantState.Listening to AssistantTimeout.LISTENING,
                AssistantState.Transcribing to AssistantTimeout.TRANSCRIPTION,
                AssistantState.Thinking to AssistantTimeout.RESPONSE,
                AssistantState.ProposingAction("action") to AssistantTimeout.ACTION_PROPOSAL,
                AssistantState.WaitingApproval("action") to AssistantTimeout.APPROVAL,
                AssistantState.Executing("action") to AssistantTimeout.EXECUTION,
                AssistantState.Speaking("response") to AssistantTimeout.SPEAKING,
            )
    }
}
