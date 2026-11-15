package io.github.mangi.eta.agent.web

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

internal class WebRequestException(val code: String, message: String, val status: Int? = null) : IOException(message)

internal data class WebHttpResponse(
    val requestedUrl: String,
    val finalUrl: String,
    val status: Int,
    val contentType: String?,
    val bytes: ByteArray,
    val truncated: Boolean,
)

/** 独立匿名 GET 请求，不继承 WebView 登录状态；关闭只取消本次运行拥有的请求。 */
internal class WebHttpTransport(baseClient: OkHttpClient) : Closeable {
    private val client = baseClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .authenticator(okhttp3.Authenticator.NONE)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val closed = AtomicBoolean(false)
    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    fun ensureActive() {
        if (closed.get() || Thread.currentThread().isInterrupted) throw WebRequestException("WEB_CANCELLED", "Web request canceled")
    }

    fun get(rawUrl: String, maxBytes: Int = MAX_RESPONSE_BYTES): WebHttpResponse =
        request(rawUrl = rawUrl, method = "GET", maxBytes = maxBytes)

    fun request(
        rawUrl: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null,
        maxBytes: Int = MAX_RESPONSE_BYTES,
    ): WebHttpResponse {
        require(maxBytes in 1..MAX_RESPONSE_BYTES)
        val normalizedMethod = method.trim().uppercase(Locale.ROOT)
        if (normalizedMethod != "GET" && normalizedMethod != "POST") {
            throw WebRequestException("INVALID_ARGUMENT", "Only GET or POST methods are supported")
        }
        val initial = validateUrl(rawUrl)
        var url = initial
        var currentMethod = normalizedMethod
        var currentBody = if (normalizedMethod == "POST") body ?: byteArrayOf() else null
        val visited = hashSetOf<String>()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            ensureActive()
            if (!visited.add("$currentMethod:${url}")) throw WebRequestException("WEB_REDIRECT_LOOP", "Web redirect loop detected")
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw WebRequestException("WEB_TIMEOUT", "Web request timed out")
            val sameOrigin = url.scheme == initial.scheme && url.host == initial.host && url.port == initial.port
            val requestBuilder = Request.Builder().url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .header("Accept", DEFAULT_ACCEPT)
            headers.forEach { (rawName, rawValue) ->
                val name = rawName.trim()
                val value = rawValue.trim()
                if (name.isEmpty()) return@forEach
                validateHeader(name, value)
                if (!sameOrigin && isSensitiveHeader(name)) return@forEach
                requestBuilder.header(name, value)
            }
            if (currentMethod == "POST") {
                val mediaType = (contentType ?: "application/json; charset=utf-8").toMediaTypeOrNull()
                requestBuilder.post((currentBody ?: byteArrayOf()).toRequestBody(mediaType))
            } else {
                requestBuilder.get()
            }
            val request = requestBuilder.build()
            val call = client.newCall(request)
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
            activeCalls += call
            if (closed.get()) call.cancel()
            try {
                call.execute().use { response ->
                    ensureActive()
                    if (response.code in REDIRECT_STATUS) {
                        if (redirectCount == MAX_REDIRECTS) throw WebRequestException("WEB_REDIRECT_LIMIT", "Web redirect limit exceeded")
                        val location = response.header("Location")
                            ?: throw WebRequestException("WEB_REDIRECT_INVALID", "Web redirect has no target URL")
                        val resolved = url.resolve(location)
                            ?: throw WebRequestException("WEB_REDIRECT_INVALID", "Invalid web redirect URL")
                        url = validateUrl(resolved.toString())
                        if (response.code in setOf(301, 302, 303) && currentMethod == "POST") {
                            currentMethod = "GET"
                            currentBody = null
                        }
                    } else {
                        val status = response.code
                        // 错误响应不读取正文，避免认证信息和服务器回显进入日志或错误说明。
                        if (!response.isSuccessful) return WebHttpResponse(initial.toString(), url.toString(), status, response.header("Content-Type"), byteArrayOf(), false)
                        val responseBody = response.body
                        val output = ByteArrayOutputStream(minOf(maxBytes, 16_384))
                        var truncated = false
                        responseBody.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                ensureActive()
                                val capacity = maxBytes - output.size()
                                val count = input.read(buffer, 0, minOf(buffer.size, capacity + 1))
                                if (count < 0) break
                                if (count > capacity) {
                                    output.write(buffer, 0, capacity)
                                    truncated = true
                                    break
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                        ensureActive()
                        return WebHttpResponse(initial.toString(), url.toString(), status, response.header("Content-Type"), output.toByteArray(), truncated)
                    }
                }
            } catch (failure: WebRequestException) {
                throw failure
            } catch (_: InterruptedIOException) {
                ensureActive()
                throw WebRequestException("WEB_TIMEOUT", "Web connection or read timed out")
            } catch (_: ProtocolException) {
                ensureActive()
                throw WebRequestException("WEB_PROTOCOL_ERROR", "Server returned an unsupported HTTP response")
            } catch (_: IOException) {
                ensureActive()
                throw WebRequestException("WEB_NETWORK_ERROR", "Could not complete the web request. Check network, certificates and network permissions.")
            } finally {
                activeCalls -= call
            }
        }
        throw WebRequestException("WEB_REDIRECT_LIMIT", "Web redirect limit exceeded")
    }

    override fun close() {
        closed.set(true)
        activeCalls.forEach(Call::cancel)
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Jean-Calcul/1.0 Mobile Safari/537.36"
        private const val DEFAULT_ACCEPT =
            "text/html,application/xhtml+xml,text/plain,text/markdown,application/json;q=0.9,*/*;q=0.1"
        private val REDIRECT_STATUS = setOf(301, 302, 303, 307, 308)

        fun validateUrl(raw: String): HttpUrl {
            if (raw.isBlank() || raw.length > 8192 || raw.any { it.isISOControl() }) {
                throw WebRequestException("INVALID_URL", "URL is empty, too long or contains invalid characters")
            }
            val url = raw.toHttpUrlOrNull() ?: throw WebRequestException("INVALID_URL", "Only complete HTTP(S) URLs are supported")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
                throw WebRequestException("URL_CREDENTIALS_UNSUPPORTED", "This tool does not accept URLs containing usernames or passwords")
            }
            return url.newBuilder().fragment(null).build().also {
                if (it.toString().length > 8192) throw WebRequestException("INVALID_URL", "Encoded URL exceeds the length limit")
            }
        }

        private fun validateHeader(name: String, value: String) {
            if (name.length > 128 || value.length > 4096 ||
                name.any { it.isISOControl() || it == ':' || it.isWhitespace() } ||
                value.any { it == '\r' || it == '\n' || it == '\u0000' }
            ) {
                throw WebRequestException("INVALID_ARGUMENT", "Header name or value contains invalid characters")
            }
        }

        private fun isSensitiveHeader(name: String): Boolean {
            val lower = name.lowercase(Locale.ROOT)
            return lower == "authorization" || lower == "proxy-authorization" ||
                lower == "cookie" || lower == "x-api-key" || lower == "api-key" ||
                lower.contains("token") || lower.contains("secret") || lower.contains("key")
        }
    }
}
