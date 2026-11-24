package io.github.mangi.eta.ui.screens.food.components

import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.FoodResult
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.food.label
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme

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
    scrollState: ScrollState = rememberScrollState(),
    compactTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiScaffoldPage(
        title = title, onBack = onBack, actions = actions, overlay = overlay,
        scrollState = scrollState, compactTitle = compactTitle,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun FoodSection(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun FoodHeading(text: String) = Text(
    text, modifier = Modifier.semantics { heading() }, style = COUITheme.textStyles.headlineXS,
)

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
) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), minHeight = 48.dp,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        Text(text, style = COUITheme.textStyles.buttonL, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun FoodAction(@StringRes label: Int, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    FoodButton(stringResource(label), onClick, primary, enabled)
}

@Composable
internal fun FoodErrorText(error: FoodResult.Failure?) {
    error?.let { FoodErrorMessage(stringResource(it.error.label())) }
}

@Composable
internal fun FoodErrorMessage(message: String) {
    Text(message, color = COUITheme.colorScheme.error, style = COUITheme.textStyles.bodyS)
}

@Composable
internal fun FoodLoading() {
    val label = stringResource(R.string.food_loading)
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        FoodSupporting(label)
    }
}

@Composable
internal fun FoodTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    errorLabel: Int? = null,
) {
    val message = errorLabel?.let { stringResource(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CouiTextField(
            value, onChange, label = label, placeholder = placeholder,
            singleLine = singleLine, minLines = minLines, maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            fieldModifier = Modifier.semantics {
                contentDescription = label
                if (message != null) error(message)
            },
        )
        if (message != null) FoodErrorMessage(message)
    }
}

@Composable
internal fun FoodNumberField(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    decimal: Boolean = false,
    @StringRes errorLabel: Int? = null,
) {
    FoodTextField(
        value, onChange, stringResource(label),
        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        errorLabel = errorLabel,
    )
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (id, name) ->
            FoodChoiceChip(name, id == selected) { onSelect(id) }
        }
    }
}


@Composable
internal fun FoodChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Chip(label = label, selected = selected, onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp))
}
