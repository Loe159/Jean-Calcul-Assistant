package fr.loevan.jeancalcul.data.conversation

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val contextSummary: String?,
    val summarizedThroughSequence: Long?,
)

@Entity(
    tableName = "assistant_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class AssistantSessionEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val kind: String,
    val modelProfileId: String?,
    val agentProfileId: String?,
    val agentBackendSessionId: String?,
    val lastAgentEventSequence: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AssistantSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["assistantSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("conversationId"), Index("assistantSessionId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val assistantSessionId: String?,
    val role: String,
    val text: String,
    val status: String,
    val sequence: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val requestId: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val errorMessage: String?,
)
