package fr.loevan.jeancalcul.feature.conversation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface VoiceConversationRecorder {
    suspend fun beginSession()

    suspend fun recordUserMessage(text: String)

    suspend fun recordAssistantMessage(text: String)

    suspend fun recordInterruption()
}

@Singleton
class PersistentVoiceConversationRecorder
    @Inject
    constructor(
        private val repository: ConversationRepository,
    ) : VoiceConversationRecorder {
        private val mutex = Mutex()
        private var conversationId: String? = null

        override suspend fun beginSession() {
            mutex.withLock { conversationId = createConversation().id }
        }

        override suspend fun recordUserMessage(text: String) = append(MessageRole.USER, text, MessageStatus.COMPLETED)

        override suspend fun recordAssistantMessage(text: String) =
            append(MessageRole.ASSISTANT, text, MessageStatus.COMPLETED)

        override suspend fun recordInterruption() =
            append(MessageRole.SYSTEM, "Interaction interrompue.", MessageStatus.INTERRUPTED)

        private suspend fun append(
            role: MessageRole,
            text: String,
            status: MessageStatus,
        ) {
            if (text.isBlank()) return
            mutex.withLock {
                val id = conversationId ?: createConversation().id.also { conversationId = it }
                val now = System.currentTimeMillis()
                repository.saveMessage(
                    Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = id,
                        role = role,
                        text = text,
                        status = status,
                        sequence = repository.nextMessageSequence(id),
                        createdAtEpochMillis = now,
                    ),
                )
            }
        }

        private suspend fun createConversation(): Conversation {
            val now = System.currentTimeMillis()
            return Conversation(
                id = UUID.randomUUID().toString(),
                title = "Session vocale",
                createdAtEpochMillis = now,
            ).also { repository.saveConversation(it) }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceConversationRecorderModule {
    @Binds
    @Singleton
    abstract fun bindVoiceConversationRecorder(
        recorder: PersistentVoiceConversationRecorder,
    ): VoiceConversationRecorder
}
