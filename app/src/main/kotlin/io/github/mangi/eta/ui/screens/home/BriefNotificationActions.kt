package io.github.mangi.eta.ui.screens.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import io.github.mangi.eta.agent.device.AgentNotificationHistoryService
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationPriority
import io.github.mangi.eta.agent.device.notifications.NotificationUserFeedback
import io.github.mangi.eta.agent.device.notifications.remote.ActionProposal
import io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.RecentEntry
import io.github.mangi.eta.data.repository.NotificationHistoryRepository.ThreadKey
import io.github.suqi8.coui.kmp.basic.SnackbarDuration
import io.github.suqi8.coui.kmp.basic.SnackbarHostState
import io.github.suqi8.coui.kmp.basic.SnackbarResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberBriefNotificationActions(
    repository: NotificationHistoryRepository,
    snackbars: SnackbarHostState,
): Pair<BriefNotificationActions, @Composable () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = remember(context, repository, snackbars, scope) {
        BriefNotificationActions(context, repository, snackbars, scope)
    }
    val controller = rememberBriefActionController(repository, actions::open)
    val currentAction = rememberUpdatedState(controller.onAction)
    val currentDialogs = rememberUpdatedState(controller.dialogs)
    return remember(actions) {
        actions.onAction = { entry, proposal -> currentAction.value(entry, proposal) }
        actions to { currentDialogs.value() }
    }
}

internal class BriefNotificationActions(
    private val context: Context,
    private val repository: NotificationHistoryRepository,
    private val snackbars: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    lateinit var onAction: (RecentEntry, ActionProposal) -> Unit

    fun open(entry: RecentEntry) {
        val opened = runCatching {
            val pendingIntent = AgentNotificationHistoryService.currentNotifications()
                ?.firstOrNull { it.key == entry.key && it.postTime == entry.postedAt }
                ?.notification?.contentIntent
            if (pendingIntent != null) pendingIntent.send()
            else {
                val intent = context.packageManager.getLaunchIntentForPackage(entry.packageName)
                    ?: error("Application indisponible")
                context.startActivity(intent)
            }
        }.isSuccess
        if (opened) markFeedback(entry, NotificationUserFeedback.OPENED)
        else launchAction { snackbars.showSnackbar("Impossible d’ouvrir l’application") }
    }

    fun markFeedback(entry: RecentEntry, feedback: NotificationUserFeedback) =
        saveFeedback(entry, feedback)

    fun correctPriority(entry: RecentEntry, priority: NotificationPriority) =
        saveFeedback(entry, NotificationUserFeedback.PRIORITY_CORRECTED, priority = priority.name)

    fun correctCategory(entry: RecentEntry, category: String) =
        saveFeedback(entry, NotificationUserFeedback.CATEGORY_CORRECTED, category = category)

    fun archiveEntries(entries: List<RecentEntry>): Job = launchAction {
        withContext(Dispatchers.IO) {
            syncArchiveFeedback(repository.archiveBriefEntries(entries))
        }
    }

    fun archiveThread(thread: ThreadKey): Job = launchAction {
        val archive = withContext(Dispatchers.IO) { repository.archiveBriefThread(thread) }
        if (archive.entries.isEmpty()) return@launchAction
        var undone = false
        try {
            if (snackbars.showSnackbar("Fil archivé", "Annuler", duration = SnackbarDuration.Long) ==
                SnackbarResult.ActionPerformed
            ) {
                withContext(Dispatchers.IO) { repository.undoBriefThreadArchive(archive) }
                undone = true
            }
        } finally {
            if (!undone) {
                withContext(NonCancellable + Dispatchers.IO) { syncArchiveFeedback(archive.entries) }
            }
        }
    }

    private fun syncArchiveFeedback(entries: List<NotificationHistoryRepository.ArchiveEntry>) {
        if (entries.isEmpty()) return
        entries.forEach { repository.feedback(it.key, it.postedAt, NotificationUserFeedback.ARCHIVED) }
        MastraNotificationSync.request(context)
    }

    private fun saveFeedback(
        entry: RecentEntry,
        feedback: NotificationUserFeedback,
        category: String? = null,
        priority: String? = null,
    ) {
        launchAction {
            withContext(Dispatchers.IO) {
                repository.feedback(entry.key, entry.postedAt, feedback, category, priority)
                NotificationActivityFeed.notifyChanged()
                MastraNotificationSync.request(context)
            }
        }
    }

    private fun launchAction(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            scope.launch { snackbars.showSnackbar("Action impossible. Réessayez.") }
        }
    }
}
