package fr.loevan.jeancalcul.assistant.service

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Android entry point for the selected assistant role.
 *
 * This service is deliberately limited to lifecycle logging. Session UI, voice resources and
 * domain work belong to the dedicated session process introduced by subsequent issues.
 */
class JeanCalculVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.i(LOG_TAG, "Assistant service activated")
    }

    override fun onShutdown() {
        Log.i(LOG_TAG, "Assistant service deactivated")
        super.onShutdown()
    }

    private companion object {
        const val LOG_TAG = "AssistantService"
    }
}
