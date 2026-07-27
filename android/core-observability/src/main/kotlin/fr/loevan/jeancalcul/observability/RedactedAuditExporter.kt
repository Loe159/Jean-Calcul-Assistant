package fr.loevan.jeancalcul.observability

import fr.loevan.jeancalcul.domain.AuditEvent
import fr.loevan.jeancalcul.domain.AuditFilter
import fr.loevan.jeancalcul.domain.AuditRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedactedAuditExporter
    @Inject
    constructor(
        private val repository: AuditRepository,
        private val redactor: AuditRedactor,
        private val json: Json,
    ) {
        suspend fun export(filter: AuditFilter = AuditFilter()): String {
            val events = repository.eventsForExport(filter).map(::redactAgain)
            val document =
                buildJsonObject {
                    put("schemaVersion", 1)
                    put("exportedAtEpochMillis", System.currentTimeMillis())
                    putJsonArray("events") {
                        events.forEach { event -> add(json.parseToJsonElement(json.encodeToString(event))) }
                    }
                }
            return json.encodeToString(document)
        }

        private fun redactAgain(event: AuditEvent): AuditEvent =
            event.copy(
                redactedArguments = redactor.storedArguments(event.redactedArguments),
                policy =
                    event.policy?.let { policy ->
                        policy.copy(justification = redactor.text(policy.justification).orEmpty())
                    },
                execution =
                    event.execution?.let { execution ->
                        execution.copy(
                            resultSummary = redactor.text(execution.resultSummary),
                            errorCode = redactor.text(execution.errorCode),
                            errorMessage = redactor.text(execution.errorMessage),
                        )
                    },
            )
    }
