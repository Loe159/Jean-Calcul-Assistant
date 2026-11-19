package io.github.mangi.eta.ui.screens.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberOverscrollEffect
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog
import io.github.mangi.eta.ui.components.coui.CouiScaffold
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import io.github.mangi.eta.ui.markdown.LazyStaticMarkdown
import io.github.mangi.eta.ui.markdown.MarkdownTone
import io.github.mangi.eta.ui.model.AgentMemoryAction
import io.github.mangi.eta.ui.model.AgentMemoryUiState
import java.text.NumberFormat
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.squircle.SquircleShape

private enum class MemoryViewMode { Preview, Edit }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgentMemoryScreen(
    state: AgentMemoryUiState,
    onAction: (AgentMemoryAction) -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var mode by rememberSaveable {
        mutableStateOf(
            if (!state.isLoading && state.savedContent.isBlank()) MemoryViewMode.Edit else MemoryViewMode.Preview,
        )
    }
    var initialModeResolved by rememberSaveable { mutableStateOf(!state.isLoading) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && !initialModeResolved) {
            initialModeResolved = true
            mode = if (state.savedContent.isBlank()) MemoryViewMode.Edit else MemoryViewMode.Preview
        }
    }
    val currentMode = mode
    val imeVisible = WindowInsets.isImeVisible
    val canClear = !state.isLoading && !state.isSaving && state.draft.isNotEmpty()

    CouiScaffold(
        title = stringResource(R.string.ui_memory_b55ff5),
        onBack = { onAction(AgentMemoryAction.NavigateBack) },
        compactTitle = true,
        actions = {
            CouiTopAppBarIconButton(
                icon = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.ui_clear_all_memory_a43bd3),
                enabled = canClear,
                onClick = { showClearDialog = true },
            )
        },
    ) { contentPadding, _, nestedScrollConnection, sidePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalCutoutPadding()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = sidePadding)
                .then(if (nestedScrollConnection != null) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MemorySettingsGroup(state = state, onAction = onAction)
            }
            MemoryDocumentCard(
                state = state,
                mode = currentMode,
                onModeChange = { mode = it },
                onDraftChange = { onAction(AgentMemoryAction.DraftChanged(it)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
            )
            MemorySaveBar(
                state = state,
                onDiscard = { onAction(AgentMemoryAction.DraftChanged(state.savedContent)) },
                onSave = { onAction(AgentMemoryAction.Save) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showClearDialog) {
        val onConfirmClear: () -> Unit = {
            showClearDialog = false
            onAction(AgentMemoryAction.Clear)
        }
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.ui_clear_all_memory_a43bd3),
            message = stringResource(R.string.ui_the_entire_contents_of_memory_md_will_be_deleted_and_83a8ac),
            confirmText = stringResource(R.string.memory_clear),
            confirmEnabled = !state.isSaving,
            destructive = true,
            onConfirm = onConfirmClear,
            onDismissRequest = { showClearDialog = false },
        )
    }

    state.notice?.let { notice ->
        CouiNoticeDialog(
            visible = true,
            title = stringResource(R.string.ui_memory_b55ff5),
            message = notice,
            confirmText = stringResource(R.string.ui_knew_cb63c6),
            onDismissRequest = { onAction(AgentMemoryAction.DismissNotice) },
        )
    }
}

@Composable
private fun MemorySettingsGroup(
    state: AgentMemoryUiState,
    onAction: (AgentMemoryAction) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        CardListGroup {
            item {
                SwitchPreference(
                    title = stringResource(R.string.ui_enable_memory_4b69b7),
                    summary = stringResource(R.string.ui_after_closing_no_memory_will_be_injected_and_the_mod_db3d23),
                    checked = state.enabled,
                    enabled = !state.isLoading,
                    onCheckedChange = { onAction(AgentMemoryAction.ToggleEnabled(it)) },
                )
            }
            item {
                BasicComponent(
                    title = stringResource(R.string.ui_core_memory_injection_budget_48b5d5),
                    summary = stringResource(R.string.memory_budget_summary, formatNumber(state.coreBudgetChars)),
                )
            }
        }
    }
}

@Composable
private fun MemoryDocumentCard(
    state: AgentMemoryUiState,
    mode: MemoryViewMode,
    onModeChange: (MemoryViewMode) -> Unit,
    onDraftChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    val overLimit = state.draftBytes > state.maxBytes
    val cardContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Notes,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MEMORY.md",
                    style = COUITheme.textStyles.bodyM,
                    fontWeight = FontWeight.Medium,
                    color = colors.labelPrimary,
                )
                Text(
                    text = "${formatBytes(state.draftBytes)} / ${formatBytes(state.maxBytes)}",
                    style = COUITheme.textStyles.bodyXS,
                    color = if (overLimit) colors.error else colors.labelSecondary,
                )
            }
            MemoryModeSwitch(mode = mode, enabled = !state.isLoading, onModeChange = onModeChange)
        }
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator(size = 24.dp)
                }
                else -> Crossfade(targetState = mode, label = "memory_mode") { current ->
                    when (current) {
                        MemoryViewMode.Preview -> MemoryPreview(
                            content = state.draft,
                            onStartEditing = { onModeChange(MemoryViewMode.Edit) },
                        )
                        MemoryViewMode.Edit -> MemoryEditor(
                            value = state.draft,
                            enabled = !state.isSaving,
                            onValueChange = onDraftChange,
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(24.dp))
            .background(COUITheme.colorScheme.card),
    ) {
        cardContent()
    }
}

@Composable
private fun MemoryPreview(content: String, onStartEditing: () -> Unit) {
    if (content.isBlank()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onStartEditing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.memory_empty_title),
                style = COUITheme.textStyles.bodyXL,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.labelPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ui_core_memory_user_name_long_term_preferences_aa6ff9),
                style = COUITheme.textStyles.bodyM,
                color = COUITheme.colorScheme.labelSecondary,
            )
        }
        return
    }
    // The theme's LocalOverscrollFactory is the COUI overscroll.
    val overscroll = rememberOverscrollEffect()
    LazyStaticMarkdown(
        content = content,
        tone = MarkdownTone.Answer,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        overscrollEffect = overscroll,
    )
}

@Composable
private fun MemoryEditor(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val colors = COUITheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null) { focusRequester.requestFocus() }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = stringResource(R.string.ui_core_memory_user_name_long_term_preferences_aa6ff9),
                style = MemoryTextStyle(),
                color = colors.labelSecondary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = MemoryTextStyle().copy(color = colors.labelPrimary),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
    }
}

@Composable
private fun MemoryTextStyle() = COUITheme.textStyles.bodyM.copy(lineHeight = 22.sp)

@Composable
private fun MemoryModeSwitch(
    mode: MemoryViewMode,
    enabled: Boolean,
    onModeChange: (MemoryViewMode) -> Unit,
) {
    val colors = COUITheme.colorScheme
    Row(
        modifier = Modifier
            .squircleSurface(color = colors.cardPressed, cornerRadius = 12.dp)
            .padding(3.dp),
    ) {
        MemoryViewMode.entries.forEach { option ->
            val selected = option == mode
            val background by animateColorAsState(
                if (selected) colors.background else colors.cardPressed,
                label = "memory_mode_tab",
            )
            Box(
                modifier = Modifier
                    .squircleSurface(color = background, cornerRadius = 9.dp)
                    .clickable(enabled = enabled && !selected) { onModeChange(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (option == MemoryViewMode.Preview) R.string.memory_mode_preview else R.string.memory_mode_edit,
                    ),
                    style = COUITheme.textStyles.bodyS,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) colors.labelPrimary else colors.labelSecondary,
                )
            }
        }
    }
}

@Composable
private fun MemorySaveBar(
    state: AgentMemoryUiState,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
) {
    val overLimit = state.draftBytes > state.maxBytes
    AnimatedVisibility(
        visible = !state.isLoading && (state.hasUnsavedChanges || state.isSaving),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (overLimit) COUITheme.colorScheme.error else COUITheme.colorScheme.primary,
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(if (overLimit) R.string.memory_over_limit else R.string.memory_unsaved_changes),
                    style = COUITheme.textStyles.bodyS,
                    color = if (overLimit) COUITheme.colorScheme.error else COUITheme.colorScheme.labelSecondary,
                    maxLines = 2,
                )
            }
            val discardText = stringResource(R.string.memory_discard)
            val saveText = stringResource(if (state.isSaving) R.string.memory_saving else R.string.memory_save)
            CouiButton(
                text = discardText,
                enabled = !state.isSaving,
                onClick = onDiscard,
                minHeight = 36.dp,
            )
            CouiButton(
                text = saveText,
                enabled = state.canSave,
                onClick = onSave,
                role = CouiButtonRole.Primary,
                minHeight = 36.dp,
            )
        }
    }
}

private fun formatBytes(bytes: Int): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_024 * 1_024 -> "%.1f KiB".format(bytes / 1_024.0)
    bytes % (1_024 * 1_024) == 0 -> "${bytes / (1_024 * 1_024)} MiB"
    else -> "%.2f MiB".format(bytes / (1_024.0 * 1_024.0))
}

private fun formatNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)
