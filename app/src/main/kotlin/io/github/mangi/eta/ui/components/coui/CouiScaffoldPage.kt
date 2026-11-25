// Page scaffold after the COUI "coui-app" skill recipe (references/scaffold.md, CouiPage.kt),
// https://github.com/suqi8/coui at df5883f7f36b075125469488c54625152e83a556, Apache-2.0.
package io.github.mangi.eta.ui.components.coui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.layout.WidePageContent
import io.github.mangi.eta.ui.layout.horizontalCutoutPadding
import io.github.mangi.eta.ui.layout.rememberIsWideScreen
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.basic.glowScrollOffsetPx
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.TopAppBarGlow
import io.github.suqi8.coui.kmp.blur.layerBackdrop
import io.github.suqi8.coui.kmp.effect.LocalMaterialBackdrop
import io.github.suqi8.coui.kmp.effect.LocalPopupMaterialBlur
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import io.github.suqi8.coui.kmp.utils.scrollEndHaptic

/** Back navigation button of a page top bar (COUI back glyph). */
@Composable
internal fun CouiBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backDescription = stringResource(R.string.action_back)
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = backDescription }) {
        Icon(
            imageVector = COUIIcons.Back,
            contentDescription = null,
            tint = COUITheme.colorScheme.labelPrimary,
        )
    }
}

/** An icon action of a page top bar. */
@Composable
internal fun CouiTopAppBarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    val accessibleModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier
    IconButton(onClick = onClick, modifier = accessibleModifier, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = when {
                !enabled -> COUITheme.colorScheme.disabledNeutral
                tint != Color.Unspecified -> tint
                else -> COUITheme.colorScheme.labelPrimary
            },
        )
    }
}

/** A text action of a page top bar. */
@Composable
internal fun CouiTopAppBarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

/**
 * The page top bar: collapsing large title on phones, pinned small title on wide windows or when
 * [compact]; the COUI 17 condensed-light glow when the page is recorded into [backdrop].
 */
@Composable
private fun CouiPageTopBar(
    title: String,
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    compact: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    titleAlpha: Float = 1f,
) {
    val colors = COUITheme.colorScheme
    val barColor = if (backdrop != null) Color.Transparent else colors.backgroundWithCard
    val titleColor = colors.labelPrimary.copy(alpha = colors.labelPrimary.alpha * titleAlpha.coerceIn(0f, 1f))
    val glow: (@Composable BoxScope.() -> Unit)? = backdrop?.let {
        {
            TopAppBarGlow(
                backdrop = it,
                scrollOffsetPx = scrollBehavior.glowScrollOffsetPx(),
                isDark = colors.isDark,
                baseColor = colors.backgroundWithCard,
                enabled = true,
            )
        }
    }
    val navigationIcon: @Composable () -> Unit = { CouiBackButton(onClick = onBack) }
    if (compact || rememberIsWideScreen()) {
        SmallTopAppBar(
            title = title,
            color = barColor,
            titleColor = titleColor,
            scrollBehavior = scrollBehavior,
            glow = glow,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    } else {
        TopAppBar(
            title = title,
            color = barColor,
            titleColor = titleColor,
            largeTitleColor = titleColor,
            scrollBehavior = scrollBehavior,
            glow = glow,
            navigationIcon = navigationIcon,
            actions = actions,
        )
    }
}

/**
 * Low-level COUI secondary page: top bar, page backdrop for overlays, and bar slots that get the
 * page as their material. [content] receives the padding to apply, the raw scaffold padding, an
 * optional extra nested scroll connection (the top bar one is already installed on the scaffold)
 * and the wide-window side padding.
 */
@Composable
internal fun CouiScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    compactTitle: Boolean = false,
    containerColor: Color = COUITheme.colorScheme.backgroundWithCard,
    titleMotionProgress: Float = 1f,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (
        contentPadding: PaddingValues,
        rawPadding: PaddingValues,
        nestedScrollConnection: NestedScrollConnection?,
        sidePadding: Dp,
    ) -> Unit,
) {
    val backdrop = rememberPageBackdrop(containerColor)
    val scrollBehavior = COUIScrollBehavior()
    val materialBackdrop = rememberMaterialBackdropProvider(backdrop)
    val popupBlur = rememberPopupMaterialBlur(backdrop)
    // Only the focused window reserves the keyboard, so a dialog's IME does not shift the page.
    val imePadding = if (LocalWindowInfo.current.isWindowFocused) Modifier.imePadding() else Modifier
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(imePadding)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        bottomBar = { bottomBar?.invoke() },
        topBar = {
            CouiPageTopBar(
                title = title,
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                compact = compactTitle,
                onBack = onBack,
                actions = actions,
                titleAlpha = titleMotionProgress,
            )
        },
        floatingActionButton = {
            if (floatingAction != null) {
                CompositionLocalProvider(
                    LocalPageBackdrop provides backdrop,
                    LocalMaterialBackdrop provides materialBackdrop,
                ) { floatingAction() }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            CompositionLocalProvider(
                LocalPageBackdrop provides backdrop,
                LocalPopupMaterialBlur provides popupBlur,
            ) {
                WidePageContent { sidePadding ->
                    content(innerPadding, innerPadding, null, sidePadding)
                }
            }
        }
    }
}

/** The scroll modifiers of every COUI page: scroll-end haptic, COUI over-scroll, top bar nested scroll. */
@Composable
internal fun Modifier.couiPageScroll(connection: NestedScrollConnection?): Modifier = this
    .horizontalCutoutPadding()
    .scrollEndHaptic()
    .overScrollVertical()
    .then(if (connection != null) Modifier.nestedScroll(connection) else Modifier)

/** A COUI secondary page whose content is a scrolling column. */
@Composable
internal fun CouiScaffoldPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    compactTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        compactTitle = compactTitle,
        actions = actions,
        floatingAction = floatingAction,
        bottomBar = bottomBar,
    ) { padding, _, connection, sidePadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .couiPageScroll(connection)
                .verticalScroll(scrollState, overscrollEffect = null)
                .padding(padding)
                .then(if (bottomBar != null) Modifier.consumeWindowInsets(padding) else Modifier)
                .padding(horizontal = sidePadding)
                .padding(top = 12.dp),
        ) {
            content()
            CouiPageBottomSpacer()
        }
        overlay()
    }
}

/** A COUI secondary page whose content is a lazy list (long or dynamic lists). */
@Composable
internal fun CouiLazyScaffoldPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = COUITheme.colorScheme.backgroundWithCard,
    listState: LazyListState = rememberLazyListState(),
    compactTitle: Boolean = false,
    titleMotionProgress: Float = 1f,
    backgroundEffect: @Composable BoxScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingAction: (@Composable () -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    CouiScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        compactTitle = compactTitle,
        containerColor = containerColor,
        titleMotionProgress = titleMotionProgress,
        actions = actions,
        floatingAction = floatingAction,
    ) { padding, _, connection, sidePadding ->
        Box(Modifier.fillMaxSize()) {
            backgroundEffect()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .couiPageScroll(connection),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = sidePadding,
                    bottom = padding.calculateBottomPadding(),
                ),
                overscrollEffect = null,
            ) {
                content()
                item(key = "coui_page_bottom_spacer") { CouiPageBottomSpacer() }
            }
        }
        overlay()
    }
}

@Composable
internal fun CouiPageBottomSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .height(24.dp)
            .navigationBarsPadding(),
    )
}
