package fr.loevan.jeancalcul.data.audit

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auditPreferences by preferencesDataStore(name = "audit_settings")

@Singleton
class AuditRetentionPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val retentionDays: Flow<Int> =
            context.auditPreferences.data.map { preferences ->
                preferences[RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS
            }

        suspend fun setRetentionDays(days: Int) {
            require(days in MIN_RETENTION_DAYS..MAX_RETENTION_DAYS)
            context.auditPreferences.edit { preferences -> preferences[RETENTION_DAYS] = days }
        }

        companion object {
            const val DEFAULT_RETENTION_DAYS = 30
            const val MIN_RETENTION_DAYS = 1
            const val MAX_RETENTION_DAYS = 3_650
            private val RETENTION_DAYS = intPreferencesKey("retention_days")
        }
    }
