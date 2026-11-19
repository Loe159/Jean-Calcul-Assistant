package io.github.mangi.eta.ui.screens.skills

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.model.AgentSkillsAction
import io.github.mangi.eta.ui.model.AgentSkillsUiState
import io.github.mangi.eta.ui.model.SkillItemUi
import io.github.mangi.eta.ui.model.canDeleteUserSkill
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

@Composable
fun AgentSkillsScreen(
    state: AgentSkillsUiState,
    onAction: (AgentSkillsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteTarget by remember { mutableStateOf<SkillItemUi?>(null) }
    val operationPending = state.isImporting || state.busySkillId != null
    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onAction(AgentSkillsAction.ImportZip(uri.toString()))
    }
    val openZipPicker = {
        zipPicker.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
            ),
        )
    }

    val installed = state.skills.filter { it.installed }
    val builtinInstalled = installed.filter { it.source == "builtin" }
    val userInstalled = installed.filter { it.canDeleteUserSkill }
    val removed = state.skills.filter { !it.installed }

    CouiScaffoldPage(
        title = stringResource(R.string.route_skills),
        onBack = { onAction(AgentSkillsAction.NavigateBack) },
        modifier = modifier,
    ) {
        PreferenceCategory(title = stringResource(R.string.ui_install_087db6))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = if (state.isImporting) stringResource(R.string.skills_checking_package) else stringResource(R.string.skills_import_zip),
                        summary = if (state.isImporting) {
                            stringResource(R.string.skills_installing_package)
                        } else {
                            stringResource(R.string.skills_choose_package)
                        },
                        endActions = {
                            if (state.isImporting) {
                                InfiniteProgressIndicator(size = 20.dp)
                            }
                        },
                        onClick = openZipPicker,
                        enabled = !operationPending,
                    )
                }
            }
        }

        if (builtinInstalled.isNotEmpty()) {
            PreferenceCategory(title = stringResource(R.string.ui_built_in_skills_1ceedf))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    builtinInstalled.forEachIndexed { index, skill ->
                        item {
                            SkillSwitchRow(
                                skill = skill,
                                enabled = !operationPending,
                                onToggle = { enabled ->
                                    onAction(AgentSkillsAction.ToggleSkill(skill.id, enabled))
                                },
                            )
                        }
                        if (index < builtinInstalled.lastIndex) {

                        }
                    }
                }
            }
        }

        if (userInstalled.isNotEmpty()) {
            PreferenceCategory(title = stringResource(R.string.ui_user_skills_748e7f))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    userInstalled.forEachIndexed { index, skill ->
                        item {
                            SkillSwitchRow(
                                skill = skill,
                                enabled = !operationPending,
                                onToggle = { enabled ->
                                    onAction(AgentSkillsAction.ToggleSkill(skill.id, enabled))
                                },
                                onDelete = { deleteTarget = skill },
                            )
                        }
                        if (index < userInstalled.lastIndex) {

                        }
                    }
                }
            }
        }

        if (removed.isNotEmpty()) {
            PreferenceCategory(title = stringResource(R.string.ui_removed_4e5c49))
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    removed.forEachIndexed { index, skill ->
                        item {
                            ArrowPreference(
                                title = skill.name,
                                summary = stringResource(R.string.ui_click_to_reinstall_dc60de),
                                onClick = {
                                    onAction(AgentSkillsAction.ReinstallBuiltin(skill.id))
                                },
                                enabled = !operationPending,
                            )
                        }
                        if (index < removed.lastIndex) {

                        }
                    }
                }
            }
        }

        if (state.skills.isEmpty() && state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator(size = 24.dp)
            }
        }
        if (state.skills.isEmpty() && !state.isLoading) {
            ListEmptyState(
                title = stringResource(R.string.ui_no_skills_installed_yet_4e960f),
                summary = stringResource(R.string.skills_choose_package),
                icon = Icons.Rounded.FolderZip,
                actionText = stringResource(R.string.skills_import_zip),
                onActionClick = if (!operationPending) openZipPicker else null,
            )
        }

        CouiPageBottomSpacer()
    }

    state.replacement?.let { replacement ->
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.ui_replace_user_skills_250e98),
            message = stringResource(R.string.skills_replace_summary, replacement.name, replacement.id),
            confirmText = stringResource(R.string.skills_replace),
            confirmEnabled = !operationPending,
            onConfirm = { onAction(AgentSkillsAction.ConfirmZipReplacement) },
            onDismissRequest = { onAction(AgentSkillsAction.CancelZipReplacement) },
        )
    }

    deleteTarget?.let { skill ->
        val onConfirmDelete: () -> Unit = {
            deleteTarget = null
            onAction(AgentSkillsAction.DeleteSkill(skill.id))
        }
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.ui_delete_user_skills_f319a9),
            message = stringResource(R.string.skills_delete_summary, skill.name),
            confirmText = stringResource(R.string.ui_delete_3755f5),
            confirmEnabled = !operationPending,
            destructive = true,
            onConfirm = onConfirmDelete,
            onDismissRequest = { deleteTarget = null },
        )
    }

    state.notice?.let { notice ->
        CouiNoticeDialog(
            visible = true,
            title = notice.title,
            message = notice.message,
            confirmText = stringResource(R.string.ui_knew_cb63c6),
            onDismissRequest = { onAction(AgentSkillsAction.DismissNotice) },
        )
    }
}
