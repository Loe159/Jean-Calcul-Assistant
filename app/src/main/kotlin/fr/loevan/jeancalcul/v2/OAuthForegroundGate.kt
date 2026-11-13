package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Never exchange a single-use OAuth code while Android has our activity in background. */
internal class OAuthForegroundGate {
    private val resumed = MutableStateFlow(false)

    fun onAppResumed() { resumed.value = true }

    fun onAppPaused() { resumed.value = false }

    suspend fun awaitAppResumed() { resumed.first { it } }
}
