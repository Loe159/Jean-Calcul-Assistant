package io.github.mangi.eta.food.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class CookingTimerScheduler(private val context: Context, private val clock: Clock) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val exact = MutableStateFlow(alarms.canScheduleExactAlarms())
    val exactAlarmsAvailable = exact.asStateFlow()

    fun refreshPermission() {
        exact.value = alarms.canScheduleExactAlarms()
    }

    fun schedule(timerId: String, deadline: Long, allowEarlyWindow: Boolean = true) {
        val pending = pendingAlarm(timerId)
        refreshPermission()
        if (exact.value) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
                return
            } catch (_: SecurityException) {
                exact.value = false
            }
        }
        val earliest = maxOf(clock.millis(), if (allowEarlyWindow) deadline - WINDOW_RADIUS_MS else deadline)
        // Android may widen the requested window. The receiver also guards against early delivery.
        alarms.setWindow(AlarmManager.RTC_WAKEUP, earliest, maxOf(0, deadline + WINDOW_RADIUS_MS - earliest), pending)
    }

    fun cancel(timerId: String) {
        alarms.cancel(pendingAlarm(timerId))
    }

    private fun pendingAlarm(timerId: String): PendingIntent = CookingTimerReceiver.pendingIntent(
        context, CookingTimerReceiver.ALARM, timerId,
    )

    companion object {
        private const val WINDOW_RADIUS_MS = 60_000L
    }
}
