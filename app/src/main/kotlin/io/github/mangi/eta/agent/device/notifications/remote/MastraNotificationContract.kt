package io.github.mangi.eta.agent.device.notifications.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Kotlin mirror of the versioned Mastra contract (homelab-ai: src/mastra/notifications/contract.ts).
 * Shared fixtures: app/src/test/resources/notification-contract/v1 (identical copies on both sides).
 */
internal object MastraContract {
    const val SCHEMA_VERSION = 1
    const val MAX_BATCH = 50
    const val MAX_FEEDBACK_BATCH = 100
    private val opaque = Regex("^[A-Za-z0-9_-]{8,64}$")
    private val hash = Regex("^[a-f0-9]{64}$")
    private val subtype = Regex("^[a-z][a-z0-9_]{0,47}$")
    val TOPICS = setOf("messages", "security", "development", "learning", "work", "calendar", "tasks",
        "finance", "shopping", "news", "promotions", "social", "games", "system", "other")

    /** Decoding tolerates new optional fields from a newer server; enums stay strict. */
    val json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }

    fun isOpaque(value: String) = opaque.matches(value)
    fun isHash(value: String) = hash.matches(value)

    /** Rejects a decision that would be unsafe to act upon, even if it parsed. */
    fun validate(d: RemoteDecision): Boolean =
        isOpaque(d.id) && d.revision >= 1 && isHash(d.contentHash) && d.topic in TOPICS &&
            subtype.matches(d.subtype) && d.reason.isNotBlank() && d.reason.length <= 160 &&
            d.actions.size <= 4 && d.actions.all { it.status == "PROPOSED" && it.actionId.startsWith("act_") } &&
            !(d.recommendedAndroidAction == AndroidAction.DISMISS && d.priority == Priority.NOW)
}

@Serializable internal enum class Priority { NOW, TODAY, LOW }
@Serializable internal enum class Interest { WANTED, NEUTRAL, UNWANTED }
@Serializable internal enum class AndroidAction { KEEP, DISMISS }
@Serializable internal enum class Uncertainty { LOW, MEDIUM, HIGH }
@Serializable internal enum class DecisionSource { RULE, USER_PREFERENCE, LEARNED_PREFERENCE, MODEL, FALLBACK }
@Serializable internal enum class Redaction { NUMERIC_CODE }
@Serializable internal enum class ActionType { CREATE_TASK, CREATE_CALENDAR_EVENT, DRAFT_REPLY, REMIND_LATER, OPEN_SOURCE_APP }

@Serializable
internal data class RemoteNotification(
    val id: String,
    val revision: Int,
    val contentHash: String,
    @SerialName("package") val packageName: String,
    val appLabel: String? = null,
    val postedAt: String,
    val title: String?,
    val content: String?,
    val subText: String?,
    val channelId: String?,
    val androidCategory: String?,
    val clearable: Boolean,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val hasRemoteInput: Boolean,
    val conversation: Boolean = false,
    val systemApp: Boolean = false,
    val appCategory: String? = null,
    val redactions: List<Redaction> = emptyList(),
)

@Serializable
internal data class AnalyzeRequest(
    val schemaVersion: Int = MastraContract.SCHEMA_VERSION,
    val deviceId: String,
    val batchId: String,
    val sentAt: String,
    val timeZone: String,
    val learningConsent: Boolean,
    val notifications: List<RemoteNotification>,
)

@Serializable
internal data class ActionProposal(
    val actionId: String,
    val type: ActionType,
    val status: String,
    val label: String,
    val payload: JsonObject,
    val missing: List<String> = emptyList(),
    val requiresApproval: Boolean,
)

@Serializable
internal data class RemoteDecision(
    val id: String,
    val revision: Int,
    val contentHash: String,
    val topic: String,
    val subtype: String,
    val priority: Priority,
    val interest: Interest,
    val recommendedAndroidAction: AndroidAction,
    val protected: Boolean,
    val reason: String,
    val uncertainty: Uncertainty,
    val source: DecisionSource,
    val ruleId: String? = null,
    val actions: List<ActionProposal> = emptyList(),
    val policyVersion: String,
    val modelVersion: String? = null,
    val decidedAt: String,
)

@Serializable
internal data class ItemError(val id: String? = null, val index: Int, val code: String, val message: String)

@Serializable
internal data class AnalyzeResponse(
    val schemaVersion: Int,
    val batchId: String,
    val decisions: List<RemoteDecision>,
    val errors: List<ItemError> = emptyList(),
)

@Serializable
internal data class FeedbackContext(@SerialName("package") val packageName: String, val channelId: String?, val subtype: String?)

@Serializable
internal data class FeedbackEvent(
    val eventId: String,
    val notificationId: String,
    val revision: Int,
    val type: String,
    val occurredAt: String,
    val correctedTopic: String? = null,
    val correctedPriority: Priority? = null,
    val actionType: ActionType? = null,
    val context: FeedbackContext? = null,
)

@Serializable
internal data class FeedbackRequest(
    val schemaVersion: Int = MastraContract.SCHEMA_VERSION,
    val deviceId: String,
    val batchId: String,
    val learningConsent: Boolean,
    val events: List<FeedbackEvent>,
)

@Serializable
internal data class FeedbackResponse(
    val schemaVersion: Int,
    val batchId: String,
    val accepted: List<String>,
    val duplicates: List<String>,
    val discarded: List<String> = emptyList(),
    val errors: List<ItemError> = emptyList(),
    val policyVersion: String,
)

@Serializable
internal data class ModelHealth(val enabled: Boolean, val available: Boolean?, val version: String?)

@Serializable
internal data class HealthResponse(
    val schemaVersion: Int,
    val status: String,
    val policyVersion: String,
    val model: ModelHealth,
    val serverTime: String,
)

@Serializable
internal data class ActionApproval(val summary: String, val fingerprint: String)

@Serializable
internal data class ActionRecord(
    val actionId: String,
    val notificationId: String,
    val revision: Int,
    val type: ActionType,
    val state: String,
    val label: String,
    val payload: JsonObject,
    val missing: List<String> = emptyList(),
    val approval: ActionApproval? = null,
    val draft: String? = null,
    val result: String? = null,
    val error: String? = null,
    val updatedAt: String,
)

@Serializable
internal data class ActionList(val schemaVersion: Int, val actions: List<ActionRecord>)

@Serializable
internal data class ActionDetails(val title: String? = null, val start: String? = null, val end: String? = null)

@Serializable
internal data class ActionCommand(
    val schemaVersion: Int = MastraContract.SCHEMA_VERSION,
    val deviceId: String,
    val command: String,
    val fingerprint: String? = null,
    val details: ActionDetails? = null,
)
