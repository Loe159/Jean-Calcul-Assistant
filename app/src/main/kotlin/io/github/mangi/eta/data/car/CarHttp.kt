package io.github.mangi.eta.data.car

import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.agent.model.AgentHttpClient
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class CarHttpFailure(val status: Int) : IOException("Car HTTP $status")

internal class CarHttpResponse(val status: Int, val body: String) {
    fun requireSuccess(): String = if (status in 200..299) body else throw CarHttpFailure(status)
}

internal class CarHttp(
    private val client: OkHttpClient = defaultCarHttpClient,
    private val now: () -> Instant = Instant::now,
    private val warn: (String) -> Unit = ::logCarWarning,
) {
    private val rateLimits = ConcurrentHashMap<String, Instant>()

    suspend fun <T> read(
        stage: CarReadStage,
        request: Request,
        sensitive: List<String?> = emptyList(),
        decode: (CarHttpResponse) -> T,
    ): T {
        var status: Int? = null
        var headers: Headers? = null
        var response: CarHttpResponse? = null
        try {
            val received = execute(request) { code, receivedHeaders ->
                status = code
                headers = receivedHeaders
            }
            response = received
            return decode(received)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = CarReadFailure(stage, status ?: (error as? CarHttpFailure)?.status, error)
            if (BuildConfig.DEBUG) warn(carReadDiagnostic(failure, request.url, headers, response?.body, sensitive))
            throw failure
        }
    }

    suspend fun execute(request: Request, onHeaders: (Int, Headers) -> Unit = { _, _ -> }): CarHttpResponse {
        val host = request.url.host
        if (rateLimits[host]?.isAfter(now()) == true) throw CarHttpFailure(429)
        val call = client.newCall(if (request.header("Accept") != null) request else request.newBuilder().header("Accept", "application/json").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            onHeaders(it.code, it.headers)
                            if (it.code == 429) {
                                val current = now()
                                val retryAt = retryAfter(it.header("Retry-After"), current)
                                    ?: it.header("X-RateLimit-Reset")?.toLongOrNull()
                                        ?.takeIf { epoch -> epoch in 0..253_402_300_799L }?.let(Instant::ofEpochSecond)
                                    ?: current.plus(Duration.ofMinutes(15))
                                rateLimits.merge(host, retryAt) { previous, next -> maxOf(previous, next) }
                            }
                            if (it.body.source().request(MAX_CAR_BODY_BYTES + 1)) {
                                throw SerializationException("Car response is too large")
                            }
                            CarHttpResponse(it.code, it.body.string())
                        }
                        continuation.resume(result)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}

private fun retryAfter(value: String?, now: Instant): Instant? {
    if (value == null) return null
    value.toLongOrNull()?.takeIf { it in 0..253_402_300_799L }?.let { return now.plusSeconds(it) }
    return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
}

private const val MAX_CAR_BODY_BYTES = 1_048_576L

private val defaultCarHttpClient by lazy {
    AgentHttpClient.client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}
