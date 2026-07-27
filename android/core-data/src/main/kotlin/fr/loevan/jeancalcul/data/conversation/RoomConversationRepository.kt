package fr.loevan.jeancalcul.data.conversation

import fr.loevan.jeancalcul.domain.AssistantSession
import fr.loevan.jeancalcul.domain.AssistantSessionKind
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationExport
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import fr.loevan.jeancalcul.domain.ProviderUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
class RoomConversationRepository
    @Inject
    constructor(
        private val dao: ConversationDao,
        private val json: Json,
    ) : ConversationRepository {
        override fun observeConversations(): Flow<List<Conversation>> =
            dao.observeConversations().map { entities -> entities.map(ConversationEntity::toDomain) }

        override fun observeMessages(conversationId: String): Flow<List<Message>> =
            dao.observeMessages(conversationId).map { entities -> entities.map(MessageEntity::toDomain) }

        override suspend fun getConversation(conversationId: String): Conversation? =
            dao.getConversation(conversationId)?.toDomain()

        override suspend fun getMessages(conversationId: String): List<Message> =
            dao.getMessages(conversationId).map(MessageEntity::toDomain)

        override suspend fun getSessions(conversationId: String): List<AssistantSession> =
            dao.getSessions(conversationId).map(AssistantSessionEntity::toDomain)

        override suspend fun getSession(sessionId: String): AssistantSession? = dao.getSession(sessionId)?.toDomain()

        override suspend fun saveConversation(conversation: Conversation) =
            dao.upsertConversation(
                conversation.toEntity(),
            )

        override suspend fun saveSession(session: AssistantSession) = dao.upsertSession(session.toEntity())

        override suspend fun saveMessage(message: Message) = dao.saveMessageAndTouch(message.toEntity())

        override suspend fun nextMessageSequence(conversationId: String): Long = dao.nextMessageSequence(conversationId)

        override suspend fun deleteMessage(messageId: String) = dao.deleteMessage(messageId)

        override suspend fun deleteConversation(conversationId: String) = dao.deleteConversation(conversationId)

        override suspend fun exportConversation(conversationId: String): String {
            val conversation =
                requireNotNull(getConversation(conversationId)) { "Unknown conversation: $conversationId" }
            return json.encodeToString(
                ConversationExport(
                    conversation = conversation,
                    sessions = getSessions(conversationId),
                    messages = getMessages(conversationId),
                ),
            )
        }
    }

private fun Conversation.toEntity() =
    ConversationEntity(id, title, createdAtEpochMillis, updatedAtEpochMillis, contextSummary, summarizedThroughSequence)

private fun ConversationEntity.toDomain() =
    Conversation(id, title, createdAtEpochMillis, updatedAtEpochMillis, contextSummary, summarizedThroughSequence)

private fun AssistantSession.toEntity() =
    AssistantSessionEntity(
        id,
        conversationId,
        kind.name,
        modelProfileId,
        agentProfileId,
        agentBackendSessionId,
        lastAgentEventSequence,
        createdAtEpochMillis,
        updatedAtEpochMillis,
    )

private fun AssistantSessionEntity.toDomain() =
    AssistantSession(
        id,
        conversationId,
        AssistantSessionKind.valueOf(kind),
        modelProfileId,
        agentProfileId,
        agentBackendSessionId,
        lastAgentEventSequence,
        createdAtEpochMillis,
        updatedAtEpochMillis,
    )

private fun Message.toEntity() =
    MessageEntity(
        id,
        conversationId,
        assistantSessionId,
        role.name,
        text,
        status.name,
        sequence,
        createdAtEpochMillis,
        updatedAtEpochMillis,
        requestId,
        usage?.inputTokens,
        usage?.outputTokens,
        errorMessage,
    )

private fun MessageEntity.toDomain() =
    Message(
        id,
        conversationId,
        assistantSessionId,
        MessageRole.valueOf(role),
        text,
        MessageStatus.valueOf(status),
        sequence,
        createdAtEpochMillis,
        updatedAtEpochMillis,
        requestId,
        if (inputTokens != null || outputTokens != null) ProviderUsage(inputTokens, outputTokens) else null,
        errorMessage,
    )
