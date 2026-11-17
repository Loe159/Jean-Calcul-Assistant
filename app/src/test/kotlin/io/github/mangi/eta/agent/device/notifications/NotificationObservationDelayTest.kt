package io.github.mangi.eta.agent.device.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationObservationDelayTest {
    @Test fun firstEligibleNotificationRunsImmediately() {
        assertEquals(0L, notificationInferenceDelayMs(250L, 0L))
    }

    @Test fun otherNotificationsRespectTheOneMinuteInterval() {
        val last = 90_000L
        assertEquals(60_000L, notificationInferenceDelayMs(last, last))
        assertEquals(45_000L, notificationInferenceDelayMs(last + 15_000L, last))
        assertEquals(0L, notificationInferenceDelayMs(last + 60_000L, last))
        assertEquals(0L, notificationInferenceDelayMs(last + 70_000L, last))
    }

    @Test fun negativeElapsedAfterRebootNeverAllowsPrematureInference() {
        assertEquals(60_000L, notificationInferenceDelayMs(1_000L, 99_000L))
    }
}
