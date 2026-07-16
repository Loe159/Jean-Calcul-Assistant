package fr.loevan.jeancalcul.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InvocationPerformanceTraceTest {
    @Test
    fun `records each latency milestone once with monotonic deltas`() {
        val clock = FakeClock()
        val logs = mutableListOf<String>()
        val trace = trace(clock, logs)

        trace.startInvocation()
        clock.advanceBy(20)
        trace.mark(PerformanceTraceEvent.FIRST_FRAME)
        clock.advanceBy(30)
        trace.mark(PerformanceTraceEvent.MICROPHONE_READY)
        clock.advanceBy(40)
        trace.mark(PerformanceTraceEvent.SPEECH_STARTED)
        clock.advanceBy(50)
        trace.mark(PerformanceTraceEvent.FIRST_TRANSCRIPTION)
        trace.mark(PerformanceTraceEvent.FIRST_TRANSCRIPTION)
        clock.advanceBy(60)
        trace.mark(PerformanceTraceEvent.FINAL_RESULT)
        clock.advanceBy(70)
        trace.mark(PerformanceTraceEvent.VOLUME_REQUESTED)
        clock.advanceBy(80)
        trace.mark(PerformanceTraceEvent.VOLUME_APPLIED)

        assertEquals(8, logs.size)
        assertTrue(logs[1].contains("event=first_frame"))
        assertTrue(logs[1].contains("elapsed_ms=20"))
        assertTrue(logs[4].contains("speech_elapsed_ms=50"))
        assertTrue(logs[7].contains("volume_elapsed_ms=80"))
    }

    @Test
    fun `captures process memory without an active invocation`() {
        val logs = mutableListOf<String>()
        val trace = trace(FakeClock(), logs)

        trace.captureMemory("service_ready")

        assertEquals(
            "performance_memory checkpoint=service_ready invocation_id=none process=assistant pid=42 total_pss_kb=1234",
            logs.single(),
        )
    }

    private fun trace(
        clock: FakeClock,
        logs: MutableList<String>,
    ): InvocationPerformanceTrace =
        InvocationPerformanceTrace(
            clock = clock,
            memorySnapshot = { ProcessMemorySnapshot("assistant", 42, 1234) },
            sink = PerformanceTraceSink(logs::add),
            invocationIdFactory = { "invocation-1" },
        )

    private class FakeClock : ElapsedRealtimeClock {
        private var now = 0L

        override fun nowMillis(): Long = now

        fun advanceBy(millis: Long) {
            now += millis
        }
    }
}
