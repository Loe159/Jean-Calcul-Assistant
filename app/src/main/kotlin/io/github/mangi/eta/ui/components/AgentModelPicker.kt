package io.github.mangi.eta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.R
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.popup.OverlayDropdownPopup
import io.github.mangi.eta.ui.components.coui.CouiChipTabs
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.model.AgentModelOptionUi
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.filterGroups
import io.github.mangi.eta.ui.model.modelCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.foundation.LocalIndication
import io.github.mangi.eta.ui.components.coui.panelBlur
import io.github.suqi8.coui.kmp.layout.BottomSheetDefaults
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet

/**
 * 聊天首页顶栏的复合标题与模型切换入口：
 * - 新对话：居中呈现轻盈的模型胶囊 `[ ✦ 模型名 ▾ ]`
 * - 对话进行中：上行会话标题，下行紧凑模型副标题，整块可点切换模型
 * - 角色对话：左侧头像直达角色详情，右侧双行展示角色名与当前模型
 */
@Composable
internal fun AgentTopBarChatHeader(
    conversationTitle: String?,
    character: RoleplayChatUi?,
    state: AgentModelPickerUiState,
    isStreaming: Boolean,
    onModelSelected: (String) -> Unit,
    onOpenCharacter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPopup by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var sheetSession by remember { mutableIntStateOf(0) }
    val selected = state.selectedModel
    val enabled = !isStreaming && !state.isChanging && state.providerGroups.isNotEmpty()
    LaunchedEffect(enabled) {
        if (!enabled) {
            showPopup = false
            showSheet = false
        }
    }

    val currentModel = selected?.displayName ?: stringResource(R.string.model_not_selected)
    val switchModelDescription = stringResource(R.string.model_switch_current, currentModel)
    val isDark = COUITheme.isDark
    val ink = COUITheme.colorScheme.labelPrimary.copy(alpha = 1f)
    val subtitleColor = ink.copy(alpha = if (isDark) 0.56f else 0.52f)
    val chevronColor = ink.copy(alpha = if (isDark) 0.46f else 0.42f)
    val interactionSource = remember { MutableInteractionSource() }

    val triggerModelPicker: () -> Unit = {
        if (state.modelCount <= ModelPopupLimit) {
            showPopup = true
        } else {
            sheetSession += 1
            showSheet = true
        }
    }

    Box(modifier = modifier) {
        when {
            character != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenCharacter)
                            .padding(2.dp),
                    ) {
                        CharacterAvatar(
                            name = character.characterName,
                            avatarPath = character.avatarPath,
                            size = 30.dp,
                            shape = CircleShape,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                enabled = enabled,
                                role = Role.Button,
                                onClick = triggerModelPicker,
                            )
                            .semantics { contentDescription = switchModelDescription }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = character.characterName,
                            style = COUITheme.textStyles.bodyM.copy(
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = COUITheme.colorScheme.labelPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ModelSubtitleRow(
                            selected = selected,
                            modelName = currentModel,
                            textColor = subtitleColor,
                            chevronColor = chevronColor,
                            showChevron = enabled,
                        )
                    }
                }
            }

            !conversationTitle.isNullOrBlank() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = triggerModelPicker,
                        )
                        .semantics { contentDescription = switchModelDescription }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = conversationTitle,
                        style = COUITheme.textStyles.bodyXL.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = COUITheme.colorScheme.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ModelSubtitleRow(
                        selected = selected,
                        modelName = currentModel,
                        textColor = subtitleColor,
                        chevronColor = chevronColor,
                        showChevron = enabled,
                    )
                }
            }

            else -> {
                val pillFill = ink.copy(alpha = if (isDark) 0.08f else 0.042f)
                val pillBorder = ink.copy(alpha = if (isDark) 0.12f else 0.07f)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(CircleShape)
                        .background(pillFill, CircleShape)
                        .border(0.5.dp, pillBorder, CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = triggerModelPicker,
                        )
                        .semantics { contentDescription = switchModelDescription }
                        .padding(start = 11.dp, end = if (enabled) 8.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    ModelBrandMark(
                        modelId = selected?.modelId,
                        sourceType = selected?.providerSourceType,
                        size = 15.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentModel,
                        style = COUITheme.textStyles.bodyM.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = COUITheme.colorScheme.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                    if (enabled) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = chevronColor,
                        )
                    }
                }
            }
        }

        val currentOnModelSelected by rememberUpdatedState(onModelSelected)
        val menuEntries = remember(state.providerGroups, state.selectedModel?.id) {
            val multipleGroups = state.providerGroups.size > 1
            state.providerGroups.map { group ->
                DropdownEntry(
                    title = group.providerName.takeIf { multipleGroups },
                    items = group.models.map { model ->
                        DropdownItem(
                            text = model.displayName,
                            selected = model.id == state.selectedModel?.id,
                            onClick = { currentOnModelSelected(model.id) },
                        )
                    },
                )
            }
        }
        OverlayDropdownPopup(
            entries = menuEntries,
            show = showPopup,
            onDismiss = { showPopup = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
            collapseOnSelection = true,
        )

        val sheetBlur = panelBlur(MaterialBlurDefaults::bottomSheet)
        OverlayBottomSheet(
            show = showSheet,
            title = stringResource(R.string.model_picker_title),
            startAction = {
                IconButton(onClick = { showSheet = false }) {
                    Icon(
                        imageVector = COUIIcons.Basic.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = COUITheme.colorScheme.labelPrimary,
                    )
                }
            },
            backgroundModifier = sheetBlur?.backgroundModifier ?: Modifier,
            backgroundColor = sheetBlur?.backgroundColor ?: BottomSheetDefaults.backgroundColor(),
            onDismissRequest = { showSheet = false },
            insideMargin = DpSize(16.dp, 0.dp),
            materialStroke = sheetBlur != null,
        ) {
            key(sheetSession) {
                ModelSheetContent(
                    state = state,
                    onModelSelected = { modelId ->
                        showSheet = false
                        onModelSelected(modelId)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelSubtitleRow(
    selected: AgentModelOptionUi?,
    modelName: String,
    textColor: Color,
    chevronColor: Color,
    showChevron: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 1.dp),
    ) {
        ModelBrandMark(
            modelId = selected?.modelId,
            sourceType = selected?.providerSourceType,
            size = 11.dp,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = modelName,
            style = COUITheme.textStyles.bodyXS.copy(
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp),
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = chevronColor,
            )
        }
    }
}

@Composable
private fun ModelQuickList(
    state: AgentModelPickerUiState,
    onModelSelected: (String) -> Unit,
) {
    val select = rememberConfirmedSelection(onModelSelected)
    val selectedId = select.pendingId ?: state.selectedModel?.id
    val showLabels = state.providerGroups.size > 1
    Column(
        modifier = Modifier
            .width(ModelPopupWidth)
            .verticalScroll(rememberScrollState())
            .padding(top = if (showLabels) 0.dp else 6.dp, bottom = 6.dp),
    ) {
        state.providerGroups.forEach { group ->
            if (showLabels) ModelProviderLabel(group.providerName)
            group.models.forEach { model ->
                ModelPickerRow(
                    model = model,
                    selected = model.id == selectedId,
                    onClick = { select(model.id) },
                )
            }
        }
    }
}

private sealed interface ModelSheetEntry {
    val key: String

    data class Header(val providerId: String, val name: String) : ModelSheetEntry {
        override val key: String get() = "header:$providerId"
    }

    data class Option(val model: AgentModelOptionUi) : ModelSheetEntry {
        override val key: String get() = "model:${model.providerId}:${model.id}"
    }
}

@Composable
private fun ModelSheetContent(
    state: AgentModelPickerUiState,
    onModelSelected: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var providerFilter by remember { mutableStateOf<String?>(null) }
    val entries = remember(state.providerGroups, query, providerFilter) {
        state.filterGroups(query)
            .filter { providerFilter == null || it.providerId == providerFilter }
            .flatMap { group ->
                // 只看一个服务商时，筛选标签已经说明了归属，分组标题只会重复。
                val header = if (providerFilter == null) {
                    listOf(ModelSheetEntry.Header(group.providerId, group.providerName))
                } else {
                    emptyList()
                }
                header + group.models.map(ModelSheetEntry::Option)
            }
    }
    val selectedIndex = entries.indexOfFirst {
        it is ModelSheetEntry.Option && it.model.id == state.selectedModel?.id
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))
    LaunchedEffect(listState) {
        snapshotFlow { query to providerFilter }.drop(1).collect { listState.scrollToItem(0) }
    }
    val select = rememberConfirmedSelection(onModelSelected)
    val selectedId = select.pendingId ?: state.selectedModel?.id
    val density = LocalDensity.current
    val sheetHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.68f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .then(Modifier),
    ) {
        CouiSearchBarBox(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.ui_search_model_df5586),
            backgroundColor = Color.Transparent,
        )
        if (state.providerGroups.size > 1) {
            val allLabel = stringResource(R.string.model_picker_filter_all)
            val tabItems = remember(state.providerGroups, allLabel) {
                listOf("" to allLabel) + state.providerGroups.map { it.providerId to it.providerName }
            }
            CouiChipTabs(
                items = tabItems,
                selectedKey = providerFilter.orEmpty(),
                onSelectedKeyChange = { key -> providerFilter = key.takeIf { it.isNotEmpty() } },
                backgroundColor = Color.Transparent,
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.page_no_matching_model_found_ae7e96),
                        style = COUITheme.textStyles.bodyM,
                        color = COUITheme.colorScheme.labelSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                    )
                }
            }
            items(entries, key = ModelSheetEntry::key, contentType = { it::class }) { entry ->
                when (entry) {
                    is ModelSheetEntry.Header -> ModelProviderLabel(entry.name, horizontalPadding = 24.dp)
                    is ModelSheetEntry.Option -> ModelPickerRow(
                        model = entry.model,
                        selected = entry.model.id == selectedId,
                        onClick = { select(entry.model.id) },
                        horizontalPadding = 12.dp,
                    )
                }
            }
        }
    }
}

/**
 * 点选后先把对勾移到新模型上，停留片刻再收起弹层，让人看清选中了谁。
 * 弹层收起会让作用域随组合一起离开，未完成的确认随之取消，不会回写已关闭的界面。
 */
private class ConfirmedSelection(
    private val onConfirm: (String) -> Unit,
    private val launch: (suspend () -> Unit) -> Unit,
) {
    var pendingId by mutableStateOf<String?>(null)
        private set

    operator fun invoke(modelId: String) {
        if (pendingId != null) return
        pendingId = modelId
        launch {
            delay(SelectionConfirmMs)
            onConfirm(modelId)
        }
    }
}

@Composable
private fun rememberConfirmedSelection(onConfirm: (String) -> Unit): ConfirmedSelection {
    val scope = rememberCoroutineScope()
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    return remember { ConfirmedSelection({ currentOnConfirm(it) }) { block -> scope.launch { block() } } }
}

@Composable
private fun ModelProviderLabel(name: String, horizontalPadding: Dp = 20.dp) {
    Text(
        text = name,
        style = COUITheme.textStyles.bodyS,
        color = COUITheme.colorScheme.labelSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun ModelPickerRow(
    model: AgentModelOptionUi,
    selected: Boolean,
    onClick: () -> Unit,
    horizontalPadding: Dp = 8.dp,
) {
    val containerColor = if (selected) COUITheme.colorScheme.containerThemeHalftone else Color.Transparent
    val contentColor = if (selected) COUITheme.colorScheme.primaryText else COUITheme.colorScheme.labelPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .squircleSurface(color = containerColor, cornerRadius = 12.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model.displayName,
            style = COUITheme.textStyles.bodyM,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.ui_current_model_a0af8f),
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
internal fun ModelBrandMark(
    modelId: String?,
    sourceType: String?,
    size: Dp,
) {
    val logo = modelOrProviderBrandLogoRes(modelId, sourceType)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(COUITheme.colorScheme.containerThemeHalftone, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Dns,
                contentDescription = null,
                modifier = Modifier.size(size * 0.56f),
                tint = COUITheme.colorScheme.primary,
            )
        }
    }
}

private val ModelPopupWidth = 248.dp

/** 不超过这个数量时弹窗一眼就能看完，不需要搜索。 */
private const val ModelPopupLimit = 8

private const val SelectionConfirmMs = 140L
