package fr.loevan.jeancalcul.v2

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.BufferedInputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/** Browser callback only. Binds IPv4 loopback, not the LAN, and never logs a request. */
internal class LoopbackOAuth : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 1000 }
    val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"
    override fun close() { server.close() }
    suspend fun await(attempt: OAuthAttempt): OAuthCode {
        try {
            while (System.currentTimeMillis() < attempt.expiresAt) {
                currentCoroutineContext().ensureActive()
                val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                socket.use {
                    it.soTimeout = 3000
                    val input = BufferedInputStream(it.getInputStream())
                    var bytes = 0
                    fun line(): String {
                        val result = StringBuilder()
                        while (true) {
                            val next = input.read()
                            if (next < 0 || ++bytes > 16_384) authFailed()
                            if (next == 10) return result.toString().removeSuffix("\r")
                            result.append(next.toChar())
                        }
                    }
                    val request = line().split(' ')
                    if (request.size != 3 || request[0] != "GET" || !request[1].startsWith("/")) authFailed()
                    var headers = 0
                    while (line().isNotEmpty()) if (++headers > 64) authFailed()
                    val url = "http://127.0.0.1:${server.localPort}${request[1]}".toHttpUrl()
                    if (url.encodedPath != "/auth/callback") {
                        it.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        continue
                    }
                    val result = runCatching { attempt.validateCallback(url) }
                    val page = if (result.isSuccess) "Autorisation reçue. Revenez dans Jean-Calcul pour terminer la connexion."
                        else "Connexion refusée. Revenez dans Jean-Calcul et réessayez."
                    val body = "<!doctype html><meta charset=utf-8><title>Jean-Calcul</title><p>$page</p>".toByteArray(Charsets.UTF_8)
                    val status = if (result.isSuccess) "200 OK" else "400 Bad Request"
                    val header = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().apply { write(header.toByteArray()); write(body); flush() }
                    return result.getOrThrow()
                }
            }
            authFailed()
        } finally { close() }
    }
}
