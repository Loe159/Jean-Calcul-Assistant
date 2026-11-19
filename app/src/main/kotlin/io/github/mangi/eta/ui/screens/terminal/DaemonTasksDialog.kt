package io.github.mangi.eta.ui.screens.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.DaemonTaskUi
import io.github.mangi.eta.ui.app.displayName
import io.github.mangi.eta.ui.components.EtaMonospace
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun DaemonTasksDialog(
    tasks: List<DaemonTaskUi>,
    onDismiss: () -> Unit,
    onStop: (String) -> Unit,
    onLoadLogs: suspend (String) -> String,
) {
    val body: @Composable () -> Unit = {
        if (tasks.isEmpty()) {
            Text(
                text = stringResource(R.string.terminal_daemon_empty),
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(items = tasks, key = { it.id }) { task ->
                    DaemonTaskRow(task = task, onStop = onStop, onLoadLogs = onLoadLogs)
                }
            }
        }
    }

    CouiNoticeDialog(
        visible = true,
        title = stringResource(R.string.terminal_daemon_tasks),
        message = null,
        onDismissRequest = onDismiss,
    ) {
        body()
    }
}

@Composable
private fun DaemonTaskRow(
    task: DaemonTaskUi,
    onStop: (String) -> Unit,
    onLoadLogs: suspend (String) -> String,
) {
    var logsExpanded by remember(task.id) { mutableStateOf(false) }
    var logs by remember(task.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val colors = COUITheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .squircleBackground(colors.surfaceWithCard, 14.dp)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = daemonStatusColor(task))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = task.description.ifBlank { task.command },
                    style = COUITheme.textStyles.bodyM.copy(fontFamily = EtaMonospace),
                    color = colors.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = daemonMeta(task),
                    style = COUITheme.textStyles.bodyXS,
                    color = colors.labelSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RowIconAction(
                icon = Icons.AutoMirrored.Rounded.Article,
                contentDescription = stringResource(
                    if (logsExpanded) R.string.terminal_daemon_hide_logs else R.string.terminal_daemon_view_logs,
                ),
                tint = if (logsExpanded) colors.primary else colors.labelSecondary,
                onClick = {
                    logsExpanded = !logsExpanded
                    if (logsExpanded && logs == null) {
                        scope.launch { logs = onLoadLogs(task.id) }
                    }
                },
            )
            if (task.running) {
                RowIconAction(
                    icon = Icons.Rounded.Stop,
                    contentDescription = stringResource(R.string.terminal_stop),
                    tint = colors.error,
                    onClick = { onStop(task.id) },
                )
            }
        }
        if (logsExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, end = 10.dp)
                    .heightIn(max = 180.dp)
                    .squircleBackground(colors.background, 10.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                if (logs == null) {
                    InfiniteProgressIndicator(size = 14.dp)
                } else {
                    Text(
                        text = logs.orEmpty(),
                        style = COUITheme.textStyles.bodyXS.copy(fontFamily = EtaMonospace),
                        color = colors.labelPrimary.copy(alpha = 0.86f),
                    )
                }
            }
        }
    }
}

@Composable
private fun daemonStatusColor(task: DaemonTaskUi) = when {
    task.running -> StatusSuccess
    task.status == "failed" || task.status == "timed_out" -> COUITheme.colorScheme.error
    else -> COUITheme.colorScheme.labelSecondary.copy(alpha = 0.5f)
}

@Composable
private fun daemonMeta(task: DaemonTaskUi): String {
    val environmentLabel = task.environment.displayName
    val stateLabel = when (task.status) {
        "completed" -> "Completed"
        "failed" -> "Execution failed"
        "timed_out" -> "Timed out"
        "stopped" -> "Stopped"
        "interrupted" -> "Interrupted"
        else -> stringResource(if (task.running) R.string.terminal_daemon_running else R.string.terminal_daemon_exited)
    }
    return "$environmentLabel · ${task.identity} · $stateLabel"
}
