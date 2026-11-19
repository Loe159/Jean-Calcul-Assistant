package io.github.mangi.eta.ui.components.coui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayInputDialog
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.ChipTabRow
import io.github.suqi8.coui.kmp.basic.CancelButton
import io.github.suqi8.coui.kmp.basic.InputField
import io.github.suqi8.coui.kmp.basic.SearchBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.theme.COUITheme

enum class CouiButtonRole {
    Primary,
    Secondary,
    Destructive,
    DestructiveFilled,
}

/** A compact text button using COUI's native press, focus, color and shape behavior. */
@Composable
internal fun CouiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: CouiButtonRole = CouiButtonRole.Secondary,
    minHeight: Dp = ButtonDefaults.MinHeight,
    contentPadding: PaddingValues = ButtonDefaults.InsideMargin,
) {
    val scheme = COUITheme.colorScheme
    val colors = when (role) {
        CouiButtonRole.Primary -> ButtonDefaults.buttonColorsPrimary()
        CouiButtonRole.Secondary -> ButtonDefaults.buttonColors()
        CouiButtonRole.Destructive -> ButtonDefaults.buttonColors(
            contentColor = scheme.error,
            disabledContentColor = scheme.errorDisabled,
        )
        CouiButtonRole.DestructiveFilled -> ButtonDefaults.buttonColors(
            color = scheme.error,
            disabledColor = scheme.errorDisabled,
            contentColor = Color.White,
            disabledContentColor = scheme.disabledOnPrimaryButton,
        )
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = minHeight,
        colors = colors,
        insideMargin = contentPadding,
    ) {
        Text(
            text = text,
            style = COUITheme.textStyles.buttonL,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A labeled COUI input, with an optional secondary label alongside its heading. */
@Composable
internal fun CouiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    rightLabel: String? = null,
    rightLabelColor: Color = Color.Unspecified,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    showClearButton: Boolean = singleLine,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrEmpty() || !rightLabel.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!label.isNullOrEmpty()) {
                    Text(
                        text = label,
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (!rightLabel.isNullOrEmpty()) {
                    Text(
                        text = rightLabel,
                        style = COUITheme.textStyles.bodyS,
                        color = rightLabelColor.takeIf { it != Color.Unspecified }
                            ?: COUITheme.colorScheme.labelSecondary,
                    )
                }
            }
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            backgroundMode = TextFieldMode.Rectangle,
            label = placeholder.orEmpty(),
            enabled = enabled,
            showClearButton = showClearButton,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = COUITheme.textStyles.bodyXL,
        )
    }
}

/** An inline COUI search control with a dismissible expanded state. */
@Composable
internal fun CouiSearchBarBox(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    onSearch: () -> Unit = {},
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val collapse: () -> Unit = {
        expanded = false
        onQueryChange("")
        keyboard?.hide()
    }
    BackHandler(enabled = expanded, onBack = collapse)
    SearchBar(
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = {
                    keyboard?.hide()
                    onSearch()
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                label = placeholder,
                enabled = enabled,
            )
        },
        onExpandedChange = { next -> if (next) expanded = true else collapse() },
        modifier = modifier.fillMaxWidth().background(backgroundColor),
        expanded = expanded,
        outsideEndAction = {
            CancelButton(
                onClick = collapse,
                text = stringResource(R.string.action_cancel),
                enabled = enabled,
            )
        },
    ) {}
}

/** Keeps model filter keys attached to COUI's native tab selection. */
@Composable
internal fun <T : Any> CouiChipTabs(
    items: List<Pair<T, String>>,
    selectedKey: T,
    onSelectedKeyChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
) {
    ChipTabRow(
        tabs = items.map { it.second },
        selectedTabIndex = items.indexOfFirst { it.first == selectedKey },
        onTabSelected = { index -> items.getOrNull(index)?.first?.let(onSelectedKeyChange) },
        modifier = modifier.fillMaxWidth().background(backgroundColor),
        enabled = enabled,
    )
}

/** The COUI 17 dialog panel: blurred over the recorded page when available, opaque otherwise. */
@Composable
private fun AppDialog(
    visible: Boolean,
    title: String?,
    message: String?,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = visible,
        title = title,
        summary = message,
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = blur != null,
        onDismissRequest = onDismissRequest,
        content = content,
    )
}

/** Extra dialog body, inset like the COUI title and summary (the dialog content slot has no padding). */
@Composable
private fun DialogBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp),
        content = content,
    )
}

/** A confirmation dialog; a [destructive] confirmation takes the COUI neutral (error-coloured) slot. */
@Composable
internal fun CouiConfirmDialog(
    visible: Boolean,
    title: String?,
    message: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    cancelText: String = stringResource(R.string.action_cancel),
    confirmEnabled: Boolean = true,
    cancelEnabled: Boolean = true,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppDialog(visible = visible, title = title, message = message, onDismissRequest = onDismissRequest) {
        if (content != null) DialogBody(content)
        val confirm = DialogButtonBarAction(text = confirmText, enabled = confirmEnabled, onClick = onConfirm)
        DialogButtonBar(
            negative = DialogButtonBarAction(text = cancelText, enabled = cancelEnabled, onClick = onDismissRequest),
            positive = confirm.takeUnless { destructive },
            neutral = confirm.takeIf { destructive },
        )
    }
}

/** An information dialog with a single closing action. */
@Composable
internal fun CouiNoticeDialog(
    visible: Boolean,
    title: String?,
    message: String?,
    onDismissRequest: () -> Unit,
    confirmText: String = stringResource(R.string.action_close),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AppDialog(visible = visible, title = title, message = message, onDismissRequest = onDismissRequest) {
        if (content != null) DialogBody(content)
        DialogButtonBar(
            negative = null,
            positive = DialogButtonBarAction(text = confirmText, onClick = onDismissRequest),
        )
    }
}

/** A single-field input dialog. */
@Composable
internal fun CouiInputDialog(
    visible: Boolean,
    title: String?,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    message: String? = null,
    placeholder: String? = null,
    confirmEnabled: Boolean = true,
    cancelText: String = stringResource(R.string.action_cancel),
) {
    val blur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayInputDialog(
        show = visible,
        value = value,
        onValueChange = onValueChange,
        backgroundModifier = blur?.backgroundModifier ?: Modifier,
        title = title,
        summary = message,
        label = placeholder.orEmpty(),
        negative = DialogButtonBarAction(text = cancelText, onClick = onDismissRequest),
        positive = DialogButtonBarAction(text = confirmText, enabled = confirmEnabled, onClick = onConfirm),
        backgroundColor = blur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        onDismissRequest = onDismissRequest,
        materialStroke = blur != null,
    )
}
