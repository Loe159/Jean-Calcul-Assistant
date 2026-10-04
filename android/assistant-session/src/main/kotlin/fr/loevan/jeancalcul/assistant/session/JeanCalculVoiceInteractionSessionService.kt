package fr.loevan.jeancalcul.assistant.session

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dagger.hilt.android.AndroidEntryPoint
import fr.loevan.jeancalcul.domain.AgentBackendFactory
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.feature.conversation.VoiceConversationRecorder
import fr.loevan.jeancalcul.observability.PersistentAuditLogger
import javax.inject.Inject

/**
 * Android endpoint that creates the isolated transparent assistant session.
 */
@AndroidEntryPoint
class JeanCalculVoiceInteractionSessionService : VoiceInteractionSessionService() {
    @Inject lateinit var conversationRecorder: VoiceConversationRecorder

    @Inject lateinit var auditLogger: PersistentAuditLogger

    @Inject lateinit var settingsRepository: AssistantSettingsRepository

    @Inject lateinit var agentBackendFactory: AgentBackendFactory

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        JeanCalculVoiceInteractionSession(
            this,
            conversationRecorder = conversationRecorder,
            auditLogger = auditLogger,
            settingsRepository = settingsRepository,
            agentBackendFactory = agentBackendFactory,
        )
}
