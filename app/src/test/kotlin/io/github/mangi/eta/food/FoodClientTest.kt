package io.github.mangi.eta.food

import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodClientTest {
    private val secret = Base64.getUrlEncoder().encodeToString(ByteArray(32))
    private val pairing = MastraPairing("https://example.test", "jc_test", secret)

    @Test
    fun signsAllMethodsAndUsesEncodedPathsWithNoQuery() = runBlocking {
        val seen = mutableListOf<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            val path = request.url.encodedPath
            assertEquals(null, request.url.query)
            assertEquals("jc_test", request.header("X-JC-Device"))
            assertEquals("1234", request.header("X-JC-Timestamp"))
            assertEquals(
                MastraSignedTransport.sign(secret, request.method, path, "1234", request.header("X-JC-Nonce")!!, body),
                request.header("X-JC-Signature"),
            )
            seen += "${request.method} $path"
            when (request.method) {
                "POST" -> chain.response(200, foodFixture("job"))
                "PATCH" -> chain.response(200, FoodContract.json.encodeToString(
                    RecipeDetailResponse(1, recipeFixture(), RecipeLibrary(true, null, "", 0, null)),
                ))
                "DELETE" -> if (path.contains("/recipes/")) chain.response(200, "{\"schemaVersion\":1}")
                    else chain.response(200, foodFixture("preferences"))
                else -> chain.response(200, foodFixture("preferences"))
            }
        }.build()
        val client = FoodClient(pairing, http) { 1234 }
        val preferences = client.preferences()
        client.updatePreferences(UpdatePreferencesRequest(preferences.declared))
        client.cancelJob("job_a")
        assertTrue(client.updateLibrary("rcp_a", UpdateRecipeLibraryRequest(favorite = true)).library.favorite)
        assertEquals(preferences, client.deleteInferredPreference("cuisine/été ?"))
        assertEquals(1, client.archiveRecipe("rcp_a").schemaVersion)
        assertEquals(
            listOf(
                "GET /mobile/v1/food/preferences",
                "PUT /mobile/v1/food/preferences",
                "POST /mobile/v1/food/jobs/job_a/cancel",
                "PATCH /mobile/v1/food/recipes/rcp_a/library",
                "DELETE /mobile/v1/food/preferences/inferred/cuisine%2F%C3%A9t%C3%A9%20%3F",
                "DELETE /mobile/v1/food/recipes/rcp_a",
            ),
            seen,
        )
        assertThrows(IllegalArgumentException::class.java) {
            MastraSignedTransport(pairing, http).execute("GET", "/mobile/v1/food/pantry?filter=1")
        }
        Unit
    }

    @Test
    fun mapsSignedApiErrorsToSharedHttpException() {
        val http = OkHttpClient.Builder().addInterceptor {
            it.response(503, "{\"error\":\"AI_DISABLED\",\"message\":\"Désactivée\"}")
        }.build()
        val error = assertThrows(MastraHttpException::class.java) { runBlocking { FoodClient(pairing, http).recipes() } }
        assertEquals("AI_DISABLED", error.code)
        assertEquals(503, error.status)
        assertTrue(!error.permanent)
    }

    @Test
    fun cuisineRoutesSignEncodedPathsAndFrozenBodies() = runBlocking {
        val week = "/planning/weeks/2026-11-23"
        val slot = "/planning/slots/2026-11-25/DINNER"
        val responses = mutableMapOf(
            "GET /shopping" to "shopping",
            "POST /shopping/items" to "shopping-operation-result",
            "PATCH /shopping/items/20" to "shopping-operation-result",
            "DELETE /shopping/items/20" to "shopping-operation-result",
            "POST /shopping/undo" to "shopping-undo-result",
            "POST /shopping/put-away" to "put-away-result",
            "POST /recipes/r%C3%A9cette%2Ftest%20%3F/shopping-items" to "shopping-operation-result",
            "PATCH /pantry/entries/31" to "pantry-entry-result",
            "GET $week" to "meal-week",
            "PUT $slot" to "planning-write-result",
            "DELETE $slot" to "planning-write-result",
            "POST /planning/move" to "planning-write-result",
            "POST $week/generate PREVIEW" to "generate-preview-result",
            "POST $week/generate APPLY" to "generate-apply-result",
            "GET $week/shopping-summary" to "shopping-summary",
            "POST $week/shopping-items" to "shopping-operation-result",
            "POST /brief/cards" to "brief-cards-v2",
        )
        val frozenRecipeBody = foodFixture("recipe-shopping-request")
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            assertEquals(null, request.url.encodedQuery)
            assertEquals(
                MastraSignedTransport.sign(secret, request.method, path, "1234", request.header("X-JC-Nonce")!!, body),
                request.header("X-JC-Signature"),
            )
            val shortPath = path.removePrefix("/mobile/v1/food")
            val mode = if (shortPath.endsWith("/generate")) {
                if (body.contains("\"PREVIEW\"")) " PREVIEW" else " APPLY"
            } else ""
            if (shortPath.startsWith("/recipes/")) assertEquals(frozenRecipeBody, body)
            if (request.method == "DELETE") assertTrue(body.contains("\"expectedRevision\""))
            chain.response(200, foodFixture(checkNotNull(responses.remove("${request.method} $shortPath$mode"))))
        }.build()
        val client = FoodClient(pairing, http) { 1234 }
        val day = MealSlotKey("2026-11-25", Meal.DINNER)
        val monday = "2026-11-23"
        client.shopping()
        client.addShoppingItem(requestFixture("shopping-add-request"))
        client.patchShoppingItem(20, requestFixture("shopping-patch-request"))
        client.deleteShoppingItem(20, requestFixture("shopping-delete-request"))
        client.undoShopping(requestFixture("shopping-undo-request"))
        client.putAway(requestFixture("put-away-request"))
        client.replayCuisine(CuisineOperationKind.RECIPE_SHOPPING, "récette/test ?", frozenRecipeBody)
        client.patchPantryEntry(31, requestFixture("pantry-entry-patch-request"))
        client.mealWeek(monday)
        client.putMealSlot(day, requestFixture("planning-put-request"))
        client.deleteMealSlot(day, requestFixture("planning-delete-request"))
        client.moveMealSlot(requestFixture("planning-move-request"))
        client.previewMealWeek(monday, requestFixture("generate-preview-request"))
        client.applyMealWeek(monday, requestFixture("generate-apply-request"))
        client.planningShoppingSummary(monday)
        client.addPlanningShoppingItems(monday, requestFixture("planning-shopping-request"))
        client.briefCardsV2(requestFixture("brief-cards-v2-request"))
        assertTrue(responses.isEmpty())
    }

    private inline fun <reified T> requestFixture(name: String): T =
        FoodContract.json.decodeFromString(foodFixture(name))
}

internal fun Interceptor.Chain.response(code: Int, body: String): Response = Response.Builder()
    .request(request()).protocol(Protocol.HTTP_1_1).code(code).message("Test").body(body.toResponseBody()).build()
