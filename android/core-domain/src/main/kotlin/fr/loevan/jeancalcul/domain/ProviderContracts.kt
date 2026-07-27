package fr.loevan.jeancalcul.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Semantic version shared by model and agent contracts. */
object ProviderContractVersion {
    const val CURRENT = "1.0.0"
}

@Serializable
enum class ContentModality {
    TEXT,
    IMAGE,
    AUDIO,
}

@Serializable
data class ProviderLimits(
    val maxContextTokens: Int? = null,
    val maxOutputTokens: Int? = null,
    val maxImagesPerRequest: Int? = null,
    val maxAudioSecondsPerRequest: Int? = null,
) {
    init {
        require(maxContextTokens == null || maxContextTokens > 0)
        require(maxOutputTokens == null || maxOutputTokens > 0)
        require(maxImagesPerRequest == null || maxImagesPerRequest >= 0)
        require(maxAudioSecondsPerRequest == null || maxAudioSecondsPerRequest >= 0)
    }
}

@Serializable
data class ModelCapabilities(
    val inputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val outputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val supportsStreaming: Boolean = true,
    val supportsCancellation: Boolean = true,
    val supportsToolCalling: Boolean = false,
    val supportsParallelToolCalls: Boolean = false,
    val limits: ProviderLimits = ProviderLimits(),
) {
    fun supports(requirements: ModelCapabilityRequirements): Boolean =
        inputModalities.containsAll(requirements.inputModalities) &&
            outputModalities.containsAll(requirements.outputModalities) &&
            (!requirements.requiresStreaming || supportsStreaming) &&
            (!requirements.requiresCancellation || supportsCancellation) &&
            (!requirements.requiresToolCalling || supportsToolCalling) &&
            (!requirements.requiresParallelToolCalls || supportsParallelToolCalls) &&
            limits.supports(requirements)
}

@Serializable
data class ModelCapabilityRequirements(
    val inputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val outputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val requiresStreaming: Boolean = true,
    val requiresCancellation: Boolean = true,
    val requiresToolCalling: Boolean = false,
    val requiresParallelToolCalls: Boolean = false,
    val minimumContextTokens: Int = 0,
    val minimumOutputTokens: Int = 0,
) {
    init {
        require(minimumContextTokens >= 0)
        require(minimumOutputTokens >= 0)
    }
}

private fun ProviderLimits.supports(requirements: ModelCapabilityRequirements): Boolean =
    (
        requirements.minimumContextTokens == 0 ||
            maxContextTokens?.let { it >= requirements.minimumContextTokens } == true
    ) &&
        (
            requirements.minimumOutputTokens == 0 ||
                maxOutputTokens?.let { it >= requirements.minimumOutputTokens } == true
        )

@Serializable
data class AgentCapabilities(
    val inputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val outputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val supportsSessionResume: Boolean = true,
    val supportsCancellation: Boolean = true,
    val supportsToolApprovals: Boolean = false,
    val supportsSkills: Boolean = false,
    val supportsLongRunningJobs: Boolean = false,
) {
    fun supports(requirements: AgentCapabilityRequirements): Boolean =
        inputModalities.containsAll(requirements.inputModalities) &&
            outputModalities.containsAll(requirements.outputModalities) &&
            (!requirements.requiresSessionResume || supportsSessionResume) &&
            (!requirements.requiresCancellation || supportsCancellation) &&
            (!requirements.requiresToolApprovals || supportsToolApprovals) &&
            (!requirements.requiresSkills || supportsSkills) &&
            (!requirements.requiresLongRunningJobs || supportsLongRunningJobs)
}

@Serializable
data class AgentCapabilityRequirements(
    val inputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val outputModalities: Set<ContentModality> = setOf(ContentModality.TEXT),
    val requiresSessionResume: Boolean = false,
    val requiresCancellation: Boolean = true,
    val requiresToolApprovals: Boolean = false,
    val requiresSkills: Boolean = false,
    val requiresLongRunningJobs: Boolean = false,
)

/** Selectable direct-model profile. Credentials are referenced indirectly and never embedded. */
@Serializable
data class ModelProfile(
    val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val connectionId: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank())
        require(providerId.isNotBlank())
        require(modelId.isNotBlank())
        require(displayName.isNotBlank())
        require(connectionId == null || connectionId.isNotBlank())
    }
}

/** Selectable agent profile. It is deliberately not a subtype of [ModelProfile]. */
@Serializable
data class AgentProfile(
    val id: String,
    val backendId: String,
    val agentId: String,
    val displayName: String,
    val connectionId: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(id.isNotBlank())
        require(backendId.isNotBlank())
        require(agentId.isNotBlank())
        require(displayName.isNotBlank())
        require(connectionId == null || connectionId.isNotBlank())
    }
}

@Serializable
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

@Serializable
data class MediaReference(
    val uri: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
) {
    init {
        require(uri.isNotBlank())
        require(mimeType.isNotBlank())
        require(sizeBytes == null || sizeBytes >= 0)
    }
}

@Serializable
sealed interface MessageContent {
    @Serializable
    data class Text(val text: String) : MessageContent {
        init {
            require(text.isNotBlank())
        }
    }

    @Serializable
    data class Image(val reference: MediaReference) : MessageContent

    @Serializable
    data class Audio(val reference: MediaReference) : MessageContent
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: List<MessageContent>,
    val toolCallId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(content.isNotEmpty())
        require(toolCallId == null || toolCallId.isNotBlank())
        require(role == MessageRole.TOOL || toolCallId == null)
        require(role != MessageRole.TOOL || toolCallId != null)
    }
}

@Serializable
data class GenerationOptions(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
) {
    init {
        require(temperature == null || temperature in 0.0..2.0)
        require(maxOutputTokens == null || maxOutputTokens > 0)
        require(stopSequences.none(String::isBlank))
    }
}

/** A direct-model request. Agent backends accept [AgentRequest] instead. */
data class ChatRequest(
    val requestId: String,
    val profile: ModelProfile,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition> = emptyList(),
    val options: GenerationOptions = GenerationOptions(),
    val contractVersion: String = ProviderContractVersion.CURRENT,
) {
    init {
        require(requestId.isNotBlank())
        require(messages.isNotEmpty())
        require(contractVersion.isNotBlank())
    }

    fun capabilityRequirements(): ModelCapabilityRequirements =
        ModelCapabilityRequirements(
            inputModalities =
                buildSet {
                    add(ContentModality.TEXT)
                    messages.flatMap(ChatMessage::content).forEach { content ->
                        when (content) {
                            is MessageContent.Audio -> add(ContentModality.AUDIO)
                            is MessageContent.Image -> add(ContentModality.IMAGE)
                            is MessageContent.Text -> Unit
                        }
                    }
                },
            requiresToolCalling = tools.isNotEmpty(),
            minimumOutputTokens = options.maxOutputTokens ?: 0,
        )
}

@Serializable
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: ModelCapabilities,
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
    }
}

@Serializable
data class ToolCall(
    val callId: String,
    val toolName: String,
    val arguments: JsonObject,
) {
    init {
        require(callId.isNotBlank())
        require(toolName.isNotBlank())
    }
}

@Serializable
data class ProviderUsage(
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
) {
    init {
        require(inputTokens == null || inputTokens >= 0)
        require(outputTokens == null || outputTokens >= 0)
    }
}

@Serializable
enum class FinishReason {
    STOP,
    LENGTH,
    TOOL_CALL,
    CANCELLED,
    ERROR,
    UNKNOWN,
}

@Serializable
enum class ProviderErrorCategory(
    val retryable: Boolean,
    val fallbackEligible: Boolean,
) {
    AUTHENTICATION(retryable = false, fallbackEligible = false),
    PERMISSION_DENIED(retryable = false, fallbackEligible = false),
    RATE_LIMITED(retryable = true, fallbackEligible = true),
    INVALID_REQUEST(retryable = false, fallbackEligible = false),
    CONTENT_REJECTED(retryable = false, fallbackEligible = false),
    MODEL_NOT_FOUND(retryable = false, fallbackEligible = true),
    CAPABILITY_MISMATCH(retryable = false, fallbackEligible = true),
    NETWORK(retryable = true, fallbackEligible = true),
    TIMEOUT(retryable = true, fallbackEligible = true),
    SERVICE_UNAVAILABLE(retryable = true, fallbackEligible = true),
    PROTOCOL(retryable = false, fallbackEligible = false),
    CANCELLED(retryable = false, fallbackEligible = false),
    UNKNOWN(retryable = false, fallbackEligible = false),
}

/** Provider-independent representation used for direct models and agent backends. */
@Serializable
data class ProviderError(
    val category: ProviderErrorCategory,
    val code: String,
    val message: String,
    val retryAfterMillis: Long? = null,
) {
    init {
        require(code.isNotBlank())
        require(message.isNotBlank())
        require(retryAfterMillis == null || retryAfterMillis >= 0)
    }

    val retryable: Boolean
        get() = category.retryable

    val fallbackEligible: Boolean
        get() = category.fallbackEligible
}

class ProviderException(
    val error: ProviderError,
    cause: Throwable? = null,
) : Exception(error.message, cause)

/** Shared marker for normalized streaming events. */
sealed interface StreamEvent {
    val requestId: String
    val sequence: Long?

    data class Started(
        override val requestId: String,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class TextDelta(
        override val requestId: String,
        val text: String,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class ToolCallArgumentsDelta(
        override val requestId: String,
        val callId: String,
        val toolName: String,
        val argumentsDelta: String,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class ToolCallReady(
        override val requestId: String,
        val call: ToolCall,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class UsageUpdated(
        override val requestId: String,
        val usage: ProviderUsage,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class Completed(
        override val requestId: String,
        val finishReason: FinishReason,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class Failed(
        override val requestId: String,
        val error: ProviderError,
        override val sequence: Long? = null,
    ) : ModelStreamEvent, AgentStreamEvent

    data class ToolApprovalRequired(
        override val requestId: String,
        val approval: AgentToolApprovalRequest,
        override val sequence: Long,
    ) : AgentStreamEvent

    data class JobUpdated(
        override val requestId: String,
        val job: AgentJob,
        override val sequence: Long,
    ) : AgentStreamEvent
}

/** Events a direct model is allowed to produce. */
sealed interface ModelStreamEvent : StreamEvent

/** Events an agent backend is allowed to produce, including approvals and long jobs. */
sealed interface AgentStreamEvent : StreamEvent
