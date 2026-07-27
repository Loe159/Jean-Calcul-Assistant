package fr.loevan.jeancalcul.data

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.loevan.jeancalcul.data.conversation.ConversationDao
import fr.loevan.jeancalcul.data.conversation.RoomConversationRepository
import fr.loevan.jeancalcul.domain.ConversationRepository
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ConversationRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindConversationRepository(repository: RoomConversationRepository): ConversationRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ConversationDatabaseModule {
    @Provides
    @Singleton
    @Suppress("SpreadOperator")
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): JeanCalculDatabase =
        Room.databaseBuilder(context, JeanCalculDatabase::class.java, JeanCalculDatabase.FILE_NAME)
            .addMigrations(*JeanCalculDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideConversationDao(database: JeanCalculDatabase): ConversationDao = database.conversationDao()

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }
}
