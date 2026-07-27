package fr.loevan.jeancalcul.data.audit

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.loevan.jeancalcul.data.JeanCalculDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuditDatabaseTest {
    private lateinit var database: JeanCalculDatabase
    private lateinit var dao: AuditDao

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                JeanCalculDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = database.auditDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun eventsCanBeFilteredPaginatedAndPurged() =
        runTest {
            dao.upsertEvent(event("a1", "audio.get_volume", "SUCCESS", 1))
            dao.upsertEvent(event("a2", "audio.set_volume", "DENIED", 2))
            dao.upsertEvent(event("a3", "audio.set_volume", "SUCCESS", 3))

            val filtered = dao.observeEvents(2, null, "audio.set_volume", "SUCCESS", 1, 0).first()
            assertEquals(listOf("a3"), filtered.map(AuditEventEntity::actionId))

            val secondPage = dao.observeEvents(null, null, null, null, 1, 1).first()
            assertEquals(listOf("a2"), secondPage.map(AuditEventEntity::actionId))

            assertEquals(1, dao.purgeOlderThan(2))
            assertEquals(2, dao.eventsForExport(null, null, null, null).size)
        }

    private fun event(
        id: String,
        toolName: String,
        outcome: String,
        occurredAt: Long,
    ) = AuditEventEntity(
        actionId = id,
        sessionId = null,
        origin = "USER_VOICE",
        toolName = toolName,
        toolVersion = "1.0.0",
        redactedArguments = "{}",
        riskLevel = "R0",
        policyDecision = "ALLOW",
        policyReason = "TOOL_DEFAULT",
        policyJustification = "default",
        approvalStatus = "AUTHORIZED",
        approvalMethod = null,
        executionSucceeded = outcome == "SUCCESS",
        executionReplayed = false,
        executionDurationMillis = 1,
        resultSummary = null,
        errorCode = if (outcome == "SUCCESS") null else "DENIED",
        errorMessage = null,
        outcome = outcome,
        occurredAtEpochMillis = occurredAt,
        updatedAtEpochMillis = occurredAt,
    )
}
