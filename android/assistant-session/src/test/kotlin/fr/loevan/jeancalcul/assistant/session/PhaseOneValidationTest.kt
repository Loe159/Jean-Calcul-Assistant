package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.ActionApproval
import fr.loevan.jeancalcul.domain.ActionApprovalMethod
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AgentPolicyProfile
import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.domain.ChatMessage
import fr.loevan.jeancalcul.domain.ChatRequest
import fr.loevan.jeancalcul.domain.DeterministicVolumeCommandInterpreter
import fr.loevan.jeancalcul.domain.FinishReason
import fr.loevan.jeancalcul.domain.MessageContent
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.ModelCapabilities
import fr.loevan.jeancalcul.domain.ModelProfile
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.domain.PolicyEvaluationContext
import fr.loevan.jeancalcul.domain.SpeechRecognitionResult
import fr.loevan.jeancalcul.domain.SpeechToTextEvent
import fr.loevan.jeancalcul.domain.SpeechToTextProvider
import fr.loevan.jeancalcul.domain.SpeechToTextRequest
import fr.loevan.jeancalcul.domain.StreamEvent
import fr.loevan.jeancalcul.domain.TextToSpeechEvent
import fr.loevan.jeancalcul.domain.TextToSpeechProvider
import fr.loevan.jeancalcul.domain.TextToSpeechRequest
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolCall
import fr.loevan.jeancalcul.domain.VoiceLocale
import fr.loevan.jeancalcul.domain.VoiceProviderDescriptor
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import fr.loevan.jeancalcul.domain.testing.FakeModelProvider
import fr.loevan.jeancalcul.toolbridge.PlatformVolume
import fr.loevan.jeancalcul.toolbridge.VolumeController
import fr.loevan.jeancalcul.toolbridge.createVolumeToolRegistry
import fr.loevan.jeancalcul.toolbridge.volumeToolAvailabilityContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhaseOneValidationTest {
    @Suppress("LongMethod")
    @Test
    fun `model tool call is confirmed by policy before registry execution and tts`() =
        runTest {
            val volumeController = ValidationVolumeController()
            val registry = createVolumeToolRegistry(volumeController, ToolAuditLogger { })
            val availability = volumeToolAvailabilityContext(isDeviceLocked = false)
            val proposal = DeterministicVolumeCommandInterpreter { "model-action" }.setMusicVolumeProposal(30)
            val model =
                FakeModelProvider(
                    defaultCapabilities = ModelCapabilities(supportsToolCalling = true),
                ).apply {
                    enqueue(
                        listOf(
                            StreamEvent.ToolCallReady(
                                REQUEST_ID,
                                ToolCall(proposal.actionId, proposal.toolName, proposal.arguments),
                            ),
                            StreamEvent.Completed(REQUEST_ID, FinishReason.TOOL_CALL),
                        ),
                    )
                }
            val request =
                ChatRequest(
                    requestId = REQUEST_ID,
                    profile = ModelProfile("profile", model.id, "model", "Modele de validation"),
                    messages =
                        listOf(
                            ChatMessage(
                                "user-message",
                                MessageRole.USER,
                                listOf(MessageContent.Text("Mets le volume a 30 %")),
                            ),
                        ),
                    tools = registry.availableDefinitions(availability),
                )

            val call = model.stream(request).filterIsInstance<StreamEvent.ToolCallReady>().first().call
            val modelProposal =
                ActionProposal(
                    actionId = call.callId,
                    toolName = call.toolName,
                    toolVersion = VolumeToolSchemas.VERSION,
                    arguments = call.arguments,
                )
            val policy = PolicyEngine()
            val now = System.currentTimeMillis()
            val decision =
                policy.evaluate(
                    requireNotNull(registry.definitionFor(modelProposal)),
                    modelProposal,
                    policyContext(ActionRequestOrigin.MODEL_PROVIDER, availability.isDeviceLocked, now),
                )

            assertEquals(PolicyDecisionType.CONFIRM, decision.type)
            val blocked = registry.execute(modelProposal, availability)
            assertEquals("POLICY_AUTHORIZATION_REQUIRED", blocked.error?.code)
            assertEquals(5, volumeController.volume.current)

            val receipt =
                policy.issueReceipt(
                    decision,
                    now,
                    ActionApproval(modelProposal.actionId, true, ActionApprovalMethod.USER_CONFIRMATION, now),
                )
            val result = registry.execute(modelProposal, availability, receipt)
            val textToSpeech = ValidationTextToSpeechProvider()
            textToSpeech.speak(
                TextToSpeechRequest("Le volume de musique est maintenant a 30 %.", VoiceLocale("fr-FR")),
            )

            assertTrue(result.isSuccess)
            assertEquals(3, volumeController.volume.current)
            assertEquals("Le volume de musique est maintenant a 30 %.", textToSpeech.lastSpokenText)
        }

    @Test
    fun `text local action remains available without any network provider`() {
        val volumeController = ValidationVolumeController()
        val registry = createVolumeToolRegistry(volumeController, ToolAuditLogger { })
        val availability = volumeToolAvailabilityContext(isDeviceLocked = false)
        val proposal = DeterministicVolumeCommandInterpreter { "offline-action" }.getVolumeProposal(VolumeStream.MUSIC)
        val policy = PolicyEngine()
        val now = System.currentTimeMillis()
        val decision =
            policy.evaluate(
                requireNotNull(registry.definitionFor(proposal)),
                proposal,
                policyContext(ActionRequestOrigin.USER_TEXT, availability.isDeviceLocked, now),
            )

        assertEquals(PolicyDecisionType.ALLOW, decision.type)
        val result = registry.execute(proposal, availability, policy.issueReceipt(decision, now))

        assertTrue(result.isSuccess)
        assertEquals("50", result.output?.get("volumePercent").toString())
    }

    @Test
    fun `one hundred deterministic voice invocations finish without crash`() =
        runTest {
            repeat(INVOCATION_COUNT) { invocation ->
                val speechToText = ValidationSpeechToTextProvider()
                val volumeController = ValidationVolumeController()
                val controller =
                    VoiceSessionController(
                        speechToTextProvider = speechToText,
                        textToSpeechProvider = ValidationTextToSpeechProvider(),
                        voiceCommandProcessor =
                            VolumeCommandProcessor(
                                interpreter = DeterministicVolumeCommandInterpreter { "unit-action-$invocation" },
                                toolRegistry = createVolumeToolRegistry(volumeController, ToolAuditLogger { }),
                                availabilityContext = { volumeToolAvailabilityContext(isDeviceLocked = false) },
                            ),
                        dispatcher = StandardTestDispatcher(testScheduler),
                    )
                runCurrent()

                controller.startListening()
                speechToText.emit(
                    SpeechToTextEvent.Final(SpeechRecognitionResult("Mets le volume a 30 %", confidence = 1f)),
                )
                runCurrent()
                assertFalse(controller.state.value.assistantState is AssistantState.Executing)

                controller.confirmPendingCommand()
                runCurrent()

                assertEquals(3, volumeController.volume.current)
                controller.close()
            }
        }

    private fun policyContext(
        origin: ActionRequestOrigin,
        isDeviceLocked: Boolean,
        nowEpochMillis: Long,
    ) = PolicyEvaluationContext(
        profile = AgentPolicyProfile("phase-one-validation"),
        origin = origin,
        isDeviceLocked = isDeviceLocked,
        isAppForeground = true,
        nowEpochMillis = nowEpochMillis,
    )

    private class ValidationSpeechToTextProvider : SpeechToTextProvider {
        private val mutableEvents = MutableSharedFlow<SpeechToTextEvent>()
        override val events: Flow<SpeechToTextEvent> = mutableEvents.asSharedFlow()
        override val descriptor = VoiceProviderDescriptor("validation.stt", "Validation STT", true, true)

        override fun isAvailable() = true

        override fun startListening(request: SpeechToTextRequest) = Unit

        override fun stopListening() = Unit

        override fun cancel() = Unit

        override fun release() = Unit

        suspend fun emit(event: SpeechToTextEvent) {
            mutableEvents.emit(event)
        }
    }

    private class ValidationTextToSpeechProvider : TextToSpeechProvider {
        override val events: Flow<TextToSpeechEvent> = MutableSharedFlow<TextToSpeechEvent>().asSharedFlow()
        override val descriptor = VoiceProviderDescriptor("validation.tts", "Validation TTS", false, true)
        var lastSpokenText: String? = null

        override fun isAvailable() = true

        override fun speak(request: TextToSpeechRequest) {
            lastSpokenText = request.text
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private class ValidationVolumeController : VolumeController {
        var volume = PlatformVolume(current = 5, maximum = 10)

        override fun read(stream: VolumeStream): PlatformVolume = volume

        override fun write(
            stream: VolumeStream,
            volume: Int,
        ) {
            this.volume = this.volume.copy(current = volume)
        }
    }

    private companion object {
        const val REQUEST_ID = "phase-one-request"
        const val INVOCATION_COUNT = 100
    }
}
