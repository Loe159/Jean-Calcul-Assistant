package io.github.mangi.eta.food

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCuisineContractTest {
    private val json = FoodContract.json

    @Test
    fun shoppingFixturesDecodeAndRequestBodiesRoundTrip() {
        val shopping = response<ShoppingResponse>("shopping")
        assertEquals(3, shopping.items.size)
        assertTrue(shopping.items.first().origins.single().origin is ShoppingOrigin.MealSlot)
        assertEquals(ShoppingItemState.STOCKED, shopping.items.last().state)

        request<ShoppingAddRequest>("shopping-add-request")
        request<ShoppingPatchRequest>("shopping-patch-request")
        request<ShoppingDeleteRequest>("shopping-delete-request")
        request<RecipeShoppingRequest>("recipe-shopping-request")
        request<ShoppingUndoRequest>("shopping-undo-request")
        request<PutAwayRequest>("put-away-request")
        request<PutAwayRequest>("put-away-new-product-request")
        response<ShoppingOperationResult>("shopping-operation-result")
        response<ShoppingOperationResult>("shopping-undo-result")
        response<ShoppingOperationResult>("put-away-result")
        assertEquals(OperationState.UNCERTAIN, response<ShoppingOperationResult>("put-away-uncertain-result").state)
    }

    @Test
    fun planningFixturesDecodeAndRequestBodiesRoundTrip() {
        assertEquals(Meal.DINNER, response<MealWeek>("meal-week").slots.single().meal)
        request<PlanningPutRequest>("planning-put-request")
        request<PlanningDeleteRequest>("planning-delete-request")
        request<PlanningMoveRequest>("planning-move-request")
        request<GeneratePreviewRequest>("generate-preview-request")
        request<GenerateApplyRequest>("generate-apply-request")
        request<PlanningShoppingRequest>("planning-shopping-request")
        response<PlanningWriteResult>("planning-write-result")
        assertEquals("synthetic-payload.synthetic-signature", response<GeneratePreviewResult>("generate-preview-result").proposalId)
        response<PlanningWriteResult>("generate-apply-result")
        response<ShoppingSummary>("shopping-summary")
    }

    @Test
    fun pantryFixturesPreserveNullablePatchAndAdditiveFields() {
        val request = request<PantryEntryPatchRequest>("pantry-entry-patch-request")
        assertEquals(PantryDatePatch.Clear, request.bestBeforeDate)
        val unchanged = request.copy(bestBeforeDate = PantryDatePatch.Unchanged, amount = 2.0)
        val amountOnly = json.encodeToString(unchanged)
        assertTrue("bestBeforeDate" !in amountOnly)
        assertEquals(unchanged, json.decodeFromString<PantryEntryPatchRequest>(amountOnly))
        val dated = request.copy(bestBeforeDate = PantryDatePatch.Set("2026-12-01"))
        assertEquals(dated, json.decodeFromString<PantryEntryPatchRequest>(json.encodeToString(dated)))

        val pantry = response<PantryResponse>("pantry")
        assertTrue(pantry.items.flatMap { it.batches }.any { it.entryId != null && it.locationKind == LocationKind.FRIDGE })
        assertEquals(PantryEntryState.SUCCEEDED, response<PantryEntryResult>("pantry-entry-result").state)
        val oldBatch = json.decodeFromString<PantryBatch>(
            """{"stockEntryId":"fixture","amount":1.0,"bestBeforeDate":null,"locationId":1,"opened":false}""",
        )
        assertNull(oldBatch.entryId)
        assertNull(oldBatch.locationKind)
        assertEquals("2026-11-20T18:45:00Z", response<RecipeListResponse>("recipe-list").recipes.first().lastCookedAt)
    }

    @Test
    fun briefV2DecodesBothGenerationsWithoutChangingLegacyDecoder() {
        request<BriefCardsRequest>("brief-cards-v2-request")
        val modern = response<BriefCardsV2Response>("brief-cards-v2")
        assertTrue(modern.cards[0] is BriefCardV2.MealPlanned)
        assertTrue(modern.cards[1] is BriefCardV2.ShoppingList)
        assertThrows(SerializationException::class.java) {
            response<BriefCardsResponse>("brief-cards-v2")
        }
        val legacy = response<BriefCardsResponse>("brief-cards")
        assertEquals(legacy.cards, response<BriefCardsV2Response>("brief-cards").cards)
        assertEquals(modern, FoodContract.decode<BriefCardsV2Response>(json.encodeToString(modern)))
    }

    @Test
    fun unknownFieldsAreAcceptedAndUnknownEnumsAreRejected() {
        val body = foodFixture("shopping").replaceFirst("{", """{"futureField":true,""")
        assertEquals(response<ShoppingResponse>("shopping"), FoodContract.decode<ShoppingResponse>(body))
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<ShoppingResponse>(body.replace("IN_BASKET", "UNKNOWN_STATE"))
        }
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<MealWeek>(foodFixture("meal-week").replace("DINNER", "BRUNCH"))
        }
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<BriefCardsV2Response>(foodFixture("brief-cards-v2").replace("MEAL_PLANNED", "NEW_KIND"))
        }
        assertThrows(SerializationException::class.java) {
            FoodContract.decode<PantryResponse>(foodFixture("pantry").replace("FRIDGE", "NEW_LOCATION"))
        }
    }

    private inline fun <reified T> request(name: String): T {
        val fixture = foodFixture(name)
        val request = json.decodeFromString<T>(fixture)
        assertEquals(
            normalizeNumbers(json.parseToJsonElement(fixture)),
            normalizeNumbers(json.parseToJsonElement(json.encodeToString(request))),
        )
        return request
    }

    private inline fun <reified T : FoodResponse> response(name: String): T =
        FoodContract.decode<T>(foodFixture(name))

    private fun normalizeNumbers(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { normalizeNumbers(it.value) })
        is JsonArray -> JsonArray(value.map(::normalizeNumbers))
        is JsonPrimitive -> if (value.isString) value else value.content.toBigDecimalOrNull()
            ?.let { JsonPrimitive(it.stripTrailingZeros()) } ?: value
    }
}
