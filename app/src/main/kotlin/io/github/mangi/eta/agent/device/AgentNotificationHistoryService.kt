package io.github.mangi.eta.agent.device

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.agent.device.notifications.NotificationProtection
import io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterCandidate
import android.os.Handler
import android.os.Looper
import java.time.LocalTime

class AgentNotificationHistoryService : NotificationListenerService() {
    private val repository by lazy { NotificationHistoryRepository(this) }
    private val pending = mutableMapOf<String, Pending>()
    private val processed = LinkedHashMap<String, Long>()
    private data class Pending(
        val pkg: String, val label: String, val score: Double,
        val expected: NotificationFilterDecision,
    )


    override fun onNotificationPosted(sbn: StatusBarNotification) {
        record(sbn)
        NotificationObservation.observe(this, sbn)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedService = this
        activeNotifications?.forEach(::record)
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

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        val action = pending.remove(sbn.key) ?: return
        val confirmed = when (action.expected) {
            NotificationFilterDecision.DISMISS -> reason == REASON_LISTENER_CANCEL
            else -> reason == REASON_SNOOZED
        }
        if (confirmed) {
            NotificationObservation.recordAction(action.pkg, action.label, action.score, action.expected)
        }
    }

    private fun applyIfStillSafe(
        original: StatusBarNotification,
        fingerprint: String,
        scores: List<Pair<String, Double>>,
        desired: NotificationFilterDecision,
    ): Boolean {
        if (connectedService !== this || !isEnabled(this)) return false
        val current = runCatching { activeNotifications?.firstOrNull { it.key == original.key } }
            .getOrNull() ?: return false
        if (current.packageName != original.packageName ||
            current.postTime != original.postTime ||
            NotificationProtection.fingerprint(current) != fingerprint ||
            !NotificationProtection.safe(this, current)) return false
        // Never remove an urgent/alerting notification even if its NLI label is "advertisement".
        val ranking = Ranking()
        val rankAvailable = runCatching { currentRanking?.getRanking(current.key, ranking) == true }
            .getOrDefault(false)
        if (!rankAvailable || ranking.importance >= NotificationManager.IMPORTANCE_HIGH) return false

        val settings = NotificationFilterSettings.load(this)
        val newDecision = NotificationFilterPolicy.decide(settings, NotificationFilterCandidate(
            app = current.packageName, safe = true, scores = scores, localHour = LocalTime.now().hour,
        ))
        if (newDecision != desired) return false

        val token = original.key + ":" + fingerprint
        val now = android.os.SystemClock.elapsedRealtime()
        val existing = processed[token]
        if (existing != null && now - existing < 48L * 60 * 60 * 1000) return false
        // Store before the call; a synchronous onNotificationRemoved callback is possible.
        pending[original.key] = Pending(current.packageName, scores.first().first, scores.first().second, desired)
        return try {
            when (desired) {
                NotificationFilterDecision.SNOOZE_1H -> snoozeNotification(current.key, 60L * 60 * 1000)
                NotificationFilterDecision.SNOOZE_24H -> snoozeNotification(current.key, 24L * 60 * 60 * 1000)
                NotificationFilterDecision.DISMISS -> cancelNotification(current.key)
                else -> { pending.remove(original.key); return false }
            }
            processed[token] = now
            while (processed.size > 100) processed.remove(processed.keys.first())
            true
        } catch (_: RuntimeException) {
            pending.remove(original.key)
            false
        }
    }

    private fun record(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        repository.record(
            key = sbn.key,
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            postedAt = sbn.postTime,
        )
    }

    companion object {
        /**
         * All final checks and system actions execute on the listener's main thread.
         * A background inference can NEVER act on an updated/removed notification.
         */
        internal fun requestSafeFilter(
            sbn: StatusBarNotification, fingerprint: String,
            scores: List<Pair<String, Double>>, decision: NotificationFilterDecision,
            onResult: (Boolean) -> Unit,
        ) {
            Handler(Looper.getMainLooper()).post {
                val service = connectedService
                val completed = service?.applyIfStillSafe(sbn, fingerprint, scores, decision) ?: false
                onResult(completed)
            }
        }

        @Volatile
        private var connectedService: AgentNotificationHistoryService? = null

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
