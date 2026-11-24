package io.github.mangi.eta.ui.screens.food.components

import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.FoodResult
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffold
import io.github.mangi.eta.ui.components.coui.couiPageScroll
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.food.label
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun foodRuntime() = (LocalContext.current.applicationContext as EtaApp).food

internal class FoodPageSlots(
    val content: @Composable () -> Unit,
    val overlay: @Composable () -> Unit,
)

@Composable
internal fun FoodPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiScaffoldPage(title = title, onBack = onBack, actions = actions, overlay = overlay) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun CookingPage(
    title: String,
    onBack: () -> Unit,
    scroll: ScrollState,
    progress: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
    overlay: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiScaffold(title, onBack, compactTitle = true, actions = actions) { padding, _, connection, sidePadding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = sidePadding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp), content = progress)
            Column(
                Modifier.weight(1f).fillMaxWidth().couiPageScroll(connection)
                    .verticalScroll(scroll, overscrollEffect = null).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                CouiPageBottomSpacer()
            }
        }
        // COUI overlays must be composed here, where the page backdrop and popup blur are provided.
        overlay()
    }
}

@Composable
internal fun FoodSection(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun FoodHeading(text: String) = Text(text, style = COUITheme.textStyles.headlineXS)

@Composable
internal fun FoodSupporting(text: String) = Text(
    text, style = COUITheme.textStyles.bodyS, color = COUITheme.colorScheme.labelSecondary,
)

@Composable
internal fun FoodButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier, minWidth = 0.dp, minHeight = 48.dp,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        Text(text, style = COUITheme.textStyles.buttonL)
    }
}

@Composable
internal fun FoodAction(@StringRes label: Int, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    FoodButton(stringResource(label), onClick, primary, enabled, Modifier.fillMaxWidth())
}

@Composable
internal fun FoodErrorText(error: FoodResult.Failure?) {
    error?.let { Text(stringResource(it.error.label()), color = COUITheme.colorScheme.error) }
}

@Composable
internal fun FoodSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    FoodSheetContainer(title, onClose) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun FoodLazySheet(title: String, onClose: () -> Unit, content: LazyListScope.() -> Unit) {
    FoodSheetContainer(title, onClose) {
        LazyColumn(
            Modifier.fillMaxWidth().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            content = content,
        )
    }
}

@Composable
private fun FoodSheetContainer(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    OverlayBottomSheet(
        show = true, title = title, onDismissRequest = onClose, showDragHandle = true,
        startAction = { CouiTopAppBarIconButton(COUIIcons.Basic.Close, stringResource(R.string.food_close), onClose) },
        insideMargin = DpSize(16.dp, 0.dp),
    ) { content() }
}

@Composable
internal fun <T> FoodChoices(label: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FoodSupporting(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (id, name) ->
            FoodChoiceChip(name, id == selected) { onSelect(id) }
        }
    }
}


@Composable
internal fun FoodChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Chip(label = label, selected = selected, onClick = onClick, modifier = Modifier.heightIn(min = 48.dp))
}
