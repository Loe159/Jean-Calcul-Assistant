package io.github.mangi.eta.food

import androidx.room.withTransaction
import io.github.mangi.eta.agent.device.notifications.remote.MastraHttpException
import io.github.mangi.eta.agent.device.notifications.remote.MastraPairing
import io.github.mangi.eta.agent.device.notifications.remote.MastraSignedTransport
import io.github.mangi.eta.data.db.CookingSessionEntity
import io.github.mangi.eta.data.db.CookingTimerEntity
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.data.db.FoodAvailabilityEntity
import io.github.mangi.eta.data.db.FoodBriefCardsEntity
import io.github.mangi.eta.data.db.FoodOutboxEntity
import io.github.mangi.eta.data.db.FoodOutboxKind
import io.github.mangi.eta.data.db.FoodOutboxState
import io.github.mangi.eta.data.db.FoodPantryEntity
import io.github.mangi.eta.data.db.FoodRecipeEntity
import io.github.mangi.eta.data.db.FoodRecipeIndexEntity
import io.github.mangi.eta.data.db.FoodRecipeLibraryEntity
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

internal enum class FoodError { NOT_PAIRED, OFFLINE, SERVER_ERROR, GROCY_UNAVAILABLE, AI_DISABLED }

internal sealed interface FoodResult<out T> {
    data class Success<T>(val value: T) : FoodResult<T>
    data class Failure(val error: FoodError, val serverCode: String? = null) : FoodResult<Nothing>
}

internal data class FoodCache<out T>(val value: T, val cachedAt: Long)

@OptIn(ExperimentalCoroutinesApi::class)
internal class FoodRepository(
    private val database: EtaDatabase,
    private val pairing: () -> MastraPairing?,
    pairingChanges: Flow<Long>,
    private val scope: CoroutineScope,
    private val scheduleSync: () -> Unit,
    private val clock: Clock = Clock.systemUTC(),
    private val clientFor: (MastraPairing) -> FoodClient = { FoodClient(it) },
    private val cachePairing: () -> MastraPairing? = pairing,
) {
    private val dao = database.foodDao()
    private val json = FoodContract.json
    private val replayMutex = Mutex()
    private val refreshMutex = Mutex()
    private val cacheOwner = pairingChanges.map { cachePairing()?.foodOwner() }.distinctUntilChanged()

    fun recipes(): Flow<FoodCache<RecipeListResponse>?> = observeCache(dao::observeRecipeIndex) { row ->
        FoodCache(FoodContract.decode(row.json), row.cachedAt)
    }

    fun recipe(id: String): Flow<FoodCache<RecipeDefinition>?> = observeCache({ dao.observeRecipe(it, id) }) { row ->
        FoodCache(json.decodeFromString(row.json), row.cachedAt)
    }

    fun library(id: String): Flow<FoodCache<RecipeLibrary>?> =
        observeCache({ dao.observeRecipeLibrary(it, id) }) { row ->
            FoodCache(json.decodeFromString(row.json), row.cachedAt)
        }

    fun availability(recipe: RecipeDefinition, servings: Int): Flow<FoodCache<Availability>?> =
        cacheOwner.flatMapLatest { owner ->
            if (owner == null) flowOf(null) else dao.observeAvailability(owner, recipe.id, recipe.version).map { rows ->
                // Recompute from the freshest inventory even if older exact-serving results exist.
                rows.maxWithOrNull(compareBy<FoodAvailabilityEntity> { Instant.parse(it.stockAsOf) }
                    .thenBy { it.servings == servings }.thenBy { it.cachedAt })
                    ?.let { row ->
                        val cached = FoodContract.decode<Availability>(row.json)
                        val availability = if (cached.servings == servings) cached else cached.recompute(recipe, servings)
                        FoodCache(availability, row.cachedAt)
                    }
            }
        }

    fun pantry(): Flow<FoodCache<PantryResponse>?> = observeCache(dao::observePantry) { row ->
        FoodCache(FoodContract.decode(row.json), row.fetchedAt)
    }

    fun briefCards(): Flow<FoodCache<BriefCardsResponse>?> =
        cacheOwner.flatMapLatest { owner ->
            if (owner == null) flowOf(null) else dao.observeBriefCards(owner).transformLatest { row ->
                val remainingMs = (row?.validUntilEpochMs ?: 0) - clock.millis()
                if (row != null && remainingMs > 0) {
                    emit(FoodCache(FoodContract.decode<BriefCardsResponse>(row.json), row.cachedAt))
                    delay(remainingMs)
                }
                emit(null)
            }
        }

    suspend fun refreshRecipes(): FoodResult<RecipeListResponse> = refresh { owner, client ->
        client.recipes().also { dao.saveRecipeIndex(FoodRecipeIndexEntity(owner, json.encodeToString(it), clock.millis())) }
    }

    suspend fun refreshRecipe(id: String): FoodResult<RecipeDetailResponse> = refresh { owner, client ->
        val detail = client.recipe(id)
        if (detail.recipe.id != id) throw SerializationException("Unexpected recipe ID")
        val now = clock.millis()
        database.withTransaction {
            dao.saveRecipe(FoodRecipeEntity(owner, id, detail.recipe.version, json.encodeToString(detail.recipe), now))
            dao.saveRecipeLibrary(FoodRecipeLibraryEntity(owner, id, json.encodeToString(detail.library), now))
        }
        detail
    }

    suspend fun refreshAvailability(recipe: RecipeDefinition, servings: Int): FoodResult<Availability> =
        refresh { owner, client ->
            val result = client.availability(recipe.id, servings)
            if (result.recipeId != recipe.id || result.recipeVersion != recipe.version || result.servings != servings) {
                throw SerializationException("Availability does not match the requested recipe version and servings")
            }
            Instant.parse(result.stockAsOf)
            dao.saveAvailability(
                FoodAvailabilityEntity(
                    owner, result.recipeId, result.recipeVersion, result.servings, json.encodeToString(result),
                    result.stockAsOf, clock.millis(),
                ),
            )
            result
        }

    suspend fun refreshPantry(): FoodResult<PantryResponse> = refresh { owner, client ->
        client.pantry().also { dao.savePantry(FoodPantryEntity(owner, json.encodeToString(it), clock.millis())) }
    }

    suspend fun refreshBriefCards(): FoodResult<BriefCardsResponse> = refresh { owner, client ->
        client.briefCards().also {
            dao.saveBriefCards(
                FoodBriefCardsEntity(owner, json.encodeToString(it), clock.millis(), Instant.parse(it.validUntil).toEpochMilli()),
            )
        }
    }

    suspend fun health(): FoodResult<FoodHealth> = remote { _, client -> client.health() }
    suspend fun assistant(request: AssistantRequest): FoodResult<AssistantResponse> {
        val currentPairing = pairing() ?: return FoodResult.Failure(FoodError.NOT_PAIRED)
        val owner = dao.session(request.session.id)?.owner
        if (owner != null && owner != currentPairing.foodOwner()) return FoodResult.Failure(FoodError.NOT_PAIRED)
        return runRemote { clientFor(currentPairing).assistant(request.session.id, request) }
    }

    fun session(id: String): Flow<CookingSession?> =
        dao.observeSession(id).map { it?.session?.toContract(it.timers) }

    fun activeSessions(): Flow<List<CookingSessionEntity>> = dao.observeActiveSessions()

    suspend fun startSession(recipe: RecipeDefinition, servings: Int = recipe.servings): String {
        require(servings > 0)
        val now = clock.millis()
        val id = "ses_${UUID.randomUUID()}"
        dao.insertSession(
            CookingSessionEntity(
                id = id,
                owner = cachePairing()?.foodOwner(),
                recipeSnapshotJson = json.encodeToString(recipe),
                servings = servings,
                currentStepId = recipe.steps.minByOrNull { it.order }?.id,
                completedStepIdsJson = "[]",
                skippedStepIdsJson = "[]",
                overridesJson = "[]",
                status = CookingStatus.ACTIVE,
                startedAt = now,
                completedAt = null,
                stockConsumptionStatus = StockConsumptionStatus.NOT_REQUESTED,
                revision = 1,
                updatedAt = now,
            ),
        )
        syncSession(id)
        return id
    }

    suspend fun nextStep(id: String) = mutate(id) { row, recipe ->
        row.copy(currentStepId = adjacentStep(recipe, row.currentStepId, 1))
    }

    suspend fun previousStep(id: String) = mutate(id) { row, recipe ->
        row.copy(currentStepId = adjacentStep(recipe, row.currentStepId, -1))
    }

    suspend fun completeStep(id: String) = setStepOutcome(id, skipped = false)
    suspend fun skipStep(id: String) = setStepOutcome(id, skipped = true)

    suspend fun setServings(id: String, servings: Int) = mutate(id) { row, _ ->
        require(servings > 0)
        row.copy(servings = servings)
    }

    suspend fun setIngredientOverride(id: String, ingredientId: String, quantity: Quantity?) =
        mutate(id) { row, recipe ->
            require(recipe.ingredients.any { it.id == ingredientId })
            require(quantity?.amount?.let { it.isFinite() && it >= 0 } != false)
            val overrides = json.decodeFromString<List<IngredientOverride>>(row.overridesJson)
                .filterNot { it.ingredientId == ingredientId }
            val updated = if (quantity == null) overrides else overrides + IngredientOverride(ingredientId, quantity)
            row.copy(overridesJson = json.encodeToString(updated))
        }

    private suspend fun setStepOutcome(id: String, skipped: Boolean) = mutate(id) { row, recipe ->
        val step = row.currentStepId ?: return@mutate row
        val completed = json.decodeFromString<List<String>>(row.completedStepIdsJson).toMutableSet()
        val skippedIds = json.decodeFromString<List<String>>(row.skippedStepIdsJson).toMutableSet()
        completed.remove(step)
        skippedIds.remove(step)
        if (skipped) skippedIds.add(step) else completed.add(step)
        row.copy(
            currentStepId = adjacentStep(recipe, step, 1),
            completedStepIdsJson = json.encodeToString(completed.toList()),
            skippedStepIdsJson = json.encodeToString(skippedIds.toList()),
        )
    }

    private fun adjacentStep(recipe: RecipeDefinition, stepId: String?, offset: Int): String? {
        val steps = recipe.steps.sortedBy { it.order }
        val index = steps.indexOfFirst { it.id == stepId }
        return steps.getOrNull(index + offset)?.id ?: stepId
    }

    private suspend fun mutate(
        id: String,
        transform: (CookingSessionEntity, RecipeDefinition) -> CookingSessionEntity,
    ) {
        val changed = database.withTransaction {
            val row = requireNotNull(dao.session(id))
            check(row.status == CookingStatus.ACTIVE)
            val updated = transform(row, json.decodeFromString(row.recipeSnapshotJson))
            if (updated == row) return@withTransaction false
            dao.updateSession(updated.nextRevision(clock.millis()))
            true
        }
        if (changed) syncSession(id)
    }

    /** Android owns this state. An offline PUT can be skipped because the next revision includes all changes. */
    fun syncSession(id: String) {
        scope.launch {
            val currentPairing = pairing() ?: return@launch
            val owner = currentPairing.foodOwner()
            val snapshot = database.withTransaction {
                val row = dao.session(id) ?: return@withTransaction null
                if (row.status != CookingStatus.ACTIVE || row.owner != null && row.owner != owner) {
                    return@withTransaction null
                }
                if (row.owner == null) dao.updateSession(row.copy(owner = owner))
                row.toContract(dao.timers(id))
            } ?: return@launch
            runRemote { clientFor(currentPairing).upsertSession(snapshot) }
        }
    }

    /** Freezes the confirmed payload in the same transaction that completes the local session. */
    suspend fun completeSession(
        id: String,
        servingsMade: Int,
        rating: Int?,
        consumptions: List<CompleteConsumption>,
        leftovers: Leftovers?,
    ): String? {
        val (operationId, created) = database.withTransaction {
            val row = requireNotNull(dao.session(id))
            val previous = dao.sessionOutbox(id)
            if (previous != null || row.stockConsumptionStatus == StockConsumptionStatus.SUCCEEDED) {
                return@withTransaction previous?.operationId to false
            }
            check(row.status == CookingStatus.ACTIVE)
            require(servingsMade > 0 && (rating == null || rating in 1..5))
            require(consumptions.all { it.grocyProductId > 0 && it.quantity.amount.isFinite() && it.quantity.amount > 0 })
            val now = clock.millis()
            val completed = row.nextRevision(now).copy(
                owner = row.owner ?: cachePairing()?.foodOwner(),
                status = CookingStatus.COMPLETED,
                completedAt = now,
                stockConsumptionStatus = StockConsumptionStatus.PENDING,
            )
            val operationId = "op_${UUID.randomUUID()}"
            val body = CompleteRequest(operationId, completed.toContract(dao.timers(id)), servingsMade, rating, consumptions, leftovers)
            dao.updateSession(completed)
            dao.insertOutbox(
                FoodOutboxEntity(
                    operationId, id, FoodOutboxKind.COMPLETE_SESSION, FoodClient.completionPath(id), json.encodeToString(body),
                    FoodOutboxState.PENDING, 0, null, now, now,
                ),
            )
            operationId to true
        }
        if (operationId != null && created) {
            scheduleSync()
            scope.launch { replayMutex.withLock { sendCompletion(operationId) } }
        }
        return operationId
    }

    suspend fun hasPendingSync(): Boolean = pairing()?.let { dao.replayable(it.foodOwner()).isNotEmpty() } ?: false

    suspend fun replayOutbox(): Boolean = replayMutex.withLock {
        val owner = pairing()?.foodOwner() ?: return@withLock false
        for (row in dao.replayable(owner)) sendCompletion(row.operationId)
        hasPendingSync()
    }

    private suspend fun sendCompletion(operationId: String) {
        val currentPairing = pairing() ?: return
        val owner = currentPairing.foodOwner()
        val claimed = database.withTransaction {
            val row = dao.outbox(operationId) ?: return@withTransaction null
            val session = dao.session(row.sessionId) ?: return@withTransaction null
            if (row.state !in replayableStates || session.stockConsumptionStatus == StockConsumptionStatus.SUCCEEDED ||
                session.owner != null && session.owner != owner
            ) return@withTransaction null
            if (session.owner == null) dao.updateSession(session.copy(owner = owner))
            row.copy(state = FoodOutboxState.SENT, attempts = row.attempts + 1, updatedAt = clock.millis())
                .also { dao.updateOutbox(it) }
        } ?: return

        val (state, error) = try {
            val result = clientFor(currentPairing).replayCompletion(claimed.endpoint, claimed.bodyJson)
            if (result.operationId != operationId) throw SerializationException("Unexpected operation ID")
            result.state to null
        } catch (error: MastraHttpException) {
            val state = if (error.permanent && error.code != "GROCY_UNAVAILABLE") {
                OperationState.FAILED
            } else OperationState.UNCERTAIN
            state to (error.code ?: "HTTP_${error.status}")
        } catch (_: IOException) {
            OperationState.UNCERTAIN to FoodError.OFFLINE.name
        } catch (_: SerializationException) {
            OperationState.UNCERTAIN to FoodError.SERVER_ERROR.name
        }
        val outboxState = when (state) {
            OperationState.SUCCEEDED, OperationState.PARTIAL -> FoodOutboxState.DONE
            OperationState.FAILED -> FoodOutboxState.FAILED
            OperationState.UNCERTAIN -> FoodOutboxState.UNCERTAIN
        }
        database.withTransaction {
            val now = clock.millis()
            val session = checkNotNull(dao.session(claimed.sessionId))
            dao.updateSession(session.nextRevision(now).copy(stockConsumptionStatus = StockConsumptionStatus.valueOf(state.name)))
            dao.updateOutbox(claimed.copy(state = outboxState, lastError = error, updatedAt = now))
        }
    }

    private fun <E, T> observeCache(query: (String) -> Flow<E?>, decode: (E) -> FoodCache<T>): Flow<FoodCache<T>?> =
        cacheOwner.flatMapLatest { owner ->
            if (owner == null) flowOf(null) else query(owner).map { it?.let(decode) }
        }

    private suspend fun <T> refresh(block: suspend (String, FoodClient) -> T): FoodResult<T> =
        refreshMutex.withLock { remote(block) }

    private suspend fun <T> remote(block: suspend (String, FoodClient) -> T): FoodResult<T> {
        val currentPairing = pairing() ?: return FoodResult.Failure(FoodError.NOT_PAIRED)
        return runRemote { block(currentPairing.foodOwner(), clientFor(currentPairing)) }
    }

    private suspend fun <T> runRemote(block: suspend () -> T): FoodResult<T> = try {
        FoodResult.Success(block())
    } catch (error: MastraHttpException) {
        val reason = when {
            error.code == "GROCY_UNAVAILABLE" -> FoodError.GROCY_UNAVAILABLE
            error.code == "AI_DISABLED" -> FoodError.AI_DISABLED
            error.status == 401 -> FoodError.NOT_PAIRED
            else -> FoodError.SERVER_ERROR
        }
        FoodResult.Failure(reason, error.code)
    } catch (_: IOException) {
        FoodResult.Failure(FoodError.OFFLINE)
    } catch (_: SerializationException) {
        FoodResult.Failure(FoodError.SERVER_ERROR)
    } catch (_: java.time.DateTimeException) {
        FoodResult.Failure(FoodError.SERVER_ERROR)
    }

    private companion object {
        val replayableStates = setOf(FoodOutboxState.PENDING, FoodOutboxState.SENT, FoodOutboxState.UNCERTAIN)
    }
}

private fun MastraPairing.foodOwner(): String = MastraSignedTransport.sha256("$baseUrl\n$deviceId")

internal fun CookingSessionEntity.nextRevision(now: Long) = copy(revision = revision + 1, updatedAt = now)

private fun CookingSessionEntity.toContract(timers: List<CookingTimerEntity>): CookingSession {
    val json = FoodContract.json
    val recipe = json.decodeFromString<RecipeDefinition>(recipeSnapshotJson)
    return CookingSession(
        id, recipe.id, recipe.version, recipe, servings, currentStepId,
        json.decodeFromString(completedStepIdsJson), json.decodeFromString(skippedStepIdsJson),
        timers.sortedWith(compareBy<CookingTimerEntity> { it.createdAt }.thenBy { it.id }).map { timer ->
            CookingTimer(
                timer.id, timer.label, timer.stepId, timer.durationSeconds, timer.state,
                timer.endsAtEpochMs?.let { Instant.ofEpochMilli(it).toString() },
                timer.remainingMs?.let { (it + 999) / 1_000 },
            )
        },
        json.decodeFromString(overridesJson), status, Instant.ofEpochMilli(startedAt).toString(),
        completedAt?.let { Instant.ofEpochMilli(it).toString() }, stockConsumptionStatus, revision,
        Instant.ofEpochMilli(updatedAt).toString(),
    )
}
