package io.github.mangi.eta.food.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.food.FoodRuntime

class CookingTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timerId = intent.data?.lastPathSegment ?: return
        val runtime = (context.applicationContext as EtaApp).food
        when (intent.action) {
            ALARM -> runFoodTask(runtime) { runtime.timers.alarmDelivered(timerId) }
            ADD_MINUTE -> runFoodTask(runtime) { runtime.timers.addTime(timerId, 1) }
            STOP -> runFoodTask(runtime) { runtime.timers.cancel(timerId) }
        }
    }

    companion object {
        internal const val ALARM = "fr.loevan.jeancalcul.v2.food.TIMER_ALARM"
        internal const val ADD_MINUTE = "fr.loevan.jeancalcul.v2.food.TIMER_ADD_MINUTE"
        internal const val STOP = "fr.loevan.jeancalcul.v2.food.TIMER_STOP"

        internal fun pendingIntent(context: Context, action: String, id: String): PendingIntent {
            val intent = Intent(context, CookingTimerReceiver::class.java)
                .setAction(action).setData("jeancalcul://cooking-timer/$id".toUri())
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

class CookingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        val runtime = (context.applicationContext as EtaApp).food
        runFoodTask(runtime) { runtime.restore() }
    }
}

private fun BroadcastReceiver.runFoodTask(runtime: FoodRuntime, work: suspend () -> Unit) {
    val pending = goAsync()
    runtime.launch { try { work() } finally { pending.finish() } }
}
