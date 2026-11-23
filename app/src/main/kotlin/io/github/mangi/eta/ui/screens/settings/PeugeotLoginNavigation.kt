package io.github.mangi.eta.ui.screens.settings

import java.net.URI

private val peugeotLoginDomains = setOf(
    "peugeot.com",
    "gigya.com",
    "stellantis.com",
    "groupe-psa.com",
    "psa-peugeot-citroen.com",
)

internal fun URI.isAllowedPeugeotLoginPage(): Boolean {
    if (!scheme.equals("https", ignoreCase = true) || (port != -1 && port != 443) || rawUserInfo != null) {
        return false
    }
    val hostname = host?.lowercase() ?: return false
    return peugeotLoginDomains.any { hostname == it || hostname.endsWith(".$it") }
}
