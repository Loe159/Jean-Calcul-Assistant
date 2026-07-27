package fr.loevan.jeancalcul.observability

import fr.loevan.jeancalcul.security.SecretRedactor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditRedactor
    @Inject
    constructor(
        private val secretRedactor: SecretRedactor,
    ) {
        fun arguments(arguments: JsonObject): String = redactElement(arguments).toString()

        fun text(value: String?): String? = value?.let(secretRedactor::redact)

        fun storedArguments(value: String): String =
            runCatching { redactElement(Json.parseToJsonElement(value)).toString() }
                .getOrElse { secretRedactor.redact(value) }

        private fun redactElement(element: JsonElement): JsonElement =
            when (element) {
                is JsonObject ->
                    JsonObject(
                        element.mapValues { (name, value) ->
                            if (SENSITIVE_KEY.containsMatchIn(name)) {
                                JsonPrimitive(SecretRedactor.REDACTED)
                            } else {
                                redactElement(value)
                            }
                        },
                    )
                is JsonArray -> JsonArray(element.map(::redactElement))
                is JsonPrimitive ->
                    if (element.isString) {
                        JsonPrimitive(secretRedactor.redact(element.content))
                    } else {
                        element
                    }
                JsonNull -> JsonNull
            }

        private companion object {
            val SENSITIVE_KEY =
                Regex("(?i)(authorization|api[_-]?key|token|password|secret|otp|one[_-]?time[_-]?code)")
        }
    }
