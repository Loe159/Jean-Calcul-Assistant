package io.github.mangi.eta.food

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun foodFixture(name: String): String =
    checkNotNull(FoodContractTest::class.java.getResource("/food-v1/$name.json")).readText()

internal fun recipeFixture(): RecipeDefinition = FoodContract.json.decodeFromString(foodFixture("recipe"))

class FoodContractTest {
    private val json = FoodContract.json

    @Test
    fun parsesAllContractPayloadFixtures() {
        val recipe = recipeFixture()
        val availability = FoodContract.decode<Availability>(foodFixture("availability"))
        val session = json.decodeFromString<CookingSession>(foodFixture("cooking-session"))
        val completion = json.decodeFromString<CompleteRequest>(foodFixture("complete-request"))
        val assistant = json.decodeFromString<AssistantRequest>(foodFixture("assistant-request"))
        val result = FoodContract.decode<OperationResult>(foodFixture("operation-result"))

        assertEquals(recipe.id, availability.recipeId)
        assertEquals(recipe, session.recipeSnapshot)
        assertEquals(recipe, completion.session.recipeSnapshot)
        assertEquals(CookingStatus.COMPLETED, completion.session.status)
        assertEquals(completion.operationId, result.operationId)
        assertEquals(session, assistant.session)
        assertEquals(assistant.requestId, FoodContract.decode<AssistantResponse>(foodFixture("assistant-response")).requestId)
        assertTrue(FoodContract.decode<PantryResponse>(foodFixture("pantry")).items.isNotEmpty())
        assertTrue(FoodContract.decode<CatalogResponse>(foodFixture("catalog")).products.isNotEmpty())
        assertTrue(FoodContract.decode<Preferences>(foodFixture("preferences")).inferred.isNotEmpty())
        assertEquals(2, FoodContract.decode<BriefCardsResponse>(foodFixture("brief-cards")).cards.size)
        assertEquals(JobState.SUCCEEDED, FoodContract.decode<Job>(foodFixture("job")).state)
        assertEquals(4, json.decodeFromString<GenerateRequest>(foodFixture("generate-request")).params.servings)

        assertEquals(completion, json.decodeFromString<CompleteRequest>(json.encodeToString(completion)))
        assertTrue(json.parseToJsonElement(json.encodeToString(completion)).jsonObject.containsKey("leftovers"))
        assertEquals(
            setOf("favorite"),
            json.decodeFromString<JsonObject>(json.encodeToString(UpdateRecipeLibraryRequest(favorite = false))).keys,
        )
    }

    @Test
    fun acceptsNewFieldsAndRejectsNewEnumValuesAndVersions() {
        val payload = foodFixture("availability")
        assertEquals(
            FoodContract.decode<Availability>(payload),
            FoodContract.decode<Availability>(payload.replaceFirst("{", "{\"future\":true,")),
        )
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<Availability>(payload.replaceFirst("\"AVAILABLE\"", "\"FUTURE\""))
        }
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<Availability>(payload.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<AssistantResponse>(foodFixture("assistant-response").replace("ADJUST_TEMPERATURE", "FUTURE"))
        }
    }

    @Test
    fun scalingMatchesEverySharedCaseExactly() {
        val cases = json.decodeFromString<List<ScalingCase>>(foodFixture("scaling-cases"))
        assertTrue(cases.size >= 40)
        for ((name, input, expected) in cases) {
            val (base, target, quantity, scaling, rounding) = input
            assertEquals(name, expected, FoodQuantities.scale(quantity, base, target, scaling, rounding))
        }
    }

    @Test
    fun renderingMatchesEverySharedCaseExactly() {
        val cases = json.decodeFromString<List<RenderCase>>(foodFixture("step-rendering-cases"))
        assertTrue(cases.isNotEmpty())
        for ((instruction, ingredients, refs, base, target, expected) in cases) {
            assertEquals(instruction, expected, FoodQuantities.renderStep(instruction, ingredients, refs, base, target))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FoodQuantities.renderStep("{{ing:unknown}}", emptyList(), emptyList(), 2, 4)
        }
    }

    @Test
    fun availabilityRecomputesExactAmountsFromTheSameDatedStock() {
        val recipe = recipeFixture()
        val cached = FoodContract.decode<Availability>(foodFixture("availability"))
        assertEquals(cached, cached.recompute(recipe, cached.servings))

        val larger = cached.recompute(recipe, 6)
        assertEquals(cached.stockAsOf, larger.stockAsOf)
        assertEquals(AvailabilityCategory.FEW_PURCHASES, larger.summary.category)
        assertEquals(3, larger.summary.missingCount)
        val cream = larger.items.first { it.ingredientId == "ing_cream" }
        assertEquals(Quantity(300.0, UnitCode.ML), cream.required)
        assertEquals(MeasuredQuantity(180.0, UnitCode.ML), cream.missing)
        assertEquals(AvailabilityStatus.PARTIAL, cream.status)

        val smaller = cached.recompute(recipe, 2)
        assertEquals(AvailabilityCategory.READY, smaller.summary.category)
        assertEquals(1.0, smaller.summary.availableRatio, 0.0)
        assertTrue(smaller.items.none { it.missing != null })

        val unknown = cached.copy(items = cached.items.map {
            if (it.ingredientId == "ing_eggs") it.copy(status = AvailabilityStatus.UNKNOWN, inStock = null) else it
        }).recompute(recipe, 2)
        assertEquals(AvailabilityStatus.UNKNOWN, unknown.items.first { it.ingredientId == "ing_eggs" }.status)
        assertEquals(AvailabilityStatus.NOT_TRACKED, unknown.items.first { it.ingredientId == "ing_salt" }.status)
        assertEquals(5.0 / 6, unknown.summary.availableRatio, 0.0)
        assertEquals(AvailabilityCategory.FEW_PURCHASES, unknown.summary.category)
        assertEquals(1, unknown.summary.missingCount)
        assertTrue(unknown.items.none { it.missing != null })
        assertThrows(IllegalArgumentException::class.java) {
            cached.recompute(recipe.copy(version = recipe.version + 1), 2)
        }
    }

    @Serializable
    private data class ScalingCase(val name: String, val input: ScalingInput, val expected: ScaledQuantity)

    @Serializable
    private data class ScalingInput(
        val baseServings: Int,
        val targetServings: Int,
        val quantity: Quantity,
        val scaling: Scaling = Scaling.LINEAR,
        val rounding: Rounding? = null,
    )

    @Serializable
    private data class RenderCase(
        val instruction: String,
        val ingredients: List<ScalableIngredient>,
        val ingredientRefs: List<IngredientRef>,
        val baseServings: Int,
        val targetServings: Int,
        val expectedRendered: String,
    )
}
