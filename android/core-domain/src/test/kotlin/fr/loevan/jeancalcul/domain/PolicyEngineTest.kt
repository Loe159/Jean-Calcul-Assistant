package fr.loevan.jeancalcul.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    @Test
    fun `policy matrix covers allow confirm biometric open panel and deny`() {
        val cases =
            listOf(
                PolicyCase(ToolRiskLevel.R0, ToolDefaultPolicy.ALLOW, PolicyDecisionType.ALLOW),
                PolicyCase(ToolRiskLevel.R2, ToolDefaultPolicy.ALLOW, PolicyDecisionType.CONFIRM),
                PolicyCase(ToolRiskLevel.R3, ToolDefaultPolicy.ALLOW, PolicyDecisionType.CONFIRM),
                PolicyCase(ToolRiskLevel.R4, ToolDefaultPolicy.ALLOW, PolicyDecisionType.BIOMETRIC),
                PolicyCase(ToolRiskLevel.R5, ToolDefaultPolicy.ALLOW, PolicyDecisionType.DENY),
            )

        cases.forEach { case ->
            val decision = engine.evaluate(definition(case.risk, case.defaultPolicy), proposal(), context())

            assertEquals("Unexpected decision for ${case.risk}", case.expected, decision.type)
            assertTrue(decision.justification.isNotBlank())
        }

        val protected = definition(ToolRiskLevel.R1, ToolDefaultPolicy.ALLOW, permission = "android.permission.TEST")
        assertEquals(
            PolicyDecisionType.OPEN_SYSTEM_PANEL,
            engine.evaluate(protected, proposal(), context()).type,
        )
    }

    @Test
    fun `sensitive decisions cannot be downgraded or shown from the background`() {
        val allowPreference =
            ActionPolicyPreference(
                toolName = TOOL_NAME,
                arguments = JsonObject(mapOf("target" to JsonPrimitive("exact"))),
                decision = PolicyDecisionType.ALLOW,
            )
        val sensitive = definition(ToolRiskLevel.R4, ToolDefaultPolicy.CONFIRM)

        val foreground = engine.evaluate(sensitive, proposal(), context(preferences = listOf(allowPreference)))
        val background =
            engine.evaluate(
                sensitive,
                proposal(),
                context(isAppForeground = false, preferences = listOf(allowPreference)),
            )

        assertEquals(PolicyDecisionType.BIOMETRIC, foreground.type)
        assertEquals(PolicyReason.RISK_REQUIRES_BIOMETRIC, foreground.reason)
        assertEquals(PolicyDecisionType.DENY, background.type)
        assertEquals(PolicyReason.APP_NOT_FOREGROUND, background.reason)
    }

    @Test
    fun `lock screen and agent profile reduce available capabilities`() {
        val lockSafe = definition(ToolRiskLevel.R0, ToolDefaultPolicy.ALLOW, lockSafe = true)
        val reversible = definition(ToolRiskLevel.R2, ToolDefaultPolicy.CONFIRM)
        val restrictedProfile = AgentPolicyProfile(id = "restricted", maximumRiskLevel = ToolRiskLevel.R1)

        assertEquals(PolicyDecisionType.ALLOW, engine.evaluate(lockSafe, proposal(), context(isLocked = true)).type)
        assertEquals(
            PolicyReason.DEVICE_LOCKED,
            engine.evaluate(reversible, proposal(), context(isLocked = true)).reason,
        )
        assertEquals(
            PolicyReason.PROFILE_RESTRICTION,
            engine.evaluate(reversible, proposal(), context(profile = restrictedProfile)).reason,
        )
    }

    @Test
    fun `parameter preference applies only to the exact matching action`() {
        val definition = definition(ToolRiskLevel.R2, ToolDefaultPolicy.CONFIRM)
        val preference =
            ActionPolicyPreference(
                toolName = TOOL_NAME,
                arguments = JsonObject(mapOf("target" to JsonPrimitive("exact"))),
                decision = PolicyDecisionType.ALLOW,
            )
        val profile = AgentPolicyProfile("trusted", allowAutomaticReversibleActions = true)

        val matching =
            engine.evaluate(
                definition,
                proposal("exact"),
                context(profile = profile, preferences = listOf(preference)),
            )
        val different =
            engine.evaluate(
                definition,
                proposal("different"),
                context(profile = profile, preferences = listOf(preference)),
            )

        assertEquals(PolicyDecisionType.ALLOW, matching.type)
        assertEquals(PolicyReason.USER_PREFERENCE, matching.reason)
        assertEquals(PolicyDecisionType.CONFIRM, different.type)
        assertEquals("\"exact\"", matching.summary.parameters.single().exactValue)
    }

    @Test
    fun `approval receipts bind method proposal and expiration`() {
        val definition = definition(ToolRiskLevel.R4, ToolDefaultPolicy.BIOMETRIC)
        val proposal = proposal()
        val decision = engine.evaluate(definition, proposal, context())
        val simpleApproval = ActionApproval(proposal.actionId, true, ActionApprovalMethod.USER_CONFIRMATION, NOW)
        val biometricApproval = ActionApproval(proposal.actionId, true, ActionApprovalMethod.BIOMETRIC, NOW)

        val rejected = engine.issueReceipt(decision, NOW, simpleApproval)
        val authorized = engine.issueReceipt(decision, NOW, biometricApproval)

        assertEquals(ActionApprovalStatus.REJECTED, rejected.status)
        assertFalse(rejected.authorizes(proposal, NOW))
        assertTrue(authorized.authorizes(proposal, NOW))
        assertFalse(authorized.authorizes(proposal.copy(actionId = "other"), NOW))
        assertFalse(authorized.authorizes(proposal, authorized.expiresAtEpochMillis))
    }

    @Test
    fun `decisions and approvals emit audit records without action parameters`() {
        val events = mutableListOf<PolicyAuditEvent>()
        val auditedEngine = PolicyEngine(PolicyAuditLogger(events::add))
        val definition = definition(ToolRiskLevel.R0, ToolDefaultPolicy.ALLOW)
        val proposal = proposal()
        val decision = auditedEngine.evaluate(definition, proposal, context())

        auditedEngine.issueReceipt(decision, NOW)

        assertEquals(listOf(PolicyAuditStage.DECISION, PolicyAuditStage.APPROVAL), events.map { it.stage })
        assertTrue(events.all { it.actionId == proposal.actionId && it.toolVersion == TOOL_VERSION })
    }

    @Test
    fun `every registered volume tool declares and follows a default policy`() {
        val decisions =
            VolumeToolSchemas.definitions.associate { definition ->
                val proposal =
                    if (definition.name == VolumeToolSchemas.GET_VOLUME_TOOL_NAME) {
                        ActionProposal(
                            "volume-read",
                            definition.name,
                            definition.version,
                            JsonObject(mapOf("stream" to JsonPrimitive(VolumeStream.MUSIC.name))),
                        )
                    } else {
                        ActionProposal(
                            "volume-write",
                            definition.name,
                            definition.version,
                            JsonObject(
                                mapOf(
                                    "stream" to JsonPrimitive(VolumeStream.MUSIC.name),
                                    "volumePercent" to JsonPrimitive(30),
                                ),
                            ),
                        )
                    }
                definition.name to engine.evaluate(definition, proposal, context()).type
            }

        assertEquals(PolicyDecisionType.ALLOW, decisions[VolumeToolSchemas.GET_VOLUME_TOOL_NAME])
        assertEquals(PolicyDecisionType.CONFIRM, decisions[VolumeToolSchemas.SET_VOLUME_TOOL_NAME])
    }

    private val engine = PolicyEngine()

    private fun context(
        profile: AgentPolicyProfile = AgentPolicyProfile("default"),
        isLocked: Boolean = false,
        isAppForeground: Boolean = true,
        preferences: List<ActionPolicyPreference> = emptyList(),
    ) = PolicyEvaluationContext(
        profile = profile,
        origin = ActionRequestOrigin.USER_TEXT,
        isDeviceLocked = isLocked,
        isAppForeground = isAppForeground,
        preferences = preferences,
        nowEpochMillis = NOW,
    )

    private fun proposal(target: String = "exact") =
        ActionProposal(
            actionId = "action-1",
            toolName = TOOL_NAME,
            toolVersion = TOOL_VERSION,
            arguments = JsonObject(mapOf("target" to JsonPrimitive(target))),
        )

    private fun definition(
        risk: ToolRiskLevel,
        defaultPolicy: ToolDefaultPolicy,
        permission: String? = null,
        lockSafe: Boolean = false,
    ) = ToolDefinition(
        name = TOOL_NAME,
        version = TOOL_VERSION,
        description = "Apply the exact requested target.",
        inputSchema = targetSchema(),
        outputSchema = targetSchema(),
        riskLevel = risk,
        requiredAndroidPermissions = setOfNotNull(permission),
        availability =
            ToolAvailability(
                lockScreenConstraint =
                    if (lockSafe) ToolLockScreenConstraint.AVAILABLE else ToolLockScreenConstraint.UNLOCKED_ONLY,
            ),
        defaultPolicy = defaultPolicy,
    )

    private fun targetSchema() =
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "additionalProperties" to JsonPrimitive(false),
                "properties" to
                    JsonObject(mapOf("target" to JsonObject(mapOf("type" to JsonPrimitive("string"))))),
                "required" to JsonArray(listOf(JsonPrimitive("target"))),
            ),
        )

    private data class PolicyCase(
        val risk: ToolRiskLevel,
        val defaultPolicy: ToolDefaultPolicy,
        val expected: PolicyDecisionType,
    )

    private companion object {
        const val TOOL_NAME = "test.action"
        const val TOOL_VERSION = "1.0.0"
        const val NOW = 1_000L
    }
}
