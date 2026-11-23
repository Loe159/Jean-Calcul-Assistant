package io.github.mangi.eta.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import io.github.mangi.eta.ui.components.coui.PreferenceValueText
import io.github.mangi.eta.ui.components.coui.rememberPageBackdrop
import io.github.mangi.eta.ui.components.coui.rememberPopupMaterialBlur
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.menu.OverlayContextMenu
import io.github.suqi8.coui.kmp.menu.OverlayIconDropdownMenu
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.LocalIndication
import io.github.suqi8.coui.kmp.squircle.SquircleShape

@Stable
private class PaneBarInsets {
    var top by mutableStateOf(0.dp)
    var bottom by mutableStateOf(0.dp)
}

@Composable
internal fun ConversationPanePanel(
    state: ConversationPaneUiState,
    width: Dp,
    listState: LazyListState,
    isRevealed: Boolean,
    activeModelLabel: String?,
    hasPermissionAttention: Boolean,
    onSearchChange: (String) -> Unit,
    onNewConversation: () -> Unit,
    onConversationSelected: (String) -> Unit,
    onConversationTogglePin: (ConversationSummaryUi) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFood: (() -> Unit)? = null,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // state.conversations 已由 AgentAppState 按标题、预览与消息内容过滤。
    val query = state.searchQuery.trim()
    val isSearching = query.isNotBlank()
    val groups = remember(state.conversations) { state.conversations.groupForDrawer() }
    val containerColor = conversationPaneContainerColor()
    val backdrop = rememberPageBackdrop(containerColor)
    val popupBlur = rememberPopupMaterialBlur(backdrop)
    val barInsets = remember { PaneBarInsets() }
    val bodyCaptureModifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(containerColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 测量顶栏与底栏真实高度（含安全区与 IME），同步给滚动画布的 contentPadding，
            // 使列表自然穿梭于顶底浮层之下并分别被各自 backdrop 采样，同时避免 Subcompose 重组开销。
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(containerColor)
                            .then(bodyCaptureModifier),
                    ) {
                        CompositionLocalProvider(LocalPopupMaterialBlur provides popupBlur) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                                )
                                .scrollEndHaptic()
                                .overScrollVertical(),
                            contentPadding = PaddingValues(
                                top = barInsets.top + 10.dp,
                                bottom = barInsets.bottom + 10.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ConversationPanelMetrics.RowGap),
                            overscrollEffect = null,
                        ) {
                            if (!isSearching) {
                                item(key = "pane_workbench") {
                                    PaneWorkbenchSection(
                                        activeModelLabel = activeModelLabel,
                                        onOpenModelProviders = onOpenModelProviders,
                                        onOpenCharacters = onOpenCharacters,
                                        onOpenTools = onOpenTools,
                                        onOpenSkills = onOpenSkills,
                                        onOpenFood = onOpenFood,
                                    )
                                }
                            }

                            if (state.conversations.isEmpty()) {
                                item(key = "pane_empty_conversations") {
                                    EmptyConversations(isSearching = isSearching)
                                }
                            } else {
                                groups.forEachIndexed { groupIndex, group ->
                                    item(key = "section-${group.section}") {
                                        ConversationSectionHeader(
                                            group = group,
                                            isFirstSection = groupIndex == 0,
                                            hasWorkbenchAbove = !isSearching,
                                        )
                                    }
                                    items(
                                        items = group.items,
                                        key = { it.id },
                                    ) { conversation ->
                                        ConversationHistoryRow(
                                            conversation = conversation,
                                            selected = conversation.id == state.selectedConversationId,
                                            onClick = { onConversationSelected(conversation.id) },
                                            onTogglePin = { onConversationTogglePin(conversation) },
                                            onRename = { onConversationRename(conversation) },
                                            onExport = { onConversationExport(conversation) },
                                            onDelete = { onConversationDelete(conversation) },
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                    ConversationPaneTopBar(
                        containerColor = containerColor,
                        backdrop = backdrop,
                        onNewConversation = onNewConversation,
                    )
                    ConversationPaneBottomDock(
                        query = state.searchQuery,
                        isRevealed = isRevealed,
                        hasPermissionAttention = hasPermissionAttention,
                        containerColor = containerColor,
                        backdrop = backdrop,
                        onSearchChange = onSearchChange,
                        onOpenPermissions = onOpenPermissions,
                        onOpenSettings = onOpenSettings,
                    )
                },
            ) { measurables, constraints ->
                val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
                val topBar = measurables[1].measure(looseConstraints)
                val bottomDock = measurables[2].measure(looseConstraints)
                val topDp = topBar.height.toDp()
                val bottomDp = bottomDock.height.toDp()
                if (barInsets.top != topDp) barInsets.top = topDp
                if (barInsets.bottom != bottomDp) barInsets.bottom = bottomDp
                val body = measurables[0].measure(constraints)
                layout(constraints.maxWidth, constraints.maxHeight) {
                    body.placeRelative(0, 0)
                    topBar.placeRelative(0, 0)
                    bottomDock.placeRelative(0, constraints.maxHeight - bottomDock.height)
                }
            }

            // 侧栏物理右侧细微收边，保证浅/深/纯黑模式下与右侧聊天舞台始终保持利落边界。
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(0.5.dp)
                    .background(conversationPaneEdgeBorderColor()),
            )
        }
    }
}

private data class WorkbenchEntry(
    val icon: ImageVector,
    val title: String,
    val trailingValue: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun PaneWorkbenchSection(
    activeModelLabel: String?,
    onOpenModelProviders: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenFood: (() -> Unit)?,
) {
    val modelSummary = activeModelLabel?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.conversation_model_unconfigured)
    val entries = listOfNotNull(
        WorkbenchEntry(
            icon = ConversationPaneIcons.Model,
            title = stringResource(R.string.conversation_dock_models),
            trailingValue = modelSummary,
            onClick = onOpenModelProviders,
        ),
        WorkbenchEntry(
            icon = ConversationPaneIcons.Tools,
            title = stringResource(R.string.route_tools),
            onClick = onOpenTools,
        ),
        WorkbenchEntry(
            icon = ConversationPaneIcons.Skills,
            title = stringResource(R.string.route_skills),
            onClick = onOpenSkills,
        ),
        onOpenFood?.let {
            WorkbenchEntry(
                icon = Icons.Rounded.Restaurant,
                title = stringResource(R.string.food_home),
                onClick = it,
            )
        },
    )

    Card(modifier = Modifier.padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding)) {
        CardListGroup {
            entries.forEach { entry ->
                item {
                    ArrowPreference(
                        title = entry.title,
                        startAction = {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = COUITheme.colorScheme.primary,
                            )
                        },
                        endActions = { PreferenceValueText(value = entry.trailingValue) },
                        onClick = entry.onClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationSectionHeader(
    group: ConversationDrawerGroup,
    isFirstSection: Boolean,
    hasWorkbenchAbove: Boolean,
) {
    val label = group.localizedLabel()
    val topPadding = when {
        isFirstSection && hasWorkbenchAbove -> ConversationPanelMetrics.FirstSectionTopPadding
        isFirstSection -> 8.dp
        else -> ConversationPanelMetrics.SectionTopPadding
    }
    val horizontalInset = ConversationPanelMetrics.PaneHorizontalPadding +
        ConversationPanelMetrics.RowHorizontalPadding

    Text(
        text = label,
        style = COUITheme.textStyles.bodyXS,
        color = COUITheme.colorScheme.labelSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalInset,
                top = topPadding,
                end = horizontalInset,
                bottom = ConversationPanelMetrics.SectionBottomPadding,
            )
            .semantics { heading() },
        textAlign = TextAlign.Start,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationHistoryRow(
    conversation: ConversationSummaryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowShape = remember { SquircleShape(ConversationPanelMetrics.RowCornerRadius) }

    val rowModifier = if (selected) {
        Modifier
            .clip(rowShape)
            .background(conversationPaneCardColor(), rowShape)
    } else {
        Modifier.clip(rowShape)
    }

    val titleColor = COUITheme.colorScheme.labelPrimary
    val summaryColor = COUITheme.colorScheme.labelSecondary
    val accentColor = COUITheme.colorScheme.primary
    val pinText = stringResource(
        if (conversation.isPinned) {
            R.string.action_unpin_conversation
        } else {
            R.string.action_pin_conversation
        },
    )
    val renameText = stringResource(R.string.action_rename)
    val deleteText = stringResource(R.string.action_delete)
    val currentTogglePin by rememberUpdatedState(onTogglePin)
    val currentRename by rememberUpdatedState(onRename)
    val currentDelete by rememberUpdatedState(onDelete)
    val menuEntries = remember(pinText, renameText, deleteText) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = pinText,
                        icon = { iconModifier -> Icon(Icons.Outlined.PushPin, null, iconModifier) },
                        onClick = { currentTogglePin() },
                    ),
                    DropdownItem(
                        text = renameText,
                        icon = { iconModifier -> Icon(Icons.Outlined.Edit, null, iconModifier) },
                        onClick = { currentRename() },
                    ),
                ),
            ),
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = deleteText,
                        icon = { iconModifier -> Icon(Icons.Outlined.Delete, null, iconModifier) },
                        onClick = { currentDelete() },
                        alert = true,
                    ),
                ),
            ),
        )
    }

    OverlayContextMenu(
        entries = menuEntries,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ConversationPanelMetrics.RowMinHeight)
                    .then(rowModifier)
                    .clickable(onClick = onClick)
                    .padding(
                        start = ConversationPanelMetrics.RowHorizontalPadding,
                        end = if (selected) 8.dp else ConversationPanelMetrics.RowHorizontalPadding,
                        top = ConversationPanelMetrics.RowVerticalPadding,
                        bottom = ConversationPanelMetrics.RowVerticalPadding,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conversation.characterName != null) {
                    CharacterAvatar(
                        name = conversation.characterName,
                        avatarPath = conversation.characterAvatarPath,
                        size = 30.dp,
                        shape = CircleShape,
                        modifier = if (selected) {
                            Modifier.border(1.5.dp, accentColor.copy(alpha = 0.75f), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    Spacer(Modifier.width(11.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    val title = conversation.title.ifBlank { conversation.preview }
                    Text(
                        text = title,
                        color = titleColor,
                        style = COUITheme.textStyles.bodyXL.copy(fontSize = 15.sp),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 标题与角色名相同（如未改名的角色会话）时不再重复第二行。
                    conversation.characterName?.takeIf { it != title }?.let { name ->
                        Text(
                            text = name,
                            style = COUITheme.textStyles.bodyS.copy(fontSize = 12.sp),
                            color = summaryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    conversation.runStatus?.let { status ->
                        Text(
                            text = status,
                            style = COUITheme.textStyles.bodyS.copy(fontSize = 12.sp),
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                if (conversation.isActiveRun) {
                    Box(
                        modifier = Modifier
                            .padding(start = ConversationPanelMetrics.ActiveDotGap)
                            .size(ConversationPanelMetrics.ActiveDotSize)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                } else if (conversation.isPinned) {
                    Icon(
                        imageVector = ConversationPaneIcons.Pin,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(13.dp),
                        tint = summaryColor.copy(alpha = 0.72f),
                    )
                }

                if (selected) {
                    OverlayIconDropdownMenu(
                        entries = menuEntries,
                        modifier = Modifier.padding(start = 4.dp),
                        minWidth = 28.dp,
                        minHeight = 28.dp,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = stringResource(R.string.action_more),
                            modifier = Modifier.size(17.dp),
                            tint = summaryColor,
                        )
                    }
                }
            }

            // 将选中指示条放在左内边距留白区，保证选中行与普通行标题严格处于同一条左对齐基线。
            if (selected && conversation.characterName == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 5.5.dp)
                        .width(2.5.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
        }
    }
}

@Composable
private fun EmptyConversations(isSearching: Boolean) {
    ListEmptyState(
        title = stringResource(if (isSearching) R.string.conversation_no_results else R.string.conversation_empty),
        summary = if (isSearching) null else stringResource(R.string.conversation_empty_hint),
        icon = if (isSearching) ConversationPaneIcons.Search else ConversationPaneIcons.BrandJeanCalcul,
    )
}


private data class ConversationDrawerGroup(
    val section: ConversationDrawerSection,
    val items: List<ConversationSummaryUi>,
)

private sealed interface ConversationDrawerSection {
    data object Pinned : ConversationDrawerSection
    data object Today : ConversationDrawerSection
    data class Dated(val label: String) : ConversationDrawerSection
}

@Composable
private fun ConversationDrawerGroup.localizedLabel(): String = when (val value = section) {
    ConversationDrawerSection.Pinned -> stringResource(R.string.conversation_section_pinned)
    ConversationDrawerSection.Today -> stringResource(R.string.conversation_section_today)
    is ConversationDrawerSection.Dated -> value.label
}

private fun List<ConversationSummaryUi>.groupForDrawer(): List<ConversationDrawerGroup> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<ConversationDrawerGroup>()
    for (conversation in this) {
        val section = conversation.drawerSection()
        val last = groups.lastOrNull()
        if (last?.section == section) {
            groups[groups.lastIndex] = last.copy(items = last.items + conversation)
        } else {
            groups += ConversationDrawerGroup(section = section, items = listOf(conversation))
        }
    }
    return groups
}

private fun ConversationSummaryUi.drawerSection(): ConversationDrawerSection = when {
    isPinned -> ConversationDrawerSection.Pinned
    isActiveRun || isUpdatedToday(updatedAtMillis) -> ConversationDrawerSection.Today
    else -> ConversationDrawerSection.Dated(timeLabel)
}

private fun isUpdatedToday(timestampMillis: Long): Boolean {
    if (timestampMillis <= 0L) return true
    val now = java.util.Calendar.getInstance()
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    return now.get(java.util.Calendar.ERA) == target.get(java.util.Calendar.ERA) &&
        now.get(java.util.Calendar.YEAR) == target.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == target.get(java.util.Calendar.DAY_OF_YEAR)
}
