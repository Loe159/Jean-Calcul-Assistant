package io.github.mangi.eta.ui.screens.settings

import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeugeotLoginNavigationTest {
    @Test
    fun acceptsLoginDomainsAndTheirSubdomains() {
        listOf(
            "https://peugeot.com",
            "https://id-dcr.peugeot.com/oidc/op/v1.0/example/authorize/",
            "https://idpcvs.peugeot.com/am/oauth2c/OAuthProxy.jsp",
            "https://gigya.com",
            "https://fidm.eu1.gigya.com/oidc/op/v1.0/example/authorize?prompt=login",
            "https://stellantis.com",
            "HTTPS://LOGIN.STELLANTIS.COM:443",
            "https://groupe-psa.com",
            "https://login.groupe-psa.com",
            "https://psa-peugeot-citroen.com",
            "https://login.psa-peugeot-citroen.com",
        ).forEach { assertTrue(it, URI(it).isAllowedPeugeotLoginPage()) }
    }

    @Test
    fun rejectsUnrelatedHostsAndLookalikeSuffixes() {
        listOf(
            "https://example.org",
            "https://evilpeugeot.com",
            "https://evilgigya.com",
            "https://peugeot.com.example.org",
            "https:///peugeot.com",
        ).forEach { assertFalse(it, URI(it).isAllowedPeugeotLoginPage()) }
    }

    @Test
    fun rejectsOtherSchemesPortsAndUserInfo() {
        listOf(
            "http://id-dcr.peugeot.com:443",
            "https://id-dcr.peugeot.com:8443",
            "https://id-dcr.peugeot.com:invalid",
            "https://user:password@id-dcr.peugeot.com",
            "https://@id-dcr.peugeot.com",
        ).forEach { assertFalse(it, URI(it).isAllowedPeugeotLoginPage()) }
    }
}
