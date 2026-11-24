package io.github.mangi.eta.food

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import androidx.room.Room
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.food.timer.CookingTimerManager
import io.github.mangi.eta.food.timer.CookingTimerNotifications
import io.github.mangi.eta.food.timer.CookingTimerReceiver
import io.github.mangi.eta.food.timer.CookingTimerScheduler
import io.github.mangi.eta.ui.MainActivityLaunchRequests
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class FoodAndroidSchedulingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val clock = TestClock()
    private lateinit var database: EtaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, EtaDatabase::class.java).build()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After fun tearDown() { database.close() }

    @Test
    @Suppress("DEPRECATION")
    fun scheduledOutboxJobIsPersistedAndRequiresNetwork() {
        FoodSyncJob.request(context)
        FoodSyncJob.request(context)
        val job = context.getSystemService(JobScheduler::class.java).allPendingJobs.single()
        assertEquals(FoodSyncJob::class.java.name, job.service.className)
        assertTrue(job.isPersisted)
        assertNotNull(job.requiredNetwork)
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, job.backoffPolicy)
        assertEquals(30_000L, job.initialBackoffMillis)
    }

    @Test
    @Suppress("DEPRECATION")
    fun timersAreIndependentAndOnlyRunningTimersAreRestored() = runBlocking {
        val repository = FoodRepository(database, { null }, MutableStateFlow(0L), this, {}, clock)
        val session = repository.startSession(recipeFixture())
        var manager = manager()
        val first = manager.start(session, "st_1", "Courgettes", 120)
        val second = manager.start(session, null, "Four", 180)
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertTrue(shadowOf(alarms).scheduledAlarms.all { it.isAllowWhileIdle && it.windowLengthMs == 0L })
        assertTrue(manager.exactAlarmsAvailable.value)

        clock.advance(30_000)
        manager.pause(first)
        manager.addTime(first, 5)
        assertEquals(390_000L, database.foodDao().timer(first)?.remainingMs)
        assertEquals(1, shadowOf(alarms).scheduledAlarms.size)
        manager.resume(first)
        assertEquals(clock.millis() + 390_000, database.foodDao().timer(first)?.endsAtEpochMs)
        manager.pause(second)
        manager.alarmDelivered(second)
        assertEquals(TimerState.PAUSED, database.foodDao().timer(second)?.state)
        assertEquals(2, repository.session(session).first()?.timers?.size)

        // Reboot clears AlarmManager but the Room state survives.
        alarms.cancelAll()
        manager = manager()
        manager.reschedule()
        val restored = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(database.foodDao().timer(first)?.endsAtEpochMs, restored.triggerAtMs)
        assertEquals(first, shadowOf(restored.operation).savedIntent.data?.lastPathSegment)

        clock.advance(390_000)
        manager.alarmDelivered(first)
        manager.alarmDelivered(first)
        assertEquals(TimerState.FINISHED, database.foodDao().timer(first)?.state)
        val alert = shadowOf(notifications).allNotifications.single()
        assertEquals(CookingTimerNotifications.TIMER_CHANNEL, alert.channelId)
        assertEquals(listOf("+1 min", "Arrêter"), alert.actions.map { it.title.toString() })
        val launch = MainActivityLaunchRequests().accept(shadowOf(alert.contentIntent).savedIntent)
        assertEquals(session, launch.cookingSessionId)
        val channel = notifications.getNotificationChannel(alert.channelId)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertTrue(channel.shouldVibrate())
        assertNotNull(channel.sound)

        manager.addTime(first, 1)
        assertEquals(clock.millis() + 60_000, database.foodDao().timer(first)?.endsAtEpochMs)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
        manager.cancel(first)
        assertEquals(TimerState.CANCELLED, database.foodDao().timer(first)?.state)
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())

        val surface = CookingTimerNotifications(context)
        surface.showActiveSession(database.foodDao().session(session))
        val ongoing = shadowOf(notifications).allNotifications.single()
        assertEquals(CookingTimerNotifications.SESSION_CHANNEL, ongoing.channelId)
        assertTrue(ongoing.flags and Notification.FLAG_ONGOING_EVENT != 0)
        surface.showActiveSession(null)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    @Suppress("DEPRECATION")
    fun inexactEarlyDeliveryNeverFinishesTimerAndOverdueTimerFinishesAfterReboot() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val repository = FoodRepository(database, { null }, MutableStateFlow(0L), this, {}, clock)
        val session = repository.startSession(recipeFixture())
        val manager = manager()
        val id = manager.start(session, null, "Four", 120)
        assertFalse(manager.exactAlarmsAvailable.value)

        val inexact = shadowOf(alarms).scheduledAlarms.single()
        assertEquals(clock.millis() + 60_000, inexact.triggerAtMs)
        assertEquals(120_000, inexact.windowLengthMs)
        assertFalse(inexact.isAllowWhileIdle)
        assertEquals(CookingTimerReceiver.ALARM, shadowOf(inexact.operation).savedIntent.action)

        clock.advance(60_000)
        manager.alarmDelivered(id)
        assertEquals(TimerState.RUNNING, database.foodDao().timer(id)?.state)
        assertEquals(clock.millis() + 60_000, shadowOf(alarms).scheduledAlarms.single().triggerAtMs)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())

        alarms.cancelAll()
        clock.advance(60_001)
        manager().reschedule()
        assertEquals(TimerState.FINISHED, database.foodDao().timer(id)?.state)
        assertEquals(1, shadowOf(notifications).allNotifications.size)
    }

    private fun manager() = CookingTimerManager(
        database, CookingTimerScheduler(context, clock), CookingTimerNotifications(context), clock, {},
    )

    private class TestClock : Clock() {
        private var now = Instant.parse("2026-11-23T18:00:00Z")
        fun advance(millis: Long) { now = now.plusMillis(millis) }
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = fixed(now, zone)
    }
}
