package io.github.mangi.eta.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FoodDao {
    @Upsert suspend fun saveRecipe(recipe: FoodRecipeEntity)
    @Upsert suspend fun saveRecipeLibrary(library: FoodRecipeLibraryEntity)
    @Upsert suspend fun saveRecipeIndex(index: FoodRecipeIndexEntity)
    @Upsert suspend fun saveAvailability(availability: FoodAvailabilityEntity)
    @Upsert suspend fun savePantry(pantry: FoodPantryEntity)
    @Upsert suspend fun saveBriefCards(cards: FoodBriefCardsEntity)

    @Query("SELECT * FROM food_recipes WHERE owner = :owner AND id = :id ORDER BY version DESC LIMIT 1")
    fun observeRecipe(owner: String, id: String): Flow<FoodRecipeEntity?>

    @Query("SELECT * FROM food_recipe_library WHERE owner = :owner AND recipeId = :id")
    fun observeRecipeLibrary(owner: String, id: String): Flow<FoodRecipeLibraryEntity?>

    @Query("SELECT * FROM food_recipe_index WHERE owner = :owner")
    fun observeRecipeIndex(owner: String): Flow<FoodRecipeIndexEntity?>

    @Query("SELECT * FROM food_recipe_index WHERE owner = :owner")
    suspend fun recipeIndex(owner: String): FoodRecipeIndexEntity?

    @Query("SELECT * FROM food_brief_cards WHERE owner = :owner")
    suspend fun briefCards(owner: String): FoodBriefCardsEntity?

    @Query("SELECT * FROM food_availability WHERE owner = :owner AND recipeId = :id AND version = :version")
    fun observeAvailability(owner: String, id: String, version: Int): Flow<List<FoodAvailabilityEntity>>

    @Query("SELECT * FROM food_pantry WHERE owner = :owner")
    fun observePantry(owner: String): Flow<FoodPantryEntity?>

    @Query("SELECT * FROM food_brief_cards WHERE owner = :owner")
    fun observeBriefCards(owner: String): Flow<FoodBriefCardsEntity?>

    @Query("DELETE FROM food_recipes WHERE owner = :owner AND id = :id")
    suspend fun deleteRecipeVersions(owner: String, id: String)

    @Query("DELETE FROM food_recipe_library WHERE owner = :owner AND recipeId = :id")
    suspend fun deleteLibrary(owner: String, id: String)

    @Query("SELECT * FROM food_outbox WHERE sessionId = :sessionId")
    fun observeSessionOutbox(sessionId: String): Flow<FoodOutboxEntity?>

    @Insert suspend fun insertSession(session: CookingSessionEntity)
    @Update suspend fun updateSession(session: CookingSessionEntity)

    @Query("SELECT * FROM cooking_sessions WHERE id = :id")
    suspend fun session(id: String): CookingSessionEntity?

    @Transaction
    @Query("SELECT * FROM cooking_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<CookingSessionWithTimers?>

    @Query("SELECT * FROM cooking_sessions WHERE status = 'ACTIVE' ORDER BY startedAt DESC, id")
    fun observeActiveSessions(): Flow<List<CookingSessionEntity>>

    @Insert suspend fun insertTimer(timer: CookingTimerEntity)
    @Update suspend fun updateTimer(timer: CookingTimerEntity)

    @Query("SELECT * FROM cooking_timers WHERE id = :id")
    suspend fun timer(id: String): CookingTimerEntity?

    @Query("SELECT * FROM cooking_timers WHERE sessionId = :sessionId ORDER BY createdAt, id")
    suspend fun timers(sessionId: String): List<CookingTimerEntity>

    @Query("SELECT * FROM cooking_timers WHERE sessionId = :sessionId ORDER BY createdAt, id")
    fun observeTimers(sessionId: String): Flow<List<CookingTimerEntity>>

    @Query("SELECT * FROM cooking_timers WHERE state = 'RUNNING'")
    suspend fun runningTimers(): List<CookingTimerEntity>

    @Insert suspend fun insertOutbox(row: FoodOutboxEntity)
    @Update suspend fun updateOutbox(row: FoodOutboxEntity)

    @Query("SELECT * FROM food_outbox WHERE sessionId = :sessionId")
    suspend fun sessionOutbox(sessionId: String): FoodOutboxEntity?

    @Query("SELECT * FROM food_outbox WHERE operationId = :operationId")
    suspend fun outbox(operationId: String): FoodOutboxEntity?

    @Query("""
        SELECT food_outbox.* FROM food_outbox JOIN cooking_sessions ON sessionId = cooking_sessions.id
        WHERE state IN ('PENDING', 'SENT', 'UNCERTAIN') AND (owner = :owner OR owner IS NULL)
        AND stockConsumptionStatus != 'SUCCEEDED'
        ORDER BY createdAt, operationId
    """)
    suspend fun replayable(owner: String): List<FoodOutboxEntity>
}
