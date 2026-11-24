package io.github.mangi.eta.food.timer

import androidx.room.withTransaction
import io.github.mangi.eta.data.db.CookingTimerEntity
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.food.CookingStatus
import io.github.mangi.eta.food.FoodContract
import io.github.mangi.eta.food.RecipeDefinition
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.food.nextRevision
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class CookingTimerManager(
    private val database: EtaDatabase,
    private val scheduler: CookingTimerScheduler,
    private val notifications: CookingTimerNotifications,
    private val clock: Clock,
    private val onSessionChanged: (String) -> Unit,
) {
    private val dao = database.foodDao()
    private val mutex = Mutex()
    val exactAlarmsAvailable = scheduler.exactAlarmsAvailable

    fun timers(sessionId: String) = dao.observeTimers(sessionId)

    suspend fun start(sessionId: String, stepId: String?, label: String, durationSeconds: Int): String = mutex.withLock {
        require(label.isNotBlank() && durationSeconds > 0)
        val timer = database.withTransaction {
            val session = requireNotNull(dao.session(sessionId))
            check(session.status == CookingStatus.ACTIVE)
            if (stepId != null) {
                val recipe = FoodContract.json.decodeFromString<RecipeDefinition>(session.recipeSnapshotJson)
                require(recipe.steps.any { it.id == stepId })
            }
            val now = clock.millis()
            val timer = CookingTimerEntity(
                "tmr_${UUID.randomUUID()}", sessionId, stepId, label, durationSeconds, TimerState.RUNNING,
                now + durationSeconds * 1_000L, null, now,
            )
            dao.insertTimer(timer)
            dao.updateSession(session.nextRevision(now))
            timer
        }
        scheduler.schedule(timer.id, checkNotNull(timer.endsAtEpochMs))
        onSessionChanged(sessionId)
        timer.id
    }

    suspend fun pause(id: String) = change(id) { timer, now ->
        if (timer.state == TimerState.RUNNING) {
            timer.copy(
                state = TimerState.PAUSED,
                remainingMs = (checkNotNull(timer.endsAtEpochMs) - now).coerceAtLeast(0),
                endsAtEpochMs = null,
            )
        } else timer
    }

    suspend fun resume(id: String) = change(id) { timer, now ->
        if (timer.state == TimerState.PAUSED) {
            timer.copy(state = TimerState.RUNNING, endsAtEpochMs = now + checkNotNull(timer.remainingMs), remainingMs = null)
        } else timer
    }

    suspend fun addTime(id: String, minutes: Int) {
        require(minutes == 1 || minutes == 5)
        val extra = minutes * 60_000L
        change(id) { timer, now ->
            when (timer.state) {
                TimerState.RUNNING -> timer.copy(endsAtEpochMs = maxOf(now, checkNotNull(timer.endsAtEpochMs)) + extra)
                TimerState.PAUSED -> timer.copy(remainingMs = checkNotNull(timer.remainingMs) + extra)
                TimerState.FINISHED -> timer.copy(state = TimerState.RUNNING, endsAtEpochMs = now + extra)
                TimerState.CANCELLED -> timer
            }
        }
    }

    suspend fun cancel(id: String) = change(id) { timer, _ ->
        timer.copy(state = TimerState.CANCELLED, endsAtEpochMs = null, remainingMs = null)
    }

    suspend fun reschedule() = mutex.withLock {
        scheduler.refreshPermission()
        for (timer in dao.runningTimers()) reconcile(timer.id, allowEarlyWindow = true)
    }

    suspend fun alarmDelivered(id: String) = mutex.withLock {
        reconcile(id, allowEarlyWindow = false)
    }

    private suspend fun reconcile(id: String, allowEarlyWindow: Boolean) {
        val timer = dao.timer(id) ?: return
        if (timer.state != TimerState.RUNNING) return
        val deadline = checkNotNull(timer.endsAtEpochMs)
        if (deadline > clock.millis()) {
            scheduler.schedule(id, deadline, allowEarlyWindow)
            return
        }
        val finished = persistChange(id) { row, _ -> row.copy(state = TimerState.FINISHED, endsAtEpochMs = null) }
        if (finished != null) {
            scheduler.cancel(id)
            notifications.timerFinished(finished)
            onSessionChanged(finished.sessionId)
        }
    }

    private suspend fun change(
        id: String,
        transform: (CookingTimerEntity, Long) -> CookingTimerEntity,
    ) = mutex.withLock {
        val timer = persistChange(id, transform) ?: return@withLock
        if (timer.state == TimerState.RUNNING) {
            scheduler.schedule(id, checkNotNull(timer.endsAtEpochMs))
        } else {
            scheduler.cancel(id)
        }
        notifications.clearTimer(id)
        onSessionChanged(timer.sessionId)
    }

    private suspend fun persistChange(
        id: String,
        transform: (CookingTimerEntity, Long) -> CookingTimerEntity,
    ): CookingTimerEntity? = database.withTransaction {
        val timer = dao.timer(id) ?: return@withTransaction null
        val now = clock.millis()
        val updated = transform(timer, now)
        if (updated == timer) return@withTransaction null
        dao.updateTimer(updated)
        val session = checkNotNull(dao.session(timer.sessionId))
        if (session.status == CookingStatus.ACTIVE) dao.updateSession(session.nextRevision(now))
        updated
    }
}
