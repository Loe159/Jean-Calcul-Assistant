package io.github.mangi.eta.agent.device

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationProtection
import io.github.mangi.eta.agent.device.notifications.remote.MastraConnection
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.AndroidActionState
import java.util.concurrent.Executors

/**
 * Single Android entry point for notifications. It no longer classifies anything:
 * 1. save locally, 2. queue for Mastra, 3. (later) receive the decision,
 * 4. re-check native protections on the main thread, 5. act, 6. confirm via callback.
 */
class AgentNotificationHistoryService : NotificationListenerService() {
    private val repository by lazy { NotificationHistoryRepository(this) }
    private val pending = mutableMapOf<String, Long>()
    private val processed = LinkedHashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val snapshot = Snapshot.of(this, sbn)
        // SQLite work never runs on the listener's main thread.
        io.execute { if (record(snapshot)) MastraNotificationSync.request(applicationContext) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedService = this
        val active = activeNotifications.orEmpty().map { Snapshot.of(this, it) }
        io.execute {
            // Replays of unchanged notifications keep their state and are not re-sent.
            val queued = active.count { record(it) }
            if (queued > 0 || MastraConnection.enabled(this)) MastraNotificationSync.request(applicationContext)
            NotificationActivityFeed.notifyChanged()
        }
    }

    override fun onListenerDisconnected() {
        pending.clear()
        processed.clear()
        if (connectedService === this) connectedService = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        pending.clear()
        processed.clear()
        if (connectedService === this) connectedService = null
        super.onDestroy()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val postedAt = pending.remove(sbn.key) ?: return
        if (postedAt != sbn.postTime) return
        // Mastra's DISMISS is not proof of removal: only this callback is.
        val state = if (reason == REASON_LISTENER_CANCEL) AndroidActionState.CONFIRMED else AndroidActionState.NONE
        io.execute {
            if (repository.setAndroidActionState(sbn.key, sbn.postTime, state,
                    if (state == AndroidActionState.CONFIRMED) "Retirée des notifications Android" else null))
                NotificationActivityFeed.notifyChanged()
        }
    }

    /** Main thread only. Every check is repeated here, against the live notification. */
    private fun dismissIfStillSafe(target: NotificationHistoryRepository.RemoteTarget): AndroidActionState {
        if (connectedService !== this || !isEnabled(this)) return AndroidActionState.FAILED
        if (NotificationFilterSettings.load(this).mode != NotificationFilterMode.PERSONALIZED)
            return AndroidActionState.SKIPPED_NO_CONSENT
        val current = runCatching { activeNotifications?.firstOrNull { it.key == target.key } }.getOrNull()
            ?: return AndroidActionState.SKIPPED_STALE
        // A late decision never acts on a replaced or updated notification.
        if (current.packageName != target.packageName || current.postTime != target.postedAt ||
            NotificationProtection.fingerprint(current) != target.fingerprint) return AndroidActionState.SKIPPED_STALE
        if (!NotificationProtection.remoteDismissAllowed(this, current)) return AndroidActionState.SKIPPED_PROTECTED
        val token = target.key + ":" + target.fingerprint
        val now = android.os.SystemClock.elapsedRealtime()
        if (processed[token]?.let { now - it < 48L * 60 * 60 * 1000 } == true) return AndroidActionState.SKIPPED_STALE
        // Stored before the call: onNotificationRemoved may run synchronously.
        pending[current.key] = current.postTime
        return try {
            cancelNotification(current.key)
            processed[token] = now
            while (processed.size > 200) processed.remove(processed.keys.first())
            AndroidActionState.REQUESTED
        } catch (_: RuntimeException) {
            pending.remove(current.key)
            AndroidActionState.FAILED
        }
    }

    private fun record(s: Snapshot): Boolean {
        val changed = repository.record(
            key = s.key, packageName = s.packageName, title = s.title, text = s.text, subText = s.subText,
            postedAt = s.postTime, channelId = s.channelId, androidCategory = s.category, fingerprint = s.fingerprint,
            meta = s.meta, queueForRemote = MastraConnection.enabled(this),
            conversationTitle = s.conversationTitle,
        )
        if (changed) NotificationActivityFeed.notifyChanged()
        return changed
    }

    /** Immutable copy taken on the listener thread; the StatusBarNotification is not shared across threads. */
    private data class Snapshot(
        val key: String, val packageName: String, val title: String?, val text: String?, val subText: String?,
        val postTime: Long, val channelId: String?, val category: String?, val fingerprint: String,
        val meta: NotificationHistoryRepository.StructuralMeta,
        val conversationTitle: String?,
    ) {
        companion object {
            fun of(context: Context, sbn: StatusBarNotification): Snapshot {
                val n = sbn.notification
                val extras = n.extras
                val app = runCatching { context.packageManager.getApplicationInfo(sbn.packageName, 0) }.getOrNull()
                val meta = NotificationHistoryRepository.StructuralMeta(
                    clearable = sbn.isClearable,
                    ongoing = n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
                    groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                    hasRemoteInput = n.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true,
                    conversation = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.isNotEmpty() == true ||
                        n.shortcutId != null || extras.containsKey(Notification.EXTRA_PEOPLE_LIST),
                    systemApp = app != null && app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    appCategory = when (app?.category) {
                        ApplicationInfo.CATEGORY_GAME -> "GAME"; ApplicationInfo.CATEGORY_AUDIO -> "AUDIO"
                        ApplicationInfo.CATEGORY_VIDEO -> "VIDEO"; ApplicationInfo.CATEGORY_IMAGE -> "IMAGE"
                        ApplicationInfo.CATEGORY_SOCIAL -> "SOCIAL"; ApplicationInfo.CATEGORY_NEWS -> "NEWS"
                        ApplicationInfo.CATEGORY_MAPS -> "MAPS"; ApplicationInfo.CATEGORY_PRODUCTIVITY -> "PRODUCTIVITY"
                        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "ACCESSIBILITY"; else -> null
                    },
                    appLabel = app?.let { runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull() },
                )
                return Snapshot(sbn.key, sbn.packageName, extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(), sbn.postTime, n.channelId, n.category,
                    NotificationProtection.fingerprint(sbn), meta,
                    extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString())
            }
        }
    }

    companion object {
        private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "notification-history") }

        @Volatile
        private var connectedService: AgentNotificationHistoryService? = null

        /** Called by the sync job after storing a Mastra DISMISS recommendation. */
        internal fun requestRemoteDismiss(context: Context, target: NotificationHistoryRepository.RemoteTarget) {
            Handler(Looper.getMainLooper()).post {
                val outcome = connectedService?.dismissIfStillSafe(target) ?: AndroidActionState.FAILED
                val reason = when (outcome) {
                    AndroidActionState.SKIPPED_PROTECTED -> "Conservée : protection Android"
                    AndroidActionState.SKIPPED_STALE -> "Conservée : notification modifiée ou disparue"
                    AndroidActionState.SKIPPED_NO_CONSENT -> "Conservée : retrait automatique désactivé"
                    AndroidActionState.FAILED -> "Conservée : retrait impossible"
                    else -> null
                }
                io.execute {
                    if (NotificationHistoryRepository(context).setAndroidActionState(target.key, target.postedAt, outcome, reason))
                        NotificationActivityFeed.notifyChanged()
                }
            }
        }

        /** The legacy on-device classifier is retired from normal operation and can no longer act. */
        @Suppress("UNUSED_PARAMETER")
        internal fun requestSafeFilter(
            sbn: StatusBarNotification, fingerprint: String,
            scores: List<Pair<String, Double>>, decision: NotificationFilterDecision,
            onResult: (Boolean) -> Unit,
        ) = onResult(false)

        internal fun currentNotifications(): Array<StatusBarNotification>? {
            val service = connectedService ?: return null
            return try {
                service.activeNotifications
            } catch (_: RuntimeException) {
                null
            }
        }

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
                ?: return false
            return manager.isNotificationListenerAccessGranted(
                ComponentName(context, AgentNotificationHistoryService::class.java),
            )
        }
    }
}
