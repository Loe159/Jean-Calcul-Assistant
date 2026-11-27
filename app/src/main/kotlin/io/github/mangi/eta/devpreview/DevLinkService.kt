package io.github.mangi.eta.devpreview

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DevLinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val client = DevHubClient.configured()
        if (client == null || !enabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        startForeground(DevHubNotifications.SERVICE_ID, DevHubNotifications.foreground(this), type)
        if (pollJob?.isActive != true) pollJob = scope.launch { poll(client) }
        return START_STICKY
    }

    private suspend fun poll(client: DevHubClient) {
        val prefs = devHubPreferences(this)
        var cursor = prefs.getLong(CURSOR, -1)
        var backoff = 1_000L
        while (scope.isActive && enabled(this)) {
            try {
                if (cursor < 0) {
                    cursor = client.state().seq
                } else {
                    val events = client.events(cursor)
                    if (!enabled(this)) return
                    if (events.resync || events.seq < cursor || events.events.isNotEmpty()) {
                        val state = client.state()
                        if (!enabled(this)) return
                        if (!events.resync && events.seq >= cursor) {
                            for (event in events.events.filter { it.seq > cursor }.sortedBy { it.seq }) {
                                when (event.type) {
                                    "build" -> state.findBuild(event.id)?.let { DevHubNotifications.build(this, it) }
                                    "review" -> state.reviews.firstOrNull { it.id == event.id && it.isOpen }
                                        ?.let { DevHubNotifications.review(this, it) }
                                }
                            }
                        }
                        refreshSignal.value++
                        cursor = if (events.resync || events.seq < cursor) state.seq else events.seq
                    } else cursor = events.seq
                }
                if (prefs.getLong(CURSOR, -1) != cursor) prefs.edit().putLong(CURSOR, cursor).apply()
                backoff = 1_000L
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("DevLinkService", "Hub poll failed (${error.javaClass.simpleName})")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val LIVE_LINK = "live_link"
        private const val CURSOR = "event_cursor"
        internal val refreshSignal = MutableStateFlow(0L)

        internal fun enabled(context: Context) =
            BuildConfig.DEV_PREVIEW && devHubPreferences(context).getBoolean(LIVE_LINK, false)

        internal fun setEnabled(context: Context, enabled: Boolean): Boolean {
            if (!BuildConfig.DEV_PREVIEW) return false
            val prefs = devHubPreferences(context).edit().putBoolean(LIVE_LINK, enabled)
            if (!enabled) prefs.remove(CURSOR)
            prefs.apply()
            if (enabled) {
                if (startIfEnabled(context)) return true
                devHubPreferences(context).edit().putBoolean(LIVE_LINK, false).apply()
                return false
            }
            context.stopService(Intent(context, DevLinkService::class.java))
            return true
        }

        internal fun startIfEnabled(context: Context): Boolean {
            if (!enabled(context) || DevHubClient.configured() == null) return false
            return runCatching { context.startForegroundService(Intent(context, DevLinkService::class.java)) }
                .onFailure { Log.w("DevLinkService", "Could not start live link (${it.javaClass.simpleName})") }
                .isSuccess
        }
    }
}

internal object DevHubNotifications {
    private const val LIVE_CHANNEL = "dev_hub_live"
    private const val ALERT_CHANNEL = "dev_hub_alerts"
    const val SERVICE_ID = 7201
    private const val CONFIRM_ID = 7202
    private const val INSTALLED_ID = 7203
    private const val EVENT_ID = 7204

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun channels(context: Context) {
        manager(context).createNotificationChannels(listOf(
            NotificationChannel(LIVE_CHANNEL, context.getString(R.string.dev_hub_live_link), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(ALERT_CHANNEL, context.getString(R.string.dev_hub_title), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    private fun notification(context: Context, title: String, channel: String): NotificationCompat.Builder {
        channels(context)
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_dev_hub_notification)
            .setContentTitle(title)
    }

    private fun open(context: Context, section: String? = null, id: String? = null): PendingIntent {
        val intent = DevHubActivity.openIntent(context, section, id)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun foreground(context: Context): Notification =
        notification(context, context.getString(R.string.dev_hub_live_active), LIVE_CHANNEL)
            .setContentIntent(open(context)).setOngoing(true).build()

    fun build(context: Context, build: DevBuild) {
        val install = open(context, DevHubActivity.TARGET_INSTALL, build.info.id)
        val notification = notification(context, context.getString(R.string.dev_hub_new_build, build.info.branch), ALERT_CHANNEL)
            .setContentText(build.message)
            .setContentIntent(open(context, DevHubActivity.TARGET_BUILD, build.info.id))
            .addAction(0, context.getString(R.string.dev_hub_install), install)
            .setAutoCancel(true).build()
        post(context, EVENT_ID, "build:${build.info.id}", notification)
    }

    fun review(context: Context, review: DevReview) {
        val notification = notification(context, context.getString(R.string.dev_hub_new_review, review.title), ALERT_CHANNEL)
            .setContentIntent(open(context, DevHubActivity.TARGET_REVIEW, review.id))
            .setAutoCancel(true).build()
        post(context, EVENT_ID, "review:${review.id}", notification)
    }

    fun installed(context: Context, branch: String?) {
        val title = if (branch == null) context.getString(R.string.dev_hub_build_installed)
            else context.getString(R.string.dev_hub_branch_installed, branch)
        val launch = open(context)
        val notification = notification(context, title, ALERT_CHANNEL)
            .setContentIntent(launch).addAction(0, context.getString(R.string.dev_hub_open), launch)
            .setAutoCancel(true).build()
        post(context, INSTALLED_ID, null, notification)
    }

    fun confirmInstall(context: Context, confirmation: Intent) {
        val launch = PendingIntent.getActivity(context, CONFIRM_ID, confirmation, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = notification(context, context.getString(R.string.dev_hub_confirm_install), ALERT_CHANNEL)
            .setContentIntent(launch).setAutoCancel(true).build()
        post(context, CONFIRM_ID, null, notification)
    }

    fun clearConfirmation(context: Context) = manager(context).cancel(CONFIRM_ID)

    private fun post(context: Context, id: Int, tag: String?, notification: Notification) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager(context).notify(tag, id, notification)
        }
    }
}
