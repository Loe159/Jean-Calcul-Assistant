package fr.loevan.jeancalcul.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import fr.loevan.jeancalcul.data.conversation.AssistantSessionEntity
import fr.loevan.jeancalcul.data.conversation.ConversationDao
import fr.loevan.jeancalcul.data.conversation.ConversationEntity
import fr.loevan.jeancalcul.data.conversation.MessageEntity

@Database(
    entities = [ConversationEntity::class, AssistantSessionEntity::class, MessageEntity::class],
    version = JeanCalculDatabase.VERSION,
    exportSchema = true,
)
abstract class JeanCalculDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "jean_calcul.db"

        /** Later issues append explicit migrations here; destructive fallback is never enabled. */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
