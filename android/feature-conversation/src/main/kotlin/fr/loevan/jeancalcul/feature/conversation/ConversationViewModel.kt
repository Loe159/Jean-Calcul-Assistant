package fr.loevan.jeancalcul.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
                val now = System.currentTimeMillis()
                val conversation = Conversation(UUID.randomUUID().toString(), "Nouvelle conversation", now)
                repository.saveConversation(conversation)
                selectedConversationId.value = conversation.id
            }
        }

        fun saveDraft() {
            val text = draft.value.trim()
            if (text.isBlank()) return
            viewModelScope.launch {
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
                draft.value = ""
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

        private suspend fun createConversationForDraft(text: String): String {
            val now = System.currentTimeMillis()
            val title = text.take(48)
            return Conversation(UUID.randomUUID().toString(), title, now).also {
                repository.saveConversation(it)
                selectedConversationId.value = it.id
            }.id
        }
    }
