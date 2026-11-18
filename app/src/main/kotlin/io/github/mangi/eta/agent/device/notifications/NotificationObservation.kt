package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import java.time.LocalTime
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Monotonic throttle, pure so it can be tested without Android lifecycle. */
internal fun notificationInferenceDelayMs(nowElapsed: Long, lastElapsed: Long): Long =
    if (lastElapsed == 0L) 0L
    else (12_000L - (nowElapsed - lastElapsed).coerceAtLeast(0L)).coerceAtLeast(0L)

/**
 * Event-driven local classifier: up to five classifications/minute and 500/day.
 * Notification texts remain on the device: retained for 7 days in the
 * existing private Now Brief history, with no cloud transmission.
 * Queued Android objects live only in memory. One delayed main-thread callback
 * is used for a bounded queue; no wake lock or permanent inference model.
 */
internal object NotificationObservation : NotificationBriefSource {
    // The notification listener is invoked on Android's main thread. The main
    // handler schedules only the next eligible inference, never background
    // polling. Raw pending StatusBarNotification objects live in RAM only.
    private val handler = Handler(Looper.getMainLooper())
    private val pending = LinkedHashMap<String, StatusBarNotification>()
    private var executing = false
    private var scheduled = false
    private val drainRunnable = Runnable {
        synchronized(this) { scheduled = false }
        drainNext()
    }
    private val executor by lazy {
        Executors.newSingleThreadExecutor { work ->
            Thread(work, "notification-local-inference").apply { isDaemon = true }
        }
    }
    private var lastExecution = 0L
    private var dailyCount = 0
    private var dayBucket = -1L
    private const val MAX_QUEUE = 64
    private const val MAX_PENDING_MS = 60 * 60_000L
    private val history = ArrayDeque<String>()
    private val recent = ArrayDeque<NotificationBriefEvent>()
    private val packages = LinkedHashSet<String>()
    private var reviewed = 0
    private var kept = 0
    private var proposed = 0
    private var skipped = 0
    private var requested = 0
    private var confirmed = 0
    private var snoozed = 0
    private var dismissed = 0
    private var errors = 0
    private var lastErrorType: String? = null
    private var filteredAds = 0
    private var filteredSpam = 0
    private val _summary = MutableStateFlow(NotificationBriefSnapshot())
    override val summary: StateFlow<NotificationBriefSnapshot> = _summary.asStateFlow()

    fun enabled(context: Context) =
        NotificationFilterSettings.load(context).mode != NotificationFilterMode.OFF

    fun setEnabled(context: Context, enabled: Boolean) =
        NotificationFilterSettings.setMode(context, if (enabled) NotificationFilterMode.OBSERVE else NotificationFilterMode.OFF)

    @Synchronized
    fun snapshot(): List<String> = history.toList()

    @Synchronized
    fun recentPackages(): List<String> = packages.toList()

    @Synchronized
    fun statistics(): String =
        "Analysées : ${reviewed + kept} · Conservées : $kept · Masquages demandés : $requested · " +
            "Confirmés : $confirmed · Ignorées : $skipped · Erreurs : $errors · En attente : ${pending.size}"

    @Synchronized
    fun diagnostics(context: Context): String {
        val config = NotificationFilterSettings.load(context)
        val ready = LocalNotificationModel.installed(context)
        val battery = isBatteryRestricted(context)
        return "Mode : ${config.mode} · Action : ${config.action} · " +
            "Catégories à filtrer : ${config.labelRules.count { it.enabled }}/${config.labelRules.size} · " +
            "Modèle : ${if (ready) "installé" else "absent"} · " +
            "Accès notifications : ${if (AgentNotificationHistoryService.isEnabled(context)) "accordé" else "manquant"} · " +
            "Batterie : ${if (battery) "analyse suspendue" else "OK"} · " +
            "File : ${pending.size} · Aujourd'hui : $dailyCount/500 · " +
            "Dernière erreur : ${lastErrorType ?: "aucune"}"
    }

    /** Publish only after classification or an action; ignored events do not wake the dashboard. */
    @Synchronized
    private fun publish() {
        _summary.value = NotificationBriefSnapshot(
            analyzed = reviewed + kept,
            kept = kept,
            proposed = proposed,
            requested = requested,
            snoozed = snoozed,
            dismissed = dismissed,
            skipped = skipped,
            errors = errors,
            filteredAds = filteredAds,
            filteredSpam = filteredSpam,
            events = recent.toList(),
        )
    }

    @Synchronized
    private fun append(
        packageName: String,
        category: String,
        score: Double,
        note: String,
        outcome: NotificationBriefOutcome,
    ) {
        // No title, body or notification key is retained in this dashboard projection.
        history.addFirst("$packageName · $category · ${String.format(Locale.FRANCE, "%.1f", score * 100)} % · $note")
        while (history.size > 50) history.removeLast()
        recent.addFirst(NotificationBriefEvent(
            packageName = packageName, category = category, score = score,
            outcome = outcome, timestampMs = System.currentTimeMillis(),
        ))
        while (recent.size > 50) recent.removeLast()
        if (packageName.length in 3..200 && packageName.contains('.')) {
            packages.add(packageName)
            while (packages.size > 60) packages.remove(packages.first())
        }
        publish()
    }

    @Synchronized
    fun recordAction(packageName: String, category: String, score: Double, action: NotificationFilterDecision) {
        confirmed++
        if (category == NotificationFilterPolicy.AD) filteredAds++
        if (category == NotificationFilterPolicy.SPAM) filteredSpam++
        when (action) {
            NotificationFilterDecision.SNOOZE_1H -> snoozed++
            NotificationFilterDecision.SNOOZE_24H -> snoozed++
            NotificationFilterDecision.DISMISS -> dismissed++
            else -> return
        }
        append(packageName, category, score, when (action) {
            NotificationFilterDecision.DISMISS -> "Suppression confirmée"
            else -> "Report confirmé"
        }, when (action) {
            NotificationFilterDecision.SNOOZE_1H -> NotificationBriefOutcome.SNOOZED_1H
            NotificationFilterDecision.SNOOZE_24H -> NotificationBriefOutcome.SNOOZED_24H
            else -> NotificationBriefOutcome.DISMISSED
        })
    }

    /** Called after the listener has persisted the received notification. */
    fun observe(context: Context, sbn: StatusBarNotification) {
        val config = NotificationFilterSettings.load(context)
        if (config.mode == NotificationFilterMode.OFF) return
        val app = context.applicationContext
        if (!LocalNotificationModel.installed(app)) {
            updateState(app, sbn, NotificationHistoryRepository.DecisionState.MODEL_MISSING)
            return
        }
        if (isBatteryRestricted(app)) {
            updateState(app, sbn, NotificationHistoryRepository.DecisionState.BATTERY_PAUSED)
            return
        }

        var overflow: StatusBarNotification? = null
        val accepted = synchronized(this) {
            refreshDay()
            if (dailyCount >= 500) false
            else {
                if (!pending.containsKey(sbn.key) && pending.size >= MAX_QUEUE) {
                    val oldestKey = pending.keys.first()
                    overflow = pending.remove(oldestKey)
                }
                pending[sbn.key] = sbn
                true
            }
        }
        overflow?.let { updateState(app, it, NotificationHistoryRepository.DecisionState.LIMIT_REACHED) }
        if (!accepted) {
            updateState(app, sbn, NotificationHistoryRepository.DecisionState.LIMIT_REACHED)
            synchronized(this) { skipped++; publish() }
            return
        }
        updateState(app, sbn, NotificationHistoryRepository.DecisionState.QUEUED)
        // The oldest eligible notification is analyzed immediately. Others
        // remain queued for their turn, at most one model load per minute.
        handler.post { drainNext() }
    }

    /** Do not leave queued rows stuck when Android/app removes their alert. */
    fun notificationGone(context: Context, sbn: StatusBarNotification) {
        // Do not dequeue it: its already-received local snapshot is still
        // eligible for classification, even though it can no longer be deleted.
        // The pending queue is bounded and has a fixed age limit.
    }

    private fun isBatteryRestricted(app: Context): Boolean {
        val power = app.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) return true
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return level >= 0 && scale > 0 && level * 100 / scale < 20
    }

    private fun updateState(
        app: Context, sbn: StatusBarNotification,
        state: NotificationHistoryRepository.DecisionState,
    ) {
        runCatching {
            if (NotificationHistoryRepository(app).updateDecision(sbn.key, sbn.postTime, state))
                NotificationActivityFeed.notifyChanged()
        }
    }

    /** A bounded, lifecycle-tied scheduler; never wakes the process on its own. */
    private fun drainNext() {
        val next: StatusBarNotification
        synchronized(this) {
            refreshDay()
            if (executing || pending.isEmpty()) return
            if (dailyCount >= 500) {
                val limited = pending.values.toList()
                pending.clear()
                serviceApp?.let { app ->
                    limited.forEach {
                        updateState(app, it, NotificationHistoryRepository.DecisionState.LIMIT_REACHED)
                    }
                }
                return
            }
            val waitMs = notificationInferenceDelayMs(SystemClock.elapsedRealtime(), lastExecution)
            if (waitMs > 0L) {
                if (!scheduled) {
                    scheduled = true
                    handler.postDelayed(drainRunnable, waitMs)
                }
                return
            }
            next = pending.remove(pending.keys.first())!!
            executing = true
        }

        val app = serviceApp
        if (app == null) {
            synchronized(this) { executing = false }
            return
        }
        // Analyze the received snapshot even if Android has since removed it.
        // Only a still-active, unchanged notification may be acted upon later.
        val live = AgentNotificationHistoryService.currentNotifications()
            ?.firstOrNull { it.key == next.key && it.postTime == next.postTime }
            ?.takeIf { NotificationProtection.fingerprint(it) == NotificationProtection.fingerprint(next) }
        val snapshot = live ?: next
        if (System.currentTimeMillis() - next.postTime > MAX_PENDING_MS) {
            updateState(app, next, NotificationHistoryRepository.DecisionState.EXPIRED)
            finishAnalysis()
            return
        }
        if (!LocalNotificationModel.installed(app)) {
            updateState(app, next, NotificationHistoryRepository.DecisionState.MODEL_MISSING)
            finishAnalysis()
            return
        }
        if (isBatteryRestricted(app)) {
            updateState(app, next, NotificationHistoryRepository.DecisionState.BATTERY_PAUSED)
            finishAnalysis()
            return
        }
        synchronized(this) {
            dailyCount++
            lastExecution = SystemClock.elapsedRealtime()
        }
        val fingerprint = NotificationProtection.fingerprint(next)
        val body = NotificationProtection.text(snapshot)
        val canRemove = live != null && NotificationProtection.safe(app, live)
        executor.execute {
            try {
                val config = NotificationFilterSettings.load(app)
                if (config.mode == NotificationFilterMode.OFF) {
                    updateState(app, next, NotificationHistoryRepository.DecisionState.EXPIRED)
                    return@execute
                }
                if (body.isBlank() || body == ".") {
                    updateState(app, next, NotificationHistoryRepository.DecisionState.NO_CONTENT)
                    return@execute
                }
                val result = LocalNotificationModel.classify(app, body, config.inferenceLabels())
                // Classification is independent of deletion safety. A protected
                // message can still be identified as advertising/spam.
                val evaluation = NotificationFilterPolicy.evaluate(
                    config, NotificationFilterCandidate(
                        app = next.packageName, safe = true, scores = result,
                        localHour = LocalTime.now().hour,
                    ),
                )
                val label = result.first().first
                val store = NotificationHistoryRepository(app)
                val preference = if (NotificationAdaptiveSettings.enabled(app))
                    store.interest(next.packageName, snapshot.notification.channelId, label,
                        snapshot.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(),
                        snapshot.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(),
                        NotificationAdaptiveSettings.minExamples(app))
                else NotificationPersonalization.Interest.NEUTRAL
                val adapted = NotificationPersonalization.safeDecision(evaluation, preference, label, config)
                val topic = store.learnedTopic(next.packageName, snapshot.notification.channelId, label,
                    snapshot.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(), body)
                    ?: NotificationTopics.classify(next.packageName, snapshot.notification.channelId,
                        snapshot.notification.category, body, "", label)
                val decision = adapted.decision
                val state = when {
                    !canRemove -> NotificationHistoryRepository.DecisionState.ANALYZED_PROTECTED
                    decision == NotificationFilterDecision.KEEP ->
                        NotificationHistoryRepository.DecisionState.KEPT
                    decision == NotificationFilterDecision.OBSERVE ->
                        NotificationHistoryRepository.DecisionState.OBSERVED
                    else -> NotificationHistoryRepository.DecisionState.PROPOSED
                }
                if (NotificationHistoryRepository(app).updateDecision(
                    next.key, next.postTime, state,
                    result.firstOrNull()?.first, result.firstOrNull()?.second,
                    reason = if (!canRemove) "Protection Android ou notification plus active" else adapted.reason,
                    topic = topic, personalInterest = preference.name,
                )) NotificationActivityFeed.notifyChanged()
                synchronized(this) {
                    if (decision == NotificationFilterDecision.KEEP || !canRemove) kept++ else reviewed++
                    if (canRemove && decision in setOf(
                            NotificationFilterDecision.SNOOZE_1H,
                            NotificationFilterDecision.SNOOZE_24H,
                            NotificationFilterDecision.DISMISS,
                        )) proposed++
                    append(next.packageName, result.first().first, result.first().second,
                        when {
                            !canRemove -> "Conservée · protégée ou absente d'Android"
                            decision == NotificationFilterDecision.KEEP -> "Conservée · ${adapted.reason}"
                            decision == NotificationFilterDecision.OBSERVE -> "Observation"
                            else -> "À filtrer : $decision"
                        },
                        when {
                            !canRemove -> NotificationBriefOutcome.KEPT
                            decision == NotificationFilterDecision.KEEP -> NotificationBriefOutcome.KEPT
                            decision == NotificationFilterDecision.OBSERVE -> NotificationBriefOutcome.OBSERVED
                            else -> NotificationBriefOutcome.PROPOSED
                        },
                    )
                }
                if (canRemove && decision in setOf(
                        NotificationFilterDecision.SNOOZE_1H,
                        NotificationFilterDecision.SNOOZE_24H,
                        NotificationFilterDecision.DISMISS,
                    )) {
                    AgentNotificationHistoryService.requestSafeFilter(
                        next, fingerprint, result, decision,
                    ) { applied ->
                        if (!applied) {
                            if (NotificationHistoryRepository(app).updateDecision(
                                next.key, next.postTime,
                                NotificationHistoryRepository.DecisionState.KEPT,
                                reason = "Action refusée par les protections Android ou notification modifiée",
                            )) NotificationActivityFeed.notifyChanged()
                            synchronized(this) {
                                append(next.packageName, result.first().first, result.first().second,
                                    "Conservée · action refusée (protection Android, priorité ou notification modifiée)",
                                    NotificationBriefOutcome.KEPT)
                            }
                        }
                        synchronized(this) {
                            if (applied) requested++ else skipped++
                            publish()
                        }
                    }
                }
            } catch (error: Exception) {
                updateState(app, next, NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR)
                synchronized(this) {
                    errors++
                    // Never log model inputs or notification text.
                    lastErrorType = error.javaClass.simpleName.take(60)
                    publish()
                }
            } finally {
                handler.post { finishAnalysis() }
            }
        }
    }

    @Volatile private var serviceApp: Context? = null

    private fun refreshDay() {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today != dayBucket) {
            dayBucket = today
            dailyCount = 0
        }
    }

    private fun finishAnalysis() {
        synchronized(this) { executing = false }
        handler.post { drainNext() }
    }

    fun attachService(context: Context) {
        serviceApp = context.applicationContext
        handler.post { drainNext() }
    }

    /** Retry only active, recently received, not-yet-classified notifications. */
    fun recoverActive(
        context: Context,
        active: Array<out StatusBarNotification>,
        includeClassified: Boolean = false,
    ): Int {
        if (active.isEmpty()) return 0
        if (NotificationFilterSettings.load(context).mode == NotificationFilterMode.OFF) return 0
        val latest = runCatching {
            NotificationHistoryRepository(context).recent(limit = 300)
                .associateBy { it.key }
        }.getOrDefault(emptyMap())
        val retryStates = setOf(
            NotificationHistoryRepository.DecisionState.RECEIVED,
            NotificationHistoryRepository.DecisionState.QUEUED,
            NotificationHistoryRepository.DecisionState.BATTERY_PAUSED,
            NotificationHistoryRepository.DecisionState.MODEL_MISSING,
            NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR,
            NotificationHistoryRepository.DecisionState.PROTECTED,
        )
        val selected = active.asSequence()
            // Games have their own cheap, explicit policy; never load ONNX for them.
            .filterNot { NotificationGameIdentity.isGame(context, it.packageName) }
            .filter {
                System.currentTimeMillis() - it.postTime in 0L..MAX_PENDING_MS
            }
            .filter { sbn ->
                latest[sbn.key]?.let { entry ->
                    entry.postedAt == sbn.postTime &&
                        (entry.state in retryStates ||
                            (includeClassified && entry.state in setOf(
                                NotificationHistoryRepository.DecisionState.KEPT,
                                NotificationHistoryRepository.DecisionState.OBSERVED,
                                NotificationHistoryRepository.DecisionState.ANALYZED_PROTECTED,
                            )))
                } == true
            }
            .take(MAX_QUEUE)
            .toList()
        selected.forEach { observe(context, it) }
        return selected.size
    }

    fun retryActive(context: Context): Int =
        recoverActive(
            context,
            AgentNotificationHistoryService.currentNotifications().orEmpty(),
            includeClassified = true,
        )

    fun detachService() {
        val app = serviceApp
        serviceApp = null
        handler.removeCallbacks(drainRunnable)
        val outstanding = synchronized(this) {
            scheduled = false
            pending.values.toList().also { pending.clear() }
        }
        // The listener is going away. Pending entries must not display
        // "in progress" forever after a process/service restart.
        if (app != null) outstanding.forEach { sbn ->
            updateState(app, sbn, NotificationHistoryRepository.DecisionState.EXPIRED)
        }
    }
}
