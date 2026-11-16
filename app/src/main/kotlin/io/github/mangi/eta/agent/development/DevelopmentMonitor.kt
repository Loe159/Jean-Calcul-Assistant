package io.github.mangi.eta.agent.development

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import io.github.mangi.eta.R
import io.github.mangi.eta.data.update.PreviewUpdates
import io.github.mangi.eta.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Resumes persisted PR monitoring whenever the app/assistant process is alive; no model polling loop. */
internal object DevelopmentMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var job: Job? = null
    private val mutableStatus = MutableStateFlow<JSONObject?>(null)
    val status = mutableStatus.asStateFlow()
    @Synchronized fun start(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            while (true) {
                runCatching { refresh(app) }
                delay(30_000)
            }
        }
    }
    suspend fun refresh(context: Context): JSONObject? {
        val result = withContext(Dispatchers.IO) {
            val store = DevelopmentStore(context)
            if (!store.enabled) return@withContext null
            val proposal = store.proposal() ?: return@withContext null
            if (proposal.optInt("number") <= 0) return@withContext null
            DevelopmentGitHubClient(store::token).status(proposal).also { status ->
                val pr = status.optJSONObject("pull_request")
                if (pr?.optString("state") == "closed" && store.proposal()?.optString("head") == proposal.optString("head")) {
                    store.saveProposal(JSONObject(proposal.toString()).put("closed", true).put("merged", pr.optBoolean("merged")))
                }
            }
        }
        mutableStatus.value = result
        if (result != null) {
            val runs = result.getJSONArray("runs")
            val latest = (0 until runs.length()).map(runs::getJSONObject).firstOrNull { it.optString("head_sha") == result.optString("head") }
            if (latest?.optString("status") == "completed") {
                val ok = latest.optString("conclusion") == "success"
                val stamp = "${result.optString("head")}:${latest.optString("conclusion")}" 
                val prefs = context.getSharedPreferences("jeancalcul_development", Context.MODE_PRIVATE)
                if (prefs.getString("notified", null) != stamp) {
                    notify(context, if (ok) "App improvement ready to test" else "App improvement needs attention",
                        if (ok) "The Android workflow passed. Open Jean-Calcul to install and validate." else "The Android workflow failed. Open App improvements for the PR and build results.")
                    prefs.edit().putString("notified", stamp).apply()
                }
                if (ok) PreviewUpdates.check(context)
            }
        }
        return result
    }
    private fun notify(context: Context, title: String, text: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("app-improvements", "App improvements", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(context, 6201, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(6201, NotificationCompat.Builder(context, "app-improvements").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true).build())
    }
}
