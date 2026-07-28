package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MvpToolPolicyTest {
    private val engine = PolicyEngine()
    private val flashlight =
        MvpToolSchemas.definitions.first { it.name == MvpToolSchemas.DEVICE_TOGGLE_FLASHLIGHT }
    private val proposal =
        ActionProposal(
            actionId = "flashlight-1",
            toolName = flashlight.name,
            toolVersion = flashlight.version,
            arguments = JsonObject(mapOf("enabled" to JsonPrimitive(true))),
        )

    @Test
    fun `missing camera permission produces an explicit system panel decision`() {
        val decision = engine.evaluate(flashlight, proposal, context(grantedPermissions = emptySet()))

        assertEquals(PolicyDecisionType.OPEN_SYSTEM_PANEL, decision.type)
        assertEquals(PolicyReason.PERMISSION_MISSING, decision.reason)
        assertEquals(setOf(ToolAndroidPermissions.CAMERA), decision.missingAndroidPermissions)
    }

    @Test
    fun `locked device denies missing permission action instead of opening UI`() {
        val decision =
            engine.evaluate(
                flashlight,
                proposal,
                context(grantedPermissions = emptySet(), isDeviceLocked = true),
            )

        assertEquals(PolicyDecisionType.DENY, decision.type)
        assertEquals(PolicyReason.DEVICE_LOCKED, decision.reason)
    }

    private fun context(
        grantedPermissions: Set<String>,
        isDeviceLocked: Boolean = false,
    ) = PolicyEvaluationContext(
        profile = AgentPolicyProfile("mvp-tools"),
        origin = ActionRequestOrigin.MODEL_PROVIDER,
        grantedAndroidPermissions = grantedPermissions,
        isDeviceLocked = isDeviceLocked,
        isAppForeground = true,
        nowEpochMillis = 1_000L,
    )
}
