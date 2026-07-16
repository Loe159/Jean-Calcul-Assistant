package fr.loevan.jeancalcul.assistant.service

import android.service.voice.VoiceInteractionService
import android.util.Log
import fr.loevan.jeancalcul.observability.AndroidPerformanceTrace

/**
 * Android entry point for the selected assistant role.
 *
 * This service is deliberately limited to lifecycle logging. Session UI, voice resources and
 * domain work belong to the dedicated session process introduced by subsequent issues.
 */
class JeanCalculVoiceInteractionService : VoiceInteractionService() {
    private lateinit var performanceTrace: AndroidPerformanceTrace

    override fun onCreate() {
        super.onCreate()
        performanceTrace = AndroidPerformanceTrace(applicationContext)
    }

    override fun onReady() {
        super.onReady()
        performanceTrace.captureMemory("service_ready")
        Log.i(LOG_TAG, "Assistant service activated")
    }

    override fun onShutdown() {
        performanceTrace.captureMemory("service_shutdown")
        Log.i(LOG_TAG, "Assistant service deactivated")
        super.onShutdown()
    }

    private companion object {
        const val LOG_TAG = "AssistantService"
    }
}
