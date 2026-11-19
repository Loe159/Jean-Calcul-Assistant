package io.github.mangi.eta.ui.screens.subagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.subagent.SubagentProfile
import io.github.mangi.eta.agent.subagent.SubagentProfileCatalog
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.SubagentSettings
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.OptionalModelPickerContent
import io.github.mangi.eta.ui.components.StatusError
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import io.github.mangi.eta.ui.model.filterGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.mangi.eta.ui.components.coui.panelBlur
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet

private data class CustomSubagentDraft(
    val originalName: String? = null,
    val name: String = "",
    val description: String = "",
    val toolsText: String = "",
    val prompt: String = "",
)

@Composable
internal fun SubagentSettingsScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val agentPrefs = remember { Prefs.localAgentPreferences() }
    val defaultEnabled = Prefs.Keys.BOOLEAN_DEFAULTS.getValue(Prefs.Keys.AGENT_SUBAGENT_ENABLED)
    var subagentEnabled by remember(agentPrefs) {
        mutableStateOf(agentPrefs?.getBoolean(Prefs.Keys.AGENT_SUBAGENT_ENABLED, defaultEnabled) ?: defaultEnabled)
    }

    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val primaryProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    val primaryModelId by RuntimeConfigRepository.selectedModelIdFlow().collectAsState(initial = null)
    val subagentSettings by RuntimeConfigRepository.subagentSettingsFlow().collectAsState(initial = SubagentSettings())

    var customProfiles by remember { mutableStateOf<List<SubagentProfile>>(emptyList()) }
    fun reloadCustomProfiles() {
        scope.launch {
            customProfiles = withContext(Dispatchers.IO) {
                SubagentProfileCatalog.listCustom()
            }
        }
    }
    LaunchedEffect(Unit) {
        RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance)
        reloadCustomProfiles()
    }

    val pickerState = remember(providers, primaryProviderId, primaryModelId) {
        AgentModelPickerProjector.project(providers, primaryProviderId, primaryModelId)
    }
    val resolvedSecondary = remember(providers, subagentSettings.modelId) {
        subagentSettings.modelId?.takeIf { it.isNotBlank() }?.let { id ->
            providers.enabledModel(id)
        }
    }
    val primaryModelName = pickerState.selectedModel?.displayName
    val secondarySummary = when {
        resolvedSecondary != null -> "${resolvedSecondary.provider.name} / ${resolvedSecondary.model.displayName}"
        !primaryModelName.isNullOrBlank() -> "Follow primary model ($primaryModelName)"
        else -> "Follow primary model"
    }

    val effectiveCapabilities = remember(providers, resolvedSecondary, primaryProviderId, primaryModelId) {
        val target = resolvedSecondary ?: providers.enabledModel(primaryModelId)
        target?.let { (provider, model) ->
            RuntimeConfigRepository.reasoningCapabilities(provider, model)
        }
    }
    val effortOptions = remember(effectiveCapabilities) {
        val efforts = effectiveCapabilities?.selectableEfforts.orEmpty()
        listOf<ReasoningEffort?>(null) + efforts
    }
    val effortLabels = remember(effortOptions) {
        effortOptions.map { effort ->
            when (effort) {
                null -> "Follow model default"
                ReasoningEffort.DEFAULT -> "Auto"
                else -> effort.displayName
            }
        }
    }
    val selectedEffortIndex = remember(effortOptions, subagentSettings.reasoningEffort) {
        val current = subagentSettings.resolvedReasoningEffort
        effortOptions.indexOf(current).takeIf { it >= 0 } ?: 0
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var inspectedProfile by remember { mutableStateOf<SubagentProfile?>(null) }
    var editorDraft by remember { mutableStateOf<CustomSubagentDraft?>(null) }
    var editorError by remember { mutableStateOf<String?>(null) }

    val onToggleSubagent: (Boolean) -> Unit = { checked ->
        val targetPrefs = agentPrefs
        if (targetPrefs != null && targetPrefs.edit().putBoolean(Prefs.Keys.AGENT_SUBAGENT_ENABLED, checked).commit()) {
            subagentEnabled = checked
            Prefs.reconcileAgentPreferences(EtaApp.serviceInstance)
            scope.launch {
                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
            }
        }
    }
    val onEffortChange: (Int) -> Unit = { index ->
        val chosen = effortOptions.getOrNull(index)
        scope.launch {
            RuntimeConfigRepository.setSubagentSettings(
                subagentSettings.copy(reasoningEffort = chosen?.wireValue),
            )
        }
    }
    val onSaveDraft: (CustomSubagentDraft) -> Unit = { activeDraft ->
        scope.launch {
            val tools = activeDraft.toolsText
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val saved = SubagentProfileCatalog.saveCustomProfile(
                        name = activeDraft.name,
                        description = activeDraft.description,
                        tools = tools,
                        prompt = activeDraft.prompt,
                    )
                    val oldName = activeDraft.originalName
                    if (oldName != null && oldName != saved.name) {
                        SubagentProfileCatalog.deleteCustomProfile(oldName)
                    }
                    saved
                }
            }
            result.fold(
                onSuccess = {
                    editorDraft = null
                    editorError = null
                    reloadCustomProfiles()
                },
                onFailure = { error ->
                    editorError = error.message ?: "Save failed"
                },
            )
        }
    }

    CouiScaffoldPage(
        title = "Subagent",
        onBack = onBack,
        actions = {
            CouiTopAppBarIconButton(
                icon = COUIIcons.Add,
                contentDescription = "New subagent",
                onClick = {
                    editorError = null
                    editorDraft = CustomSubagentDraft()
                },
            )
        },
        overlay = {
            val dark = COUITheme.isDark
            val sheetBlur = panelBlur(MaterialBlurDefaults::bottomSheet)
            OverlayBottomSheet(
                show = showModelSheet,
                title = "Subagent model",
                startAction = {
                    IconButton(onClick = { showModelSheet = false }) {
                        Icon(
                            imageVector = COUIIcons.Basic.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = COUITheme.colorScheme.labelPrimary,
                        )
                    }
                },
                backgroundModifier = sheetBlur?.backgroundModifier ?: Modifier,
                backgroundColor = sheetBlur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
                onDismissRequest = { showModelSheet = false },
                insideMargin = DpSize(16.dp, 0.dp),
                materialStroke = sheetBlur != null,
            ) {
                OptionalModelPickerContent(
                    pickerState = pickerState,
                    selectedSecondaryModelId = resolvedSecondary?.model?.id,
                    followTitle = "Follow primary model",
                    followSubtitle = primaryModelName?.let { "Current: $it" } ?: "Inherits the conversation's primary model by default",
                    onSelectModel = { modelId ->
                        showModelSheet = false
                        scope.launch {
                            RuntimeConfigRepository.setSubagentSettings(
                                subagentSettings.copy(modelId = modelId),
                            )
                        }
                    },
                )
            }

            val currentProfile = inspectedProfile
            OverlayBottomSheet(
                show = currentProfile != null,
                title = currentProfile?.name.orEmpty(),
                startAction = {
                    IconButton(onClick = { inspectedProfile = null }) {
                        Icon(
                            imageVector = COUIIcons.Basic.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = COUITheme.colorScheme.labelPrimary,
                        )
                    }
                },
                backgroundModifier = sheetBlur?.backgroundModifier ?: Modifier,
                backgroundColor = sheetBlur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
                onDismissRequest = { inspectedProfile = null },
                insideMargin = DpSize(16.dp, 0.dp),
                materialStroke = sheetBlur != null,
            ) {
                if (currentProfile != null) {
                    SubagentProfileDetailContent(
                        profile = currentProfile,
                        onEdit = if (!currentProfile.builtin) {
                            {
                                val toolsText = currentProfile.explicitTools
                                    ?.takeUnless { it == setOf("*") }
                                    ?.joinToString(", ")
                                    .orEmpty()
                                inspectedProfile = null
                                editorError = null
                                editorDraft = CustomSubagentDraft(
                                    originalName = currentProfile.name,
                                    name = currentProfile.name,
                                    description = currentProfile.summary,
                                    toolsText = toolsText,
                                    prompt = currentProfile.roleInstruction,
                                )
                            }
                        } else {
                            null
                        },
                        onDelete = if (!currentProfile.builtin) {
                            {
                                val targetName = currentProfile.name
                                inspectedProfile = null
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        SubagentProfileCatalog.deleteCustomProfile(targetName)
                                    }
                                    reloadCustomProfiles()
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            val activeDraft = editorDraft
            CouiConfirmDialog(
                visible = activeDraft != null,
                title = if (activeDraft?.originalName != null) "Edit subagent" else "New subagent",
                message = null,
                confirmText = "Save",
                onConfirm = { if (activeDraft != null) onSaveDraft(activeDraft) },
                onDismissRequest = { editorDraft = null },
            ) {
                if (activeDraft != null) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CouiTextField(
                            value = activeDraft.name,
                            onValueChange = { editorDraft = activeDraft.copy(name = it) },
                            label = "Name (kebab-case, e.g. bill-analyzer)",
                            singleLine = true,
                        )
                        CouiTextField(
                            value = activeDraft.description,
                            onValueChange = { editorDraft = activeDraft.copy(description = it) },
                            label = "Description",
                            singleLine = true,
                        )
                        CouiTextField(
                            value = activeDraft.toolsText,
                            onValueChange = { editorDraft = activeDraft.copy(toolsText = it) },
                            label = "Tools (comma-separated; blank allows all non-GUI tools)",
                            singleLine = true,
                        )
                        CouiTextField(
                            value = activeDraft.prompt,
                            onValueChange = { editorDraft = activeDraft.copy(prompt = it) },
                            label = "System prompt",
                            singleLine = false,
                            minLines = 4,
                            maxLines = 8,
                        )
                        editorError?.let { err ->
                            Text(
                                text = err,
                                style = COUITheme.textStyles.bodyS,
                                color = StatusError,
                            )
                        }
                    }
                }
            }
        },
    ) {
        PreferenceCategory(title = "General and model")
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = "Enable subagents",
                        checked = subagentEnabled,
                        enabled = agentPrefs != null,
                        onCheckedChange = onToggleSubagent,
                    )
                }
                item {
                    ArrowPreference(
                        title = "Subagent model",
                        summary = secondarySummary,
                        onClick = { showModelSheet = true },
                        enabled = subagentEnabled,
                    )
                }
                if (effortOptions.size > 1) {
                    item {
                        OverlayDropdownPreference(
                            title = "Reasoning level",
                            items = effortLabels,
                            selectedIndex = selectedEffortIndex,
                            enabled = subagentEnabled,
                            onSelectedIndexChange = onEffortChange,
                        )
                    }
                }
            }
        }

        PreferenceCategory(title = "Built-in Subagents")
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                SubagentProfile.BUILTIN_PROFILES.forEachIndexed { index, profile ->
                    item {
                        ArrowPreference(
                            title = profile.name,
                            summary = profile.summary,
                            onClick = { inspectedProfile = profile },
                        )
                    }
                    if (index < SubagentProfile.BUILTIN_PROFILES.lastIndex) {

                    }
                }
            }
        }

        PreferenceCategory(title = "Custom Subagents")
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                if (customProfiles.isEmpty()) {
                    item {
                        ListEmptyState(
                            title = "No custom subagents yet",
                            summary = "Ask Jean-Calcul to create a subagent in agents/<name>.md, or tap + to create one",
                            icon = Icons.Rounded.Extension,
                            actionText = "New subagent",
                            onActionClick = {
                                        editorError = null
                                        editorDraft = CustomSubagentDraft()
                                    },
                        )
                    }
                } else {
                    customProfiles.forEachIndexed { index, profile ->
                        item {
                            ArrowPreference(
                                title = profile.name,
                                summary = profile.summary,
                                onClick = { inspectedProfile = profile },
                            )
                        }
                        if (index < customProfiles.lastIndex) {

                        }
                    }
                }
            }
        }

        CouiPageBottomSpacer()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubagentProfileDetailContent(
    profile: SubagentProfile,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = profile.summary,
            style = COUITheme.textStyles.bodyXL,
            fontWeight = FontWeight.Medium,
            color = COUITheme.colorScheme.labelPrimary,
        )
        Text(
            text = if (profile.builtin) profile.whenToUse else profile.roleInstruction,
            style = COUITheme.textStyles.bodyM,
            color = COUITheme.colorScheme.labelSecondary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Tools",
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profile.toolHighlights.forEach { toolName ->
                    Box(
                        modifier = Modifier
                            .squircleSurface(
                                color = COUITheme.colorScheme.surfaceWithCard,
                                cornerRadius = 8.dp,
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = toolName,
                            style = COUITheme.textStyles.bodyXS.copy(fontFamily = FontFamily.Monospace),
                            color = COUITheme.colorScheme.labelSecondary,
                        )
                    }
                }
            }
        }
        if (onEdit != null || onDelete != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onDelete != null) {
                    CouiButton(
                        text = "Delete",
                        onClick = onDelete,
                        role = CouiButtonRole.Destructive,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onEdit != null) {
                    CouiButton(
                        text = "Edit",
                        onClick = onEdit,
                        role = CouiButtonRole.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
