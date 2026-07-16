package fr.loevan.jeancalcul.assistant.session

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Android endpoint that creates the isolated transparent assistant session.
 */
class JeanCalculVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = JeanCalculVoiceInteractionSession(this)
}
