package io.github.mangi.eta.agent.device.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock

/**
 * Event-driven, opt-in, deliberately sparse observer. Does not cancel a notification.
 * Keeps only category, app package and scores in volatile memory; never raw texts.
 */
internal object NotificationObservation {
    private const val KEY = "observation_enabled"
    private val executing = AtomicBoolean(false)
    private val executor by lazy { Executors.newSingleThreadExecutor { job ->
        Thread(job, "notification-local-inference").apply { isDaemon = true }
    } }
    private var lastExecution = 0L
    private val history = ArrayDeque<String>()
    private var reviewedCount = 0
    private var keptCount = 0
    private var errors = 0

    fun enabled(context: Context) = context.getSharedPreferences("notification_local", Context.MODE_PRIVATE)
        .getBoolean(KEY, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("notification_local", Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, enabled).apply()
    }
    @Synchronized
    fun snapshot(): List<String> = history.toList()
    @Synchronized
    fun statistics(): String = "À examiner : $reviewedCount · Conservées : $keptCount · Erreurs : $errors"

    fun observe(context: Context, sbn: StatusBarNotification) {
        if (!enabled(context) || !LocalNotificationModel.installed(context)) return
        val notification = sbn.notification
        if (sbn.packageName == context.packageName ||
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0 ||
            notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
            notification.category in setOf(
                Notification.CATEGORY_CALL, Notification.CATEGORY_MESSAGE,
                Notification.CATEGORY_ALARM, Notification.CATEGORY_EVENT,
                Notification.CATEGORY_REMINDER, Notification.CATEGORY_TRANSPORT,
                Notification.CATEGORY_SYSTEM, Notification.CATEGORY_SERVICE,
            )
        ) return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = "$title. $body".trim()
        if (text.length < 8) return
        // Never classify obvious OTP, 2FA, security notices or password resets.
        val safe = !Regex("(?i)(code de (connexion|sécurité|vérification)|otp|2fa|authentication|authentification|password|mot de passe|connexion détectée|security alert)").containsMatchIn(text)
        if (!safe) return
        // Only one item each minute; do not build a queue of incoming notifications.
        val now = SystemClock.elapsedRealtime()
        synchronized(this) {
            if (now - lastExecution < 60_000 || executing.get()) return
            lastExecution = now
            executing.set(true)
        }
        val app = context.applicationContext
        val packageName = sbn.packageName
        executor.execute {
            try {
                if (!enabled(app)) return@execute
                val results = LocalNotificationModel.classify(app, text)
                val top = results.first()
                val decision = NotificationDecisionPolicy.decide(NotificationSafetyInput(
                    hasUsableText = true,
                    isModelReady = true,
                    predictedLabel = top.first,
                ))
                synchronized(this) {
                    if (decision == NotificationDecision.REVIEW) reviewedCount++ else keptCount++
                    history.addFirst("$packageName · ${top.first} · ${"%.0f".format(top.second * 100)} % · $decision")
                    while (history.size > 20) history.removeLast()
                }
            } catch (_: Exception) {
                synchronized(this) { errors++ }
            } finally {
                executing.set(false)
            }
        }
    }
}
