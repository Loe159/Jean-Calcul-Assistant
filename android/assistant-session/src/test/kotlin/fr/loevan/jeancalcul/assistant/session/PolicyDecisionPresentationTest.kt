package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ActionRequestOrigin
import fr.loevan.jeancalcul.domain.AgentPolicyProfile
import fr.loevan.jeancalcul.domain.PolicyDecisionType
import fr.loevan.jeancalcul.domain.PolicyEngine
import fr.loevan.jeancalcul.domain.PolicyEvaluationContext
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import fr.loevan.jeancalcul.ui.ActionCardState
import fr.loevan.jeancalcul.ui.ApprovalSheetState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyDecisionPresentationTest {
    @Test
    fun `approval presentation exposes exact reviewed parameters`() {
        val definition = VolumeToolSchemas.definitions.first { it.name == VolumeToolSchemas.SET_VOLUME_TOOL_NAME }
        val proposal =
            ActionProposal(
                actionId = "volume-approval",
                toolName = definition.name,
                toolVersion = definition.version,
                arguments =
                    JsonObject(
                        mapOf(
                            "stream" to JsonPrimitive(VolumeStream.MUSIC.name),
                            "volumePercent" to JsonPrimitive(30),
                        ),
                    ),
            )
        val decision =
            PolicyEngine().evaluate(
                definition,
                proposal,
                PolicyEvaluationContext(
                    profile = AgentPolicyProfile("local"),
                    origin = ActionRequestOrigin.USER_VOICE,
                    isDeviceLocked = false,
                    isAppForeground = true,
                    nowEpochMillis = 1_000L,
                ),
            )

        val card = decision.toActionCardData()

        assertEquals(PolicyDecisionType.CONFIRM, decision.type)
        assertEquals(ApprovalSheetState.SimpleConfirmation, decision.toApprovalSheetState())
        assertEquals(ActionCardState.ConfirmationRequired, card.state)
        assertEquals(listOf("stream", "volumePercent"), card.details.map { it.label })
        assertEquals("30", card.details.first { it.label == "volumePercent" }.value)
    }
}
