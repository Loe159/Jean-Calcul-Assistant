package io.github.mangi.eta.ui.screens.backup

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.core.safeStackTrace
import io.github.mangi.eta.data.repository.EtaBackupSummary
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

@Composable
internal fun DataBackupScreen(
    context: Context,
    onBack: () -> Unit,
    onExport: suspend (OutputStream) -> EtaBackupSummary,
    onImport: suspend (InputStream) -> EtaBackupSummary,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    fun showFailure(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        AndroidAgentLogger.error("Agent backup UI failed: type=${throwable.safeLogType()}\n${throwable.safeStackTrace()}")
        Toast.makeText(
            context,
            throwable.message ?: context.getString(R.string.data_backup_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error(context.getString(R.string.data_backup_file_open_failed))
                val summary = output.use { onExport(it) }
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.data_backup_exported,
                        summary.conversationCount,
                        summary.providerCount,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (throwable: Throwable) {
                showFailure(throwable)
            } finally {
                busy = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportDialog = true
        }
    }

    CouiScaffoldPage(
        title = stringResource(R.string.data_backup_title),
        onBack = onBack,
    ) {
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    BasicComponent(
                        title = stringResource(R.string.data_backup_warning_title),
                        summary = stringResource(R.string.data_backup_warning_summary),
                    )
                }
            }
        }
        PreferenceCategory(title = stringResource(R.string.data_backup_actions))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.data_backup_export),
                        summary = if (busy) {
                            stringResource(R.string.data_backup_working)
                        } else {
                            stringResource(R.string.data_backup_export_summary)
                        },
                        endActions = {
                            if (busy) {
                                InfiniteProgressIndicator(size = 20.dp)
                            }
                        },
                        onClick = { exportLauncher.launch(defaultBackupFileName()) },
                        enabled = !busy,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.data_backup_import),
                        summary = stringResource(R.string.data_backup_import_summary),
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        enabled = !busy,
                    )
                }
            }
        }
        CouiPageBottomSpacer()
    }

    if (showImportDialog) {
        val confirmText = if (busy) {
            stringResource(R.string.data_backup_working)
        } else {
            stringResource(R.string.action_import)
        }
        val onDismissImport: () -> Unit = {
            if (!busy) {
                showImportDialog = false
                pendingImportUri = null
            }
        }
        val onConfirmImport: () -> Unit = {
            val uri = pendingImportUri
            if (uri != null) {
                showImportDialog = false
                scope.launch {
                    busy = true
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error(context.getString(R.string.data_backup_file_open_failed))
                        val summary = input.use { onImport(it) }
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.data_backup_imported,
                                summary.conversationCount,
                                summary.providerCount,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (throwable: Throwable) {
                        showFailure(throwable)
                    } finally {
                        pendingImportUri = null
                        busy = false
                    }
                }
            }
        }
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.data_backup_import_confirm_title),
            message = stringResource(R.string.data_backup_import_confirm_summary),
            confirmText = confirmText,
            confirmEnabled = !busy,
            cancelEnabled = !busy,
            destructive = true,
            onConfirm = onConfirmImport,
            onDismissRequest = onDismissImport,
        )
    }
}

private fun defaultBackupFileName(): String =
    "Jean-Calcul-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.eta-backup.json"
