package fr.loevan.jeancalcul.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.loevan.jeancalcul.data.audit.AuditDao
import fr.loevan.jeancalcul.data.audit.AuditEventEntity
import fr.loevan.jeancalcul.data.conversation.AssistantSessionEntity
import fr.loevan.jeancalcul.data.conversation.ConversationDao
import fr.loevan.jeancalcul.data.conversation.ConversationEntity
import fr.loevan.jeancalcul.data.conversation.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        AssistantSessionEntity::class,
        MessageEntity::class,
        AuditEventEntity::class,
    ],
    version = JeanCalculDatabase.VERSION,
    exportSchema = true,
)
abstract class JeanCalculDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun auditDao(): AuditDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "jean_calcul.db"

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `audit_events` (
                            `actionId` TEXT NOT NULL,
                            `sessionId` TEXT,
                            `origin` TEXT,
                            `toolName` TEXT NOT NULL,
                            `toolVersion` TEXT NOT NULL,
                            `redactedArguments` TEXT NOT NULL,
                            `riskLevel` TEXT,
                            `policyDecision` TEXT,
                            `policyReason` TEXT,
                            `policyJustification` TEXT,
                            `approvalStatus` TEXT,
                            `approvalMethod` TEXT,
                            `executionSucceeded` INTEGER,
                            `executionReplayed` INTEGER,
                            `executionDurationMillis` INTEGER,
                            `resultSummary` TEXT,
                            `errorCode` TEXT,
                            `errorMessage` TEXT,
                            `outcome` TEXT NOT NULL,
                            `occurredAtEpochMillis` INTEGER NOT NULL,
                            `updatedAtEpochMillis` INTEGER NOT NULL,
                            PRIMARY KEY(`actionId`)
                        )
                        """.trimIndent(),
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_audit_events_occurredAtEpochMillis` " +
                            "ON `audit_events` (`occurredAtEpochMillis`)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_audit_events_toolName` ON `audit_events` (`toolName`)",
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_audit_events_outcome` ON `audit_events` (`outcome`)",
                    )
                }
            }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
