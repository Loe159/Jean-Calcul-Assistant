package fr.loevan.jeancalcul.assistant.session

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Minimal session endpoint required by Android's voice-interaction metadata.
 *
 * Its transparent Compose surface and interaction lifecycle are intentionally deferred to issue #11.
 */
class JeanCalculVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = VoiceInteractionSession(this)
}
