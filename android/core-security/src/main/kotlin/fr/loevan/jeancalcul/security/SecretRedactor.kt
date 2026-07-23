package fr.loevan.jeancalcul.security

/** Sanitizes diagnostic text before it reaches Logcat or a crash-reporting boundary. */
class SecretRedactor {
    fun redact(
        message: String,
        additionalSecrets: Iterable<CharArray> = emptyList(),
    ): String {
        var redacted =
            CREDENTIAL_PATTERN.replace(message) { match ->
                buildString {
                    append(match.groupValues[1])
                    append(match.groupValues[2])
                    match.groupValues[3].takeIf(String::isNotEmpty)?.let { append("$it ") }
                    append(REDACTED)
                }
            }
        redacted =
            BEARER_PATTERN.replace(redacted) { match ->
                "${match.groupValues[1]}$REDACTED"
            }
        additionalSecrets
            .filter(CharArray::isNotEmpty)
            .forEach { secret -> redacted = redacted.replaceExact(secret) }
        return redacted
    }

    fun errorReport(
        throwable: Throwable,
        additionalSecrets: Iterable<CharArray> = emptyList(),
    ): RedactedErrorReport =
        RedactedErrorReport(
            exceptionType = throwable.javaClass.name,
            message = redact(throwable.message.orEmpty(), additionalSecrets),
        )

    fun maskForDisplay(hasSecret: Boolean): String = if (hasSecret) DISPLAY_MASK else ""

    private fun String.replaceExact(secret: CharArray): String {
        val result = StringBuilder(length)
        var index = 0
        while (index < length) {
            if (matchesAt(index, secret)) {
                result.append(REDACTED)
                index += secret.size
            } else {
                result.append(this[index])
                index += 1
            }
        }
        return result.toString()
    }

    private fun String.matchesAt(
        startIndex: Int,
        expected: CharArray,
    ): Boolean {
        if (startIndex + expected.size > length) return false
        return expected.indices.all { offset -> this[startIndex + offset] == expected[offset] }
    }

    companion object {
        const val REDACTED = "[REDACTED]"
        private const val DISPLAY_MASK = "********"
        private val CREDENTIAL_PATTERN =
            Regex(
                pattern =
                    "(?i)\\b(authorization|x-api-key|api[_-]?key|access[_-]?token|token|password|secret)" +
                        "(\\s*[:=]\\s*)(?:(bearer|basic)\\s+)?(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;]+)",
            )
        private val BEARER_PATTERN = Regex("(?i)\\b((?:bearer|basic)\\s+)[^\\s,;]+")
    }
}

/** Safe diagnostic representation that deliberately drops the original throwable and its causes. */
data class RedactedErrorReport(
    val exceptionType: String,
    val message: String,
)
