package fr.loevan.jeancalcul.domain

import kotlinx.serialization.Serializable

/** Locally persisted conversation metadata. Message content remains in [Message]. */
@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val contextSummary: String? = null,
    val summarizedThroughSequence: Long? = null,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(createdAtEpochMillis >= 0)
        require(updatedAtEpochMillis >= createdAtEpochMillis)
        require(contextSummary == null || contextSummary.isNotBlank())
        require(summarizedThroughSequence == null || summarizedThroughSequence >= 0)
        require((contextSummary == null) == (summarizedThroughSequence == null))
    }
}

@Serializable
enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

/** A durable message. Provider-specific remote session state is deliberately excluded. */
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val assistantSessionId: String? = null,
    val role: MessageRole,
    val text: String,
    val status: MessageStatus,
    val sequence: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
    val requestId: String? = null,
    val usage: ProviderUsage? = null,
    val errorMessage: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(conversationId.isNotBlank())
        require(assistantSessionId == null || assistantSessionId.isNotBlank())
        require(text.isNotBlank() || status != MessageStatus.COMPLETED)
        require(sequence >= 0)
        require(createdAtEpochMillis >= 0)
        require(updatedAtEpochMillis >= createdAtEpochMillis)
        require(requestId == null || requestId.isNotBlank())
        require(errorMessage == null || errorMessage.isNotBlank())
        require(status == MessageStatus.FAILED || errorMessage == null)
    }
}

@Serializable
enum class AssistantSessionKind {
    MODEL,
    AGENT,
}

/**
 * Local routing state for one provider profile.
 *
 * A direct model never receives a backend session reference. An agent reference is persisted here,
 * not in conversation messages, so resuming an agent cannot be confused with replaying model history.
 */
@Serializable
data class AssistantSession(
    val id: String,
    val conversationId: String,
    val kind: AssistantSessionKind,
    val modelProfileId: String? = null,
    val agentProfileId: String? = null,
    val agentBackendSessionId: String? = null,
    val lastAgentEventSequence: Long? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
) {
    init {
        require(id.isNotBlank())
        require(conversationId.isNotBlank())
        require(createdAtEpochMillis >= 0)
        require(updatedAtEpochMillis >= createdAtEpochMillis)
        require(lastAgentEventSequence == null || lastAgentEventSequence >= 0)
        when (kind) {
            AssistantSessionKind.MODEL -> {
                require(!modelProfileId.isNullOrBlank())
                require(agentProfileId == null)
                require(agentBackendSessionId == null)
                require(lastAgentEventSequence == null)
            }

            AssistantSessionKind.AGENT -> {
                require(modelProfileId == null)
                require(!agentProfileId.isNullOrBlank())
                require(agentBackendSessionId == null || agentBackendSessionId.isNotBlank())
                require(agentBackendSessionId != null || lastAgentEventSequence == null)
            }
        }
    }
}

@Serializable
data class ConversationExport(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val conversation: Conversation,
    val sessions: List<AssistantSession>,
    val messages: List<Message>,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
        require(sessions.all { it.conversationId == conversation.id })
        require(messages.all { it.conversationId == conversation.id })
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
