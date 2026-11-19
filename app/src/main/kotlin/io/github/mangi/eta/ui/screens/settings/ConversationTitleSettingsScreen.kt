package io.github.mangi.eta.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ConversationTitleSettings
import io.github.mangi.eta.data.model.ReasoningEffort
import io.github.mangi.eta.data.model.enabledModel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.components.OptionalModelPickerContent
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.model.AgentModelPickerProjector
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.mangi.eta.ui.components.coui.panelBlur
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet

@Composable
internal fun ConversationTitleSettingsScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val primaryProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    val primaryModelId by RuntimeConfigRepository.selectedModelIdFlow().collectAsState(initial = null)
    val titleSettings by RuntimeConfigRepository.conversationTitleSettingsFlow()
        .collectAsState(initial = ConversationTitleSettings())

    LaunchedEffect(Unit) {
        RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance)
    }

    val pickerState = remember(providers, primaryProviderId, primaryModelId) {
        AgentModelPickerProjector.project(providers, primaryProviderId, primaryModelId)
    }
    val resolvedCustom = remember(providers, titleSettings.modelId) {
        titleSettings.modelId?.takeIf { it.isNotBlank() }?.let { id ->
            providers.enabledModel(id)
        }
    }
    val primaryModelName = pickerState.selectedModel?.displayName
    val followTitle = stringResource(R.string.conversation_title_settings_follow_conversation)
    val followSubtitle = primaryModelName?.let {
        stringResource(R.string.conversation_title_settings_follow_conversation_with_name, it)
    } ?: stringResource(R.string.conversation_title_settings_follow_conversation_hint)
    val modelSummary = when {
        resolvedCustom != null -> "${resolvedCustom.provider.name} / ${resolvedCustom.model.displayName}"
        !primaryModelName.isNullOrBlank() ->
            stringResource(R.string.conversation_title_settings_follow_conversation_with_name, primaryModelName)
        else -> followTitle
    }

    val effectiveCapabilities = remember(providers, resolvedCustom, primaryModelId) {
        val target = resolvedCustom ?: providers.enabledModel(primaryModelId)
        target?.let { (provider, model) ->
            RuntimeConfigRepository.reasoningCapabilities(provider, model)
        }
    }
    val effortOptions = remember(effectiveCapabilities) {
        val efforts = effectiveCapabilities?.selectableEfforts.orEmpty()
        listOf<ReasoningEffort?>(null) + efforts
    }
    val autoMinimalLabel = stringResource(R.string.conversation_title_settings_reasoning_auto_minimal)
    val providerDefaultLabel = stringResource(R.string.conversation_title_settings_reasoning_provider_default)
    val effortLabels = remember(effortOptions, autoMinimalLabel, providerDefaultLabel) {
        effortOptions.map { effort ->
            when (effort) {
                null -> autoMinimalLabel
                ReasoningEffort.DEFAULT -> providerDefaultLabel
                else -> effort.displayName
            }
        }
    }
    val selectedEffortIndex = remember(effortOptions, titleSettings.reasoningEffort) {
        val current = titleSettings.resolvedReasoningEffort
        effortOptions.indexOf(current).takeIf { it >= 0 } ?: 0
    }

    var showModelSheet by remember { mutableStateOf(false) }

    val onToggleEnabled: (Boolean) -> Unit = { checked ->
        scope.launch {
            RuntimeConfigRepository.setConversationTitleSettings(
                titleSettings.copy(enabled = checked),
            )
        }
    }
    val onSelectModel: (String?) -> Unit = { modelId ->
        showModelSheet = false
        val nextTarget = modelId?.let { providers.enabledModel(it) } ?: providers.enabledModel(primaryModelId)
        val nextCapabilities = nextTarget?.let { (provider, model) ->
            RuntimeConfigRepository.reasoningCapabilities(provider, model)
        }
        val currentEffort = titleSettings.resolvedReasoningEffort
        val keepEffort = currentEffort != null &&
            nextCapabilities?.selectableEfforts?.contains(currentEffort) == true
        scope.launch {
            RuntimeConfigRepository.setConversationTitleSettings(
                titleSettings.copy(
                    modelId = modelId,
                    reasoningEffort = if (keepEffort) currentEffort.wireValue else null,
                ),
            )
        }
    }
    val onEffortChange: (Int) -> Unit = { index ->
        val chosen = effortOptions.getOrNull(index)
        scope.launch {
            RuntimeConfigRepository.setConversationTitleSettings(
                titleSettings.copy(reasoningEffort = chosen?.wireValue),
            )
        }
    }

    val pageTitle = stringResource(R.string.conversation_title_settings_title)
    val groupTitle = stringResource(R.string.conversation_title_settings_group)
    val enabledTitle = stringResource(R.string.conversation_title_settings_enabled)
    val modelTitle = stringResource(R.string.conversation_title_settings_model)
    val effortTitle = stringResource(R.string.conversation_title_settings_reasoning_effort)
    val unsupportedSummary = stringResource(R.string.conversation_title_settings_reasoning_unsupported)
    val noteText = stringResource(R.string.conversation_title_settings_note)

    CouiScaffoldPage(
        title = pageTitle,
        onBack = onBack,
        overlay = {
            val dark = COUITheme.isDark
            val sheetBlur = panelBlur(MaterialBlurDefaults::bottomSheet)
            OverlayBottomSheet(
                show = showModelSheet,
                title = modelTitle,
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
                    selectedSecondaryModelId = resolvedCustom?.model?.id,
                    followTitle = followTitle,
                    followSubtitle = followSubtitle,
                    onSelectModel = onSelectModel,
                )
            }
        },
    ) {
        PreferenceCategory(title = groupTitle)
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = enabledTitle,
                        checked = titleSettings.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                }
                item {
                    ArrowPreference(
                        title = modelTitle,
                        summary = modelSummary,
                        onClick = { showModelSheet = true },
                        enabled = titleSettings.enabled,
                    )
                }
                if (effortOptions.size > 1) {
                    item {
                        OverlayDropdownPreference(
                            title = effortTitle,
                            items = effortLabels,
                            selectedIndex = selectedEffortIndex,
                            enabled = titleSettings.enabled,
                            onSelectedIndexChange = onEffortChange,
                        )
                    }
                } else {
                    item {
                        BasicComponent(
                            title = effortTitle,
                            summary = unsupportedSummary,
                            enabled = false,
                        )
                    }
                }
            }
        }
        PreferenceFooter(text = noteText)
        CouiPageBottomSpacer()
    }
}
