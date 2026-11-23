package io.github.mangi.eta.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal enum class UnitCode {
    @SerialName("mg") MG,
    @SerialName("g") G,
    @SerialName("kg") KG,
    @SerialName("ml") ML,
    @SerialName("cl") CL,
    @SerialName("l") L,
    @SerialName("tsp") TSP,
    @SerialName("tbsp") TBSP,
    @SerialName("cup") CUP,
    @SerialName("piece") PIECE,
    @SerialName("clove") CLOVE,
    @SerialName("slice") SLICE,
    @SerialName("can") CAN,
    @SerialName("pack") PACK,
    @SerialName("bunch") BUNCH,
    @SerialName("pinch") PINCH,
}

@Serializable internal data class Quantity(val amount: Double?, val unit: UnitCode)
@Serializable internal data class MeasuredQuantity(val amount: Double, val unit: UnitCode)
@Serializable internal enum class Scaling { LINEAR, FIXED, TO_TASTE }
@Serializable internal enum class Rounding { WHOLE, HALF }
@Serializable internal enum class Difficulty { EASY, MEDIUM, HARD }
@Serializable internal enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK, DESSERT }

@Serializable
internal enum class Equipment {
    OVEN, STOVE, MICROWAVE, AIR_FRYER, BLENDER, MIXER, GRILL, STEAMER, PRESSURE_COOKER, KETTLE,
}

@Serializable
internal data class Substitution(
    val name: String,
    val grocyProductId: Long?,
    val quantity: Quantity,
    val note: String,
)

@Serializable
internal data class RecipeIngredient(
    val id: String,
    val grocyProductId: Long?,
    val name: String,
    val quantity: Quantity,
    val scaling: Scaling,
    val preparation: String?,
    val group: String?,
    val optional: Boolean,
    val substitutions: List<Substitution>,
    val rounding: Rounding? = null,
)

@Serializable internal data class IngredientRef(val ingredientId: String, val fraction: Double)
@Serializable internal enum class TemperatureMode { OVEN, PAN, WATER }
@Serializable internal enum class TemperatureUnit { C }
@Serializable internal data class Temperature(val value: Double?, val unit: TemperatureUnit, val mode: TemperatureMode)
@Serializable internal data class TimerSuggestion(val label: String, val durationSeconds: Int)
@Serializable internal data class RecipeImage(val url: String)

@Serializable
internal data class RecipeStep(
    val id: String,
    val order: Int,
    val title: String,
    val instruction: String,
    val ingredientRefs: List<IngredientRef>,
    val durationSeconds: Int?,
    val temperature: Temperature?,
    val timerSuggestion: TimerSuggestion?,
    val equipment: List<Equipment>,
    val image: RecipeImage?,
    val tips: List<String>,
)

@Serializable internal enum class RecipeSourceKind { GENERATED, GROCY, IMPORTED, MANUAL, ADAPTED }

@Serializable
internal data class RecipeSource(
    val kind: RecipeSourceKind,
    val url: String?,
    val model: String?,
    val parentRecipeId: String?,
)

@Serializable internal enum class NutritionSource { ESTIMATE }

@Serializable
internal data class NutritionEstimate(
    val kcalPerServing: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: NutritionSource,
)

@Serializable internal enum class AllergenCheck { NOT_GUARANTEED, VERIFIED_FROM_PRODUCTS }

@Serializable
internal data class RecipeDefinition(
    val id: String,
    val version: Int,
    val grocyRecipeId: Long?,
    val title: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val activeTimeMinutes: Int,
    val difficulty: Difficulty,
    val mealTypes: List<MealType>,
    val cuisine: String?,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val equipment: List<Equipment>,
    val tags: List<String>,
    val source: RecipeSource,
    val image: RecipeImage?,
    val nutritionEstimate: NutritionEstimate?,
    val allergenCheck: AllergenCheck,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
internal data class RecipeDraft(
    val grocyRecipeId: Long?,
    val title: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val activeTimeMinutes: Int,
    val difficulty: Difficulty,
    val mealTypes: List<MealType>,
    val cuisine: String?,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
    val equipment: List<Equipment>,
    val tags: List<String>,
    val source: RecipeSource,
    val image: RecipeImage?,
    val nutritionEstimate: NutritionEstimate?,
    val allergenCheck: AllergenCheck,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable internal data class CreateRecipeRequest(val recipe: RecipeDraft)

@Serializable
internal data class RecipeSummary(
    val id: String,
    val version: Int,
    val title: String,
    val description: String,
    val servings: Int,
    val prepTimeMinutes: Int,
    val cookTimeMinutes: Int,
    val difficulty: Difficulty,
    val mealTypes: List<MealType>,
    val cuisine: String?,
    val tags: List<String>,
    val image: RecipeImage?,
    val favorite: Boolean,
)

@Serializable
internal data class RecipeLibrary(
    val favorite: Boolean,
    val rating: Int?,
    val notes: String,
    val timesCooked: Int,
    val lastCookedAt: String?,
)

@Serializable
internal data class UpdateRecipeLibraryRequest(
    val favorite: Boolean? = null,
    val rating: Int? = null,
    val notes: String? = null,
)

@Serializable
internal data class RecipeListResponse(override val schemaVersion: Int, val recipes: List<RecipeSummary>) : FoodResponse

@Serializable
internal data class RecipeDetailResponse(
    override val schemaVersion: Int,
    val recipe: RecipeDefinition,
    val library: RecipeLibrary,
) : FoodResponse

@Serializable
internal data class RecipeWriteResponse(override val schemaVersion: Int, val recipe: RecipeDefinition) : FoodResponse
