package fr.loevan.jeancalcul.feature.conversation

import fr.loevan.jeancalcul.domain.AgentBackend
import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.AssistantSession
import fr.loevan.jeancalcul.domain.AssistantSessionKind
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.ConversationRepository
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.ModelProvider
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.observability.PerformanceTrace
import fr.loevan.jeancalcul.observability.PerformanceTraceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class ConversationHandle(
    val conversation: Conversation,
    val session: AssistantSession,
)

/** Persists provider streams as one progressively updated assistant message. */
@Suppress("TooManyFunctions")
class ConversationOrchestrator
    @Inject
    constructor(
        private val repository: ConversationRepository,
    ) {
        internal var performanceTrace: PerformanceTrace = NoOpConversationPerformanceTrace

        private val activeRequests = ConcurrentHashMap<String, ActiveRequest>()

        suspend fun createModelConversation(
            profile: ModelProfile,
            title: String = "Nouvelle conversation",
        ): ConversationHandle = createConversation(title, AssistantSessionKind.MODEL, profile.id)

        suspend fun createAgentConversation(
            profile: AgentProfile,
            title: String = "Nouvelle conversation",
        ): ConversationHandle = createConversation(title, AssistantSessionKind.AGENT, profile.id)

        suspend fun sendToModel(
            handle: ConversationHandle,
            profile: ModelProfile,
            provider: ModelProvider,
            text: String,
        ): Message = sendModel(handle, profile, provider, text.trim(), null)

        suspend fun retryModelResponse(
            responseMessageId: String,
            profile: ModelProfile,
            provider: ModelProvider,
        ): Message {
            val (handle, response, userMessage) = retryContext(responseMessageId)
            require(handle.session.kind == AssistantSessionKind.MODEL)
            require(handle.session.modelProfileId == profile.id)
            return sendModel(handle, profile, provider, userMessage.text, response)
        }

        suspend fun sendToAgent(
            handle: ConversationHandle,
            profile: AgentProfile,
            backend: AgentBackend,
            text: String,
        ): Message = sendAgent(handle, profile, backend, text.trim(), null)

        suspend fun retryAgentResponse(
            responseMessageId: String,
            profile: AgentProfile,
            backend: AgentBackend,
        ): Message {
            val (handle, response, userMessage) = retryContext(responseMessageId)
            require(handle.session.kind == AssistantSessionKind.AGENT)
            require(handle.session.agentProfileId == profile.id)
            return sendAgent(handle, profile, backend, userMessage.text, response)
        }

        suspend fun cancel(conversationId: String) {
            activeRequests[conversationId]?.cancel?.invoke()
        }

        private suspend fun createConversation(
            title: String,
            kind: AssistantSessionKind,
            profileId: String,
        ): ConversationHandle {
            val now = System.currentTimeMillis()
            val conversation = Conversation(UUID.randomUUID().toString(), title.trim(), now)
            val session =
                AssistantSession(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversation.id,
                    kind = kind,
                    modelProfileId = profileId.takeIf { kind == AssistantSessionKind.MODEL },
                    agentProfileId = profileId.takeIf { kind == AssistantSessionKind.AGENT },
                    createdAtEpochMillis = now,
                )
            repository.saveConversation(conversation)
            repository.saveSession(session)
            return ConversationHandle(conversation, session)
        }

        private suspend fun sendModel(
            handle: ConversationHandle,
            profile: ModelProfile,
            provider: ModelProvider,
            text: String,
            retryResponse: Message?,
        ): Message {
            require(text.isNotBlank())
            require(handle.session.kind == AssistantSessionKind.MODEL)
            val requestId = UUID.randomUUID().toString()
            if (retryResponse == null) appendUserMessage(handle, text, requestId)
            var response = prepareResponse(handle, requestId, retryResponse)
            val request =
                ChatRequest(
                    requestId = requestId,
                    profile = profile,
                    messages = repository.getMessages(handle.conversation.id).toChatMessages(),
                )
            val activeRequest = ActiveRequest { provider.cancel(requestId) }
            activeRequests[handle.conversation.id] = activeRequest
            var firstTokenRecorded = false
            try {
                provider.stream(request).collect { event ->
                    if (!firstTokenRecorded && event is StreamEvent.TextDelta && event.text.isNotEmpty()) {
                        firstTokenRecorded = true
                        performanceTrace.mark(PerformanceTraceEvent.FIRST_TOKEN)
                    }
                    response = applyStreamEvent(response, event)
                    repository.saveMessage(response)
                    if (event is StreamEvent.Failed) throw ProviderException(event.error)
                }
            } catch (cancelled: CancellationException) {
                provider.cancel(requestId)
                response = response.finished(MessageStatus.INTERRUPTED)
                repository.saveMessage(response)
                throw cancelled
            } catch (failure: ProviderException) {
                response = response.failed(failure.error.message)
                repository.saveMessage(response)
            } finally {
                activeRequests.remove(handle.conversation.id, activeRequest)
            }
            return response
        }

        private suspend fun sendAgent(
            initialHandle: ConversationHandle,
            profile: AgentProfile,
            backend: AgentBackend,
            text: String,
            retryResponse: Message?,
        ): Message {
            require(text.isNotBlank())
            require(initialHandle.session.kind == AssistantSessionKind.AGENT)
            val session = ensureRemoteAgentSession(initialHandle.session, profile, backend)
            val handle = initialHandle.copy(session = session)
            val requestId = UUID.randomUUID().toString()
            if (retryResponse == null) appendUserMessage(handle, text, requestId)
            var response = prepareResponse(handle, requestId, retryResponse)
            val request = AgentRequest(requestId, repository.getMessages(handle.conversation.id).toChatMessages())
            val run = backend.sendMessage(requireNotNull(session.agentBackendSessionId), request)
            val activeRequest =
                ActiveRequest { backend.cancel(requireNotNull(session.agentBackendSessionId), run.id) }
            activeRequests[handle.conversation.id] = activeRequest
            var updatedSession = session
            try {
                backend.streamEvents(
                    requireNotNull(session.agentBackendSessionId),
                    session.lastAgentEventSequence,
                ).collect { event ->
                    response = applyStreamEvent(response, event)
                    repository.saveMessage(response)
                    event.sequence?.let { sequence ->
                        updatedSession =
                            updatedSession.copy(
                                lastAgentEventSequence = sequence,
                                updatedAtEpochMillis = System.currentTimeMillis(),
                            )
                        repository.saveSession(updatedSession)
                    }
                    if (event is StreamEvent.Failed) throw ProviderException(event.error)
                }
            } catch (cancelled: CancellationException) {
                backend.cancel(requireNotNull(session.agentBackendSessionId), run.id)
                response = response.finished(MessageStatus.INTERRUPTED)
                repository.saveMessage(response)
                throw cancelled
            } catch (failure: ProviderException) {
                response = response.failed(failure.error.message)
                repository.saveMessage(response)
            } finally {
                activeRequests.remove(handle.conversation.id, activeRequest)
            }
            return response
        }

        private suspend fun ensureRemoteAgentSession(
            session: AssistantSession,
            profile: AgentProfile,
            backend: AgentBackend,
        ): AssistantSession {
            val remote =
                session.agentBackendSessionId?.let { backend.resumeSession(profile, it) }
                    ?: backend.createSession(profile)
            return session.copy(
                agentBackendSessionId = remote.id,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ).also { repository.saveSession(it) }
        }

        private suspend fun appendUserMessage(
            handle: ConversationHandle,
            text: String,
            requestId: String,
        ) {
            val now = System.currentTimeMillis()
            repository.saveMessage(
                Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = handle.conversation.id,
                    assistantSessionId = handle.session.id,
                    role = MessageRole.USER,
                    text = text,
                    status = MessageStatus.COMPLETED,
                    sequence = repository.nextMessageSequence(handle.conversation.id),
                    createdAtEpochMillis = now,
                    requestId = requestId,
                ),
            )
        }

        private suspend fun prepareResponse(
            handle: ConversationHandle,
            requestId: String,
            retryResponse: Message?,
        ): Message {
            val now = System.currentTimeMillis()
            val response =
                retryResponse?.copy(
                    text = "",
                    status = MessageStatus.STREAMING,
                    updatedAtEpochMillis = now,
                    requestId = requestId,
                    usage = null,
                    errorMessage = null,
                ) ?: Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = handle.conversation.id,
                    assistantSessionId = handle.session.id,
                    role = MessageRole.ASSISTANT,
                    text = "",
                    status = MessageStatus.STREAMING,
                    sequence = repository.nextMessageSequence(handle.conversation.id),
                    createdAtEpochMillis = now,
                    requestId = requestId,
                )
            repository.saveMessage(response)
            return response
        }

        private suspend fun retryContext(responseMessageId: String): RetryContext {
            val conversations = repository.observeConversations()
            var target: Message? = null
            var targetConversation: Conversation? = null
            var messages: List<Message> = emptyList()
            for (conversation in conversations.firstSnapshot()) {
                val current = repository.getMessages(conversation.id)
                current.firstOrNull { it.id == responseMessageId }?.let {
                    target = it
                    targetConversation = conversation
                    messages = current
                }
                if (target != null) break
            }
            val response = requireNotNull(target) { "Unknown response: $responseMessageId" }
            require(response.status == MessageStatus.FAILED || response.status == MessageStatus.INTERRUPTED)
            val user =
                requireNotNull(
                    messages.lastOrNull { it.role == MessageRole.USER && it.sequence < response.sequence },
                ) { "No user message precedes response $responseMessageId" }
            val sessionId = requireNotNull(response.assistantSessionId)
            val session = requireNotNull(repository.getSession(sessionId))
            return RetryContext(ConversationHandle(requireNotNull(targetConversation), session), response, user)
        }

        private data class ActiveRequest(val cancel: suspend () -> Unit)

        private data class RetryContext(
            val handle: ConversationHandle,
            val response: Message,
            val userMessage: Message,
        )
    }

private fun Message.finished(status: MessageStatus) =
    copy(status = status, updatedAtEpochMillis = System.currentTimeMillis(), errorMessage = null)

private fun Message.failed(error: String) =
    copy(
        status = MessageStatus.FAILED,
        updatedAtEpochMillis = System.currentTimeMillis(),
        errorMessage = error.ifBlank { "Erreur fournisseur" },
    )

private fun applyStreamEvent(
    message: Message,
    event: fr.loevan.jeancalcul.domain.StreamEvent,
): Message =
    when (event) {
        is StreamEvent.TextDelta ->
            message.copy(
                text = message.text + event.text,
                status = MessageStatus.STREAMING,
                updatedAtEpochMillis = System.currentTimeMillis(),
            )

        is StreamEvent.UsageUpdated ->
            message.copy(usage = event.usage, updatedAtEpochMillis = System.currentTimeMillis())

        is StreamEvent.Completed ->
            when (event.finishReason) {
                FinishReason.CANCELLED -> message.finished(MessageStatus.INTERRUPTED)
                FinishReason.ERROR -> message.failed("Erreur fournisseur")
                else -> message.finished(MessageStatus.COMPLETED)
            }

        is StreamEvent.Failed -> message.failed(event.error.message)
        else -> message
    }

private fun List<Message>.toChatMessages(): List<ChatMessage> =
    filter { it.text.isNotBlank() && it.role != MessageRole.TOOL }
        .map { message ->
            ChatMessage(
                id = message.id,
                role = message.role,
                content = listOf(MessageContent.Text(message.text)),
            )
        }

private suspend fun kotlinx.coroutines.flow.Flow<List<Conversation>>.firstSnapshot(): List<Conversation> = first()

private object NoOpConversationPerformanceTrace : PerformanceTrace {
    override fun startInvocation() = Unit

    override fun mark(event: PerformanceTraceEvent) = Unit

    override fun captureMemory(checkpoint: String) = Unit

    override fun finishInvocation(reason: String) = Unit
}
