package fr.loevan.jeancalcul.feature.conversation

import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AssistantSession
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationExport
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelDescriptor
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ModelProvider
import fr.loevan.jeancalcul.domain.ModelStreamEvent
import fr.loevan.jeancalcul.domain.ProviderError
import fr.loevan.jeancalcul.domain.ProviderErrorCategory
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.domain.testing.FakeAgentBackend
import fr.loevan.jeancalcul.domain.testing.FakeModelProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationOrchestratorTest {
    private val profile = ModelProfile("profile", "provider", "model", "Modele")

    @Test
    fun `text deltas are persisted progressively`() =
        runTest {
            val repository = InMemoryConversationRepository()
            val firstDelta = CompletableDeferred<Unit>()
            val continueStream = CompletableDeferred<Unit>()
            val provider = GatedModelProvider(firstDelta, continueStream)
            val orchestrator = ConversationOrchestrator(repository)
            val handle = orchestrator.createModelConversation(profile)

            val job = launch { orchestrator.sendToModel(handle, profile, provider, "Bonjour") }
            firstDelta.await()

            val streaming = repository.getMessages(handle.conversation.id).last()
            assertEquals("Bon", streaming.text)
            assertEquals(MessageStatus.STREAMING, streaming.status)

            continueStream.complete(Unit)
            job.join()

            val completed = repository.getMessages(handle.conversation.id).last()
            assertEquals("Bonjour", completed.text)
            assertEquals(MessageStatus.COMPLETED, completed.status)
        }

    @Test
    fun `retry reuses user and assistant messages without duplication`() =
        runTest {
            val repository = InMemoryConversationRepository()
            val provider = FakeModelProvider()
            val orchestrator = ConversationOrchestrator(repository)
            val handle = orchestrator.createModelConversation(profile)
            provider.enqueue(
                listOf(
                    StreamEvent.Started("ignored"),
                    StreamEvent.Failed(
                        "ignored",
                        ProviderError(ProviderErrorCategory.NETWORK, "offline", "Hors connexion"),
                    ),
                ),
            )

            val failed = orchestrator.sendToModel(handle, profile, provider, "Bonjour")
            assertEquals(MessageStatus.FAILED, failed.status)

            provider.enqueue(
                listOf(
                    StreamEvent.TextDelta("ignored", "Reponse"),
                    StreamEvent.Completed("ignored", FinishReason.STOP),
                ),
            )
            val retried = orchestrator.retryModelResponse(failed.id, profile, provider)
            val messages = repository.getMessages(handle.conversation.id)

            assertEquals(2, messages.size)
            assertEquals(1, messages.count { it.role == MessageRole.USER })
            assertEquals(failed.id, retried.id)
            assertEquals(MessageStatus.COMPLETED, retried.status)
        }

    @Test
    fun `cancelling collection interrupts message and provider`() =
        runTest {
            val repository = InMemoryConversationRepository()
            val firstDelta = CompletableDeferred<Unit>()
            val provider = HangingModelProvider(firstDelta)
            val orchestrator = ConversationOrchestrator(repository)
            val handle = orchestrator.createModelConversation(profile)
            val job = launch { orchestrator.sendToModel(handle, profile, provider, "Bonjour") }
            firstDelta.await()

            job.cancelAndJoin()

            assertEquals(MessageStatus.INTERRUPTED, repository.getMessages(handle.conversation.id).last().status)
            assertEquals(1, provider.cancelledRequestIds.size)
        }

    @Test
    fun `agent remote session is persisted separately from message history`() =
        runTest {
            val repository = InMemoryConversationRepository()
            val backend = FakeAgentBackend()
            val agentProfile = AgentProfile("agent-profile", "backend", "agent", "Agent")
            val orchestrator = ConversationOrchestrator(repository)
            val handle = orchestrator.createAgentConversation(agentProfile)
            backend.enqueue(
                "fake-session-1",
                listOf(
                    StreamEvent.TextDelta("request", "Termine", sequence = 1),
                    StreamEvent.Completed("request", FinishReason.STOP, sequence = 2),
                ),
            )

            orchestrator.sendToAgent(handle, agentProfile, backend, "Prepare ceci")

            val session = repository.getSession(handle.session.id)!!
            assertEquals("fake-session-1", session.agentBackendSessionId)
            assertEquals(2L, session.lastAgentEventSequence)
            assertTrue(repository.getMessages(handle.conversation.id).all { it.assistantSessionId == session.id })
            assertFalse(repository.getMessages(handle.conversation.id).any { it.text.contains("fake-session-1") })
        }
}

private class GatedModelProvider(
    private val firstDelta: CompletableDeferred<Unit>,
    private val continueStream: CompletableDeferred<Unit>,
) : BaseTestModelProvider() {
    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> =
        flow {
            emit(StreamEvent.TextDelta(request.requestId, "Bon"))
            firstDelta.complete(Unit)
            continueStream.await()
            emit(StreamEvent.TextDelta(request.requestId, "jour"))
            emit(StreamEvent.Completed(request.requestId, FinishReason.STOP))
        }
}

private class HangingModelProvider(
    private val firstDelta: CompletableDeferred<Unit>,
) : BaseTestModelProvider() {
    val cancelledRequestIds = mutableListOf<String>()

    override fun stream(request: ChatRequest): Flow<ModelStreamEvent> =
        flow {
            emit(StreamEvent.TextDelta(request.requestId, "En cours"))
            firstDelta.complete(Unit)
            awaitCancellation()
        }

    override suspend fun cancel(requestId: String) {
        cancelledRequestIds += requestId
    }
}

private abstract class BaseTestModelProvider : ModelProvider {
    override val id = "test"

    override suspend fun capabilities(profile: ModelProfile) = ModelCapabilities()

    override suspend fun listModels(profile: ModelProfile) =
        listOf(ModelDescriptor(profile.modelId, profile.displayName, ModelCapabilities()))

    override suspend fun cancel(requestId: String) = Unit
}

private class InMemoryConversationRepository : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val sessions = mutableMapOf<String, AssistantSession>()

    override fun observeConversations() = conversations

    override fun observeMessages(conversationId: String) =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun getConversation(conversationId: String) =
        conversations.value.firstOrNull { it.id == conversationId }

    override suspend fun getMessages(conversationId: String) = observeMessages(conversationId).value

    override suspend fun getSessions(conversationId: String) =
        sessions.values.filter {
            it.conversationId == conversationId
        }

    override suspend fun getSession(sessionId: String) = sessions[sessionId]

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.update { current -> current.filterNot { it.id == conversation.id } + conversation }
    }

    override suspend fun saveSession(session: AssistantSession) {
        sessions[session.id] = session
    }

    override suspend fun saveMessage(message: Message) {
        observeMessages(message.conversationId).update { current ->
            (current.filterNot { it.id == message.id } + message).sortedBy(Message::sequence)
        }
    }

    override suspend fun nextMessageSequence(conversationId: String): Long =
        (getMessages(conversationId).maxOfOrNull(Message::sequence) ?: -1) + 1

    override suspend fun deleteMessage(messageId: String) {
        messages.values.forEach { flow ->
            flow.update { current -> current.filterNot { message -> message.id == messageId } }
        }
    }

    override suspend fun deleteConversation(conversationId: String) {
        conversations.update { it.filterNot { conversation -> conversation.id == conversationId } }
        messages.remove(conversationId)
        sessions.entries.removeAll { it.value.conversationId == conversationId }
    }

    override suspend fun exportConversation(conversationId: String): String =
        Json.encodeToString(
            ConversationExport(
                conversation = requireNotNull(getConversation(conversationId)),
                sessions = getSessions(conversationId),
                messages = getMessages(conversationId),
            ),
        )
}
