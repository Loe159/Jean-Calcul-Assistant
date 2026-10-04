package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AgentBackend
import fr.loevan.jeancalcul.domain.AgentBackendFactory
import fr.loevan.jeancalcul.domain.AgentProfile
import fr.loevan.jeancalcul.domain.AgentRequest
import fr.loevan.jeancalcul.domain.AgentToolResultSink
import fr.loevan.jeancalcul.domain.AssistantSettingsRepository
import fr.loevan.jeancalcul.domain.AssistantSettingsValidator
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ProviderException
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.toolbridge.LocalAgentToolRuntime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.util.UUID

internal fun interface VoiceAgentProcessor {
    suspend fun process(transcript: String): VoiceCommandOutcome
}

/** Runs a power-button voice turn through the same active AgentBackend and Android tools as chat. */
internal class VoiceAgentCommandProcessor(
    private val settingsRepository: AssistantSettingsRepository,
    private val agentBackendFactory: AgentBackendFactory,
    private val toolRuntimeFactory: (profileId: String) -> LocalAgentToolRuntime,
) : VoiceAgentProcessor {
    override suspend fun process(transcript: String): VoiceCommandOutcome {
        val text = transcript.trim()
        if (text.isEmpty()) return VoiceCommandOutcome.Invalid("La demande est vide.")

        val settings = settingsRepository.settings.first()
        val configured =
            settings.agentProfiles.firstOrNull { it.profile.id == settings.activeAgentProfileId }
                ?: return VoiceCommandOutcome.Invalid(
                    "Aucun agent actif n'est configure. Ouvrez Jean Calcul pour connecter Codex Companion.",
                )
        val activationErrors = AssistantSettingsValidator.agentActivationErrors(configured, settings)
        if (activationErrors.isNotEmpty()) {
            return VoiceCommandOutcome.Failure(activationErrors.joinToString(" "))
        }

        val profile = configured.profile
        val connection =
            settings.providers.firstOrNull { it.id == profile.connectionId }
                ?: return VoiceCommandOutcome.Failure("La connexion de l'agent actif est introuvable.")
        val backend =
            runCatching { agentBackendFactory.create(connection, profile) }
                .getOrElse { return VoiceCommandOutcome.Failure(it.message ?: "Impossible d'ouvrir l'agent.") }

        return try {
            val response = runTurn(backend, profile, text, toolRuntimeFactory(profile.id))
            VoiceCommandOutcome.Completed(response.ifBlank { "Termine." })
        } catch (error: ProviderException) {
            VoiceCommandOutcome.Failure(error.error.message)
        } catch (error: Exception) {
            VoiceCommandOutcome.Failure(error.message ?: "L'agent n'a pas pu repondre.")
        }
    }

    private suspend fun runTurn(
        backend: AgentBackend,
        profile: AgentProfile,
        transcript: String,
        runtime: LocalAgentToolRuntime,
    ): String {
        val session = backend.createSession(profile)
        val requestId = UUID.randomUUID().toString()
        val run =
            backend.sendMessage(
                session.id,
                AgentRequest(
                    requestId = requestId,
                    messages =
                        listOf(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.USER,
                                content = listOf(MessageContent.Text(transcript)),
                            ),
                        ),
                    availableTools = runtime.availableTools(),
                ),
            )
        val response = StringBuilder()
        backend.streamEvents(session.id).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> response.append(event.text)
                is StreamEvent.ToolCallReady -> {
                    val sink =
                        backend as? AgentToolResultSink
                            ?: error("Le backend agent ne sait pas renvoyer les resultats d'outils Android.")
                    val result = runtime.execute(event.call)
                    sink.submitToolResult(session.id, run.id, event.call.callId, result)
                }
                is StreamEvent.Failed -> throw ProviderException(event.error)
                else -> Unit
            }
        }
        return response.toString().trim()
    }
}
