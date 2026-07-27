package fr.loevan.jeancalcul.domain

import kotlinx.coroutines.flow.Flow

/** Persistence boundary for local conversation history. */
@Suppress("TooManyFunctions")
interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(conversationId: String): Flow<List<Message>>

    suspend fun getConversation(conversationId: String): Conversation?

    suspend fun getMessages(conversationId: String): List<Message>

    suspend fun getSessions(conversationId: String): List<AssistantSession>

    suspend fun getSession(sessionId: String): AssistantSession?

    suspend fun saveConversation(conversation: Conversation)

    suspend fun saveSession(session: AssistantSession)

    suspend fun saveMessage(message: Message)

    suspend fun nextMessageSequence(conversationId: String): Long

    suspend fun deleteMessage(messageId: String)

    suspend fun deleteConversation(conversationId: String)

    suspend fun exportConversation(conversationId: String): String
}
