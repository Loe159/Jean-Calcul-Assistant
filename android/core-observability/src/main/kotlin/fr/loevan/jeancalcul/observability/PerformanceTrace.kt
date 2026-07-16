package fr.loevan.jeancalcul.observability

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.UUID

/** Milestones required to measure the phase-0 invocation path without logging spoken content. */
enum class PerformanceTraceEvent(
    val wireName: String,
) {
    FIRST_FRAME("first_frame"),
    MICROPHONE_READY("microphone_ready"),
    SPEECH_STARTED("speech_started"),
    FIRST_TRANSCRIPTION("first_transcription"),
    FINAL_RESULT("final_result"),
    VOLUME_REQUESTED("volume_requested"),
    VOLUME_APPLIED("volume_applied"),
}

/** Correlates performance markers emitted by one assistant invocation. */
interface PerformanceTrace {
    fun startInvocation()

    fun mark(event: PerformanceTraceEvent)

    fun captureMemory(checkpoint: String)

    fun finishInvocation(reason: String)
}

internal fun interface ElapsedRealtimeClock {
    fun nowMillis(): Long
}

internal data class ProcessMemorySnapshot(
    val processName: String,
    val processId: Int,
    val totalPssKb: Int?,
)

internal fun interface PerformanceTraceSink {
    fun log(message: String)
}

/** Pure trace formatter kept testable independently from Android system services. */
internal class InvocationPerformanceTrace(
    private val clock: ElapsedRealtimeClock,
    private val memorySnapshot: () -> ProcessMemorySnapshot,
    private val sink: PerformanceTraceSink,
    private val invocationIdFactory: () -> String,
) : PerformanceTrace {
    private var invocation: ActiveInvocation? = null

    override fun startInvocation() {
        val startedAt = clock.nowMillis()
        invocation = ActiveInvocation(id = invocationIdFactory(), startedAtMillis = startedAt)
        log("invocation_received", startedAt)
    }

    override fun mark(event: PerformanceTraceEvent) {
        val active = invocation ?: return
        if (!active.recordedEvents.add(event)) return

        val now = clock.nowMillis()
        when (event) {
            PerformanceTraceEvent.SPEECH_STARTED -> active.speechStartedAtMillis = now
            PerformanceTraceEvent.VOLUME_REQUESTED -> active.volumeRequestedAtMillis = now
            else -> Unit
        }
        log(event.wireName, now)
    }

    override fun captureMemory(checkpoint: String) {
        val snapshot = memorySnapshot()
        val invocationId = invocation?.id ?: "none"
        sink.log(
            "performance_memory checkpoint=$checkpoint invocation_id=$invocationId " +
                "process=${snapshot.processName} pid=${snapshot.processId} " +
                "total_pss_kb=${snapshot.totalPssKb ?: "unavailable"}",
        )
    }

    override fun finishInvocation(reason: String) {
        val active = invocation ?: return
        val now = clock.nowMillis()
        log("invocation_finished", now, "reason=$reason")
        invocation = null
    }

    private fun log(
        event: String,
        now: Long,
        extra: String = "",
    ) {
        val active = invocation ?: return
        val speechElapsed = active.speechStartedAtMillis?.let { now - it }
        val volumeElapsed = active.volumeRequestedAtMillis?.let { now - it }
        sink.log(
            buildString {
                append("performance_event event=$event invocation_id=${active.id} ")
                append("elapsed_ms=${now - active.startedAtMillis}")
                speechElapsed?.let { append(" speech_elapsed_ms=$it") }
                volumeElapsed?.let { append(" volume_elapsed_ms=$it") }
                if (extra.isNotBlank()) append(" $extra")
            },
        )
    }

    private data class ActiveInvocation(
        val id: String,
        val startedAtMillis: Long,
        val recordedEvents: MutableSet<PerformanceTraceEvent> = mutableSetOf(),
        var speechStartedAtMillis: Long? = null,
        var volumeRequestedAtMillis: Long? = null,
    )
}

/** Android adapter that writes redacted, machine-readable markers to Logcat. */
class AndroidPerformanceTrace(
    context: Context,
) : PerformanceTrace {
    private val appContext = context.applicationContext
    private val trace =
        InvocationPerformanceTrace(
            clock = ElapsedRealtimeClock(SystemClock::elapsedRealtime),
            memorySnapshot = ::readMemorySnapshot,
            sink = PerformanceTraceSink { message -> Log.i(LOG_TAG, message) },
            invocationIdFactory = { UUID.randomUUID().toString() },
        )

    override fun startInvocation() = trace.startInvocation()

    override fun mark(event: PerformanceTraceEvent) = trace.mark(event)

    override fun captureMemory(checkpoint: String) = trace.captureMemory(checkpoint)

    override fun finishInvocation(reason: String) = trace.finishInvocation(reason)

    private fun readMemorySnapshot(): ProcessMemorySnapshot {
        val processId = Process.myPid()
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val totalPssKb =
            activityManager
                ?.getProcessMemoryInfo(intArrayOf(processId))
                ?.firstOrNull()
                ?.totalPss
        return ProcessMemorySnapshot(
            processName = processName(),
            processId = processId,
            totalPssKb = totalPssKb,
        )
    }

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            appContext.packageName
        }

    private companion object {
        const val LOG_TAG = "JeanCalculPerf"
    }
}
