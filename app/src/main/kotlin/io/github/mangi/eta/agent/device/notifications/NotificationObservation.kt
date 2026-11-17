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
    else (60_000L - (nowElapsed - lastElapsed).coerceAtLeast(0L)).coerceAtLeast(0L)

/**
 * Event-driven filter: at most one classification/minute and 100/day.
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
    private const val MAX_QUEUE = 8
    private const val MAX_PENDING_MS = 10 * 60_000L
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
            "Publicités : ${if (config.filterAds) "oui" else "non"} · " +
            "Spam : ${if (config.filterSpam) "oui" else "non"} · " +
            "Modèle : ${if (ready) "installé" else "absent"} · " +
            "Accès notifications : ${if (AgentNotificationHistoryService.isEnabled(context)) "accordé" else "manquant"} · " +
            "Batterie : ${if (battery) "analyse suspendue" else "OK"} · " +
            "File : ${pending.size} · Aujourd'hui : $dailyCount/100 · " +
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
        if (!NotificationProtection.safe(app, sbn)) {
            updateState(app, sbn, NotificationHistoryRepository.DecisionState.PROTECTED)
            return
        }
        if (isBatteryRestricted(app)) {
            updateState(app, sbn, NotificationHistoryRepository.DecisionState.BATTERY_PAUSED)
            return
        }

        var overflow: StatusBarNotification? = null
        val accepted = synchronized(this) {
            refreshDay()
            if (dailyCount >= 100) false
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
        val removed = synchronized(this) {
            pending[sbn.key]?.takeIf { it.postTime == sbn.postTime }?.also {
                pending.remove(sbn.key)
            }
        }
        if (removed != null)
            updateState(context.applicationContext, removed,
                NotificationHistoryRepository.DecisionState.EXPIRED)
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
            if (dailyCount >= 100) {
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
        val live = AgentNotificationHistoryService.currentNotifications()
            ?.firstOrNull { it.key == next.key && it.postTime == next.postTime }
        if (live == null || System.currentTimeMillis() - next.postTime > MAX_PENDING_MS ||
            NotificationProtection.fingerprint(live) != NotificationProtection.fingerprint(next)) {
            updateState(app, next, NotificationHistoryRepository.DecisionState.EXPIRED)
            finishAnalysis()
            return
        }
        if (!LocalNotificationModel.installed(app)) {
            updateState(app, next, NotificationHistoryRepository.DecisionState.MODEL_MISSING)
            finishAnalysis()
            return
        }
        if (!NotificationProtection.safe(app, live)) {
            updateState(app, next, NotificationHistoryRepository.DecisionState.PROTECTED)
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
        val fingerprint = NotificationProtection.fingerprint(live)
        val body = NotificationProtection.text(live)
        executor.execute {
            try {
                val config = NotificationFilterSettings.load(app)
                if (config.mode == NotificationFilterMode.OFF) {
                    updateState(app, live, NotificationHistoryRepository.DecisionState.EXPIRED)
                    return@execute
                }
                val result = LocalNotificationModel.classify(app, body)
                val decision = NotificationFilterPolicy.decide(
                    config, NotificationFilterCandidate(
                        app = live.packageName, safe = true, scores = result,
                        localHour = LocalTime.now().hour,
                    ),
                )
                val state = when (decision) {
                    NotificationFilterDecision.KEEP ->
                        NotificationHistoryRepository.DecisionState.KEPT
                    NotificationFilterDecision.OBSERVE ->
                        NotificationHistoryRepository.DecisionState.OBSERVED
                    else -> NotificationHistoryRepository.DecisionState.PROPOSED
                }
                if (NotificationHistoryRepository(app).updateDecision(
                    live.key, live.postTime, state,
                    result.firstOrNull()?.first, result.firstOrNull()?.second,
                )) NotificationActivityFeed.notifyChanged()
                synchronized(this) {
                    if (decision == NotificationFilterDecision.KEEP) kept++ else reviewed++
                    if (decision in setOf(
                            NotificationFilterDecision.SNOOZE_1H,
                            NotificationFilterDecision.SNOOZE_24H,
                            NotificationFilterDecision.DISMISS,
                        )) proposed++
                    append(live.packageName, result.first().first, result.first().second,
                        when (decision) {
                            NotificationFilterDecision.KEEP -> "Conservée"
                            NotificationFilterDecision.OBSERVE -> "Observation"
                            else -> "À filtrer : $decision"
                        },
                        when (decision) {
                            NotificationFilterDecision.KEEP -> NotificationBriefOutcome.KEPT
                            NotificationFilterDecision.OBSERVE -> NotificationBriefOutcome.OBSERVED
                            else -> NotificationBriefOutcome.PROPOSED
                        },
                    )
                }
                if (decision in setOf(
                        NotificationFilterDecision.SNOOZE_1H,
                        NotificationFilterDecision.SNOOZE_24H,
                        NotificationFilterDecision.DISMISS,
                    )) {
                    AgentNotificationHistoryService.requestSafeFilter(
                        live, fingerprint, result, decision,
                    ) { applied ->
                        synchronized(this) {
                            if (applied) requested++ else skipped++
                            publish()
                        }
                    }
                }
            } catch (error: Exception) {
                updateState(app, live, NotificationHistoryRepository.DecisionState.ANALYSIS_ERROR)
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
    fun recoverActive(context: Context, active: Array<out StatusBarNotification>): Int {
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
        )
        val selected = active.asSequence()
            .filter {
                System.currentTimeMillis() - it.postTime in 0L..MAX_PENDING_MS
            }
            .filter { sbn ->
                latest[sbn.key]?.let { entry ->
                    entry.state in retryStates && entry.postedAt == sbn.postTime
                } == true
            }
            .take(MAX_QUEUE)
            .toList()
        selected.forEach { observe(context, it) }
        return selected.size
    }

    fun retryActive(context: Context): Int =
        recoverActive(context,
            AgentNotificationHistoryService.currentNotifications().orEmpty())

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
