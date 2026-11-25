package io.github.mangi.eta.food

import android.app.Application
import androidx.room.Room
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.FoodOutboxState
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FoodRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val dbName = "food-${UUID.randomUUID()}.db"
    private lateinit var database: EtaDatabase
    private val clock = Clock.fixed(Instant.parse("2026-11-23T18:00:00Z"), ZoneOffset.UTC)
    private val pairingChanges = MutableStateFlow(0L)
    private val paired = MastraPairing("http://localhost", "jc_test", Base64.getUrlEncoder().encodeToString(ByteArray(32)))
    private var pairing: MastraPairing? = null
    private val scheduled = AtomicInteger()

    @Before fun setUp() { openDatabase() }
    @After fun tearDown() {
        database.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun simultaneousStartsOpenOneSessionAndReplacementRequiresTheCurrentId() = runBlocking {
        val recipe = recipeFixture()
        val other = recipe.copy(id = "recipe_other")
        val third = recipe.copy(id = "recipe_third")
        val repository = repository(this)
        val starts = List(4) { async { repository.startSession(recipe, servings = 4) } }.awaitAll()
        val original = (starts.first() as CookingStart.Open).sessionId
        assertTrue(starts.all { it == CookingStart.Open(original) })
        repository.completeStep(original)
        assertEquals(CookingStart.Open(original), repository.startSession(recipe, servings = 2))
        val conflict = repository.startSession(other) as CookingStart.Conflict
        assertEquals(original, conflict.session.id)
        assertEquals("st_2", conflict.session.currentStepId)
        assertEquals(4, conflict.session.servings)
        assertEquals(listOf(original), repository.activeSessions().first().map { it.id })

        val replacement = (repository.startSession(other, replacingSessionId = original) as CookingStart.Open).sessionId
        val staleConfirmation = repository.startSession(third, replacingSessionId = original) as CookingStart.Conflict
        assertEquals(replacement, staleConfirmation.session.id)
        assertEquals(CookingStatus.ABANDONED, repository.session(original).first()?.status)
        assertEquals(replacement, repository.activeSession().first()?.id)
    }

    @Test
    fun completionWithoutStockFreezesServingsAndRatingAndReplaysHistory() = runBlocking {
        val repository = repository(this, Interceptor { chain ->
            val input = FoodContract.json.decodeFromString<CompleteRequest>(Buffer().also { chain.request().body?.writeTo(it) }.readUtf8())
            assertTrue(chain.request().url.encodedPath.endsWith("/complete"))
            assertEquals(3, input.servingsMade)
            assertEquals(4, input.rating)
            assertTrue(input.consumptions.isEmpty())
            assertNull(input.leftovers)
            chain.response(200, result(input.operationId, OperationState.SUCCEEDED))
        })
        val id = (repository.startSession(recipeFixture()) as CookingStart.Open).sessionId
        val operation = repository.completeSession(id, 3, 4, emptyList(), null)
        assertEquals(StockConsumptionStatus.PENDING, repository.session(id).first()?.stockConsumptionStatus)
        assertEquals(operation, repository.completionRequest(id).first()?.operationId)
        assertTrue(repository.activeSessions().first().isEmpty())
        pairing = paired
        assertFalse(repository.replayOutbox())
        assertEquals(StockConsumptionStatus.SUCCEEDED, repository.session(id).first()?.stockConsumptionStatus)
    }

    @Test
    fun sessionResumesFromDiskWithFrozenRecipeAndStepOutcomes() = runBlocking {
        var repository = repository(this)
        val recipe = recipeFixture()
        val id = (repository.startSession(recipe) as CookingStart.Open).sessionId
        repository.nextStep(id)
        assertEquals("st_2", repository.session(id).first()?.currentStepId)
        repository.previousStep(id)
        repository.completeStep(id)
        repository.skipStep(id)
        repository.nextStep(id)
        repository.setServings(id, 6)
        val eggs = Quantity(3.0, UnitCode.PIECE)
        repository.setIngredientOverride(id, "ing_eggs", eggs)
        val before = checkNotNull(repository.session(id).first())

        database.close()
        openDatabase()
        repository = repository(this)
        val resumed = checkNotNull(repository.session(id).first())
        assertEquals(before, resumed)
        assertEquals(recipe, resumed.recipeSnapshot)
        assertEquals(6, resumed.servings)
        assertEquals("st_3", resumed.currentStepId)
        assertEquals(listOf("st_1"), resumed.completedStepIds)
        assertEquals(listOf("st_2"), resumed.skippedStepIds)
        assertEquals(listOf(IngredientOverride("ing_eggs", eggs)), resumed.ingredientOverrides)
        assertEquals(7L, resumed.revision)
        repository.previousStep(id)
        repository.completeStep(id)
        val corrected = checkNotNull(repository.session(id).first())
        assertEquals(listOf("st_1", "st_2"), corrected.completedStepIds)
        assertTrue(corrected.skippedStepIds.isEmpty())
        repository.setIngredientOverride(id, "ing_eggs", Quantity(0.0, UnitCode.PIECE))
        val withZeroEggs = checkNotNull(repository.session(id).first())
        val eggStep = recipe.steps.first { it.id == "st_2" }
        assertTrue(FoodQuantities.renderStep(withZeroEggs, eggStep).contains("(0 au total)"))
    }

    @Test
    fun completionIsSentImmediatelyThenReplayedVerbatimAcrossDatabaseReopen() = runBlocking {
        pairing = paired
        val requests = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val responses = AtomicInteger()
        val server = Interceptor { chain ->
            val request = chain.request()
            val wireBody = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            if (request.method == "PUT") {
                return@Interceptor chain.response(200, "{\"schemaVersion\":1,\"session\":$wireBody}")
            }
            requests.add(request.url.encodedPath to wireBody)
            val completion = FoodContract.json.decodeFromString<CompleteRequest>(wireBody)
            when (responses.getAndIncrement()) {
                0 -> throw IOException("lost acknowledgement")
                1 -> chain.response(200, result(completion.operationId, OperationState.UNCERTAIN))
                else -> chain.response(200, result(completion.operationId, OperationState.SUCCEEDED))
            }
        }
        var repository = repository(this, server)
        val id = (repository.startSession(recipeFixture()) as CookingStart.Open).sessionId
        val operationId = checkNotNull(repository.completeSession(id, 4, null, emptyList(), null))
        withTimeout(5_000) {
            while (database.foodDao().outbox(operationId)?.state != FoodOutboxState.UNCERTAIN) delay(10)
        }
        assertTrue(scheduled.get() > 0)
        assertEquals(1, requests.size)
        val saved = checkNotNull(database.foodDao().outbox(operationId))
        assertEquals(saved.endpoint to saved.bodyJson, requests.single())
        assertEquals(operationId, FoodContract.json.decodeFromString<CompleteRequest>(saved.bodyJson).operationId)

        database.close()
        openDatabase()
        repository = repository(this, server)
        assertTrue(repository.replayOutbox())
        assertEquals(StockConsumptionStatus.UNCERTAIN, repository.session(id).first()?.stockConsumptionStatus)
        assertFalse(repository.replayOutbox())
        assertEquals(StockConsumptionStatus.SUCCEEDED, repository.session(id).first()?.stockConsumptionStatus)
        assertEquals(operationId, repository.completeSession(id, 6, 5, emptyList(), null))
        assertFalse(repository.replayOutbox())
        assertEquals(List(3) { saved.endpoint to saved.bodyJson }, requests)
        assertEquals(3, database.foodDao().outbox(operationId)?.attempts)
        assertEquals(FoodOutboxState.DONE, database.foodDao().outbox(operationId)?.state)
    }

    @Test
    fun pantryCacheSurvivesFailuresAndOutboxStaysWithOriginalPairing() = runBlocking {
        var serverCode = "GROCY_UNAVAILABLE"
        var mode = 0
        var completionCalls = 0
        val repository = repository(this, Interceptor { chain ->
            val request = chain.request()
            when {
                request.url.encodedPath.endsWith("/complete") -> {
                    completionCalls++
                    throw IOException()
                }
                request.method == "PUT" -> chain.response(500, "{}")
                mode == 0 -> chain.response(200, foodFixture("pantry"))
                mode == 1 -> chain.response(503, "{\"error\":\"$serverCode\",\"message\":\"Indisponible\"}")
                else -> throw IOException()
            }
        })
        assertEquals(FoodResult.Failure(FoodError.NOT_PAIRED), repository.refreshPantry())
        pairing = paired
        pairingChanges.value++
        assertTrue(repository.refreshPantry() is FoodResult.Success)
        val cached = repository.pantry().first()
        assertNotNull(cached)
        assertEquals(clock.millis(), cached?.cachedAt)
        mode = 1
        assertEquals(FoodResult.Failure(FoodError.GROCY_UNAVAILABLE, serverCode), repository.refreshPantry())
        serverCode = "AI_DISABLED"
        assertEquals(FoodResult.Failure(FoodError.AI_DISABLED, serverCode), repository.health())
        serverCode = "INTERNAL_ERROR"
        assertEquals(FoodResult.Failure(FoodError.SERVER_ERROR, serverCode), repository.refreshPantry())
        mode = 2
        assertEquals(FoodResult.Failure(FoodError.OFFLINE), repository.refreshPantry())
        assertEquals(cached, repository.pantry().first())

        val id = (repository.startSession(recipeFixture()) as CookingStart.Open).sessionId
        pairing = null
        val op = checkNotNull(repository.completeSession(id, 2, null, emptyList(), null))
        assertEquals(FoodOutboxState.PENDING, database.foodDao().outbox(op)?.state)
        pairing = paired.copy(deviceId = "jc_other")
        pairingChanges.value++
        assertFalse(repository.replayOutbox())
        assertEquals(0, completionCalls)
        assertNull(repository.pantry().first())
    }

    @Test
    fun assistantChangesPersistOnlyInTheActiveSession() = runBlocking {
        val recipe = recipeFixture()
        var repository = repository(this)
        val id = (repository.startSession(recipe, servings = 4) as CookingStart.Open).sessionId
        val replacement = AssistantProposal.ReplaceIngredient(
            "replace", "Remplacer les courgettes", ProposalImpact.MAJOR,
            ReplacementPatch("ing_courgette", "aubergines", MeasuredQuantity(400.0, UnitCode.G), 42L),
        )
        val note = AssistantProposal.AddStepNote(
            "note", "Couvrir", ProposalImpact.MINOR, NotePatch("st_2", "Couvrir la casserole"),
        )
        repository.applyProposal(id, "req_1", replacement)
        repository.applyProposal(id, "req_1", note)
        repository.applyProposal(id, "req_1", note)
        repository.jumpToStep(id, "st_2")
        repository.applyProposal(id, "req_2", AssistantProposal.SkipStep(
            "skip", "Ignorer", ProposalImpact.MAJOR, StepPatch("st_2"),
        ))
        database.close()
        openDatabase()
        repository = repository(this)
        val session = checkNotNull(repository.session(id).first())
        assertEquals("st_3", session.currentStepId)
        assertEquals(listOf("st_2"), session.skippedStepIds)
        assertEquals(42L, session.recipeSnapshot.ingredients.first().grocyProductId)
        assertEquals(listOf(IngredientOverride("ing_courgette", Quantity(400.0, UnitCode.G))), session.ingredientOverrides)
        assertEquals(1, session.recipeSnapshot.steps.single { it.id == "st_2" }.tips.count { it == "Couvrir la casserole" })
        val frozen = database.foodDao().session(id)!!.recipeSnapshotJson
        assertEquals(recipe, FoodContract.json.decodeFromString<RecipeDefinition>(frozen))
    }

    @Test
    fun partialCompletionPreservesLineDetailsAcrossReopen() = runBlocking {
        pairing = paired
        val repository = repository(this, Interceptor { chain ->
            val request = chain.request()
            val json = FoodContract.json
            if (request.method == "PUT") return@Interceptor chain.response(500, "{}")
            val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
            val input = json.decodeFromString<CompleteRequest>(body)
            val line = OperationLine("ing_courgette", 3, Quantity(500.0, UnitCode.G), null, null,
                OperationLineState.NEEDS_CONVERSION, "Conversion manquante")
            chain.response(200, json.encodeToString(OperationResult(1, input.operationId, OperationState.PARTIAL, false, listOf(line))))
        })
        val id = (repository.startSession(recipeFixture(), 4) as CookingStart.Open).sessionId
        assertNull(repository.completionResult(id).first())
        repository.completeSession(id, 4, 5, emptyList(), null)
        withTimeout(5_000) { repository.completionResult(id).first { it != null } }
        database.close()
        openDatabase()
        val resumed = repository(this)
        assertEquals(StockConsumptionStatus.PARTIAL, resumed.session(id).first()?.stockConsumptionStatus)
        assertEquals(OperationLineState.NEEDS_CONVERSION, resumed.completionResult(id).first()?.lines?.single()?.state)
    }

    @Test
    fun basketProjectsImmediatelyAndReplaysVerbatimOnlyForTheMatchingPairing() = runBlocking {
        pairing = paired
        val confirmed = FoodContract.decode<ShoppingResponse>(foodFixture("shopping"))
        val line = confirmed.items.first()
        val before = confirmed.copy(items = listOf(line.copy(state = ShoppingItemState.TODO)))
        val after = confirmed.copy(items = listOf(line.copy(revision = "confirmed")))
        val bodies = Collections.synchronizedList(mutableListOf<String>())
        val writes = AtomicInteger()
        val refreshFails = AtomicBoolean(false)
        val request = FoodContract.json.decodeFromString<ShoppingPatchRequest>(foodFixture("shopping-patch-request"))
            .copy(expectedRevision = line.revision)
        val server = Interceptor { chain ->
            val wire = chain.request()
            if (wire.method == "GET") {
                if (refreshFails.get()) throw IOException("refresh unavailable")
                chain.response(200, FoodContract.json.encodeToString(if (writes.get() >= 3) after else before))
            } else {
                assertEquals("PATCH", wire.method)
                assertTrue(wire.url.encodedPath.endsWith("/shopping/items/${line.id}"))
                bodies += Buffer().also { wire.body!!.writeTo(it) }.readUtf8()
                when (writes.incrementAndGet()) {
                    1 -> throw IOException("ack lost")
                    2 -> chain.response(200, shoppingResult(request.operationId, OperationState.UNCERTAIN))
                    else -> {
                        refreshFails.set(true)
                        chain.response(200, shoppingResult(request.operationId, OperationState.SUCCEEDED))
                    }
                }
            }
        }
        var repository = repository(this, server)
        assertEquals(FoodResult.Success(before), repository.refreshShopping())
        assertEquals(FoodResult.Success(request.operationId), repository.setShoppingBasketState(
            line.id, request.operationId, line.revision, ShoppingBasketState.IN_BASKET,
        ))
        assertEquals(ShoppingItemState.IN_BASKET, repository.shopping().first()!!.items.single().state)
        assertEquals(ShoppingItemState.TODO, repository.shopping().first()!!.cache!!.value.items.single().state)
        withTimeout(5_000) { repository.cuisineOperation(request.operationId).first { it?.state == FoodOutboxState.UNCERTAIN } }

        repository = repository(this, server)
        pairing = paired.copy(deviceId = "jc_other")
        pairingChanges.value++
        assertNull(repository.shopping().first()!!.cache)
        assertNull(repository.cuisineOperation(request.operationId).first())
        assertFalse(repository.replayOutbox())
        assertEquals(1, writes.get())

        pairing = paired
        pairingChanges.value++
        assertTrue(repository.replayOutbox())
        val uncertain = repository.cuisineOperation(request.operationId).first()!!
        assertEquals(OperationState.UNCERTAIN, (uncertain.result as ShoppingOperationResult).state)
        assertEquals(2, uncertain.attempts)
        assertTrue(repository.replayOutbox())
        assertTrue(repository.cuisineOperation(request.operationId).first()!!.awaitingRefresh)
        assertEquals(ShoppingItemState.IN_BASKET, repository.shopping().first()!!.items.single().state)

        refreshFails.set(false)
        assertFalse(repository.replayOutbox())
        assertEquals(after.items, repository.shopping().first()!!.items)
        val done = repository.cuisineOperation(request.operationId).first()!!
        assertEquals(FoodOutboxState.DONE, done.state)
        assertFalse(done.awaitingRefresh)
        val frozenBody = FoodContract.json.encodeToString(request)
        assertEquals(List(3) { frozenBody }, bodies)
        assertEquals(FoodResult.Failure(FoodError.SERVER_ERROR, "OPERATION_CONFLICT"), repository.deleteShoppingItem(
            line.id, ShoppingDeleteRequest(request.operationId, line.revision),
        ))
    }

    @Test
    fun undoOfAnOfflineAddWaitsForItsSourceAndRollsBackOnConflict() = runBlocking {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val addId = UUID.randomUUID().toString()
        val undoId = UUID.randomUUID().toString()
        val add = ShoppingAddRequest(addId, ShoppingAddItem.FreeText("Article fictif", ShoppingQuantity(1.0, 3)))
        val response = FoodContract.decode<ShoppingResponse>(foodFixture("shopping"))
        val newLine = response.items[1].copy(
            state = ShoppingItemState.TODO, name = "Article fictif",
            origins = listOf(ShoppingContribution("$addId:0", ShoppingQuantity(1.0, 3), ShoppingOrigin.Manual)),
        )
        val tries = AtomicInteger()
        val server = Interceptor { chain ->
            val path = chain.request().url.encodedPath
            when {
                path.endsWith("/items") -> {
                    calls += "add"
                    if (tries.getAndIncrement() == 0) chain.response(200, shoppingResult(addId, OperationState.UNCERTAIN))
                    else chain.response(200, shoppingResult(addId, OperationState.SUCCEEDED))
                }
                path.endsWith("/undo") -> {
                    calls += "undo"
                    chain.response(409, """{"error":"REVISION_CONFLICT","message":"Conflit"}""")
                }
                else -> chain.response(200, FoodContract.json.encodeToString(response.copy(items = listOf(newLine))))
            }
        }
        var repository = repository(this, server, cachePairing = { paired })
        assertEquals(FoodResult.Success(addId), repository.addShoppingItem(add))
        val draft = repository.shopping().first()!!.items.single()
        assertTrue(draft.id < 0)
        assertEquals("Article fictif", draft.name)
        assertEquals(FoodResult.Success(undoId), repository.undoShopping(ShoppingUndoRequest(undoId, addId)))
        assertTrue(repository.shopping().first()!!.items.isEmpty())

        database.close()
        openDatabase()
        repository = repository(this, server, cachePairing = { paired })
        assertTrue(repository.shopping().first()!!.items.isEmpty())
        pairing = paired
        assertTrue(repository.replayOutbox())
        assertEquals(listOf("add"), calls)
        assertEquals(FoodOutboxState.PENDING, repository.cuisineOperation(undoId).first()!!.state)
        assertFalse(repository.replayOutbox())
        assertEquals(listOf("add", "add", "undo"), calls)
        assertEquals("REVISION_CONFLICT", repository.cuisineOperation(undoId).first()!!.lastError)
        assertEquals(listOf(newLine), repository.shopping().first()!!.items)
    }

    @Test
    fun pantryLotPatchProjectsAndThenUsesTheServerRevision() = runBlocking {
        pairing = paired
        val pantry = FoodContract.decode<PantryResponse>(foodFixture("pantry"))
        val request = FoodContract.json.decodeFromString<PantryEntryPatchRequest>(foodFixture("pantry-entry-patch-request"))
        val response = FoodContract.decode<PantryEntryResult>(foodFixture("pantry-entry-result"))
        val changed = checkNotNull(response.entry)
        val applied = AtomicBoolean(false)
        val repository = repository(this, Interceptor { chain ->
            if (chain.request().method == "PATCH") {
                assertTrue(chain.request().url.encodedPath.endsWith("/pantry/entries/31"))
                val wire = Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()
                assertEquals(request, FoodContract.json.decodeFromString<PantryEntryPatchRequest>(wire))
                if (!applied.get()) throw IOException()
                chain.response(200, foodFixture("pantry-entry-result"))
            } else {
                val updated = pantry.items.first().copy(bestBeforeDate = null, batches = listOf(changed))
                val result = if (applied.get()) pantry.copy(items = listOf(updated) + pantry.items.drop(1)) else pantry
                chain.response(200, FoodContract.json.encodeToString(result))
            }
        })
        assertEquals(FoodResult.Success(pantry), repository.refreshPantry())
        assertEquals(FoodResult.Success(request.operationId), repository.patchPantryEntry(31, request))
        val projected = repository.pantry().first()!!.value.items.first().batches.single()
        assertNull(projected.bestBeforeDate)
        assertEquals(request.expectedRevision, projected.revision)
        withTimeout(5_000) { repository.cuisineOperation(request.operationId).first { it?.state == FoodOutboxState.UNCERTAIN } }
        applied.set(true)
        assertFalse(repository.replayOutbox())
        assertEquals(changed, repository.pantry().first()!!.value.items.first().batches.single())
        assertEquals(response, repository.cuisineOperation(request.operationId).first()!!.result)
    }

    @Test
    fun planningAndPutAwayReturnOfflineThenUseTheSameRequestOnRetry() = runBlocking {
        pairing = paired
        val week = FoodContract.decode<MealWeek>(foodFixture("meal-week"))
        val request = FoodContract.json.decodeFromString<PlanningPutRequest>(foodFixture("planning-put-request"))
        val putAway = FoodContract.json.decodeFromString<PutAwayRequest>(foodFixture("put-away-request"))
        val offline = AtomicBoolean(true)
        val bodies = Collections.synchronizedList(mutableListOf<Pair<String, String>>())
        val repository = repository(this, Interceptor { chain ->
            val wire = chain.request()
            val path = wire.url.encodedPath
            if (wire.method != "GET") bodies += path to Buffer().also { wire.body!!.writeTo(it) }.readUtf8()
            if (offline.get()) throw IOException("offline")
            when {
                path.contains("/slots/") -> chain.response(200, foodFixture("planning-write-result"))
                path.endsWith("/put-away") -> chain.response(200, shoppingResult(putAway.operationId, OperationState.PARTIAL))
                path.endsWith("/shopping") -> chain.response(200, foodFixture("shopping"))
                path.endsWith("/pantry") -> chain.response(200, foodFixture("pantry"))
                else -> chain.response(200, foodFixture("meal-week"))
            }
        })
        val slot = week.slots.single().key
        assertEquals(FoodResult.Failure(FoodError.OFFLINE), repository.putMealSlot(slot, request))
        assertEquals(FoodResult.Failure(FoodError.OFFLINE), repository.putAway(putAway))
        assertFalse(repository.hasPendingSync())
        assertNull(repository.mealWeek(week.weekStart).first())
        assertNull(repository.shopping().first()!!.cache)
        offline.set(false)
        assertTrue(repository.putMealSlot(slot, request) is FoodResult.Success)
        assertEquals(week, repository.mealWeek(week.weekStart).first()!!.value)
        assertTrue(repository.putAway(putAway) is FoodResult.Success)
        assertNotNull(repository.shopping().first()!!.cache)
        assertNotNull(repository.pantry().first())
        assertEquals(bodies.take(2), bodies.drop(2))
    }

    @Test
    fun briefV2CachesOnlyTheRequestedKindsAndLeavesTheHistoricalCacheReadable() = runBlocking {
        pairing = paired
        val repository = repository(this, Interceptor { chain ->
            if (chain.request().method == "GET") chain.response(200, foodFixture("brief-cards"))
            else chain.response(200, foodFixture("brief-cards-v2"))
        })
        val kinds = setOf(BriefCardKind.MEAL_PLANNED, BriefCardKind.SHOPPING_LIST)
        assertTrue(repository.refreshBriefCards() is FoodResult.Success)
        assertTrue(repository.refreshBriefCardsV2(kinds) is FoodResult.Success)
        val legacy = repository.briefCards().first()
        assertNotNull(legacy)
        val modern = repository.briefCardsV2(kinds).first()!!.value.cards
        assertEquals(2, modern.size)
        assertNull(repository.briefCardsV2().first())
        repository.briefFeedback(BriefFeedbackRequest("fixture-event", modern.first().id, BriefFeedbackAction.DISMISSED))
        assertEquals(listOf(modern.last()), repository.briefCardsV2(kinds).first()!!.value.cards)
        assertEquals(legacy, repository.briefCards().first())
    }

    private fun repository(
        scope: CoroutineScope,
        server: Interceptor = Interceptor { throw IOException() },
        cachePairing: () -> MastraPairing? = { pairing },
    ): FoodRepository {
        val http = OkHttpClient.Builder().addInterceptor(server).build()
        return FoodRepository(
            database, { pairing }, pairingChanges, scope, { scheduled.incrementAndGet() }, clock,
            clientFor = { FoodClient(it, http) }, cachePairing = cachePairing,
        )
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, EtaDatabase::class.java, dbName).build()
    }

    private fun result(id: String, state: OperationState) =
        FoodContract.json.encodeToString(OperationResult(1, id, state, false, emptyList()))

    private fun shoppingResult(id: String, state: OperationState) =
        FoodContract.json.encodeToString(ShoppingOperationResult(1, id, false, state, emptyList()))
}
