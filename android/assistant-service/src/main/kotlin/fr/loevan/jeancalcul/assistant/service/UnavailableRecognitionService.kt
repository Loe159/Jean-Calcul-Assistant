package fr.loevan.jeancalcul.assistant.service

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Metadata-required recognition endpoint.
 *
 * Speech recognition is intentionally not implemented before issue #13. Returning an explicit
 * client error keeps this placeholder deterministic and avoids acquiring audio resources.
 */
class UnavailableRecognitionService : RecognitionService() {
    override fun onStartListening(
        intent: Intent?,
        callback: Callback,
    ) {
        callback.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(callback: Callback) = Unit

    override fun onCancel(callback: Callback) = Unit
}
