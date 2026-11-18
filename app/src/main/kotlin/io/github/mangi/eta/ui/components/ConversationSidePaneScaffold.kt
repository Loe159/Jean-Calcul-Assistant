package io.github.mangi.eta.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private object ConversationPaneMetrics {
    val PaneMaxWidth = 340.dp
    val PaneWidthFraction = 0.84f
    const val ChatScrimAlpha = 0.16f
    const val SettleDampingRatio = 1f
    const val SettleStiffness = 146f
    const val SettleVisibilityThresholdPx = 0.5f
    const val SettlePositionThresholdFraction = 0.5f
}

@Composable
fun ConversationSidePaneScaffold(
    state: ConversationPaneUiState,
    visible: Boolean,
    briefVisible: Boolean = false,
    backHandlerEnabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onOpenBrief: () -> Unit = {},
    onDismissBrief: () -> Unit = {},
    onSearchChange: (String) -> Unit,
    onNewConversation: () -> Unit = {},
    onConversationSelected: (String) -> Unit,
    onConversationTogglePin: (ConversationSummaryUi) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenModelProviders: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    activeModelLabel: String? = null,
    hasPermissionAttention: Boolean = false,
    briefContent: @Composable (revealProgress: () -> Float, onItemTouchChange: (Boolean) -> Unit) -> Unit = { _, _ -> },
    content: @Composable () -> Unit,
) {
    val sceneLifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val paneWidth = minOf(maxWidth * ConversationPaneMetrics.PaneWidthFraction, ConversationPaneMetrics.PaneMaxWidth)
        val paneWidthPx = with(density) { paneWidth.toPx() }
        val fullWidthPx = with(density) { maxWidth.toPx() }
        val anchors = remember(paneWidthPx, fullWidthPx) {
            DraggableAnchors {
                ConversationPaneAnchor.Closed at 0f
                ConversationPaneAnchor.Open at paneWidthPx
                ConversationPaneAnchor.Brief at -fullWidthPx
            }
        }
        val paneDragState = remember {
            AnchoredDraggableState(
                initialValue = when {
                    briefVisible -> ConversationPaneAnchor.Brief
                    visible -> ConversationPaneAnchor.Open
                    else -> ConversationPaneAnchor.Closed
                },
                anchors = anchors,
            )
        }
        // Read the drag offset in the layer's graphics block, not by
        // recomposing the whole dashboard during every pointer movement.
        val briefRevealProgress = remember(paneDragState, fullWidthPx) {
            { briefPaneMotion(paneDragState.offset, fullWidthPx).progress }
        }
        val settleAnimation = remember {
            spring<Float>(
                dampingRatio = ConversationPaneMetrics.SettleDampingRatio,
                stiffness = ConversationPaneMetrics.SettleStiffness,
                visibilityThreshold = ConversationPaneMetrics.SettleVisibilityThresholdPx,
            )
        }
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = paneDragState,
            positionalThreshold = { distance ->
                distance * ConversationPaneMetrics.SettlePositionThresholdFraction
            },
            animationSpec = settleAnimation,
        )
        val currentVisible by rememberUpdatedState(visible)
        val currentBriefVisible by rememberUpdatedState(briefVisible)
        val currentOnOpen by rememberUpdatedState(onOpen)
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val currentOnOpenBrief by rememberUpdatedState(onOpenBrief)
        val currentOnDismissBrief by rememberUpdatedState(onDismissBrief)
        val hapticFeedback = LocalHapticFeedback.current
        val currentHapticFeedback by rememberUpdatedState(hapticFeedback)
        val dragInteractions = remember { MutableInteractionSource() }
        // A touch on an activity card belongs to that card until pointer-up.
        // Never let the full-page anchoredDraggable steal a rightward dismissal.
        var notificationTouchActive by remember { mutableStateOf(false) }
        val isDragging by dragInteractions.collectIsDraggedAsState()
        val isHistoryRevealed by remember(paneDragState) {
            derivedStateOf { paneDragState.offset > 0.5f }
        }
        val isBriefRevealed by remember(paneDragState) {
            derivedStateOf { paneDragState.offset < -0.5f }
        }
        val isRevealed by remember(paneDragState) {
            derivedStateOf { kotlin.math.abs(paneDragState.offset) > 0.5f }
        }
        val panelListState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val closePane: () -> Unit = {
            focusManager.clearFocus()
            keyboard?.hide()
            if (currentBriefVisible) {
                currentOnDismissBrief()
            } else if (currentVisible) {
                currentOnDismiss()
            } else {
                scope.launch { paneDragState.animateTo(ConversationPaneAnchor.Closed, settleAnimation) }
            }
        }
        LaunchedEffect(isRevealed) {
            if (isRevealed) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }

        SideEffect {
            paneDragState.updateAnchors(anchors)
        }

        LaunchedEffect(visible, briefVisible, paneWidthPx, fullWidthPx) {
            if (!visible && isRevealed) {
                focusManager.clearFocus()
                keyboard?.hide()
            }
            val target = when {
                briefVisible -> ConversationPaneAnchor.Brief
                visible -> ConversationPaneAnchor.Open
                else -> ConversationPaneAnchor.Closed
            }
            if (paneDragState.targetValue != target || paneDragState.settledValue != target) {
                paneDragState.animateTo(target, settleAnimation)
            }
        }

        LaunchedEffect(paneDragState) {
            var previousAnchor = paneDragState.settledValue
            conversationPaneSettledValues(paneDragState) { isDragging }.collectLatest { settledValue ->
                if (settledValue != previousAnchor) {
                    // D4: haptic only when the page has actually snapped into place.
                    currentHapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
                previousAnchor = settledValue
                val settledOpen = settledValue == ConversationPaneAnchor.Open
                val settledBrief = settledValue == ConversationPaneAnchor.Brief
                if (settledBrief != currentBriefVisible) {
                    if (settledBrief) currentOnOpenBrief() else currentOnDismissBrief()
                }
                if (settledOpen != currentVisible) {
                    if (settledOpen) {
                        currentOnOpen()
                    } else {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        currentOnDismiss()
                    }
                }
            }
        }

        // NavDisplay 的退出条目在转场期间仍会保留组合；仅允许已稳定显示的首页
        // 处理侧栏返回，避免它抢先消费二级页面的第一次返回事件。
        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = isRevealed &&
                backHandlerEnabled &&
                sceneLifecycleState == Lifecycle.State.RESUMED,
            onBackCompleted = closePane,
        )

        Box(
            modifier = Modifier.fillMaxSize()
                .clipToBounds()
                .background(conversationPaneContainerColor())
                // 手势由共同容器拥有，侧栏和聊天舞台均可拖动；子级先处理滚动与选择。
                .anchoredDraggable(
                    state = paneDragState,
                    reverseDirection = false,
                    orientation = Orientation.Horizontal,
                    enabled = backHandlerEnabled && sceneLifecycleState == Lifecycle.State.RESUMED &&
                        !notificationTouchActive,
                    flingBehavior = flingBehavior,
                    interactionSource = dragInteractions,
                ),
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            // 两个槽位并排测量和放置，由同一位移推移；不在聊天内容下面叠放侧栏。
            // 侧栏保留组合，避免每次起手重新创建列表和搜索框。
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    ConversationPanePanel(
                        state = state,
                        width = paneWidth,
                        listState = panelListState,
                        isRevealed = isHistoryRevealed,
                        activeModelLabel = activeModelLabel,
                        hasPermissionAttention = hasPermissionAttention,
                        onSearchChange = onSearchChange,
                        onNewConversation = onNewConversation,
                        onConversationSelected = onConversationSelected,
                        onConversationTogglePin = onConversationTogglePin,
                        onConversationRename = onConversationRename,
                        onConversationExport = onConversationExport,
                        onConversationDelete = onConversationDelete,
                        onOpenSettings = onOpenSettings,
                        onOpenModelProviders = onOpenModelProviders,
                        onOpenTools = onOpenTools,
                        onOpenSkills = onOpenSkills,
                        onOpenCharacters = onOpenCharacters,
                        onOpenPermissions = onOpenPermissions,
                        modifier = Modifier
                            .focusProperties { onEnter = { if (!isHistoryRevealed) cancelFocusChange() } }
                            .focusGroup()
                            .then(if (!isHistoryRevealed) Modifier.clearAndSetSemantics {} else Modifier),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clipToBounds() // D1 parallax stays inside the full-screen Brief surface.
                            .focusProperties { onEnter = { if (!isBriefRevealed) cancelFocusChange() } }
                            .focusGroup()
                            .then(if (!isBriefRevealed) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        // The same physical drag fraction drives both the panel position
                        // and its ambient backdrop. No visibility-triggered fade or lag.
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            // D1: content lags behind the horizontal spring by at most 18dp.
                            translationX = -(1f - briefRevealProgress()) * 18.dp.toPx()
                        }) {
                            briefContent(briefRevealProgress) { notificationTouchActive = it }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .drawWithContent {
                                drawContent()
                                val offset = paneDragState.offset
                                val progress = if (offset < 0f) {
                                    briefPaneMotion(offset, fullWidthPx).progress
                                } else conversationPaneMotion(offset, paneWidthPx).progress
                                drawRect(Color.Black, alpha = ConversationPaneMetrics.ChatScrimAlpha * progress)
                            }
                            .background(MiuixTheme.colorScheme.background),
                    ) {
                        Box(
                            modifier = Modifier
                                .focusProperties {
                                    onEnter = { if (isRevealed) cancelFocusChange() }
                                }
                                .focusGroup()
                                .then(if (isRevealed) Modifier.clearAndSetSemantics {} else Modifier),
                        ) {
                            content()
                        }
                        if (isRevealed) {
                            Box(
                                modifier = Modifier.fillMaxSize().clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClickLabel = stringResource(R.string.action_close),
                                    onClick = closePane,
                                ),
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val panel = measurables[0].measure(
                    Constraints.fixed(paneWidthPx.roundToInt(), constraints.maxHeight),
                )
                val brief = measurables[1].measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight),
                )
                val page = measurables[2].measure(
                    Constraints.fixed(constraints.maxWidth, constraints.maxHeight),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    // Three physically adjacent surfaces: history | conversation | fullscreen brief.
                    val offset = paneDragState.offset.takeIf { it.isFinite() }
                        ?.coerceIn(-fullWidthPx, paneWidthPx) ?: 0f
                    val x = offset.roundToInt()
                    panel.placeWithLayer(x - panel.width, 0)
                    // No exposed right-hand edge: the neighboring page is fully offscreen.
                    brief.placeWithLayer(x + page.width, 0)
                    page.placeWithLayer(x, 0)
                }
            }
            // Only the unobtrusive lower pager remains visible on the chat.
            val progress = briefPaneMotion(paneDragState.offset, fullWidthPx).progress
            if (!currentVisible && progress < 0.98f) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp)
                        .graphicsLayer { alpha = (1f - progress).coerceIn(0f, 1f) }
                        .semantics {
                            contentDescription = if (progress < 0.5f) "Chat, page 1 sur 2"
                                else "Briefing, page 2 sur 2"
                        },
                ) {
                    Box(Modifier.padding(horizontal = 3.dp)
                        .width((18f - 10f * progress).dp).height(4.dp)
                        .clip(CircleShape).background(MiuixTheme.colorScheme.onBackground
                            .copy(alpha = 0.62f - progress * 0.35f)))
                    Box(Modifier.padding(horizontal = 3.dp)
                        .width((8f + 10f * progress).dp).height(4.dp)
                        .clip(CircleShape).background(MiuixTheme.colorScheme.onBackground
                            .copy(alpha = 0.25f + progress * 0.42f)))
                }
            }
        }
    }
}
