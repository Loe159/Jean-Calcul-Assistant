package fr.loevan.jeancalcul.toolbridge

import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAvailability
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolDefaultPolicy
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.domain.ToolDeviceCapabilities
import fr.loevan.jeancalcul.domain.ToolLockScreenConstraint
import fr.loevan.jeancalcul.domain.ToolRiskLevel
import fr.loevan.jeancalcul.domain.VolumeStream
import fr.loevan.jeancalcul.domain.VolumeToolSchemas
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {
    @Test
    fun `unknown tools and versions never reach an executor`() {
        var executionCount = 0
        val registry =
            registryFor(echoDefinition()) { proposal ->
                executionCount += 1
                ToolExecutionOutcome.Success(proposal.arguments)
            }

        val unknownTool = registry.execute(proposal(name = "test.unknown"), availableContext())
        val unknownVersion = registry.execute(proposal(version = "2.0.0"), availableContext())

        assertEquals("UNKNOWN_TOOL", unknownTool.error?.code)
        assertEquals("TOOL_VERSION_UNAVAILABLE", unknownVersion.error?.code)
        assertEquals(0, executionCount)
    }

    @Test
    fun `input and output schemas reject unknown or malformed properties`() {
        var executionCount = 0
        val invalidOutputRegistry =
            registryFor(echoDefinition()) {
                executionCount += 1
                ToolExecutionOutcome.Success(
                    JsonObject(
                        mapOf(
                            "value" to JsonPrimitive("ok"),
                            "unexpected" to JsonPrimitive(true),
                        ),
                    ),
                )
            }

        val invalidInput =
            invalidOutputRegistry.execute(
                proposal(arguments = JsonObject(mapOf("value" to JsonPrimitive(42)))),
                availableContext(),
            )
        val unknownProperty =
            invalidOutputRegistry.execute(
                proposal(
                    actionId = "unknown-property",
                    arguments =
                        JsonObject(
                            mapOf(
                                "value" to JsonPrimitive("ok"),
                                "unexpected" to JsonPrimitive(true),
                            ),
                        ),
                ),
                availableContext(),
            )
        val invalidOutputProposal = proposal(actionId = "invalid-output")
        val invalidOutput =
            invalidOutputRegistry.execute(
                invalidOutputProposal,
                availableContext(),
                policyReceipt(
                    echoDefinition(),
                    invalidOutputProposal,
                    availableContext(),
                ),
            )

        assertEquals("INPUT_SCHEMA_INVALID", invalidInput.error?.code)
        assertEquals("INPUT_SCHEMA_INVALID", unknownProperty.error?.code)
        assertEquals("OUTPUT_SCHEMA_INVALID", invalidOutput.error?.code)
        assertEquals(1, executionCount)
    }

    @Test
    fun `discovery filters capabilities permissions and lock state`() {
        val permissionDefinition =
            echoDefinition(
                name = "test.protected",
                requiredPermission = "android.permission.TEST",
                requiredCapability = "device.test",
                lockScreenConstraint = ToolLockScreenConstraint.UNLOCKED_ONLY,
            )
        val registry = registryFor(permissionDefinition) { ToolExecutionOutcome.Success(it.arguments) }

        assertTrue(registry.availableDefinitions(availableContext()).isEmpty())
        assertTrue(
            registry.availableDefinitions(
                ToolAvailabilityContext(
                    deviceCapabilities = setOf("device.test"),
                    grantedAndroidPermissions = setOf("android.permission.TEST"),
                    isDeviceLocked = true,
                ),
            ).isEmpty(),
        )
        assertEquals(
            listOf("test.protected"),
            registry.availableDefinitions(
                ToolAvailabilityContext(
                    deviceCapabilities = setOf("device.test"),
                    grantedAndroidPermissions = setOf("android.permission.TEST"),
                    isDeviceLocked = false,
                ),
            ).map(ToolDefinition::name),
        )
    }

    @Test
    fun `volume discovery never advertises writes while device is locked`() {
        val controller = FakeVolumeController()
        val registry = createVolumeToolRegistry(controller, ToolAuditLogger { })

        val advertised = registry.availableDefinitions(volumeToolAvailabilityContext(isDeviceLocked = true))

        assertEquals(listOf(VolumeToolSchemas.GET_VOLUME_TOOL_NAME), advertised.map(ToolDefinition::name))
        val denied = registry.execute(volumeSetProposal(), volumeToolAvailabilityContext(isDeviceLocked = true))
        assertEquals("TOOL_UNAVAILABLE", denied.error?.code)
        assertEquals(0, controller.writeCount)
    }

    @Test
    fun `valid tool execution requires a matching policy receipt`() {
        var executionCount = 0
        val registry =
            registryFor(echoDefinition()) {
                executionCount += 1
                ToolExecutionOutcome.Success(it.arguments)
            }

        val denied = registry.execute(proposal(), availableContext())

        assertEquals("POLICY_AUTHORIZATION_REQUIRED", denied.error?.code)
        assertEquals(0, executionCount)
    }

    @Test
    fun `idempotency replays results rejects conflicts and honors expiration`() {
        var executionCount = 0
        val events = mutableListOf<ToolAuditEvent>()
        val registry =
            ToolRegistry(
                registrations =
                    listOf(
                        ToolRegistration(echoDefinition()) { proposal ->
                            executionCount += 1
                            ToolExecutionOutcome.Success(proposal.arguments)
                        },
                    ),
                auditLogger = ToolAuditLogger(events::add),
                clock = { 1_000L },
            )
        val firstProposal = proposal(expiresAtEpochMillis = 2_000L)

        val firstReceipt = policyReceipt(echoDefinition(), firstProposal, availableContext(), nowEpochMillis = 1_000L)
        val first = registry.execute(firstProposal, availableContext(), firstReceipt)
        val replay = registry.execute(firstProposal, availableContext(), firstReceipt)
        val conflictingProposal =
            firstProposal.copy(arguments = JsonObject(mapOf("value" to JsonPrimitive("different"))))
        val conflict =
            registry.execute(
                conflictingProposal,
                availableContext(),
                policyReceipt(echoDefinition(), conflictingProposal, availableContext(), nowEpochMillis = 1_000L),
            )
        val expired =
            registry.execute(
                proposal(actionId = "expired", expiresAtEpochMillis = 1_000L),
                availableContext(),
            )

        assertTrue(first.isSuccess)
        assertTrue(replay.replayed)
        assertEquals("IDEMPOTENCY_CONFLICT", conflict.error?.code)
        assertEquals("ACTION_EXPIRED", expired.error?.code)
        assertEquals(1, executionCount)
        assertTrue(events.all { it.toolVersion == "1.0.0" })
    }

    private fun registryFor(
        definition: ToolDefinition,
        executor: ToolExecutor,
    ) = ToolRegistry(
        registrations = listOf(ToolRegistration(definition, executor)),
        auditLogger = ToolAuditLogger { },
    )

    private fun proposal(
        actionId: String = "action-1",
        name: String = "test.echo",
        version: String = "1.0.0",
        arguments: JsonObject = JsonObject(mapOf("value" to JsonPrimitive("ok"))),
        expiresAtEpochMillis: Long? = null,
    ) = ActionProposal(
        actionId = actionId,
        toolName = name,
        toolVersion = version,
        arguments = arguments,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )

    private fun echoDefinition(
        name: String = "test.echo",
        requiredPermission: String? = null,
        requiredCapability: String? = null,
        lockScreenConstraint: ToolLockScreenConstraint = ToolLockScreenConstraint.AVAILABLE,
    ) = ToolDefinition(
        name = name,
        version = "1.0.0",
        description = "Echo one string value.",
        inputSchema = stringValueSchema(),
        outputSchema = stringValueSchema(),
        riskLevel = ToolRiskLevel.R0,
        requiredAndroidPermissions = setOfNotNull(requiredPermission),
        availability =
            ToolAvailability(
                requiredDeviceCapabilities = setOfNotNull(requiredCapability),
                lockScreenConstraint = lockScreenConstraint,
            ),
        defaultPolicy = ToolDefaultPolicy.ALLOW,
    )

    private fun stringValueSchema() =
        JsonObject(
            mapOf(
                "type" to JsonPrimitive("object"),
                "additionalProperties" to JsonPrimitive(false),
                "properties" to
                    JsonObject(
                        mapOf(
                            "value" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                        ),
                    ),
                "required" to JsonArray(listOf(JsonPrimitive("value"))),
            ),
        )

    private fun availableContext() =
        ToolAvailabilityContext(
            deviceCapabilities = setOf(ToolDeviceCapabilities.VOLUME_READ),
            isDeviceLocked = false,
        )

    private fun volumeSetProposal() =
        ActionProposal(
            actionId = "volume-set",
            toolName = VolumeToolSchemas.SET_VOLUME_TOOL_NAME,
            toolVersion = VolumeToolSchemas.VERSION,
            arguments =
                JsonObject(
                    mapOf(
                        "stream" to JsonPrimitive(VolumeStream.MUSIC.name),
                        "volumePercent" to JsonPrimitive(30),
                    ),
                ),
        )

    private class FakeVolumeController : VolumeController {
        var writeCount = 0

        override fun read(stream: VolumeStream) = PlatformVolume(current = 5, maximum = 10)

        override fun write(
            stream: VolumeStream,
            volume: Int,
        ) {
            writeCount += 1
        }
    }
}
