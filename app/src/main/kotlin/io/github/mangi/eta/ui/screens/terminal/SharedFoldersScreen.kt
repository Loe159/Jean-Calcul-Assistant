package io.github.mangi.eta.ui.screens.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.terminal.AlpineEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.SharedFolderMount
import io.github.mangi.eta.agent.terminal.SharedFolderMounts
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.runOneShotShell
import io.github.mangi.eta.agent.terminal.shellQuote
import io.github.mangi.eta.ui.components.EtaMonospace
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.suqi8.coui.kmp.basic.CardListGroup

@Composable
internal fun SharedFoldersScreen(
    context: Context,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val shellSupervisor = remember { ShellProcessSupervisor() }
    DisposableEffect(Unit) {
        onDispose { shellSupervisor.beginClosing() }
    }
    val rootfsPaths = remember(context.applicationContext) {
        listOf(
            AlpineEnvironmentPaths.rootfsDir(context.applicationContext).absolutePath,
            LinuxEnvironmentPaths.rootfsDir(
                context.applicationContext,
                LinuxDistribution.DEBIAN,
            ).absolutePath,
        )
    }

    var mounts by remember { mutableStateOf(SharedFolderMounts.current()) }
    var sourceExists by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var showPicker by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SharedFolderMount?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val publicAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) showPicker = true
        else notice = context.getString(R.string.capability_workspace_public_denied)
    }

    fun refreshSources(list: List<SharedFolderMount>) {
        if (list.isEmpty()) {
            sourceExists = emptyMap()
            return
        }
        coroutineScope.launch {
            sourceExists = withContext(Dispatchers.IO) { probeSources(shellSupervisor, list) }
        }
    }

    LaunchedEffect(Unit) { refreshSources(mounts) }

    val onAddMountClick: () -> Unit = {
        if (TerminalRuntime.rootAvailable || Environment.isExternalStorageManager()) {
            showPicker = true
        } else {
            try {
                publicAccessLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            } catch (_: android.content.ActivityNotFoundException) {
                notice = context.getString(R.string.capability_workspace_public_denied)
            }
        }
    }

    CouiScaffoldPage(
        title = stringResource(R.string.shared_folders_title),
        onBack = onBack,
    ) {
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                if (mounts.isEmpty()) {
                    item {
                        BasicComponent(
                            title = stringResource(R.string.shared_folders_empty),
                            summary = stringResource(R.string.shared_folders_entry_summary),
                        )
                    }
                }
                mounts.forEach { mount ->
                    item {
                        val missing = sourceExists[mount.sourcePath] == false
                                        BasicComponent(
                                            title = mount.name,
                                            summary = buildString {
                                                append(mount.sourcePath)
                                                append("\n")
                                                append(
                                                    context.getString(
                                                        R.string.shared_folders_mount_point,
                                                        "${SharedFolderMounts.LINUX_MOUNTS_ROOT}/${mount.name}",
                                                    ),
                                                )
                                                if (missing) {
                                                    append(" · ")
                                                    append(context.getString(R.string.shared_folders_source_missing))
                                                }
                                            },
                                            summaryColor = BasicComponentDefaults.summaryColor(color = if (missing) COUITheme.colorScheme.error else Color.Unspecified),
                                            endActions = {
                                                IconButton(onClick = { removeTarget = mount }) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = stringResource(R.string.action_delete),
                                                        modifier = Modifier.size(20.dp),
                                                        tint = COUITheme.colorScheme.labelSecondary,
                                                    )
                                                }
                                            },
                                        )
                    }
                }
                item {
                    BasicComponent(
                        title = stringResource(R.string.shared_folders_add),
                        titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.primary),
                        onClick = onAddMountClick,
                    )
                }
            }
        }

        notice?.let { message ->
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        BasicComponent(
                            title = message,
                            titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.error),
                            endActions = {
                                IconButton(onClick = { notice = null }) {
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

        PreferenceFooter(text = stringResource(R.string.shared_folders_footer))
        CouiPageBottomSpacer()
    }

    if (showPicker) {
        SharedFolderPickerDialog(
            context = context,
            supervisor = shellSupervisor,
            existing = mounts,
            extraForbiddenRoots = rootfsPaths,
            onDismiss = { showPicker = false },
            onConfirm = { source, name ->
                val updated = mounts + SharedFolderMount(name = name, sourcePath = source)
                if (SharedFolderMounts.save(updated)) {
                    mounts = updated
                    refreshSources(updated)
                    showPicker = false
                } else {
                    notice = context.getString(R.string.shared_folders_error_save)
                }
            },
        )
    }

    removeTarget?.let { target ->
        val message = stringResource(R.string.shared_folders_remove_message, target.name, target.sourcePath)
        val onConfirmRemove: () -> Unit = {
            val updated = mounts.filterNot { it.name == target.name }
            if (SharedFolderMounts.save(updated)) {
                mounts = updated
                removeTarget = null
                refreshSources(updated)
                coroutineScope.launch(Dispatchers.IO) {
                    runOneShotShell(
                        processSupervisor = shellSupervisor,
                        identity = TerminalRuntime.defaultIdentity(TerminalEnvironment.ANDROID),
                        command = "rmdir " +
                            shellQuote("${SharedFolderMounts.ANDROID_MOUNTS_ROOT}/${target.name}") +
                            " 2>/dev/null",
                        timeoutSeconds = 10,
                    )
                }
            } else {
                notice = context.getString(R.string.shared_folders_error_save)
                removeTarget = null
            }
        }
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.shared_folders_remove_title),
            message = message,
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = onConfirmRemove,
            onDismissRequest = { removeTarget = null },
        )
    }
}

private fun probeSources(
    supervisor: ShellProcessSupervisor,
    mounts: List<SharedFolderMount>,
): Map<String, Boolean> {
    val script = mounts.joinToString("\n") { mount ->
        "if [ -d ${shellQuote(mount.sourcePath)} ]; then echo '${mount.name} 1'; else echo '${mount.name} 0'; fi"
    }
    val result = runOneShotShell(
        processSupervisor = supervisor,
        identity = TerminalRuntime.defaultIdentity(TerminalEnvironment.ANDROID),
        command = script,
        timeoutSeconds = 15,
    )
    if (result.exitCode != 0) return emptyMap()
    return result.output.decodeToString().lineSequence().mapNotNull { line ->
        val parts = line.trim().split(" ")
        if (parts.size == 2) parts[0] to (parts[1] == "1") else null
    }.toMap()
}

@Composable
private fun SharedFolderPickerDialog(
    context: Context,
    supervisor: ShellProcessSupervisor,
    existing: List<SharedFolderMount>,
    extraForbiddenRoots: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (sourcePath: String, name: String) -> Unit,
) {
    var path by remember { mutableStateOf("/sdcard") }
    var pathInput by remember { mutableStateOf("/sdcard") }
    var entries by remember { mutableStateOf<List<String>?>(null) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var nameInput by remember { mutableStateOf("sdcard") }
    var nameTouched by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun navigateTo(target: String) {
        val normalized = SharedFolderMounts.normalizeSourcePath(target) ?: "/"
        path = normalized
        pathInput = normalized
        browseError = null
        formError = null
        if (!nameTouched) nameInput = SharedFolderMounts.defaultName(normalized)
    }

    LaunchedEffect(path) {
        entries = null
        val result = withContext(Dispatchers.IO) {
            runOneShotShell(
                processSupervisor = supervisor,
                identity = TerminalRuntime.defaultIdentity(TerminalEnvironment.ANDROID),
                command = "cd ${shellQuote(path)} && find . -mindepth 1 -maxdepth 1 -type d",
                timeoutSeconds = 15,
            )
        }
        if (result.exitCode != 0) {
            browseError = context.getString(R.string.shared_folders_browse_unavailable)
            entries = emptyList()
        } else {
            browseError = null
            entries = result.output.decodeToString().lineSequence()
                .map { it.removePrefix("./") }
                .filter { it.isNotBlank() && !it.startsWith(".") }
                .sorted()
                .toList()
        }
    }

    val onValidateAndConfirm: () -> Unit = {
        val sourceError = SharedFolderMounts.validateSource(path, existing, extraForbiddenRoots)
        val errorText = when (sourceError) {
            SharedFolderMounts.SourceError.INVALID_PATH ->
                context.getString(R.string.shared_folders_error_path_invalid)
            SharedFolderMounts.SourceError.FORBIDDEN_ROOT ->
                context.getString(R.string.shared_folders_error_path_forbidden)
            SharedFolderMounts.SourceError.DUPLICATE ->
                context.getString(R.string.shared_folders_error_path_duplicate)
            null -> when (SharedFolderMounts.validateName(nameInput, existing)) {
                SharedFolderMounts.NameError.INVALID ->
                    context.getString(R.string.shared_folders_error_name_invalid)
                SharedFolderMounts.NameError.DUPLICATE ->
                    context.getString(R.string.shared_folders_error_name_duplicate)
                null -> if (existing.size >= SharedFolderMounts.MAX_MOUNTS) {
                    context.getString(
                        R.string.shared_folders_error_limit,
                        SharedFolderMounts.MAX_MOUNTS,
                    )
                } else {
                    null
                }
            }
        }
        if (errorText != null) {
            formError = errorText
        } else {
            onConfirm(path, nameInput.trim())
        }
    }
    val bodyContent: @Composable () -> Unit = {
        CouiTextField(
            value = pathInput,
            onValueChange = { pathInput = it },
            label = stringResource(R.string.shared_folders_path_label),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { navigateTo(pathInput) }),
        )
        if (browseError != null) {
            Text(
                text = browseError.orEmpty(),
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .padding(top = 8.dp),
        ) {
            if (path != "/") {
                item(key = "..") {
                    PickerRow(
                        label = "../",
                        onClick = {
                            navigateTo(path.trimEnd('/').substringBeforeLast('/').ifBlank { "/" })
                        },
                    )
                }
            }
            when {
                entries == null -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        InfiniteProgressIndicator(size = 20.dp)
                    }
                }
                entries.orEmpty().isEmpty() && browseError == null -> item(key = "empty") {
                    Text(
                        text = stringResource(R.string.shared_folders_picker_no_subfolders),
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
                else -> items(entries.orEmpty(), key = { it }) { entry ->
                    PickerRow(
                        label = entry,
                        onClick = { navigateTo(path.trimEnd('/') + "/" + entry) },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.shared_folders_selected_source, path),
            style = COUITheme.textStyles.bodyS.copy(fontFamily = EtaMonospace),
            color = COUITheme.colorScheme.labelSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        CouiTextField(
            value = nameInput,
            onValueChange = {
                nameInput = it
                nameTouched = true
                formError = null
            },
            label = stringResource(R.string.shared_folders_name_label),
            singleLine = true,
            modifier = Modifier.padding(top = 8.dp),
        )
        formError?.let { error ->
            Text(
                text = error,
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    CouiConfirmDialog(
        visible = true,
        title = stringResource(R.string.shared_folders_picker_title),
        message = null,
        confirmText = stringResource(R.string.shared_folders_add),
        onConfirm = onValidateAndConfirm,
        onDismissRequest = onDismiss,
    ) {
        bodyContent()
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = COUITheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = COUITheme.textStyles.bodyM.copy(fontFamily = EtaMonospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
