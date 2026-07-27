package fr.loevan.jeancalcul.toolbridge

import android.util.Log
import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import fr.loevan.jeancalcul.domain.ActionApprovalReceipt
import fr.loevan.jeancalcul.domain.ActionProposal
import fr.loevan.jeancalcul.domain.ToolAuditEvent
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAuditStage
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolAvailabilityStatus
import fr.loevan.jeancalcul.domain.ToolDefinition
import fr.loevan.jeancalcul.domain.ToolError
import fr.loevan.jeancalcul.domain.ToolResult
import fr.loevan.jeancalcul.domain.authorizes
import kotlinx.serialization.json.JsonObject

fun interface ToolExecutor {
    fun execute(proposal: ActionProposal): ToolExecutionOutcome
}

sealed interface ToolExecutionOutcome {
    data class Success(val output: JsonObject) : ToolExecutionOutcome

    data class Failure(val error: ToolError) : ToolExecutionOutcome
}

data class ToolRegistration(
    val definition: ToolDefinition,
    val executor: ToolExecutor,
)

/**
 * The only entry point for discovering and executing local Android tools.
 *
 * Requests are resolved by exact name and version, checked against the current device context,
 * validated on both sides of execution, and deduplicated by idempotency key.
 */
class ToolRegistry(
    registrations: Collection<ToolRegistration>,
    private val auditLogger: ToolAuditLogger = AndroidToolAuditLogger,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxIdempotencyEntries: Int = DEFAULT_IDEMPOTENCY_ENTRIES,
) {
    private val schemaValidator = ToolJsonSchemaValidator()
    private val registrationsByKey: Map<ToolKey, ToolRegistration>
    private val idempotencyHistory =
        object : LinkedHashMap<String, IdempotencyRecord>(maxIdempotencyEntries, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IdempotencyRecord>): Boolean =
                size > maxIdempotencyEntries
        }

    init {
        require(registrations.isNotEmpty())
        require(maxIdempotencyEntries > 0)
        registrationsByKey = registrations.associateBy { it.definition.key() }
        require(registrationsByKey.size == registrations.size) { "Duplicate tool name and version." }
        registrations.forEach { registration ->
            schemaValidator.compile(registration.definition.inputSchema)
            schemaValidator.compile(registration.definition.outputSchema)
        }
    }

    fun availableDefinitions(context: ToolAvailabilityContext): List<ToolDefinition> =
        registrationsByKey.values
            .map(ToolRegistration::definition)
            .filter { it.availabilityIn(context) is ToolAvailabilityStatus.Available }
            .sortedWith(compareBy(ToolDefinition::name, ToolDefinition::version))

    fun definitionFor(proposal: ActionProposal): ToolDefinition? = registrationsByKey[proposal.key()]?.definition

    @Synchronized
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun execute(
        proposal: ActionProposal,
        context: ToolAvailabilityContext,
        approvalReceipt: ActionApprovalReceipt? = null,
    ): ToolResult {
        val startedAtEpochMillis = clock()
        audit(proposal, ToolAuditStage.REQUESTED, "Tool request received.", startedAtEpochMillis)

        if (proposal.expiresAtEpochMillis?.let { startedAtEpochMillis >= it } == true) {
            return failure(proposal, "ACTION_EXPIRED", "The tool request has expired.", startedAtEpochMillis)
        }

        val registration = registrationsByKey[proposal.key()]
        if (registration == null) {
            val code =
                if (registrationsByKey.keys.any { it.name == proposal.toolName }) {
                    "TOOL_VERSION_UNAVAILABLE"
                } else {
                    "UNKNOWN_TOOL"
                }
            return failure(proposal, code, "The requested tool or version is not registered.", startedAtEpochMillis)
        }

        val availability = registration.definition.availabilityIn(context)
        if (availability is ToolAvailabilityStatus.Unavailable) {
            return failure(
                proposal,
                "TOOL_UNAVAILABLE",
                "The tool is unavailable: ${availability.reason.name}.",
                startedAtEpochMillis,
            )
        }

        if (!schemaValidator.isValid(registration.definition.inputSchema, proposal.arguments)) {
            return failure(
                proposal,
                "INPUT_SCHEMA_INVALID",
                "The tool arguments do not match the declared schema.",
                startedAtEpochMillis,
            )
        }

        if (
            approvalReceipt?.authorizes(
                proposal = proposal,
                nowEpochMillis = clock(),
                isDeviceLocked = context.isDeviceLocked,
                isAppForeground = context.isAppForeground,
            ) != true
        ) {
            return failure(
                proposal,
                "POLICY_AUTHORIZATION_REQUIRED",
                "A valid Policy Engine approval receipt is required.",
                startedAtEpochMillis,
            )
        }

        idempotencyHistory[proposal.idempotencyKey]?.let { previous ->
            return if (previous.signature == proposal.signature()) {
                previous.result.copy(replayed = true).also { replayed ->
                    audit(
                        proposal,
                        ToolAuditStage.REPLAYED,
                        "Idempotent result replayed.",
                        startedAtEpochMillis,
                        replayed,
                    )
                }
            } else {
                failure(
                    proposal,
                    "IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used for another action.",
                    startedAtEpochMillis,
                )
            }
        }

        audit(proposal, ToolAuditStage.VALIDATED, "Tool request validated.", startedAtEpochMillis)
        val outcome =
            try {
                registration.executor.execute(proposal)
            } catch (_: RuntimeException) {
                ToolExecutionOutcome.Failure(ToolError("EXECUTION_FAILED", "The tool execution failed."))
            }

        val result =
            when (outcome) {
                is ToolExecutionOutcome.Failure ->
                    failure(proposal, outcome.error.code, outcome.error.message, startedAtEpochMillis)

                is ToolExecutionOutcome.Success -> {
                    if (schemaValidator.isValid(registration.definition.outputSchema, outcome.output)) {
                        ToolResult(
                            actionId = proposal.actionId,
                            toolName = proposal.toolName,
                            toolVersion = proposal.toolVersion,
                            output = outcome.output,
                        ).also { result ->
                            audit(
                                proposal,
                                ToolAuditStage.RESULT,
                                "Tool execution completed.",
                                startedAtEpochMillis,
                                result,
                            )
                        }
                    } else {
                        failure(
                            proposal,
                            "OUTPUT_SCHEMA_INVALID",
                            "The tool result does not match the declared schema.",
                            startedAtEpochMillis,
                        )
                    }
                }
            }

        idempotencyHistory[proposal.idempotencyKey] = IdempotencyRecord(proposal.signature(), result)
        return result
    }

    private fun failure(
        proposal: ActionProposal,
        code: String,
        message: String,
        startedAtEpochMillis: Long,
    ): ToolResult =
        ToolResult(
            actionId = proposal.actionId,
            toolName = proposal.toolName,
            toolVersion = proposal.toolVersion,
            error = ToolError(code, message),
        ).also { result -> audit(proposal, ToolAuditStage.ERROR, code, startedAtEpochMillis, result) }

    private fun audit(
        proposal: ActionProposal,
        stage: ToolAuditStage,
        message: String,
        startedAtEpochMillis: Long,
        result: ToolResult? = null,
    ) {
        val occurredAtEpochMillis = clock()
        runCatching {
            auditLogger.log(
                ToolAuditEvent(
                    actionId = proposal.actionId,
                    toolName = proposal.toolName,
                    toolVersion = proposal.toolVersion,
                    arguments = proposal.arguments,
                    stage = stage,
                    message = message,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                    durationMillis = (occurredAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0),
                    result = result,
                ),
            )
        }
    }

    private fun ToolDefinition.key() = ToolKey(name, version)

    private fun ActionProposal.key() = ToolKey(toolName, toolVersion)

    private fun ActionProposal.signature() = ToolRequestSignature(toolName, toolVersion, arguments)

    private data class ToolKey(
        val name: String,
        val version: String,
    )

    private data class ToolRequestSignature(
        val name: String,
        val version: String,
        val arguments: JsonObject,
    )

    private data class IdempotencyRecord(
        val signature: ToolRequestSignature,
        val result: ToolResult,
    )

    private companion object {
        const val DEFAULT_IDEMPOTENCY_ENTRIES = 256
        const val LOAD_FACTOR = 0.75f
    }
}

private class ToolJsonSchemaValidator {
    private val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    private val compiledSchemas = mutableMapOf<JsonObject, JsonSchema>()

    fun compile(schema: JsonObject): JsonSchema =
        compiledSchemas.getOrPut(schema) {
            factory.getSchema(schema.toString(), InputFormat.JSON)
        }

    fun isValid(
        schema: JsonObject,
        value: JsonObject,
    ): Boolean = compile(schema).validate(value.toString(), InputFormat.JSON).isEmpty()
}

private object AndroidToolAuditLogger : ToolAuditLogger {
    override fun log(event: ToolAuditEvent) {
        Log.i(
            "ToolRegistryAudit",
            "${event.stage}:${event.toolName}:${event.toolVersion}:${event.actionId}:${event.message}",
        )
    }
}
