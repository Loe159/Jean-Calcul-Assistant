package io.github.mangi.eta.agent.voice

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.service.voice.VoiceInteractionSession
import io.github.mangi.eta.agent.media.AgentImageCodec
import io.github.mangi.eta.agent.model.AgentModelClient
import io.github.mangi.eta.agent.model.AssistantScreenContextProjection
import io.github.mangi.eta.core.AndroidAgentLogger
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Session 与浮窗同进程共享一次唤醒的快照，关闭或替换后不再接纳迟到结果。 */
internal class EtaAssistantScreenContext(
    showFlags: Int,
    private val foregroundApps: List<ComponentName>,
) {
    val id: String = UUID.randomUUID().toString()
    private val capturedAt = System.currentTimeMillis()
    private val startedAt = SystemClock.elapsedRealtime()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ready = CompletableDeferred<Unit>()
    private val activities = mutableMapOf<Int, Pair<Boolean, String>>()
    private val receivedActivities = mutableSetOf<Int>()
    private var expectedActivities = 1
    private var pendingActivities = 0
    private var assistComplete = showFlags and VoiceInteractionSession.SHOW_WITH_ASSIST == 0
    private var screenshotComplete = showFlags and VoiceInteractionSession.SHOW_WITH_SCREENSHOT == 0
    private var screenshotReceived = false
    private var image: AgentModelClient.ModelImage? = null
    private var closed = false
    private var ownScreen = ""
    private var ownText = ""
    private var privateScreen = false
    private var fallbackImage: AgentModelClient.ModelImage? = null
    private var fallbackJob: kotlinx.coroutines.Job? = null

    fun acceptOwnApp(capture: io.github.mangi.eta.agent.development.OwnAppScreenContext.Capture) {
        ownScreen = capture.screen
        ownText = capture.text
        privateScreen = capture.sensitive
        val bitmap = capture.bitmap ?: return
        fallbackJob = scope.launch {
            try {
                val encoded = runCatching {
                    capture.ready?.await()
                    AgentImageCodec.fromScreenContextBitmap(bitmap, source = "jeancalcul_screen_context")
                }.getOrNull()
                synchronized(this@EtaAssistantScreenContext) { if (!closed) fallbackImage = encoded }
            } finally {
                // Joining this job must also observe cleanup, not race a completion callback.
                capture.release()
            }
            // A job cancelled before it starts still has to release the copy destination.
        }.also { job -> job.invokeOnCompletion { capture.release() } }
    }


    init { completeIfReady() }

    fun acceptAssist(context: Context, state: VoiceInteractionSession.AssistState) {
        if (privateScreen) { synchronized(this) { assistComplete = true; completeIfReady() }; return }
        synchronized(this) {
            if (closed || state.index in receivedActivities) return
            if (receivedActivities.size >= MAX_ACTIVITIES && !state.isFocused) return
            receivedActivities += state.index
            expectedActivities = maxOf(expectedActivities, state.count.coerceIn(1, MAX_ACTIVITIES))
            pendingActivities++
        }
        scope.launch {
            val text = try {
                EtaAssistContentReader.read(context, state)
            } catch (exception: Exception) {
                AndroidAgentLogger.warn("Assistant app context parsing failed: type=${exception.javaClass.simpleName}")
                "Could not read app content"
            }
            synchronized(this@EtaAssistantScreenContext) {
                if (closed) return@synchronized
                activities[state.index] = state.isFocused to text
                pendingActivities--
                assistComplete = pendingActivities == 0 && receivedActivities.size >= expectedActivities
                completeIfReady()
            }
        }
    }

    fun acceptScreenshot(bitmap: Bitmap?) {
        if (privateScreen) { bitmap?.recycle(); synchronized(this) { screenshotComplete = true; completeIfReady() }; return }
        synchronized(this) {
            if (closed || screenshotReceived) {
                bitmap?.recycle()
                return
            }
            screenshotReceived = true
        }
        val job = scope.launch {
            val capturedImage = try {
                bitmap?.let { AgentImageCodec.fromScreenContextBitmap(it, source = "assistant_screen_context") }
            } catch (exception: Exception) {
                AndroidAgentLogger.warn("Assistant screenshot encoding failed: type=${exception.javaClass.simpleName}")
                null
            }
            synchronized(this@EtaAssistantScreenContext) {
                if (closed) return@synchronized
                image = capturedImage
                screenshotComplete = true
                completeIfReady()
            }
        }
        // 即使任务在执行前取消，也要释放系统回调交付的 Bitmap。
        job.invokeOnCompletion { if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() }
    }

    suspend fun snapshot(): Snapshot {
        val remaining = (WAIT_MILLIS - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0)
        if (remaining > 0) withTimeoutOrNull(remaining) { ready.await() }
        withTimeoutOrNull(WAIT_MILLIS) { fallbackJob?.join() }
        return synchronized(this) {
            if (closed) return@synchronized Snapshot()
            val chosenImage = if (privateScreen) null else image ?: fallbackImage
            val text = buildString {
                appendLine("Capture time (Unix milliseconds): $capturedAt; this is the screen at assistant invocation, not the live state after actions.")
                if (foregroundApps.isNotEmpty()) {
                    appendLine("Foreground app: " + foregroundApps.take(MAX_ACTIVITIES)
                        .joinToString { it.flattenToShortString().take(512) })
                }
                if (ownText.isNotEmpty()) appendLine(ownText)
                appendLine(if (chosenImage != null) "A screenshot is attached." else "The system did not provide a usable screenshot.")
                if (activities.isEmpty()) appendLine("The system has not provided app content.")
                activities.entries.sortedWith(compareByDescending<Map.Entry<Int, Pair<Boolean, String>>> { it.value.first }
                    .thenBy { it.key }).take(MAX_ACTIVITIES).forEach { appendLine(it.value.second) }
            }
            Snapshot(AssistantScreenContextProjection.bound(text), chosenImage, ownScreen)
        }
    }

    fun close() {
        synchronized(this) {
            closed = true
            image = null
            fallbackImage = null
            activities.clear()
            ready.complete(Unit)
        }
        scope.cancel()
    }

    private fun completeIfReady() {
        if (assistComplete && screenshotComplete) ready.complete(Unit)
    }

    data class Snapshot(val text: String = "", val image: AgentModelClient.ModelImage? = null, val appScreen: String = "")

    companion object {
        private const val MAX_ACTIVITIES = 4
        private const val WAIT_MILLIS = 2_000L
    }
}

/** 最多保留当前一次唤醒，Intent 只传 ID，不传图片或界面树。 */
internal object EtaAssistantScreenContexts {
    private var current: EtaAssistantScreenContext? = null

    @Synchronized
    fun begin(flags: Int, foregroundApps: List<ComponentName>): EtaAssistantScreenContext {
        current?.close()
        return EtaAssistantScreenContext(flags, foregroundApps).also { current = it }
    }

    @Synchronized
    fun find(id: String?): EtaAssistantScreenContext? = current?.takeIf { it.id == id }

    @Synchronized
    fun release(id: String?) {
        if (current?.id != id) return
        current?.close()
        current = null
    }
}
