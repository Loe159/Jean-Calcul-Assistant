package io.github.mangi.eta.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import io.github.mangi.eta.food.CookingStatus
import io.github.mangi.eta.food.StockConsumptionStatus
import io.github.mangi.eta.food.TimerState

@Entity(tableName = "food_recipes", primaryKeys = ["owner", "id", "version"])
internal data class FoodRecipeEntity(
    val owner: String, val id: String, val version: Int, val json: String, val cachedAt: Long,
)

@Entity(tableName = "food_recipe_library", primaryKeys = ["owner", "recipeId"])
internal data class FoodRecipeLibraryEntity(
    val owner: String, val recipeId: String, val json: String, val cachedAt: Long,
)

@Entity(tableName = "food_recipe_index")
internal data class FoodRecipeIndexEntity(@PrimaryKey val owner: String, val json: String, val cachedAt: Long)

@Entity(tableName = "food_availability", primaryKeys = ["owner", "recipeId", "version", "servings"])
internal data class FoodAvailabilityEntity(
    val owner: String,
    val recipeId: String,
    val version: Int,
    val servings: Int,
    val json: String,
    val stockAsOf: String,
    val cachedAt: Long,
)

@Entity(tableName = "food_pantry")
internal data class FoodPantryEntity(@PrimaryKey val owner: String, val json: String, val fetchedAt: Long)

@Entity(tableName = "cooking_sessions")
internal data class CookingSessionEntity(
    @PrimaryKey val id: String,
    val owner: String?,
    val recipeSnapshotJson: String,
    val servings: Int,
    val currentStepId: String?,
    val completedStepIdsJson: String,
    val skippedStepIdsJson: String,
    val overridesJson: String,
    val status: CookingStatus,
    val startedAt: Long,
    val completedAt: Long?,
    val stockConsumptionStatus: StockConsumptionStatus,
    val revision: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "cooking_timers",
    foreignKeys = [ForeignKey(
        entity = CookingSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
internal data class CookingTimerEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val stepId: String?,
    val label: String,
    val durationSeconds: Int,
    val state: TimerState,
    val endsAtEpochMs: Long?,
    val remainingMs: Long?,
    val createdAt: Long,
)

internal data class CookingSessionWithTimers(
    @Embedded val session: CookingSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId") val timers: List<CookingTimerEntity>,
)

internal enum class FoodOutboxKind { COMPLETE_SESSION }
internal enum class FoodOutboxState { PENDING, SENT, DONE, FAILED, UNCERTAIN }

@Entity(tableName = "food_outbox", indices = [Index(value = ["sessionId"], unique = true)])
internal data class FoodOutboxEntity(
    @PrimaryKey val operationId: String,
    val sessionId: String,
    val kind: FoodOutboxKind,
    val endpoint: String,
    val bodyJson: String,
    val state: FoodOutboxState,
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "food_brief_cards")
internal data class FoodBriefCardsEntity(
    @PrimaryKey val owner: String, val json: String, val cachedAt: Long, val validUntilEpochMs: Long,
)
