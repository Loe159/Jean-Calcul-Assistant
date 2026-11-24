package io.github.mangi.eta.food

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

class FoodSyncJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val runtime = (application as EtaApp).food
        running = runtime.launch {
            val retry = try {
                runtime.repository.replayOutbox()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AndroidAgentLogger.warn("Food sync failed: type=${error.safeLogType()}")
                true
            }
            withContext(Dispatchers.Main) {
                running = null
                jobFinished(params, retry)
            }
            if (!retry) runtime.schedulePendingSync()
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        running = null
        return true
    }

    companion object {
        private const val JOB_ID = 0x464F4F44

        @Synchronized
        internal fun request(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, FoodSyncJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setMinimumLatency(3_000)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(job)
        }
    }
}
