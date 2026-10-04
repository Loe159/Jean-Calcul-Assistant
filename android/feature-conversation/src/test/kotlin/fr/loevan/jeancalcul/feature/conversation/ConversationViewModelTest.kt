package fr.loevan.jeancalcul.feature.conversation

import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRun
import fr.loevan.jeancalcul.domain.AgentRunStatus
import fr.loevan.jeancalcul.domain.AgentSession
import fr.loevan.jeancalcul.domain.AssistantSession
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.ConfiguredAgentProfile
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationExport
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.network.codex.CodexChatGptAgentBackend
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `send routes active ChatGPT agent through orchestrator and persists streamed answer`() =
        runTest {
            val repository = InMemoryConversationRepository()
            val profile = chatGptProfile()
            val settings = StaticSettingsRepository(settingsWith(profile))
            val backend = mockk<CodexChatGptAgentBackend>()
            coEvery { backend.createSession(profile) } returns AgentSession("remote-1", profile.id, true)
            coEvery { backend.sendMessage("remote-1", any()) } returns
                AgentRun("run-1", "remote-1", "request", AgentRunStatus.RUNNING)
            every { backend.streamEvents("remote-1", any()) } returns
                flowOf(
                    StreamEvent.TextDelta("request", "Bonjour depuis ChatGPT", sequence = 101),
                    StreamEvent.Completed("request", FinishReason.STOP, sequence = 102),
                )
            val viewModel =
                ConversationViewModel(
                    repository,
                    settings,
                    ConversationOrchestrator(repository),
                    backend,
                )
            val states = mutableListOf<ConversationUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect(states::add)
            }

            viewModel.updateDraft("Bonjour")
            viewModel.saveDraft()
            advanceUntilIdle()

            val conversation = repository.observeConversations().value.single()
            val messages = repository.getMessages(conversation.id)
            assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), messages.map(Message::role))
            assertEquals("Bonjour depuis ChatGPT", messages.last().text)
            assertEquals("", states.last().draft)
            assertEquals(null, states.last().errorMessage)
            assertEquals("remote-1", repository.getSessions(conversation.id).single().agentBackendSessionId)
        }

    @Test
    fun `draft is retained when companion session cannot start`() =
        runTest {
            val repository = InMemoryConversationRepository()
            val profile = chatGptProfile()
            val settings = StaticSettingsRepository(settingsWith(profile))
            val backend = mockk<CodexChatGptAgentBackend>()
            coEvery { backend.createSession(profile) } throws IllegalStateException("Companion hors ligne")
            val viewModel =
                ConversationViewModel(
                    repository,
                    settings,
                    ConversationOrchestrator(repository),
                    backend,
                )
            val states = mutableListOf<ConversationUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect(states::add)
            }

            viewModel.updateDraft("Bonjour")
            viewModel.saveDraft()
            advanceUntilIdle()

            assertEquals("Bonjour", states.last().draft)
            assertTrue(states.last().errorMessage?.contains("hors ligne") == true)
            val conversation = repository.observeConversations().value.single()
            assertTrue(repository.getMessages(conversation.id).isEmpty())
        }

    private fun chatGptProfile() =
        AgentProfile(
            id = "agent-profile",
            backendId = CodexChatGptAgentBackend.ID,
            agentId = "gpt-test",
            displayName = "ChatGPT",
            connectionId = "companion",
        )

    private fun settingsWith(profile: AgentProfile) =
        AssistantSettings(
            agentProfiles = listOf(ConfiguredAgentProfile(profile)),
            activeAgentProfileId = profile.id,
        )
}

private class StaticSettingsRepository(
    initial: AssistantSettings,
) : AssistantSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<AssistantSettings> = state

    override suspend fun update(transform: (AssistantSettings) -> AssistantSettings) {
        state.value = transform(state.value)
    }
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
        sessions.values.filter { it.conversationId == conversationId }

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
            flow.update { current -> current.filterNot { it.id == messageId } }
        }
    }

    override suspend fun deleteConversation(conversationId: String) {
        conversations.update { current -> current.filterNot { it.id == conversationId } }
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
