package com.jeancalcul.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "hermes_settings")

data class HermesSettings(
    val baseUrl: String = "http://127.0.0.1:8765",
    val token: String = ""
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val BaseUrl = stringPreferencesKey("base_url")
        val Token = stringPreferencesKey("token")
    }

    val settings: Flow<HermesSettings> = context.settingsDataStore.data.map { preferences ->
        HermesSettings(
            baseUrl = preferences[Keys.BaseUrl] ?: HermesSettings().baseUrl,
            token = preferences[Keys.Token].orEmpty()
        )
    }

    suspend fun save(baseUrl: String, token: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.BaseUrl] = baseUrl.trim().trimEnd('/')
            preferences[Keys.Token] = token.trim()
        }
    }
}
