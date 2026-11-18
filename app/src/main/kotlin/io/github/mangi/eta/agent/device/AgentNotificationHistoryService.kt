package io.github.mangi.eta.agent.device

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationObservation
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationProtection
import io.github.mangi.eta.agent.device.notifications.NotificationFilterDecision
import io.github.mangi.eta.agent.device.notifications.NotificationFilterPolicy
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import io.github.mangi.eta.agent.device.notifications.NotificationFilterCandidate
import io.github.mangi.eta.agent.device.notifications.NotificationAdaptiveSettings
import io.github.mangi.eta.agent.device.notifications.NotificationPersonalization
import io.github.mangi.eta.agent.device.notifications.NotificationGameIdentity
import io.github.mangi.eta.agent.device.notifications.NotificationGameSettings
import io.github.mangi.eta.agent.device.notifications.NotificationGamePolicy
import io.github.mangi.eta.agent.device.notifications.GameNotificationKind
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
        val postedAt: Long,
        val gameKind: GameNotificationKind? = null,
    )


    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (record(sbn) && !handleGameNotification(sbn)) NotificationObservation.observe(this, sbn)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectedService = this
        NotificationObservation.attachService(this)
        val active = activeNotifications.orEmpty()
        active.forEach { sbn ->
            // Game alerts use the deterministic fast path even after a service restart.
            if (record(sbn)) handleGameNotification(sbn)
        }
        // The old implementation left rows as RECEIVED when throttled and
        // silently lost work on process restart. Recover recent still-active
        // alerts, but never reprocess already classified or dismissed entries.
        NotificationObservation.recoverActive(this, active)
        NotificationActivityFeed.notifyChanged()
    }

    override fun onListenerDisconnected() {
        NotificationObservation.detachService()
        pending.clear()
        processed.clear()
        if (connectedService === this) connectedService = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        NotificationObservation.detachService()
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
        NotificationObservation.notificationGone(this, sbn)
        val action = pending.remove(sbn.key) ?: return
        val confirmed = when (action.expected) {
            NotificationFilterDecision.DISMISS -> reason == REASON_LISTENER_CANCEL
            else -> reason == REASON_SNOOZED
        }
        if (confirmed) {
            // The model's proposal is not proof of removal. Persist the state
            // only after Android confirms the actual listener action.
            NotificationHistoryRepository.confirmedState(action.expected)?.let { state ->
                repository.updateDecision(sbn.key, action.postedAt, state, action.label,
                    if (action.gameKind == null) action.score else null)
                NotificationActivityFeed.notifyChanged()
            }
            if (action.gameKind == null)
                NotificationObservation.recordAction(action.pkg, action.label, action.score, action.expected)
        }
    }

    /** Game rules are cheap, deterministic and do not load the 300 MB ONNX model. */
    private fun handleGameNotification(sbn: StatusBarNotification): Boolean {
        if (!NotificationGameIdentity.isGame(this, sbn.packageName)) return false
        val n = sbn.notification
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val kind = NotificationGamePolicy.classify(title, body, n.category)
        val config = NotificationFilterSettings.load(this)
        val preference = if (NotificationAdaptiveSettings.enabled(this))
            repository.interest(sbn.packageName, n.channelId, "games:${kind.name}", title, body,
                NotificationAdaptiveSettings.minExamples(this))
        else NotificationPersonalization.Interest.NEUTRAL
        val evaluation = NotificationGamePolicy.evaluate(config,
            io.github.mangi.eta.agent.device.notifications.NotificationFilterEvaluation(
                NotificationFilterDecision.KEEP, "Notification de jeu classée localement"),
            preference, gameApp = true, kind = kind,
            enabledKinds = NotificationGameSettings.enabledKinds(this), localHour = LocalTime.now().hour)
        val wantsRemoval = evaluation.decision == NotificationFilterDecision.DISMISS
        val isSafe = wantsRemoval && NotificationProtection.safe(this, sbn)
        repository.updateDecision(sbn.key, sbn.postTime,
            if (isSafe) NotificationHistoryRepository.DecisionState.PROPOSED
            else NotificationHistoryRepository.DecisionState.KEPT,
            label = "games:${kind.name}",
            reason = if (wantsRemoval && !isSafe) "Conservée : protection Android" else evaluation.reason,
            topic = "games", personalInterest = preference.name)
        NotificationActivityFeed.notifyChanged()
        if (isSafe) requestSafeGameFilter(sbn, NotificationProtection.fingerprint(sbn)) { applied ->
            if (!applied && repository.updateDecision(sbn.key, sbn.postTime,
                    NotificationHistoryRepository.DecisionState.KEPT,
                    reason = "Conservée : notification changée ou priorité élevée"))
                NotificationActivityFeed.notifyChanged()
        }
        return true
    }

    private fun applyIfStillSafe(
        original: StatusBarNotification,
        fingerprint: String,
        scores: List<Pair<String, Double>>,
        desired: NotificationFilterDecision,
        gameRule: Boolean = false,
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
        val gameKind: GameNotificationKind?
        val newDecision = if (gameRule) {
            if (!NotificationGameIdentity.isGame(this, current.packageName)) return false
            val n = current.notification
            val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            gameKind = NotificationGamePolicy.classify(title, body, n.category)
            val preference = if (NotificationAdaptiveSettings.enabled(this))
                repository.interest(current.packageName, n.channelId, "games:${gameKind.name}", title, body,
                    NotificationAdaptiveSettings.minExamples(this))
            else NotificationPersonalization.Interest.NEUTRAL
            NotificationGamePolicy.evaluate(settings,
                io.github.mangi.eta.agent.device.notifications.NotificationFilterEvaluation(
                    NotificationFilterDecision.KEEP, "Notification de jeu"),
                preference, gameApp = true, kind = gameKind,
                enabledKinds = NotificationGameSettings.enabledKinds(this),
                localHour = LocalTime.now().hour).decision
        } else {
            gameKind = null
            val base = NotificationFilterPolicy.evaluate(settings, NotificationFilterCandidate(
                app = current.packageName, safe = true, scores = scores, localHour = LocalTime.now().hour,
            ))
            val preference = if (NotificationAdaptiveSettings.enabled(this))
                repository.interest(current.packageName, current.notification.channelId,
                    scores.first().first, current.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    current.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
                    NotificationAdaptiveSettings.minExamples(this))
            else NotificationPersonalization.Interest.NEUTRAL
            NotificationPersonalization.safeDecision(base, preference, scores.first().first, settings).decision
        }
        if (newDecision != desired) return false

        val token = original.key + ":" + fingerprint
        val now = android.os.SystemClock.elapsedRealtime()
        val existing = processed[token]
        if (existing != null && now - existing < 48L * 60 * 60 * 1000) return false
        // Store before the call; a synchronous onNotificationRemoved callback is possible.
        pending[original.key] = Pending(current.packageName,
            if (gameRule) "games:${gameKind?.name}" else scores.first().first,
            if (gameRule) 0.0 else scores.first().second,
            desired, current.postTime, gameKind)
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

    private fun record(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val changed = repository.record(
            key = sbn.key,
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            postedAt = sbn.postTime,
            channelId = sbn.notification.channelId,
            androidCategory = sbn.notification.category,
            fingerprint = NotificationProtection.fingerprint(sbn),
        )
        if (changed) NotificationActivityFeed.notifyChanged()
        return changed
    }

    companion object {
        /**
         * All final checks and system actions execute on the listener's main thread.
         * A background inference can NEVER act on an updated/removed notification.
         */
        internal fun requestSafeGameFilter(
            sbn: StatusBarNotification, fingerprint: String,
            onResult: (Boolean) -> Unit,
        ) {
            Handler(Looper.getMainLooper()).post {
                val completed = connectedService?.applyIfStillSafe(sbn, fingerprint,
                    scores = emptyList(), desired = NotificationFilterDecision.DISMISS, gameRule = true)
                    ?: false
                onResult(completed)
            }
        }

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
