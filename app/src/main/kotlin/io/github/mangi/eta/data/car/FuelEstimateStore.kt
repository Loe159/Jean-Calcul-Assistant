package io.github.mangi.eta.data.car

import android.content.Context
import kotlinx.serialization.encodeToString

// Persistance locale de l'ancre d'estimation (compteur + carburant% + autonomie du dernier relevé).
// Non sensible (pas de VIN, pas de secret) : SharedPreferences privé suffit.
internal class FuelEstimateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): FuelEstimateState = prefs.getString(KEY, null)?.let {
        runCatching { carJson.decodeFromString<FuelEstimateState>(it) }.getOrNull()
    } ?: FuelEstimateState()

    fun write(state: FuelEstimateState) {
        prefs.edit().putString(KEY, carJson.encodeToString(state)).apply()
    }

    private companion object {
        const val PREFS = "eta_fuel_estimate"
        const val KEY = "state"
    }
}
