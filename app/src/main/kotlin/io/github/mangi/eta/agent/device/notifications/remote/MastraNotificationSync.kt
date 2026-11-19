package io.github.mangi.eta.agent.device.notifications.remote

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.SyncState
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Sends queued notifications and feedback to Mastra, then stores the decisions.
 * Event-driven: scheduled when a notification or feedback is recorded, with a network
 * constraint and exponential backoff. No polling loop, nothing on the main thread.
 */
internal class MastraNotificationSync(
    private val context: Context,
    private val repository: NotificationHistoryRepository = NotificationHistoryRepository(context),
    private val client: (MastraPairing) -> MastraNotificationClient = { MastraNotificationClient(it) },
    /** Asks the listener to remove a notification after its own checks; never assumed to succeed. */
    private val dismisser: (NotificationHistoryRepository.RemoteTarget) -> Unit = { target ->
        io.github.mangi.eta.agent.device.AgentNotificationHistoryService.requestRemoteDismiss(context, target)
    },
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val pairingProvider: () -> MastraPairing? =
        { if (MastraConnection.enabled(context)) MastraConnection.load(context) else null },
    private val learningConsent: () -> Boolean = { MastraConnection.learningConsent(context) },
    private val status: (String) -> Unit = { MastraConnection.setLastStatus(context, it) },
) {
    enum class Outcome { DONE, RETRY, NOT_CONFIGURED }

    fun run(maxBatches: Int = 5): Outcome {
        val pairing = pairingProvider() ?: return Outcome.NOT_CONFIGURED
        val api = client(pairing)
        var retry = false
        for (batch in 0 until maxBatches) {
            val pending = repository.pendingRemote(MastraContract.MAX_BATCH)
            if (pending.isEmpty()) break
            if (!sendBatch(api, pairing, pending)) { retry = true; break }
        }
        if (!sendFeedback(api, pairing)) retry = true
        if (repository.pendingRemote(1).isNotEmpty()) retry = true
        NotificationActivityFeed.notifyChanged()
        return if (retry) Outcome.RETRY else Outcome.DONE
    }

    /** Builds exactly what is sent; the same function recomputes the hash when a decision arrives. */
    fun toRemote(p: NotificationHistoryRepository.PendingRemote): RemoteNotification {
        val f = NotificationPrivacyFilter.filter(p.title, p.text, p.subText)
        val postedAt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(p.postedAt), zone).toString()
        val hash = MastraNotificationClient.sha256(listOf(p.packageName, f.title, f.content, f.subText, p.channelId,
            p.androidCategory, postedAt).joinToString("\u0000") { it ?: "\u0001" })
        return RemoteNotification(
            id = p.eventId, revision = p.revision, contentHash = hash, packageName = p.packageName,
            appLabel = p.meta.appLabel?.take(100), postedAt = postedAt,
            title = f.title?.take(2000), content = f.content?.take(4000), subText = f.subText?.take(500),
            channelId = p.channelId?.take(150), androidCategory = p.androidCategory?.take(50),
            clearable = p.meta.clearable, ongoing = p.meta.ongoing, groupSummary = p.meta.groupSummary,
            hasRemoteInput = p.meta.hasRemoteInput, conversation = p.meta.conversation, systemApp = p.meta.systemApp,
            appCategory = p.meta.appCategory, redactions = if (f.redacted) listOf(Redaction.NUMERIC_CODE) else emptyList(),
        )
    }

    private fun sendBatch(api: MastraNotificationClient, pairing: MastraPairing,
                          pending: List<NotificationHistoryRepository.PendingRemote>): Boolean {
        val ids = pending.map { it.eventId }
        repository.markRemote(ids, SyncState.SENT)
        val started = System.nanoTime()
        val response = try {
            api.analyze(AnalyzeRequest(deviceId = pairing.deviceId, batchId = MastraNotificationClient.opaqueId("batch"),
                sentAt = OffsetDateTime.now(zone).toString(), timeZone = zone.id,
                learningConsent = learningConsent(), notifications = pending.map(::toRemote)))
        } catch (e: MastraHttpException) {
            // Never a reason to remove anything: notifications simply stay pending or rejected.
            repository.markRemote(ids, if (e.permanent) SyncState.REJECTED else SyncState.ERROR,
                if (e.permanent) "Refusée par le homelab" else "Analyse en attente : homelab indisponible")
            status("Erreur ${e.status}${e.code?.let { " · $it" }.orEmpty()}")
            return e.permanent
        } catch (e: Exception) {
            repository.markRemote(ids, SyncState.ERROR, "Analyse en attente : homelab injoignable")
            status("Homelab injoignable")
            return false
        }
        val byId = pending.associateBy { it.eventId }
        for (decision in response.decisions) {
            val local = byId[decision.id] ?: continue
            if (!MastraContract.validate(decision)) {
                repository.markRemote(listOf(decision.id), SyncState.REJECTED, "Décision incohérente ignorée")
                continue
            }
            val target = repository.applyRemoteDecision(decision) { row -> toRemote(row).contentHash }
            if (target != null && local.revision == decision.revision) dismisser(target)
        }
        for (error in response.errors) {
            val id = error.id ?: pending.getOrNull(error.index)?.eventId ?: continue
            repository.markRemote(listOf(id), if (error.code == "INVALID") SyncState.REJECTED else SyncState.ERROR,
                "Analyse refusée : ${error.code}")
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        status("Synchronisé · ${response.decisions.size} décision(s) · $ms ms")
        return true
    }

    private fun sendFeedback(api: MastraNotificationClient, pairing: MastraPairing): Boolean {
        val items = repository.pendingFeedback(MastraContract.MAX_FEEDBACK_BATCH)
        if (items.isEmpty()) return true
        val events = items.mapNotNull { runCatching { lenient.decodeFromString(FeedbackEvent.serializer(), it.payload) }.getOrNull() }
        return try {
            if (events.isNotEmpty()) api.feedback(FeedbackRequest(deviceId = pairing.deviceId,
                batchId = MastraNotificationClient.opaqueId("fb"), learningConsent = learningConsent(),
                events = events))
            repository.removeFeedback(items.map { it.id })
            true
        } catch (e: MastraHttpException) {
            if (e.permanent) repository.removeFeedback(items.map { it.id })
            e.permanent
        } catch (_: Exception) { false }
    }

    companion object {
        private val lenient = Json { ignoreUnknownKeys = true }
        private const val JOB_ID = 0x4A43_0001
        private val running = AtomicBoolean(false)
        private val dirty = AtomicBoolean(false)
        private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "mastra-notification-sync") }

        /** Coalesces bursts into one batch; a running job picks up new work itself. */
        fun request(context: Context, delayMs: Long = 3_000) {
            if (!MastraConnection.enabled(context)) return
            if (running.get()) { dirty.set(true); return }
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, MastraNotificationSyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delayMs)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build())
        }

        internal fun execute(context: Context, done: (Boolean) -> Unit) {
            executor.execute {
                running.set(true)
                var retry = false
                try {
                    do {
                        dirty.set(false)
                        retry = MastraNotificationSync(context.applicationContext).run() == Outcome.RETRY
                    } while (dirty.get() && !retry)
                } catch (_: Exception) {
                    retry = true
                } finally {
                    running.set(false)
                }
                done(retry)
            }
        }

        fun decodeActions(json: String?): List<ActionProposal> = runCatching {
            MastraContract.json.decodeFromString(ListSerializer(ActionProposal.serializer()), json ?: return emptyList())
        }.getOrDefault(emptyList())
    }
}

/** Runs [MastraNotificationSync] off the main thread; Android reschedules with backoff on failure. */
class MastraNotificationSyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        MastraNotificationSync.execute(this) { retry -> jobFinished(params, retry) }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean = true
}
