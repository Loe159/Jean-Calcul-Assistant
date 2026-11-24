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

    suspend fun briefFeedback(request: BriefFeedbackRequest) {
        raw("POST", "/brief/feedback", FoodContract.json.encodeToString(request))
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

        private fun segment(value: String): String {
            require(value.isNotBlank() && value != "." && value != "..")
            return HttpUrl.Builder().scheme("https").host("localhost").addPathSegment(value).build()
                .encodedPathSegments.single()
        }
    }
}
