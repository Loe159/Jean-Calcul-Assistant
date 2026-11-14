package io.github.mangi.eta.data.repository

import io.github.mangi.eta.data.datastore.SettingsDataStore
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceUiStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object AppearanceSettingsRepository {
    fun settingsFlow(): Flow<AppearanceSettings> = SettingsDataStore.appearanceSettingsFlow().map { it.normalized() }

    suspend fun settings(): AppearanceSettings = SettingsDataStore.settings().appearance.normalized()

    suspend fun update(settings: AppearanceSettings) {
        SettingsDataStore.setAppearanceSettings(settings)
    }

    suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        SettingsDataStore.updateAppearanceSettings(transform)
    }

    suspend fun selectUiStyle(style: AppearanceUiStyle) {
        SettingsDataStore.selectUiStyle(AppearanceUiStyle.COUI)
    }
}
