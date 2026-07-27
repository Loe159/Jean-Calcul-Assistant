package fr.loevan.jeancalcul.domain.testing

import fr.loevan.jeancalcul.domain.AgentBackend
import fr.loevan.jeancalcul.domain.AgentBackendState
import fr.loevan.jeancalcul.domain.AgentBackendStatus
import fr.loevan.jeancalcul.domain.AgentCapabilities
import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.AgentRun
import fr.loevan.jeancalcul.domain.AgentRunStatus
import fr.loevan.jeancalcul.domain.AgentSession
import fr.loevan.jeancalcul.domain.AgentSkillDescriptor
import fr.loevan.jeancalcul.domain.AgentStreamEvent
import fr.loevan.jeancalcul.domain.AgentToolApproval
import fr.loevan.jeancalcul.domain.AgentToolDescriptor
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ModelProvider
import fr.loevan.jeancalcul.domain.ModelStreamEvent
import fr.loevan.jeancalcul.domain.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Deterministic direct-model fake for consumer tests. It never performs network I/O. */
class FakeModelProvider(
    override val id: String = "fake-model-provider",
    private val defaultCapabilities: ModelCapabilities = ModelCapabilities(),
) : ModelProvider {
    val requests = mutableListOf<ChatRequest>()
    val cancelledRequestIds = mutableListOf<String>()
    private val scriptedResponses = ArrayDeque<List<ModelStreamEvent>>()

    fun enqueue(events: List<ModelStreamEvent>) {
        scriptedResponses.addLast(events)
    }

    override suspend fun capabilities(profile: ModelProfile): ModelCapabilities = defaultCapabilities

    override suspend fun listModels(profile: ModelProfile): List<ModelDescriptor> =
        listOf(ModelDescriptor(profile.modelId, profile.displayName, defaultCapabilities))

    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> =
        flow {
            requests += request
            val events =
                if (scriptedResponses.isEmpty()) {
                    listOf(
                        StreamEvent.Started(request.requestId),
                        StreamEvent.Completed(request.requestId, FinishReason.STOP),
                    )
                } else {
                    scriptedResponses.removeFirst()
                }
            events.forEach { emit(it) }
        }

    override suspend fun cancel(requestId: String) {
        cancelledRequestIds += requestId
    }
}

/** Deterministic session-owning agent fake for consumer tests. It never performs network I/O. */
@Suppress("TooManyFunctions")
class FakeAgentBackend(
    override val id: String = "fake-agent-backend",
    private val defaultCapabilities: AgentCapabilities = AgentCapabilities(),
    private val models: List<ModelDescriptor> = emptyList(),
    private val tools: List<AgentToolDescriptor> = emptyList(),
    private val skills: List<AgentSkillDescriptor> = emptyList(),
) : AgentBackend {
    val sentRequests = mutableListOf<Pair<String, AgentRequest>>()
    val cancelledRuns = mutableListOf<Pair<String, String>>()
    val approvals = mutableListOf<Pair<String, AgentToolApproval>>()
    private val sessions = mutableMapOf<String, AgentSession>()
    private val scriptedEvents = mutableMapOf<String, ArrayDeque<List<AgentStreamEvent>>>()
    private var sessionCounter = 0
    private var runCounter = 0

    fun enqueue(
        sessionId: String,
        events: List<AgentStreamEvent>,
    ) {
        scriptedEvents.getOrPut(sessionId, ::ArrayDeque).addLast(events)
    }

    override suspend fun capabilities(profile: AgentProfile): AgentCapabilities = defaultCapabilities

    override suspend fun createSession(profile: AgentProfile): AgentSession {
        val session =
            AgentSession(
                "fake-session-${++sessionCounter}",
                profile.id,
                defaultCapabilities.supportsSessionResume,
            )
        sessions[session.id] = session
        return session
    }

    override suspend fun resumeSession(
        profile: AgentProfile,
        sessionId: String,
    ): AgentSession {
        val existing = sessions[sessionId]
        if (existing != null) return existing
        return AgentSession(sessionId, profile.id, defaultCapabilities.supportsSessionResume).also {
            sessions[sessionId] = it
        }
    }

    override suspend fun sendMessage(
        sessionId: String,
        request: AgentRequest,
    ): AgentRun {
        require(sessionId in sessions) { "Unknown fake session: $sessionId" }
        sentRequests += sessionId to request
        return AgentRun(
            id = "fake-run-${++runCounter}",
            sessionId = sessionId,
            requestId = request.requestId,
            status = AgentRunStatus.RUNNING,
        )
    }

    override fun streamEvents(
        sessionId: String,
        afterSequence: Long?,
    ): Flow<AgentStreamEvent> =
        flow {
            require(sessionId in sessions) { "Unknown fake session: $sessionId" }
            val events = scriptedEvents[sessionId]?.removeFirstOrNull().orEmpty()
            events.filter { afterSequence == null || (it.sequence ?: Long.MIN_VALUE) > afterSequence }.forEach {
                emit(it)
            }
        }

    override suspend fun cancel(
        sessionId: String,
        runId: String,
    ) {
        cancelledRuns += sessionId to runId
    }

    override suspend fun listModels(profile: AgentProfile): List<ModelDescriptor> = models

    override suspend fun listTools(profile: AgentProfile): List<AgentToolDescriptor> = tools

    override suspend fun listSkills(profile: AgentProfile): List<AgentSkillDescriptor> = skills

    override suspend fun approveTool(
        sessionId: String,
        approval: AgentToolApproval,
    ) {
        approvals += sessionId to approval
    }

    override suspend fun getStatus(profile: AgentProfile): AgentBackendStatus =
        AgentBackendStatus(AgentBackendState.AVAILABLE)
}
