package io.github.mangi.eta.food

import android.content.Context
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.data.db.EtaDatabase
import io.github.mangi.eta.food.timer.CookingTimerManager
import io.github.mangi.eta.food.timer.CookingTimerNotifications
import io.github.mangi.eta.food.timer.CookingTimerScheduler
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Application-scoped wiring shared by the UI, receivers and JobService. */
internal class FoodRuntime(private val context: Context, private val scope: CoroutineScope) {
    private val database = EtaDatabase.get(context)
    private val clock = Clock.systemUTC()
    private val notifications = CookingTimerNotifications(context)
    val repository = FoodRepository(
        database,
        pairing = { MastraConnection.loadEnabled(context) },
        cachePairing = { MastraConnection.load(context) },
        pairingChanges = MastraConnection.pairingChanges,
        scope = scope,
        scheduleSync = { FoodSyncJob.request(context) },
        clock = clock,
    )
    val timers = CookingTimerManager(
        database, CookingTimerScheduler(context, clock), notifications, clock, repository::syncSession,
    )

    fun start() {
        launch {
            repository.activeSessions().map { it.firstOrNull() }
                .distinctUntilChangedBy { it?.id }
                .collect { notifications.showActiveSession(it) }
        }
        launch { MastraConnection.pairingChanges.collect { schedulePendingSync() } }
    }

    suspend fun restore() {
        timers.reschedule()
        notifications.showActiveSession(repository.activeSessions().first().firstOrNull())
        schedulePendingSync()
    }

    suspend fun schedulePendingSync() {
        if (repository.hasPendingSync()) FoodSyncJob.request(context)
    }

    fun launch(block: suspend () -> Unit) = scope.launch { block() }
}
