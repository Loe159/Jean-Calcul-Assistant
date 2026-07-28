package fr.loevan.jeancalcul.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.assistantSettingsDataStore by preferencesDataStore(name = "assistant_settings")

@Singleton
class DataStoreAssistantSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AssistantSettingsRepository {
        private val updateMutex = Mutex()
        private val json = Json { ignoreUnknownKeys = true }

        override val settings: Flow<AssistantSettings> =
            context.assistantSettingsDataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }.map(::decode)

        override suspend fun update(transform: (AssistantSettings) -> AssistantSettings) {
            updateMutex.withLock {
                context.assistantSettingsDataStore.edit { preferences ->
                    val updated = transform(decode(preferences))
                    preferences[SETTINGS_JSON] = json.encodeToString(updated)
                }
            }
        }

        private fun decode(preferences: Preferences): AssistantSettings {
            val encoded = preferences[SETTINGS_JSON] ?: return AssistantSettings()
            return try {
                json.decodeFromString<AssistantSettings>(encoded)
            } catch (_: SerializationException) {
                AssistantSettings()
            } catch (_: IllegalArgumentException) {
                AssistantSettings()
            }
        }

        private companion object {
            val SETTINGS_JSON = stringPreferencesKey("settings_json_v1")
        }
    }

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataModule {
    @Provides
    @Singleton
    fun provideAssistantSettingsRepository(
        implementation: DataStoreAssistantSettingsRepository,
    ): AssistantSettingsRepository = implementation
}
