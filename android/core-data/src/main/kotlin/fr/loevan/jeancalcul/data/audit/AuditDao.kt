package fr.loevan.jeancalcul.data.audit

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("LongParameterList")
interface AuditDao {
    @Query(
        """
        SELECT * FROM audit_events
        WHERE (:fromEpochMillis IS NULL OR occurredAtEpochMillis >= :fromEpochMillis)
          AND (:toEpochMillis IS NULL OR occurredAtEpochMillis <= :toEpochMillis)
          AND (:toolName IS NULL OR toolName = :toolName)
          AND (:outcome IS NULL OR outcome = :outcome)
        ORDER BY occurredAtEpochMillis DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeEvents(
        fromEpochMillis: Long?,
        toEpochMillis: Long?,
        toolName: String?,
        outcome: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<AuditEventEntity>>

    @Query("SELECT * FROM audit_events WHERE actionId = :actionId")
    suspend fun getEvent(actionId: String): AuditEventEntity?

    @Upsert
    suspend fun upsertEvent(event: AuditEventEntity)

    @Query(
        """
        SELECT * FROM audit_events
        WHERE (:fromEpochMillis IS NULL OR occurredAtEpochMillis >= :fromEpochMillis)
          AND (:toEpochMillis IS NULL OR occurredAtEpochMillis <= :toEpochMillis)
          AND (:toolName IS NULL OR toolName = :toolName)
          AND (:outcome IS NULL OR outcome = :outcome)
        ORDER BY occurredAtEpochMillis DESC
        """,
    )
    suspend fun eventsForExport(
        fromEpochMillis: Long?,
        toEpochMillis: Long?,
        toolName: String?,
        outcome: String?,
    ): List<AuditEventEntity>

    @Query("DELETE FROM audit_events WHERE occurredAtEpochMillis < :cutoffEpochMillis")
    suspend fun purgeOlderThan(cutoffEpochMillis: Long): Int
}
