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
import io.github.mangi.eta.agent.runtime.AgentExternalArchivePayload
import io.github.mangi.eta.agent.runtime.AgentRuntimeWire
import io.github.mangi.eta.ui.MainActivity

/** Only the run's state is shown on the lock screen; the question and model output stay in chat. */
internal object DevelopmentRunNotice {
    fun completed(context: Context, request: AgentRuntimeWire.RunRequest, result: AgentRuntimeWire.RunResult) {
        val store = DevelopmentStore(context)
        if (!store.wasRunUsed(request.runId) || !store.enabled) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "App improvements", NotificationManager.IMPORTANCE_DEFAULT))
        val handoff = request.handoff
        val key = handoff?.takeIf { it.source == AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE }
            ?.let { AgentExternalArchivePayload.from(it.payload)?.conversationKey }
            ?: store.conversationKey()
        val intent = if (key != null) Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_ARCHIVED_CONVERSATION)
            .putExtra(MainActivity.EXTRA_HANDOFF_SOURCE, AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE)
            .putExtra(MainActivity.EXTRA_CONVERSATION_KEY, key)
        else Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val question = store.questionPending() && store.questionRunId() == request.runId && result.ok
        val title = when {
            question -> "Jean-Calcul needs your answer"
            result.ok -> "Jean-Calcul finished the improvement task"
            else -> "Jean-Calcul could not finish the improvement"
        }
        val body = when {
            question -> "Open the conversation to answer and continue."
            result.ok -> "Open the conversation to review the result."
            else -> "Open the conversation to review the error and retry."
        }
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }

    private const val CHANNEL = "app-improvements"
    private const val NOTIFICATION_ID = 6202
}
