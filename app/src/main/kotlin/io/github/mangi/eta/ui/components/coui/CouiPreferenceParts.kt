package io.github.mangi.eta.ui.components.coui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import io.github.suqi8.coui.kmp.preference.PreferenceCategoryDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The value shown at the end of a preference row, styled like COUI's own assignment text
 * (`TextAppearance.COUI.List.Assignment.End`, which coui-preference keeps internal).
 */
@Composable
internal fun RowScope.PreferenceValueText(
    value: String?,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
) {
    if (value.isNullOrEmpty()) return
    Text(
        text = value,
        modifier = Modifier
            .padding(end = 4.dp)
            .weight(1f, fill = false),
        fontSize = COUITheme.textStyles.bodyS.fontSize,
        color = when {
            !enabled -> COUITheme.colorScheme.disabledNeutral
            color != Color.Unspecified -> color
            else -> COUITheme.colorScheme.labelSecondary
        },
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** An explanatory note under a settings card, aligned with the category titles. */
@Composable
internal fun PreferenceFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PreferenceCategoryDefaults.TitleMarginStartLarge)
            .padding(top = 0.dp, bottom = 16.dp),
        style = COUITheme.textStyles.bodyS,
        color = COUITheme.colorScheme.labelSecondary,
    )
}

/**
 * A settings section whose rows are free composables: the category title (with an optional end
 * widget) above a COUI card inset 16dp from the page edges and from the next section.
 */
@Composable
internal fun PreferenceSection(
    title: String?,
    modifier: Modifier = Modifier,
    endWidget: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) PreferenceCategory(title = title, endWidget = endWidget)
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            content = content,
        )
    }
}
