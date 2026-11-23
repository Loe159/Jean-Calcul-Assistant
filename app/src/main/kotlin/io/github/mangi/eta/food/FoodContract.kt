package io.github.mangi.eta.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

internal interface FoodResponse {
    val schemaVersion: Int
}

internal object FoodContract {
    val json = Json { ignoreUnknownKeys = true }

    inline fun <reified T : FoodResponse> decode(body: String): T =
        json.decodeFromString<T>(body).also {
            if (it.schemaVersion != 1) throw SerializationException("Unsupported food schema version")
        }
}

@Serializable internal data class FoodAcknowledgement(override val schemaVersion: Int) : FoodResponse

@Serializable
internal enum class GrocyHealth {
    @SerialName("ok") OK,
    @SerialName("unreachable") UNREACHABLE,
    @SerialName("misconfigured") MISCONFIGURED,
}

@Serializable
internal enum class AiHealth {
    @SerialName("enabled") ENABLED,
    @SerialName("disabled") DISABLED,
}

@Serializable
internal data class FoodHealth(
    override val schemaVersion: Int,
    val grocy: GrocyHealth,
    val grocyVersion: String?,
    val ai: AiHealth,
    val serverTime: String,
) : FoodResponse

@Serializable internal enum class Budget { LOW, MEDIUM, ANY }
@Serializable internal enum class SuggestMode { FROM_PANTRY, SURPRISE }
@Serializable internal enum class GenerateMode { FROM_PANTRY, CRAVING, SURPRISE, IMPORT_TEXT }

@Serializable
internal data class GenerationParams(
    val servings: Int,
    val excludedIngredients: List<String>,
    val allergies: List<String>,
    val equipment: List<Equipment>,
    val prioritizePantry: Boolean,
    val useExpiringFirst: Boolean,
    val maxTotalMinutes: Int? = null,
    val difficulty: Difficulty? = null,
    val mealType: MealType? = null,
    val cuisine: String? = null,
    val budget: Budget? = null,
)

@Serializable internal data class SuggestRequest(val mode: SuggestMode, val params: GenerationParams)

@Serializable
internal data class RecipeSuggestion(
    val recipe: RecipeSummary,
    val availability: AvailabilitySummary,
    val score: Double,
    val reasons: List<String>,
)

@Serializable
internal data class SuggestResponse(override val schemaVersion: Int, val suggestions: List<RecipeSuggestion>) : FoodResponse

@Serializable
internal data class GenerateRequest(
    val requestId: String,
    val mode: GenerateMode,
    val params: GenerationParams,
    val prompt: String? = null,
    val text: String? = null,
)

@Serializable internal data class AdaptRequest(val requestId: String, val instruction: String, val servings: Int? = null)
@Serializable internal enum class JobState { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }
@Serializable internal enum class JobKind { GENERATE, ADAPT }
@Serializable internal enum class JobStage { PANTRY, LIBRARY, GENERATING, VALIDATING, AVAILABILITY }

@Serializable
internal data class GenerateResponse(override val schemaVersion: Int, val jobId: String, val state: JobState) : FoodResponse

@Serializable internal data class AdaptResponse(override val schemaVersion: Int, val jobId: String) : FoodResponse
@Serializable internal data class JobProgress(val stage: JobStage, val message: String)
@Serializable internal data class GeneratedRecipe(val recipe: RecipeDefinition, val availability: Availability)
@Serializable internal data class JobResult(val recipes: List<GeneratedRecipe>)
@Serializable internal data class JobError(val code: String, val message: String)

@Serializable
internal data class Job(
    override val schemaVersion: Int,
    val jobId: String,
    val kind: JobKind,
    val state: JobState,
    val progress: JobProgress,
    val result: JobResult? = null,
    val error: JobError? = null,
) : FoodResponse

@Serializable
internal data class DeclaredPreferences(
    val allergies: List<String>,
    val excludedIngredients: List<String>,
    val dietary: List<String>,
    val equipment: List<Equipment>,
    val defaultServings: Int,
    val maxWeeknightMinutes: Int,
    val cuisinesLiked: List<String>,
)

@Serializable
internal data class InferredPreference(
    val key: String,
    val value: JsonElement,
    val confidence: Double,
    val evidenceCount: Long,
    val updatedAt: String,
)

@Serializable
internal data class Preferences(
    override val schemaVersion: Int,
    val declared: DeclaredPreferences,
    val inferred: List<InferredPreference>,
) : FoodResponse

@Serializable internal data class UpdatePreferencesRequest(val declared: DeclaredPreferences)

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
internal sealed interface BriefCard {
    val id: String
    val priority: Int
    val title: String
    val subtitle: String

    @Serializable @SerialName("RECIPE_RECOMMENDATION")
    data class RecipeRecommendation(
        override val id: String,
        override val priority: Int,
        override val title: String,
        override val subtitle: String,
        val recipeId: String,
        val totalMinutes: Int,
        val availableRatio: Double,
        val image: RecipeImage?,
        val reasons: List<String>,
    ) : BriefCard

    @Serializable @SerialName("EXPIRING_PRODUCTS")
    data class ExpiringProducts(
        override val id: String,
        override val priority: Int,
        override val title: String,
        override val subtitle: String,
        val products: List<ExpiringProduct>,
        val suggestedRecipeIds: List<String>,
    ) : BriefCard
}

@Serializable internal enum class ExpiryStatus { DUE_SOON, EXPIRED }

@Serializable
internal data class ExpiringProduct(
    val grocyProductId: Long, val name: String, val bestBeforeDate: String, val status: ExpiryStatus,
)

@Serializable
internal data class BriefCardsResponse(
    override val schemaVersion: Int,
    val generatedAt: String,
    val validUntil: String,
    val cards: List<BriefCard>,
) : FoodResponse

@Serializable internal enum class BriefFeedbackAction { OPENED, DISMISSED, COOKED }
@Serializable internal data class BriefFeedbackRequest(val eventId: String, val cardId: String, val action: BriefFeedbackAction)
