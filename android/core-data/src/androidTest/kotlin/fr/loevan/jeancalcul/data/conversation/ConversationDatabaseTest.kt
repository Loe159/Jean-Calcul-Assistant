package fr.loevan.jeancalcul.data.conversation

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.loevan.jeancalcul.data.JeanCalculDatabase
import fr.loevan.jeancalcul.domain.Conversation
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDatabaseTest {
    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            JeanCalculDatabase::class.java,
        )

    private lateinit var database: JeanCalculDatabase
    private lateinit var dao: ConversationDao

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                JeanCalculDatabase::class.java,
            ).allowMainThreadQueries().build()
        dao = database.conversationDao()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun conversationMessagesPersistAndCascadeOnDelete() =
        runTest {
            dao.upsertConversation(ConversationEntity("c1", "Test", 1, 1, null, null))
            dao.upsertMessage(
                MessageEntity(
                    id = "m1",
                    conversationId = "c1",
                    assistantSessionId = null,
                    role = "USER",
                    text = "Bonjour",
                    status = "COMPLETED",
                    sequence = 0,
                    createdAtEpochMillis = 2,
                    updatedAtEpochMillis = 2,
                    requestId = null,
                    inputTokens = null,
                    outputTokens = null,
                    errorMessage = null,
                ),
            )

            assertEquals("Bonjour", dao.observeMessages("c1").first().single().text)

            dao.deleteConversation("c1")
            assertEquals(emptyList<MessageEntity>(), dao.getMessages("c1"))
            assertNull(dao.getConversation("c1"))
        }

    @Test
    fun currentSchemaUsesVersionTwo() {
        assertEquals(JeanCalculDatabase.VERSION, database.openHelper.readableDatabase.version)
    }

    @Test
    fun exportedVersionOneSchemaCanBeOpenedWithoutDestructiveFallback() {
        migrationHelper.createDatabase(MIGRATION_DATABASE, JeanCalculDatabase.VERSION).close()
        migrationHelper
            .runMigrationsAndValidate(
                MIGRATION_DATABASE,
                JeanCalculDatabase.VERSION,
                true,
                *JeanCalculDatabase.MIGRATIONS,
            ).close()
    }

    @Test
    fun migrationFromVersionOneAddsAuditStorageWithoutChangingConversations() {
        val versionOne = migrationHelper.createDatabase(MIGRATION_DATABASE_FROM_ONE, 1)
        versionOne.execSQL(
            "INSERT INTO conversations " +
                "(id, title, createdAtEpochMillis, updatedAtEpochMillis, contextSummary, summarizedThroughSequence) " +
                "VALUES ('c-before-audit', 'Existing', 1, 1, NULL, NULL)",
        )
        versionOne.close()

        val migrated =
            migrationHelper.runMigrationsAndValidate(
                MIGRATION_DATABASE_FROM_ONE,
                JeanCalculDatabase.VERSION,
                true,
                JeanCalculDatabase.MIGRATION_1_2,
            )
        migrated.query("SELECT id FROM conversations WHERE id = 'c-before-audit'").use { cursor ->
            assertTrue(cursor.moveToFirst())
        }
        migrated.query("SELECT COUNT(*) FROM audit_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun repositoryExportsThenDeletesConversation() =
        runTest {
            val repository =
                RoomConversationRepository(
                    dao,
                    Json {
                        prettyPrint = true
                        encodeDefaults = true
                    },
                )
            repository.saveConversation(Conversation("c2", "Export", 1))
            repository.saveMessage(
                Message(
                    id = "m2",
                    conversationId = "c2",
                    role = MessageRole.USER,
                    text = "Bonjour",
                    status = MessageStatus.COMPLETED,
                    sequence = 0,
                    createdAtEpochMillis = 2,
                ),
            )

            val exported = repository.exportConversation("c2")
            assertTrue(exported.contains("Bonjour"))
            assertTrue(exported.contains("schemaVersion"))

            repository.deleteConversation("c2")
            assertNull(repository.getConversation("c2"))
        }

    private companion object {
        const val MIGRATION_DATABASE = "conversation-migration-test"
        const val MIGRATION_DATABASE_FROM_ONE = "conversation-migration-from-one-test"
    }
}
