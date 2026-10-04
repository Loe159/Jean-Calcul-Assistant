package fr.loevan.jeancalcul.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AssistantSessionKind
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import fr.loevan.jeancalcul.network.codex.CodexChatGptAgentBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
        private val codexBackend: CodexChatGptAgentBackend,
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
                runCatching {
                    val profile = activeAgentProfile()
                    if (profile == null) {
                        createLocalConversation("Nouvelle conversation")
                    } else {
                        requireCodexProfile(profile)
                        orchestrator.createAgentConversation(profile).also {
                            selectedConversationId.value = it.conversation.id
                        }
                    }
                }.onSuccess {
                    errorMessage.value = null
                }.onFailure(::showConversationError)
            }
        }

        fun saveDraft() {
            val text = draft.value.trim()
            if (text.isBlank()) return
            viewModelScope.launch {
                runCatching {
                    val profile = activeAgentProfile()
                    if (profile == null) {
                        saveLocalDraft(text)
                    } else {
                        requireCodexProfile(profile)
                        val handle = agentHandle(profile, text)
                        val response = orchestrator.sendToAgent(handle, profile, codexBackend, text)
                        draft.value = ""
                        response
                    }
                }.onSuccess {
                    errorMessage.value = null
                }.onFailure(::showConversationError)
            }
        }

        fun cancelActive() {
            val conversationId = selectedConversationId.value ?: return
            viewModelScope.launch {
                runCatching { orchestrator.cancel(conversationId) }
                    .onFailure(::showConversationError)
            }
        }

        fun retry(responseMessageId: String) {
            viewModelScope.launch {
                runCatching {
                    val profile =
                        activeAgentProfile()
                            ?: error("Aucun profil agent actif pour relancer cette réponse.")
                    requireCodexProfile(profile)
                    orchestrator.retryAgentResponse(responseMessageId, profile, codexBackend)
                }.onSuccess {
                    errorMessage.value = null
                }.onFailure(::showConversationError)
            }
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

        private suspend fun activeAgentProfile(): AgentProfile? {
            val settings = settingsRepository.settings.first()
            val activeId = settings.activeAgentProfileId ?: return null
            return settings.agentProfiles.firstOrNull { it.profile.id == activeId }?.profile
                ?: error("Le profil agent actif est introuvable.")
        }

        private fun requireCodexProfile(profile: AgentProfile) {
            check(profile.backendId == CodexChatGptAgentBackend.ID) {
                "Le profil agent actif n'utilise pas le compagnon ChatGPT pris en charge par cette version."
            }
        }

        private suspend fun agentHandle(
            profile: AgentProfile,
            titleSource: String,
        ): ConversationHandle {
            val selectedId = selectedConversationId.value
            if (selectedId != null) {
                val conversation = repository.getConversation(selectedId)
                val session =
                    repository
                        .getSessions(selectedId)
                        .lastOrNull {
                            it.kind == AssistantSessionKind.AGENT &&
                                it.agentProfileId == profile.id
                        }
                if (conversation != null && session != null) {
                    return ConversationHandle(conversation, session)
                }
            }
            return orchestrator
                .createAgentConversation(profile, titleSource.take(MAX_TITLE_LENGTH))
                .also { selectedConversationId.value = it.conversation.id }
        }

        private suspend fun saveLocalDraft(text: String): Message {
            val conversationId = selectedConversationId.value ?: createLocalConversation(text).id
            val now = System.currentTimeMillis()
            val message =
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = MessageRole.USER,
                    text = text,
                    status = MessageStatus.COMPLETED,
                    sequence = repository.nextMessageSequence(conversationId),
                    createdAtEpochMillis = now,
                )
            repository.saveMessage(message)
            draft.value = ""
            return message
        }

        private suspend fun createLocalConversation(titleSource: String): Conversation {
            val now = System.currentTimeMillis()
            val title = titleSource.take(MAX_TITLE_LENGTH).ifBlank { "Nouvelle conversation" }
            return Conversation(UUID.randomUUID().toString(), title, now).also {
                repository.saveConversation(it)
                selectedConversationId.value = it.id
            }
        }

        private fun showConversationError(error: Throwable) {
            errorMessage.value =
                error.message?.takeIf(String::isNotBlank)
                    ?: "La conversation n'a pas pu etre terminee."
        }

        private companion object {
            const val MAX_TITLE_LENGTH = 48
        }
    }
