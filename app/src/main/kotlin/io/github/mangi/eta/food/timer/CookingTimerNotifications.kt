package io.github.mangi.eta.food.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.mangi.eta.R
import io.github.mangi.eta.data.db.CookingSessionEntity
import io.github.mangi.eta.data.db.CookingTimerEntity
import io.github.mangi.eta.food.FoodContract
import io.github.mangi.eta.food.RecipeDefinition
import io.github.mangi.eta.ui.MainActivity

internal class CookingTimerNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun timerFinished(timer: CookingTimerEntity) {
        createChannels()
        val notification = NotificationCompat.Builder(context, TIMER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(timer.label)
            .setContentText(context.getString(R.string.food_timer_finished))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openSession(timer.sessionId))
            .addAction(0, context.getString(R.string.food_timer_add_minute),
                CookingTimerReceiver.pendingIntent(context, CookingTimerReceiver.ADD_MINUTE, timer.id))
            .addAction(0, context.getString(R.string.food_timer_stop),
                CookingTimerReceiver.pendingIntent(context, CookingTimerReceiver.STOP, timer.id))
            .build()
        post(timer.id, notification)
    }

    fun clearTimer(id: String) = manager.cancel(id, NOTIFICATION_ID)

    fun showActiveSession(session: CookingSessionEntity?) {
        if (session == null) {
            manager.cancel(SESSION_TAG, NOTIFICATION_ID)
            return
        }
        createChannels()
        val recipe = FoodContract.json.decodeFromString<RecipeDefinition>(session.recipeSnapshotJson)
        val notification = NotificationCompat.Builder(context, SESSION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.food_cooking_in_progress))
            .setContentText(recipe.title)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openSession(session.id))
            .build()
        post(SESSION_TAG, notification)
    }

    private fun post(tag: String, notification: Notification) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && manager.areNotificationsEnabled()
        ) {
            manager.notify(tag, NOTIFICATION_ID, notification)
        }
    }

    private fun openSession(id: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_COOKING_SESSION)
            .setData("jeancalcul://cooking-session/$id".toUri())
            .putExtra(MainActivity.EXTRA_COOKING_SESSION_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun createChannels() {
        val timer = NotificationChannel(TIMER_CHANNEL, context.getString(R.string.food_timer_channel), NotificationManager.IMPORTANCE_HIGH)
            .apply {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
                enableVibration(true)
            }
        val session = NotificationChannel(
            SESSION_CHANNEL, context.getString(R.string.food_cooking_in_progress), NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannels(listOf(timer, session))
    }

    companion object {
        const val TIMER_CHANNEL = "cooking_timers"
        const val SESSION_CHANNEL = "cooking_sessions"
        private const val SESSION_TAG = "active_cooking_session"
        private const val NOTIFICATION_ID = 1

        fun isOwnCookingNotification(ownPackage: String, sourcePackage: String, channelId: String?): Boolean =
            ownPackage == sourcePackage && (channelId == TIMER_CHANNEL || channelId == SESSION_CHANNEL)
    }
}
