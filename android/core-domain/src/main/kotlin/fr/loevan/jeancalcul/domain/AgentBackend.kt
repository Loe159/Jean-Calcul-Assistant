package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow

/** Agent-only request. Keeping it separate from [ChatRequest] prevents accidental model routing. */
data class AgentRequest(
    val requestId: String,
    val messages: List<ChatMessage>,
    val availableTools: List<ToolDefinition> = emptyList(),
    val contractVersion: String = ProviderContractVersion.CURRENT,
) {
    init {
        require(requestId.isNotBlank())
        require(messages.isNotEmpty())
        require(contractVersion.isNotBlank())
    }
}

data class AgentSession(
    val id: String,
    val profileId: String,
    val resumable: Boolean,
) {
    init {
        require(id.isNotBlank())
        require(profileId.isNotBlank())
    }
}

data class AgentRun(
    val id: String,
    val sessionId: String,
    val requestId: String,
    val status: AgentRunStatus,
) {
    init {
        require(id.isNotBlank())
        require(sessionId.isNotBlank())
        require(requestId.isNotBlank())
    }
}

enum class AgentRunStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class AgentToolApprovalRequest(
    val approvalId: String,
    val call: ToolCall,
    val summary: String,
) {
    init {
        require(approvalId.isNotBlank())
        require(summary.isNotBlank())
    }
}

data class AgentToolApproval(
    val approvalId: String,
    val approved: Boolean,
    val reason: String? = null,
) {
    init {
        require(approvalId.isNotBlank())
        require(reason == null || reason.isNotBlank())
    }
}

data class AgentJob(
    val id: String,
    val status: AgentJobStatus,
    val progressPercent: Int? = null,
) {
    init {
        require(id.isNotBlank())
        require(progressPercent == null || progressPercent in 0..100)
    }
}

enum class AgentJobStatus {
    QUEUED,
    RUNNING,
    WAITING_INPUT,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class AgentToolDescriptor(
    val name: String,
    val version: String,
) {
    init {
        require(name.isNotBlank())
        require(version.isNotBlank())
    }
}

data class AgentSkillDescriptor(
    val id: String,
    val displayName: String,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
    }
}

enum class AgentBackendState {
    AVAILABLE,
    DEGRADED,
    OFFLINE,
}

data class AgentBackendStatus(
    val state: AgentBackendState,
    val message: String? = null,
) {
    init {
        require(message == null || message.isNotBlank())
    }
}

/**
 * Session-owning agent backend. It cannot be passed where [ModelProvider] is required.
 *
 * Event sequence numbers support resuming a stream after disconnection. Cancelling collection must
 * stop the active transport subscription; [cancel] interrupts the backend run itself.
 */
@Suppress("TooManyFunctions")
interface AgentBackend {
    val id: String

    suspend fun capabilities(profile: AgentProfile): AgentCapabilities

    suspend fun createSession(profile: AgentProfile): AgentSession

    suspend fun resumeSession(
        profile: AgentProfile,
        sessionId: String,
    ): AgentSession

    suspend fun sendMessage(
        sessionId: String,
        request: AgentRequest,
    ): AgentRun

    fun streamEvents(
        sessionId: String,
        afterSequence: Long? = null,
    ): Flow<AgentStreamEvent>

    suspend fun cancel(
        sessionId: String,
        runId: String,
    )

    suspend fun listModels(profile: AgentProfile): List<ModelDescriptor>

    suspend fun listTools(profile: AgentProfile): List<AgentToolDescriptor>

    suspend fun listSkills(profile: AgentProfile): List<AgentSkillDescriptor>

    suspend fun approveTool(
        sessionId: String,
        approval: AgentToolApproval,
    )

    suspend fun getStatus(profile: AgentProfile): AgentBackendStatus
}
