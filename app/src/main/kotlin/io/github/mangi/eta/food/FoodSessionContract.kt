package io.github.mangi.eta.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@Serializable internal enum class CookingStatus { ACTIVE, COMPLETED, ABANDONED }
@Serializable internal enum class TimerState { RUNNING, PAUSED, FINISHED, CANCELLED }
@Serializable internal enum class StockConsumptionStatus { NOT_REQUESTED, PENDING, SUCCEEDED, PARTIAL, FAILED, UNCERTAIN }

@Serializable
internal data class CookingTimer(
    val id: String,
    val label: String,
    val stepId: String?,
    val durationSeconds: Int,
    val state: TimerState,
    val endsAt: String?,
    val remainingSeconds: Long?,
)

@Serializable internal data class IngredientOverride(val ingredientId: String, val quantity: Quantity)

@Serializable
internal data class CookingSession(
    val id: String,
    val recipeId: String,
    val recipeVersion: Int,
    val recipeSnapshot: RecipeDefinition,
    val servings: Int,
    val currentStepId: String?,
    val completedStepIds: List<String>,
    val skippedStepIds: List<String>,
    val timers: List<CookingTimer>,
    val ingredientOverrides: List<IngredientOverride>,
    val status: CookingStatus,
    val startedAt: String,
    val completedAt: String?,
    val stockConsumptionStatus: StockConsumptionStatus,
    val revision: Long,
    val updatedAt: String,
)

@Serializable
internal data class CookingSessionResponse(override val schemaVersion: Int, val session: CookingSession) : FoodResponse

@Serializable internal enum class OperationState { SUCCEEDED, PARTIAL, FAILED, UNCERTAIN }

@Serializable
internal enum class OperationLineState {
    SUCCEEDED, SKIPPED_NOT_TRACKED, NEEDS_CONVERSION, INSUFFICIENT_STOCK, FAILED, UNCERTAIN,
}

@Serializable internal data class AppliedStock(val amount: Double, val grocyQuId: Long)

@Serializable
internal data class OperationLine(
    val ingredientId: String?,
    val grocyProductId: Long?,
    val requested: Quantity,
    val applied: AppliedStock?,
    val transactionId: String?,
    val state: OperationLineState,
    val message: String?,
    val code: String? = null,
)

@Serializable
internal data class OperationResult(
    override val schemaVersion: Int,
    val operationId: String,
    val state: OperationState,
    val replayed: Boolean,
    val lines: List<OperationLine>,
    val leftoverProductId: Long? = null,
    val code: String? = null,
) : FoodResponse

@Serializable
internal data class CompleteConsumption(val ingredientId: String, val grocyProductId: Long, val quantity: MeasuredQuantity)

@Serializable internal data class Leftovers(val servings: Int, val bestBeforeDate: String, val locationId: Long)

@Serializable
internal data class CompleteRequest(
    val operationId: String,
    val session: CookingSession,
    val servingsMade: Int,
    val rating: Int?,
    val consumptions: List<CompleteConsumption>,
    val leftovers: Leftovers?,
)

@Serializable internal data class AssistantRequest(val requestId: String, val message: String, val session: CookingSession)
@Serializable internal enum class ProposalImpact { MINOR, MAJOR }

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
internal sealed interface AssistantProposal {
    val id: String
    val summary: String
    val impact: ProposalImpact

    @Serializable @SerialName("REPLACE_INGREDIENT")
    data class ReplaceIngredient(
        override val id: String, override val summary: String, override val impact: ProposalImpact,
        val patch: ReplacementPatch,
    ) : AssistantProposal

    @Serializable @SerialName("CHANGE_QUANTITY")
    data class ChangeQuantity(
        override val id: String, override val summary: String, override val impact: ProposalImpact,
        val patch: QuantityPatch,
    ) : AssistantProposal

    @Serializable @SerialName("ADJUST_DURATION")
    data class AdjustDuration(
        override val id: String, override val summary: String, override val impact: ProposalImpact,
        val patch: DurationPatch,
    ) : AssistantProposal

    @Serializable @SerialName("ADJUST_TEMPERATURE")
    data class AdjustTemperature(
        override val id: String, override val summary: String, override val impact: ProposalImpact,
        val patch: TemperaturePatch,
    ) : AssistantProposal

    @Serializable @SerialName("ADD_STEP_NOTE")
    data class AddStepNote(
        override val id: String, override val summary: String, override val impact: ProposalImpact,
        val patch: NotePatch,
    ) : AssistantProposal

    @Serializable @SerialName("SKIP_STEP")
    data class SkipStep(
        override val id: String, override val summary: String, override val impact: ProposalImpact,
        val patch: StepPatch,
    ) : AssistantProposal
}

@Serializable
internal data class ReplacementPatch(
    val ingredientId: String, val newName: String, val quantity: MeasuredQuantity, val grocyProductId: Long?,
)

@Serializable internal data class QuantityPatch(val ingredientId: String, val quantity: MeasuredQuantity)
@Serializable internal data class DurationPatch(val stepId: String, val durationSeconds: Int)
@Serializable internal data class TemperaturePatch(val stepId: String, val temperature: Temperature)
@Serializable internal data class NotePatch(val stepId: String, val note: String)
@Serializable internal data class StepPatch(val stepId: String)

@Serializable
internal data class AssistantResponse(
    override val schemaVersion: Int,
    val requestId: String,
    val answer: String,
    val proposals: List<AssistantProposal>,
    val degraded: Boolean,
) : FoodResponse
