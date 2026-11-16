package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import java.time.LocalTime
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Event-driven filter: at most one classification/minute and 100/day.
 * Raw texts exist only during that invocation, never in history or network calls.
 * No service, wake lock, timer, or inference model kept active between events.
 */
internal object NotificationObservation : NotificationBriefSource {
    private val executing = AtomicBoolean(false)
    private val executor by lazy {
        Executors.newSingleThreadExecutor { work ->
            Thread(work, "notification-local-inference").apply { isDaemon = true }
        }
    }
    private var lastExecution = 0L
    private var dailyCount = 0
    private var dayBucket = -1L
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
        "À examiner : $reviewed · Conservées : $kept · Masquages demandés : $requested · Confirmés : $confirmed · Ignorées : $skipped · Erreurs : $errors"

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
        if (NotificationFilterSettings.isValidPackageName(packageName)) {
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

    fun observe(context: Context, sbn: StatusBarNotification) {
        val config = NotificationFilterSettings.load(context)
        if (config.mode == NotificationFilterMode.OFF ||
            !LocalNotificationModel.installed(context) ||
            !NotificationProtection.safe(context, sbn)) return

        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) return
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0 && level * 100 / scale < 20) return

        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            val today = System.currentTimeMillis() / 86_400_000L
            if (today != dayBucket) { dayBucket = today; dailyCount = 0 }
            if (dailyCount >= 100 || now - lastExecution < 60_000 || executing.get()) {
                skipped++
                return
            }
            dailyCount++
            lastExecution = now
            executing.set(true)
        }

        val app = context.applicationContext
        val packageName = sbn.packageName
        val fingerprint = NotificationProtection.fingerprint(sbn)
        val text = NotificationProtection.text(sbn)
        executor.execute {
            try {
                val liveConfig = NotificationFilterSettings.load(app)
                if (liveConfig.mode == NotificationFilterMode.OFF || !NotificationProtection.safe(app, sbn)) return@execute
                val results = LocalNotificationModel.classify(app, text)
                val decision = NotificationFilterPolicy.decide(liveConfig, NotificationFilterCandidate(
                    app = packageName, safe = true, scores = results, localHour = LocalTime.now().hour,
                ))
                synchronized(this) {
                    if (decision == NotificationFilterDecision.KEEP) kept++ else reviewed++
                    if (decision in setOf(NotificationFilterDecision.SNOOZE_1H,
                            NotificationFilterDecision.SNOOZE_24H, NotificationFilterDecision.DISMISS)) proposed++
                    append(packageName, results.first().first, results.first().second,
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
                if (decision in setOf(NotificationFilterDecision.SNOOZE_1H,
                        NotificationFilterDecision.SNOOZE_24H, NotificationFilterDecision.DISMISS)) {
                    AgentNotificationHistoryService.requestSafeFilter(
                        sbn, fingerprint, results, decision,
                    ) { applied ->
                        synchronized(this) {
                            if (applied) requested++ else skipped++
                            publish()
                        }
                    }
                }
            } catch (_: Exception) {
                synchronized(this) { errors++; publish() }
            } finally {
                executing.set(false)
            }
        }
    }
}
