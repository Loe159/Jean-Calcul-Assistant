package fr.loevan.jeancalcul.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.loevan.jeancalcul.domain.AgentBackendFactory
import fr.loevan.jeancalcul.domain.AssistantSessionKind
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.AssistantSettingsValidator
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ConversationUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: String? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel
    @Inject
    constructor(
        private val repository: ConversationRepository,
        private val settingsRepository: AssistantSettingsRepository,
        private val orchestrator: ConversationOrchestrator,
        private val agentBackendFactory: AgentBackendFactory,
    ) : ViewModel() {
        private val selectedConversationId = MutableStateFlow<String?>(null)
        private val draft = MutableStateFlow("")
        private val errorMessage = MutableStateFlow<String?>(null)
        private val conversations = repository.observeConversations()
        private val messages =
            selectedConversationId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else repository.observeMessages(id)
            }

        val uiState =
            combine(conversations, selectedConversationId, messages, draft, errorMessage, ::ConversationUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

        init {
            viewModelScope.launch {
                conversations.collect { current ->
                    val selected = selectedConversationId.value
                    if (selected == null || current.none { it.id == selected }) {
                        selectedConversationId.value = current.firstOrNull()?.id
                    }
                }
            }
        }

        fun selectConversation(id: String) {
            selectedConversationId.value = id
            errorMessage.value = null
        }

        fun updateDraft(value: String) {
            draft.value = value
        }

        fun newConversation() {
            viewModelScope.launch {
                val settings = settingsRepository.settings.first()
                val activeAgent = settings.agentProfiles.firstOrNull { it.profile.id == settings.activeAgentProfileId }
                val conversation =
                    if (activeAgent != null) {
                        orchestrator.createAgentConversation(activeAgent.profile).conversation
                    } else {
                        val now = System.currentTimeMillis()
                        Conversation(UUID.randomUUID().toString(), "Nouvelle conversation", now).also {
                            repository.saveConversation(it)
                        }
                    }
                selectedConversationId.value = conversation.id
                errorMessage.value = null
            }
        }

        fun saveDraft() {
            val text = draft.value.trim()
            if (text.isBlank()) return
            draft.value = ""
            viewModelScope.launch {
                runCatching { sendDraft(text) }
                    .onSuccess {
                        errorMessage.value = null
                    }.onFailure { error ->
                        if (draft.value.isEmpty()) draft.value = text
                        errorMessage.value = error.message ?: "Impossible de contacter le backend actif."
                    }
            }
        }

        private suspend fun sendDraft(text: String) {
            val settings = settingsRepository.settings.first()
            val configuredAgent =
                settings.agentProfiles.firstOrNull { it.profile.id == settings.activeAgentProfileId }
            if (configuredAgent == null) {
                persistLocalDraft(text)
                return
            }
            val activationErrors = AssistantSettingsValidator.agentActivationErrors(configuredAgent, settings)
            require(activationErrors.isEmpty()) { activationErrors.joinToString(" ") }
            val profile = configuredAgent.profile
            val connection =
                settings.providers.firstOrNull { it.id == profile.connectionId }
                    ?: error("Le backend agent actif n'a plus de connexion configuree.")
            val backend = agentBackendFactory.create(connection, profile)
            val selected = selectedConversationId.value
            val handle =
                selected?.let { conversationId ->
                    val conversation = repository.getConversation(conversationId) ?: return@let null
                    val session =
                        repository.getSessions(conversationId).lastOrNull {
                            it.kind == AssistantSessionKind.AGENT && it.agentProfileId == profile.id
                        } ?: return@let null
                    ConversationHandle(conversation, session)
                } ?: orchestrator.createAgentConversation(profile, text.take(48))
            selectedConversationId.value = handle.conversation.id
            orchestrator.sendToAgent(handle, profile, backend, text)
        }

        private suspend fun persistLocalDraft(text: String) {
            val conversationId = selectedConversationId.value ?: createConversationForDraft(text)
            val now = System.currentTimeMillis()
            repository.saveMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    text = text,
                    status = MessageStatus.COMPLETED,
                    sequence = repository.nextMessageSequence(conversationId),
                    createdAtEpochMillis = now,
                ),
            )
        }

        fun deleteSelected() {
            selectedConversationId.value?.let { id ->
                viewModelScope.launch { repository.deleteConversation(id) }
            }
        }

        fun exportSelected(onExported: (title: String, json: String) -> Unit) {
            val id = selectedConversationId.value ?: return
            viewModelScope.launch {
                runCatching {
                    val conversation = requireNotNull(repository.getConversation(id))
                    conversation.title to repository.exportConversation(id)
                }.onSuccess { (title, json) ->
                    errorMessage.value = null
                    onExported(title, json)
                }.onFailure { error ->
                    errorMessage.value = error.message ?: "Impossible d'exporter la conversation."
                }
            }
        }

        private suspend fun createConversationForDraft(text: String): String {
            val now = System.currentTimeMillis()
            val title = text.take(48)
            return Conversation(UUID.randomUUID().toString(), title, now).also {
                repository.saveConversation(it)
                selectedConversationId.value = it.id
            }.id
        }
    }
