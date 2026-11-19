package io.github.mangi.eta.agent.device.notifications.remote

/**
 * Local data minimisation before sending to the homelab. This is NOT business
 * classification: it only masks numeric codes (OTP, PIN, card fragments) that
 * are never needed to classify a notification.
 */
internal object NotificationPrivacyFilter {
    // Space-separated groups ("123 456") are codes too; dashes are left alone so ISO dates stay readable.
    private val numericCode = Regex("(?<![\\p{L}\\p{N}-])\\d(?: ?\\d){3,7}(?![\\p{L}\\p{N}-])")
    private val longNumber = Regex("\\d{9,}")

    data class Filtered(val title: String?, val content: String?, val subText: String?, val redacted: Boolean)

    fun filter(title: String?, content: String?, subText: String?): Filtered {
        var redacted = false
        fun mask(value: String?): String? = value?.let { text ->
            val masked = longNumber.replace(numericCode.replace(text) { m ->
                // Keep times, years and short counters readable; mask code-like digit groups.
                val digits = m.value.filter(Char::isDigit)
                if (digits.length == 4 && digits.toIntOrNull()?.let { it in 1900..2100 } == true) m.value
                else { redacted = true; "#".repeat(digits.length.coerceAtMost(8)) }
            }) { redacted = true; "#########" }
            masked
        }
        return Filtered(mask(title), mask(content), mask(subText), redacted)
    }
}
