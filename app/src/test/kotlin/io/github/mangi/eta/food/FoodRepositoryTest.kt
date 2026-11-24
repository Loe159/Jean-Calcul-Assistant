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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
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
    fun sessionResumesFromDiskWithFrozenRecipeAndStepOutcomes() = runBlocking {
        var repository = repository(this)
        val recipe = recipeFixture()
        val id = repository.startSession(recipe)
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
        val id = repository.startSession(recipeFixture())
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

        val id = repository.startSession(recipeFixture())
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
        val id = repository.startSession(recipe, servings = 4)
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
        val id = repository.startSession(recipeFixture(), 4)
        assertNull(repository.completionResult(id).first())
        repository.completeSession(id, 4, 5, emptyList(), null)
        withTimeout(5_000) { repository.completionResult(id).first { it != null } }
        database.close()
        openDatabase()
        val resumed = repository(this)
        assertEquals(StockConsumptionStatus.PARTIAL, resumed.session(id).first()?.stockConsumptionStatus)
        assertEquals(OperationLineState.NEEDS_CONVERSION, resumed.completionResult(id).first()?.lines?.single()?.state)
    }

    private fun repository(scope: CoroutineScope, server: Interceptor = Interceptor { throw IOException() }): FoodRepository {
        val http = OkHttpClient.Builder().addInterceptor(server).build()
        return FoodRepository(
            database, { pairing }, pairingChanges, scope, { scheduled.incrementAndGet() }, clock,
            clientFor = { FoodClient(it, http) },
        )
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, EtaDatabase::class.java, dbName).build()
    }

    private fun result(id: String, state: OperationState) =
        FoodContract.json.encodeToString(OperationResult(1, id, state, false, emptyList()))
}
