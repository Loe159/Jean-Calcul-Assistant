package fr.loevan.jeancalcul.domain

import fr.loevan.jeancalcul.domain.testing.FakeAgentBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBackendContractsTest {
    @Test
    fun `fake agent owns resumable sessions approvals jobs and cancellation`() =
        runTest {
            val backend =
                FakeAgentBackend(
                    defaultCapabilities =
                        AgentCapabilities(
                            supportsToolApprovals = true,
                            supportsSkills = true,
                            supportsLongRunningJobs = true,
                        ),
                )
            val profile = AgentProfile("agent-profile", "fake", "assistant", "Fake agent")
            val session = backend.createSession(profile)
            val request = AgentRequest("agent-request", listOf(userMessage()))
            val run = backend.sendMessage(session.id, request)
            val approval = AgentToolApproval("approval-1", approved = true)
            backend.enqueue(
                session.id,
                listOf(
                    StreamEvent.Started(run.requestId, sequence = 1),
                    StreamEvent.JobUpdated(
                        requestId = run.requestId,
                        job = AgentJob("job-1", AgentJobStatus.RUNNING, progressPercent = 25),
                        sequence = 2,
                    ),
                ),
            )

            val events = backend.streamEvents(session.id, afterSequence = 1).toList()
            backend.approveTool(session.id, approval)
            backend.cancel(session.id, run.id)
            val resumed = backend.resumeSession(profile, session.id)

            assertEquals(session, resumed)
            assertEquals(request, backend.sentRequests.single().second)
            assertTrue(events.single() is StreamEvent.JobUpdated)
            assertEquals(session.id to approval, backend.approvals.single())
            assertEquals(session.id to run.id, backend.cancelledRuns.single())
        }

    @Test
    fun `agent capabilities are checked independently from model capabilities`() =
        runTest {
            val capabilities =
                AgentCapabilities(
                    supportsToolApprovals = true,
                    supportsLongRunningJobs = false,
                )
            val backend = FakeAgentBackend(defaultCapabilities = capabilities)
            val profile = AgentProfile("agent-profile", "fake", "assistant", "Fake agent")

            assertTrue(
                backend.capabilities(profile).supports(
                    AgentCapabilityRequirements(requiresToolApprovals = true),
                ),
            )
        }

    private fun userMessage() =
        ChatMessage(
            id = "message-1",
            role = MessageRole.USER,
            content = listOf(MessageContent.Text("Prepare un resume")),
        )
}
