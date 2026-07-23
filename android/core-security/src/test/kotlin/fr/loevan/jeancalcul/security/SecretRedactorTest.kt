package fr.loevan.jeancalcul.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactorTest {
    private val redactor = SecretRedactor()

    @Test
    fun `redacts common credential fields and authorization headers`() {
        val message =
            "Authorization: Bearer sk-live, x-api-key=api-value password='pass-value' token: token-value"

        val redacted = redactor.redact(message)

        assertFalse(redacted.contains("sk-live"))
        assertFalse(redacted.contains("api-value"))
        assertFalse(redacted.contains("pass-value"))
        assertFalse(redacted.contains("token-value"))
        assertEquals(4, REDACTION_REGEX.findAll(redacted).count())
    }

    @Test
    fun `redacts exact values without exposing a SecretValue`() {
        val secret = "custom-sensitive-value".toCharArray()

        val redacted = redactor.redact("Failure for custom-sensitive-value.", listOf(secret))

        assertEquals("Failure for [REDACTED].", redacted)
    }

    @Test
    fun `creates a sanitized crash report without retaining the throwable`() {
        val report =
            redactor.errorReport(
                IllegalStateException("Request failed with api_key=sk-crash"),
            )

        assertEquals(IllegalStateException::class.java.name, report.exceptionType)
        assertFalse(report.message.contains("sk-crash"))
        assertFalse(report.toString().contains("sk-crash"))
    }

    @Test
    fun `uses a constant UI mask`() {
        assertEquals("********", redactor.maskForDisplay(hasSecret = true))
        assertEquals("", redactor.maskForDisplay(hasSecret = false))
    }

    private companion object {
        val REDACTION_REGEX = Regex(Regex.escape(SecretRedactor.REDACTED))
    }
}
