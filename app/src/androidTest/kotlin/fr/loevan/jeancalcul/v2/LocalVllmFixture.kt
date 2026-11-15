package fr.loevan.jeancalcul.v2

import android.content.Context
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/** Loopback-only synthetic transport in the test APK, never in the application APK. */
internal class LocalVllmFixture(context: Context) : AutoCloseable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    val requests = AtomicInteger()
    private val thread = Thread {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: Exception) { break }
            socket.use {
                val input = it.getInputStream().buffered()
                fun line(): String {
                    val result = StringBuilder()
                    while (true) { val c = input.read(); if (c == -1 || c == 10) break; if (c != 13) result.append(c.toChar()) }
                    return result.toString()
                }
                line()
                var length = 0
                while (true) {
                    val header = line(); if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                var remaining = length
                val buffer = ByteArray(8192)
                while (remaining > 0) { val n = input.read(buffer, 0, minOf(buffer.size, remaining)); if (n < 0) break; remaining -= n }
                requests.incrementAndGet()
                val output = it.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(("data: {\"id\":\"fixture\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Synthetic local reply\"},\"finish_reason\":null}]}\n\n" +
                    "data: {\"id\":\"fixture\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                    "data: [DONE]\n\n").toByteArray(StandardCharsets.UTF_8))
                output.flush()
            }
        }
    }.apply { isDaemon = true; start() }
    init {
        runBlocking {
            ProviderRepository.init(context)
            ProviderRepository.ensureBuiltInsMerged()
            val model = Model(id = "e2e-local-model", modelId = "synthetic", displayName = "Synthetic vLLM", toolCall = true)
            val provider = OpenAiCompatibleProviderSetting(id = "e2e-local", name = "vLLM synthetic",
                baseUrl = "http://127.0.0.1:${server.localPort}/v1", models = listOf(model))
            if (ProviderRepository.providerById(provider.id) == null) ProviderRepository.addProvider(provider)
            else ProviderRepository.updateProvider(provider)
            RuntimeConfigRepository.setSelectedProviderId(provider.id)
            RuntimeConfigRepository.setSelectedModelId(model.id)
        }
    }
    override fun close() { server.close(); thread.join(2000) }
}
