package fr.loevan.jeancalcul.v2

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

/**
 * Android's InetAddress resolver can fail while browser DNS works.
 * Only public OpenAI hosts get a TLS-protected DNS fallback.
 * TLS validation of the eventual OpenAI request remains unchanged.
 */
internal class FallbackOpenAiDns(
    private val systemDns: Dns,
    private val encryptedDns: Dns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = try {
        systemDns.lookup(hostname)
    } catch (error: UnknownHostException) {
        if (hostname != "auth.openai.com" && hostname != "api.openai.com") throw error
        encryptedDns.lookup(hostname)
    }
}

internal fun openAiDns(): Dns {
    // Bootstrap with literal addresses, so this lookup never depends on the
    // Android resolver that failed. The resolver's TLS hostname is still
    // verified by OkHttp; no certificate or hostname checks are bypassed.
    val bootstrap = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
    val encrypted = DnsOverHttps.Builder()
        .client(bootstrap)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByName("1.1.1.1"),
            InetAddress.getByName("1.0.0.1"),
            InetAddress.getByName("2606:4700:4700::1111"),
            InetAddress.getByName("2606:4700:4700::1001"),
        )
        .includeIPv6(true)
        .build()
    return FallbackOpenAiDns(Dns.SYSTEM, encrypted)
}
