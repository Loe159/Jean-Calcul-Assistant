package io.github.mangi.eta.food

import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal class FoodClient(
    pairing: MastraPairing,
    http: OkHttpClient = defaultHttp,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val transport = MastraSignedTransport(pairing, http, clock)

    suspend fun health(): FoodHealth = get("/health")
    suspend fun pantry(): PantryResponse = get("/pantry")
    suspend fun catalog(): CatalogResponse = get("/catalog")

    suspend fun addPantryItem(request: PantryAddRequest): OperationResult =
        send("POST", "/pantry/items", request)

    suspend fun consumePantryItem(productId: Long, request: PantryConsumeRequest): OperationResult =
        send("POST", "/pantry/items/$productId/consume", request)

    suspend fun patchPantryEntry(entryId: Long, request: PantryEntryPatchRequest): PantryEntryResult =
        writeCuisine(CuisineOperationKind.PANTRY_ENTRY_PATCH, entryId.toString(), request)

    suspend fun shopping(): ShoppingResponse = get("/shopping")

    suspend fun addShoppingItem(request: ShoppingAddRequest): ShoppingOperationResult =
        writeCuisine(CuisineOperationKind.SHOPPING_ADD, null, request)

    suspend fun patchShoppingItem(itemId: Long, request: ShoppingPatchRequest): ShoppingOperationResult =
        writeCuisine(CuisineOperationKind.SHOPPING_PATCH, itemId.toString(), request)

    suspend fun deleteShoppingItem(itemId: Long, request: ShoppingDeleteRequest): ShoppingOperationResult =
        writeCuisine(CuisineOperationKind.SHOPPING_DELETE, itemId.toString(), request)

    suspend fun addRecipeShoppingItems(recipeId: String, request: RecipeShoppingRequest): ShoppingOperationResult =
        writeCuisine(CuisineOperationKind.RECIPE_SHOPPING, recipeId, request)

    suspend fun undoShopping(request: ShoppingUndoRequest): ShoppingOperationResult =
        writeCuisine(CuisineOperationKind.SHOPPING_UNDO, null, request)

    suspend fun putAway(request: PutAwayRequest): ShoppingOperationResult =
        send("POST", "/shopping/put-away", request)

    suspend fun mealWeek(weekStart: String): MealWeek = get(weekPath(weekStart))

    suspend fun putMealSlot(slot: MealSlotKey, request: PlanningPutRequest): PlanningWriteResult =
        send("PUT", slotPath(slot), request)

    suspend fun deleteMealSlot(slot: MealSlotKey, request: PlanningDeleteRequest): PlanningWriteResult =
        send("DELETE", slotPath(slot), request)

    suspend fun moveMealSlot(request: PlanningMoveRequest): PlanningWriteResult =
        send("POST", "/planning/move", request)

    suspend fun previewMealWeek(weekStart: String, request: GeneratePreviewRequest): GeneratePreviewResult =
        send("POST", "${weekPath(weekStart)}/generate", request)

    suspend fun applyMealWeek(weekStart: String, request: GenerateApplyRequest): PlanningWriteResult =
        send("POST", "${weekPath(weekStart)}/generate", request)

    suspend fun planningShoppingSummary(weekStart: String): ShoppingSummary =
        get("${weekPath(weekStart)}/shopping-summary")

    suspend fun addPlanningShoppingItems(weekStart: String, request: PlanningShoppingRequest): ShoppingOperationResult =
        send("POST", "${weekPath(weekStart)}/shopping-items", request)

    suspend fun recipes(): RecipeListResponse = get("/recipes")
    suspend fun recipe(id: String): RecipeDetailResponse = get(recipePath(id))
    suspend fun createRecipe(request: CreateRecipeRequest): RecipeWriteResponse = send("POST", "/recipes", request)

    suspend fun updateLibrary(id: String, request: UpdateRecipeLibraryRequest): RecipeDetailResponse =
        send("PATCH", "${recipePath(id)}/library", request)

    suspend fun duplicateRecipe(id: String): RecipeWriteResponse = decode("POST", "${recipePath(id)}/duplicate")

    suspend fun archiveRecipe(id: String): FoodAcknowledgement = decode("DELETE", recipePath(id))

    suspend fun availability(id: String, servings: Int): Availability =
        send("POST", "${recipePath(id)}/availability", AvailabilityRequest(servings))

    suspend fun suggest(request: SuggestRequest): SuggestResponse = send("POST", "/recipes/suggest", request)
    suspend fun generate(request: GenerateRequest): GenerateResponse = send("POST", "/recipes/generate", request)
    suspend fun adapt(id: String, request: AdaptRequest): AdaptResponse = send("POST", "${recipePath(id)}/adapt", request)
    suspend fun job(id: String): Job = get("/jobs/${segment(id)}")
    suspend fun cancelJob(id: String): Job = decode("POST", "/jobs/${segment(id)}/cancel")
    suspend fun preferences(): Preferences = get("/preferences")
    suspend fun updatePreferences(request: UpdatePreferencesRequest): Preferences = send("PUT", "/preferences", request)

    suspend fun deleteInferredPreference(key: String): Preferences =
        decode("DELETE", "/preferences/inferred/${segment(key)}")

    suspend fun upsertSession(session: CookingSession): CookingSessionResponse =
        send("PUT", "/cooking-sessions/${segment(session.id)}", session)

    suspend fun complete(request: CompleteRequest): OperationResult =
        replayCompletion(completionPath(request.session.id), FoodContract.json.encodeToString(request))

    /** Replay the persisted UTF-8 JSON without decoding or re-encoding it. */
    suspend fun replayCompletion(path: String, body: String): OperationResult {
        require(completionPathPattern.matches(path))
        return FoodContract.decode(transport.enqueue("POST", path, body, timeoutMillis = DEFAULT_TIMEOUT_MS))
    }

    suspend fun assistant(id: String, request: AssistantRequest): AssistantResponse =
        send("POST", "/cooking-sessions/${segment(id)}/assistant", request, ASSISTANT_TIMEOUT_MS)

    suspend fun briefCards(): BriefCardsResponse = get("/brief/cards")

    suspend fun briefCardsV2(request: BriefCardsRequest): BriefCardsV2Response =
        send("POST", "/brief/cards", request)

    suspend fun briefFeedback(request: BriefFeedbackRequest) {
        raw("POST", "/brief/feedback", FoodContract.json.encodeToString(request))
    }

    suspend fun replayCuisine(kind: CuisineOperationKind, target: String?, body: String): CuisineOperationResult =
        when (kind) {
            CuisineOperationKind.PANTRY_ENTRY_PATCH -> cuisineRequest<PantryEntryResult>(kind, target, body)
            else -> cuisineRequest<ShoppingOperationResult>(kind, target, body)
        }

    private suspend inline fun <reified T : CuisineOperationResult, reified R> writeCuisine(
        kind: CuisineOperationKind, target: String?, body: R,
    ): T = cuisineRequest(kind, target, FoodContract.json.encodeToString(body))

    private suspend inline fun <reified T : CuisineOperationResult> cuisineRequest(
        kind: CuisineOperationKind, target: String?, body: String,
    ): T {
        val path = when (kind) {
            CuisineOperationKind.SHOPPING_ADD -> "/shopping/items"
            CuisineOperationKind.SHOPPING_PATCH, CuisineOperationKind.SHOPPING_DELETE ->
                "/shopping/items/${segment(requireNotNull(target))}"
            CuisineOperationKind.RECIPE_SHOPPING -> "${recipePath(requireNotNull(target))}/shopping-items"
            CuisineOperationKind.SHOPPING_UNDO -> "/shopping/undo"
            CuisineOperationKind.PANTRY_ENTRY_PATCH -> "/pantry/entries/${segment(requireNotNull(target))}"
        }
        return decode(kind.method, path, body)
    }

    private suspend inline fun <reified T : FoodResponse> get(path: String): T = decode("GET", path)

    private suspend inline fun <reified T : FoodResponse, reified R> send(
        method: String, path: String, body: R, timeout: Long = DEFAULT_TIMEOUT_MS,
    ): T = decode(method, path, FoodContract.json.encodeToString(body), timeout)

    private suspend inline fun <reified T : FoodResponse> decode(
        method: String, path: String, body: String? = null, timeout: Long = DEFAULT_TIMEOUT_MS,
    ): T = FoodContract.decode(raw(method, path, body, timeout))

    private suspend fun raw(method: String, path: String, body: String? = null, timeout: Long = DEFAULT_TIMEOUT_MS) =
        transport.enqueue(method, BASE_PATH + path, body, timeoutMillis = timeout)

    companion object {
        private const val BASE_PATH = "/mobile/v1/food"
        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val ASSISTANT_TIMEOUT_MS = 30_000L
        private val defaultHttp = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .readTimeout(ASSISTANT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        private val completionPathPattern = Regex(
            "$BASE_PATH/cooking-sessions/ses_[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}/complete",
        )

        fun completionPath(sessionId: String) = "$BASE_PATH/cooking-sessions/${segment(sessionId)}/complete"

        private fun recipePath(id: String) = "/recipes/${segment(id)}"
        private fun weekPath(weekStart: String) = "/planning/weeks/${segment(weekStart)}"
        private fun slotPath(slot: MealSlotKey) = "/planning/slots/${segment(slot.date)}/${segment(slot.meal.name)}"

        private fun segment(value: String): String {
            require(value.isNotBlank() && value != "." && value != "..")
            return HttpUrl.Builder().scheme("https").host("localhost").addPathSegment(value).build()
                .encodedPathSegments.single()
        }
    }
}
