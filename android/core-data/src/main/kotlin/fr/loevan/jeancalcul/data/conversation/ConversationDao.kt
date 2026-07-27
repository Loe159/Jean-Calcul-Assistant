package fr.loevan.jeancalcul.data.conversation

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtEpochMillis DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sequence ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY sequence ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM assistant_sessions WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    suspend fun getSessions(conversationId: String): List<AssistantSessionEntity>

    @Query("SELECT * FROM assistant_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): AssistantSessionEntity?

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Upsert
    suspend fun upsertSession(session: AssistantSessionEntity)

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Query("SELECT COALESCE(MAX(sequence) + 1, 0) FROM messages WHERE conversationId = :conversationId")
    suspend fun nextMessageSequence(conversationId: String): Long

    @Query("UPDATE conversations SET updatedAtEpochMillis = :updatedAt WHERE id = :conversationId")
    suspend fun touchConversation(
        conversationId: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Transaction
    suspend fun saveMessageAndTouch(message: MessageEntity) {
        upsertMessage(message)
        touchConversation(message.conversationId, message.updatedAtEpochMillis)
    }
}
