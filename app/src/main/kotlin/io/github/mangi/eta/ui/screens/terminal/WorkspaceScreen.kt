package io.github.mangi.eta.ui.screens.terminal

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.WorkspaceEntry
import io.github.mangi.eta.ui.app.WorkspaceFileStore
import io.github.mangi.eta.ui.components.EtaPreferenceGroupItem
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

@Composable
internal fun WorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember(context.applicationContext) { WorkspaceFileStore(context) }
    val scope = rememberCoroutineScope()
    var path by rememberSaveable { mutableStateOf("") }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<WorkspaceEntry>?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<WorkspaceMessage?>(null) }
    var publicAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val accessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        publicAccess = Environment.isExternalStorageManager()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            busy = true
            try {
                var succeeded = 0
                uris.forEach { if (store.importFile(it)) succeeded++ }
                message = if (succeeded == uris.size) {
                    WorkspaceMessage(resources.getString(R.string.capability_workspace_imported), isError = false)
                } else {
                    WorkspaceMessage(
                        resources.getQuantityString(R.plurals.capability_workspace_partial_import, succeeded, succeeded),
                        isError = true,
                    )
                }
                if (succeeded > 0) path = "imports"
                revision++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = WorkspaceMessage(resources.getString(R.string.capability_workspace_failed), isError = true)
            } finally {
                busy = false
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = pendingExport
        pendingExport = null
        if (uri != null && source != null) scope.launch {
            busy = true
            try {
                store.exportFile(source, uri)
                message = WorkspaceMessage(resources.getString(R.string.capability_workspace_exported), isError = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = WorkspaceMessage(resources.getString(R.string.capability_workspace_failed), isError = true)
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(path) { entries = null }
    LaunchedEffect(path, revision) {
        try {
            entries = store.list(path)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            entries = emptyList()
            message = WorkspaceMessage(resources.getString(R.string.capability_workspace_failed), isError = true)
        }
    }
    val onOpenPublicAccess: () -> Unit = {
        try {
            accessLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: android.content.ActivityNotFoundException) {
            message = WorkspaceMessage(resources.getString(R.string.capability_workspace_failed), isError = true)
        }
    }

    val listContent: LazyListScope.() -> Unit = {
        item(key = "workspace-info") {
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        BasicComponent(
                            title = stringResource(R.string.capability_workspace_private),
                            summary = stringResource(R.string.capability_workspace_private_summary),
                        )
                    }
                }
            }
        }
        item(key = "actions") {
            val publicSummary = if (publicAccess) {
                stringResource(R.string.capability_workspace_public_granted)
            } else {
                stringResource(R.string.capability_workspace_public_summary)
            }
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.capability_workspace_import),
                            endActions = {
                                if (busy) {
                                    InfiniteProgressIndicator(size = 20.dp)
                                }
                            },
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = !busy,
                        )
                    }
                    item {
                        ArrowPreference(
                            title = stringResource(R.string.capability_workspace_public),
                            summary = publicSummary,
                            onClick = onOpenPublicAccess,
                        )
                    }
                }
            }
        }
        message?.let { current ->
            item(key = "message") {
                Card(modifier = Modifier.animateItem().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            BasicComponent(
                                title = current.text,
                                titleColor = BasicComponentDefaults.titleColor(color = if (current.isError) COUITheme.colorScheme.error else Color.Unspecified),
                                endActions = {
                                    IconButton(onClick = { message = null }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.action_close),
                                            modifier = Modifier.size(18.dp),
                                            tint = COUITheme.colorScheme.labelSecondary,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        item(key = "path") {
            val sectionText = if (path.isBlank()) stringResource(R.string.capability_workspace_files) else path
            PreferenceCategory(
                title = sectionText,
            )
        }
        val currentEntries = entries
        val hasParent = path.isNotBlank()
        if (hasParent) {
            item(key = "parent") {
                EtaPreferenceGroupItem(
                    isFirst = true,
                    isLast = currentEntries.isNullOrEmpty(),
                    hasLeadingIcon = true,
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.capability_workspace_parent),
                        startAction = { Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(24.dp), tint = COUITheme.colorScheme.primary) },
                        onClick = { path = path.substringBeforeLast('/', "") },
                    )
                }
            }
        }
        when {
            currentEntries == null -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfiniteProgressIndicator(size = 24.dp)
                }
            }
            currentEntries.isEmpty() -> item(key = "empty") {
                ListEmptyState(
                    title = stringResource(R.string.capability_workspace_empty),
                    summary = stringResource(R.string.capability_workspace_empty_summary),
                    icon = Icons.Rounded.FolderOpen,
                )
            }
            else -> itemsIndexed(currentEntries, key = { _, entry -> entry.path }) { index, entry ->
                val summary = if (entry.directory) stringResource(R.string.capability_workspace_directory)
                else stringResource(
                    R.string.capability_workspace_file_export,
                    Formatter.formatShortFileSize(context, entry.size),
                )
                val icon = if (entry.directory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile
                val onEntryClick: () -> Unit = {
                    if (entry.directory) {
                        path = entry.path
                    } else {
                        pendingExport = entry.path
                        exportLauncher.launch(entry.name)
                    }
                }
                EtaPreferenceGroupItem(
                    isFirst = index == 0 && !hasParent,
                    isLast = index == currentEntries.lastIndex,
                    hasLeadingIcon = true,
                ) {
                    ArrowPreference(
                        title = entry.name,
                        summary = summary,
                        startAction = {
                            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (entry.directory) COUITheme.colorScheme.primary else COUITheme.colorScheme.labelSecondary)
                        },
                        onClick = onEntryClick,
                        enabled = !busy,
                    )
                }
            }
        }
    }

    CouiLazyScaffoldPage(
        title = stringResource(R.string.capability_workspace),
        onBack = onBack,
        content = listContent,
    )
}

private data class WorkspaceMessage(val text: String, val isError: Boolean)
