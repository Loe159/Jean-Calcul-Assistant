package io.github.mangi.eta.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.rememberMaterialBackdropProvider
import io.github.suqi8.coui.kmp.basic.CancelButton
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.SearchBarType
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.theme.COUITheme

internal object ConversationPanelMetrics {
    val PaneHorizontalPadding = 16.dp
    val TopBarHeight = 56.dp
    val BottomDockVerticalPadding = 10.dp
    val BrandBadgeSize = 26.dp
    val BrandIconSize = 16.dp
    val NewChatIconSize = 20.dp
    val DockIconSize = 22.dp

    val FirstSectionTopPadding = 22.dp
    val SectionTopPadding = 20.dp
    val SectionBottomPadding = 6.dp
    val RowMinHeight = 44.dp
    val RowGap = 3.dp
    val RowCornerRadius = 14.dp
    val RowHorizontalPadding = 14.dp
    val RowVerticalPadding = 10.dp
    val ActiveDotSize = 6.dp
    val ActiveDotGap = 8.dp
    val ActionIconSize = 20.dp
}

/** The pane background: the COUI page-with-cards colour, so cards and the selected row stand out. */
@Composable
internal fun conversationPaneContainerColor(): Color = COUITheme.colorScheme.backgroundWithCard

/** The colour of cards and of the selected row inside the pane. */
@Composable
internal fun conversationPaneCardColor(): Color = COUITheme.colorScheme.cardBackground

/** The hairline between the pane and the conversation stage. */
@Composable
internal fun conversationPaneEdgeBorderColor(): Color = COUITheme.colorScheme.divider

/**
 * Pane header: brand and the new conversation action. [backdrop] records the list scrolling under
 * the header, so the COUI 17 button material can blur it.
 */
@Composable
internal fun ConversationPaneTopBar(
    containerColor: Color,
    backdrop: LayerBackdrop?,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to containerColor,
                    0.8f to containerColor.copy(alpha = 0.94f),
                    1.0f to Color.Transparent,
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = ConversationPanelMetrics.PaneHorizontalPadding)
                .height(ConversationPanelMetrics.TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaneBrandHeader(modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalMaterialBackdrop provides materialBackdrop) {
                IconButton(onClick = onNewConversation) {
                    Icon(
                        imageVector = ChatActionIcons.NewChat,
                        contentDescription = stringResource(R.string.action_new_conversation),
                        modifier = Modifier.size(ConversationPanelMetrics.NewChatIconSize),
                        tint = COUITheme.colorScheme.labelPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaneBrandHeader(modifier: Modifier = Modifier) {
    val colors = COUITheme.colorScheme
    val accent = colors.primary
    val badgeBrush = remember(accent, colors.isDark) {
        Brush.linearGradient(
            colors = listOf(accent, lerp(accent, if (colors.isDark) Color.White else Color.Black, 0.12f)),
        )
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(ConversationPanelMetrics.BrandBadgeSize)
                .clip(CircleShape)
                .background(badgeBrush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ConversationPaneIcons.BrandJeanCalcul,
                contentDescription = null,
                modifier = Modifier.size(ConversationPanelMetrics.BrandIconSize),
                tint = colors.labelOnColor,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = COUITheme.textStyles.headlineS,
            color = colors.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pane dock: the COUI search bar with the permissions and settings entries beside it.
 * The bar is [SearchBarType.NonInstant] so both outer buttons stay beside the capsule.
 */
@Composable
internal fun ConversationPaneBottomDock(
    query: String,
    isRevealed: Boolean,
    hasPermissionAttention: Boolean,
    containerColor: Color,
    backdrop: LayerBackdrop?,
    onSearchChange: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only avoid the IME while the pane is open and its window focused, so dialogs or the chat
    // composer's keyboard do not relayout the hidden pane.
    val applyIme = isRevealed && LocalWindowInfo.current.isWindowFocused
    val dockInsets = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
        .let { base -> if (applyIme) base.union(WindowInsets.ime) else base }
    val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
    val keyboard = LocalSoftwareKeyboardController.current

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.isNotEmpty() && !searchExpanded) searchExpanded = true
    }
    val collapse: () -> Unit = {
        searchExpanded = false
        onSearchChange("")
        keyboard?.hide()
    }
    BackHandler(enabled = searchExpanded, onBack = collapse)

    val permissionsDesc = if (hasPermissionAttention) {
        stringResource(R.string.conversation_permission_attention)
    } else {
        stringResource(R.string.route_permissions)
    }
    val iconTint = COUITheme.colorScheme.labelPrimary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.25f to containerColor.copy(alpha = 0.72f),
                    0.55f to containerColor.copy(alpha = 0.94f),
                    1.0f to containerColor,
                ),
            )
            .windowInsetsPadding(dockInsets)
            .padding(
                top = ConversationPanelMetrics.BottomDockVerticalPadding + 10.dp,
                bottom = ConversationPanelMetrics.BottomDockVerticalPadding,
            ),
    ) {
        CompositionLocalProvider(LocalMaterialBackdrop provides materialBackdrop) {
            SearchBar(
                inputField = {
                    InputField(
                        query = query,
                        onQueryChange = onSearchChange,
                        onSearch = { keyboard?.hide() },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = stringResource(R.string.conversation_search_hint),
                    )
                },
                onExpandedChange = { next -> if (next) searchExpanded = true else collapse() },
                expanded = searchExpanded,
                type = SearchBarType.NonInstant,
                insideMargin = DpSize(ConversationPanelMetrics.PaneHorizontalPadding, 0.dp),
                outerPrimaryButton = {
                    IconButton(
                        onClick = onOpenPermissions,
                        redDot = if (hasPermissionAttention) 0 else null,
                    ) {
                        Icon(
                            imageVector = ConversationPaneIcons.Permissions,
                            contentDescription = permissionsDesc,
                            modifier = Modifier.size(ConversationPanelMetrics.DockIconSize),
                            tint = iconTint,
                        )
                    }
                },
                outerSecondaryButton = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = ConversationPaneIcons.Settings,
                            contentDescription = stringResource(R.string.route_settings),
                            modifier = Modifier.size(ConversationPanelMetrics.DockIconSize),
                            tint = iconTint,
                        )
                    }
                },
                outsideEndAction = {
                    CancelButton(onClick = collapse, text = stringResource(R.string.action_cancel))
                },
            ) {}
        }
    }
}
