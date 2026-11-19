package io.github.mangi.eta.ui.screens.tools

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.EtaPreferenceGroupItem
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiLazyScaffoldPage
import io.github.mangi.eta.ui.model.AgentToolsAction
import io.github.mangi.eta.ui.model.AgentToolsUiState
import io.github.mangi.eta.ui.model.ToolGroupUi
import io.github.mangi.eta.ui.model.projectToolGroups
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import androidx.compose.foundation.LocalIndication

private const val ALL_GROUP_FILTER = "__all__"

@Composable
fun AgentToolsScreen(
    state: AgentToolsUiState,
    onAction: (AgentToolsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    remember(context.applicationContext) {
        Prefs.initLocal(context)
        true
    }
    val localPrefs = remember { Prefs.localAgentPreferences() }
    var disabledTools by remember { mutableStateOf(Prefs.disabledTools(localPrefs)) }
    DisposableEffect(localPrefs) {
        val target = localPrefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key == Prefs.Keys.AGENT_DISABLED_TOOLS_JSON) {
                disabledTools = Prefs.disabledTools(changed)
            }
        }
        target.registerOnSharedPreferenceChangeListener(listener)
        onDispose { target.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val setToolEnabled: (String, Boolean) -> Unit = { toolId, enabled ->
        val canonical = Prefs.canonicalToolSwitchId(toolId)
        val next = disabledTools.toMutableSet().apply {
            if (enabled) {
                remove(toolId)
                remove(canonical)
            } else {
                add(canonical)
            }
        }
        disabledTools = next
        Prefs.setToolEnabled(toolId, enabled, EtaApp.serviceInstance)
    }

    val setGroupEnabled: (Collection<String>, Boolean) -> Unit = { toolIds, enabled ->
        val next = disabledTools.toMutableSet().apply {
            toolIds.forEach { id ->
                val canonical = Prefs.canonicalToolSwitchId(id)
                if (enabled) {
                    remove(id)
                    remove(canonical)
                } else {
                    add(canonical)
                }
            }
        }
        disabledTools = next
        Prefs.setToolsEnabled(toolIds, enabled, EtaApp.serviceInstance)
    }

    val capabilities = rememberDeviceCapabilities()
    var showAll by rememberSaveable { mutableStateOf(false) }
    var selectedGroupId by rememberSaveable { mutableStateOf(ALL_GROUP_FILTER) }
    val currentListState = rememberLazyListState()
    val allListState = rememberLazyListState()
    val visibleGroups = projectToolGroups(
        state.groups,
        showAll,
        capabilities.root.isGranted,
        capabilities.tools.colorOs,
    )
    val activeFilterId = if (selectedGroupId == ALL_GROUP_FILTER || visibleGroups.any { it.id == selectedGroupId }) {
        selectedGroupId
    } else {
        ALL_GROUP_FILTER
    }
    val displayedGroups = if (activeFilterId == ALL_GROUP_FILTER) {
        visibleGroups
    } else {
        visibleGroups.filter { it.id == activeFilterId }
    }
    val tabs = listOf(
        false to stringResource(R.string.capability_current_device),
        true to stringResource(R.string.capability_all),
    )

    CouiLazyScaffoldPage(
        title = stringResource(R.string.ui_tool_ability_9f0f80),
        onBack = { onAction(AgentToolsAction.NavigateBack) },
        modifier = modifier,
        listState = if (showAll) allListState else currentListState,
    ) {
        item(key = "capability-view") {
            CouiChipTabs(
                items = tabs,
                selectedKey = showAll,
                onSelectedKeyChange = { showAll = it },
            )
        }
        item(key = "category-filter") {
            ToolCategoryFilterStrip(
                groups = visibleGroups,
                disabledTools = disabledTools,
                selectedGroupId = activeFilterId,
                onSelectGroup = { selectedGroupId = it },
            )
        }
        if (activeFilterId == ALL_GROUP_FILTER) {
            item(key = "capability-discovery") {
                Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                    CardListGroup {
                        item {
                            ArrowPreference(
                                title = stringResource(R.string.capability_enhancements),
                                summary = stringResource(R.string.capability_enhancements_summary),
                                onClick = { onAction(AgentToolsAction.OpenEnhancements) },
                            )
                        }
                    }
                }
            }
        }
        displayedGroups.forEach { group ->
            val enabledCount = group.tools.count { Prefs.isToolEnabledInSet(it.id, disabledTools) }
            val allEnabled = enabledCount == group.tools.size
            item(key = "${group.id}-title") {
                PreferenceCategory(
                    title = "${group.title} · $enabledCount/${group.tools.size}",
                    endWidget = {
                        GroupBatchTogglePill(
                            allEnabled = allEnabled,
                            onToggleAll = {
                                setGroupEnabled(group.tools.map { it.id }, !allEnabled)
                            },
                        )
                    },
                )
            }
            itemsIndexed(
                items = group.tools,
                key = { _, tool -> "${group.id}-${tool.id}" },
            ) { index, tool ->
                EtaPreferenceGroupItem(
                    isFirst = index == 0,
                    isLast = index == group.tools.lastIndex,
                    hasLeadingIcon = true,
                ) {
                    ToolSwitchRow(
                        tool = tool,
                        checked = Prefs.isToolEnabledInSet(tool.id, disabledTools),
                        onCheckedChange = { enabled -> setToolEnabled(tool.id, enabled) },
                        rootGranted = capabilities.root.isGranted,
                        capabilities = capabilities.tools,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCategoryFilterStrip(
    groups: List<ToolGroupUi>,
    disabledTools: Set<String>,
    selectedGroupId: String,
    onSelectGroup: (String) -> Unit,
) {
    val totalCount = groups.sumOf { it.tools.size }
    val totalEnabled = groups.sumOf { group ->
        group.tools.count { Prefs.isToolEnabledInSet(it.id, disabledTools) }
    }
    val chips = remember(groups, totalEnabled, totalCount, disabledTools) {
        buildList {
            add(ALL_GROUP_FILTER to "All $totalEnabled/$totalCount")
            groups.forEach { group ->
                val enabled = group.tools.count { Prefs.isToolEnabledInSet(it.id, disabledTools) }
                add(group.id to "${group.title} $enabled/${group.tools.size}")
            }
        }
    }
    val horizontalPadding = 16.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (key, label) ->
            val selected = key == selectedGroupId
            val source = remember { MutableInteractionSource() }
            val dark = COUITheme.isDark
            Box(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) COUITheme.colorScheme.primary else COUITheme.colorScheme.card,
                        CircleShape,
                    )
                    .selectable(
                        selected = selected,
                        interactionSource = source,
                        indication = LocalIndication.current,
                        enabled = true,
                        role = Role.Tab,
                        onClick = { onSelectGroup(key) },
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = COUITheme.textStyles.bodyS,
                    color = if (selected) Color.White else COUITheme.colorScheme.labelPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun GroupBatchTogglePill(
    allEnabled: Boolean,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = if (allEnabled) "Disable all" else "Enable all"
    val source = remember { MutableInteractionSource() }
    val dark = COUITheme.isDark
    val textColor = when {
        !enabled -> COUITheme.colorScheme.disabledNeutral
        allEnabled -> COUITheme.colorScheme.labelSecondary
        else -> COUITheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onToggleAll,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = COUITheme.textStyles.bodyXS,
            color = textColor,
            maxLines = 1,
        )
    }
}
