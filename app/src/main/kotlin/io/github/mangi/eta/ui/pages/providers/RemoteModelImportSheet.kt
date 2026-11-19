package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.repository.RemoteModelCatalog
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.model.ModelSearchQuery
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.panelBlur
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.basic.Checkbox

/** 一次拉取的待选结果；确认时保留连接和账号绑定，避免将过期目录写到另一连接。 */
internal data class RemoteModelImport(
    val catalog: RemoteModelCatalog,
    val candidates: List<Model>,
)

@Composable
internal fun RemoteModelImportSheet(
    pending: RemoteModelImport?,
    onDismissRequest: () -> Unit,
    onConfirm: (RemoteModelImport, Set<String>) -> Unit,
) {
    var shown by remember { mutableStateOf(pending) }
    if (pending != null) shown = pending
    var selected by remember(pending) { mutableStateOf(emptySet<String>()) }
    val confirmText = if (selected.isNotEmpty()) {
        stringResource(R.string.remote_model_import_confirm, selected.size)
    } else {
        stringResource(R.string.remote_model_import_pick_hint)
    }
    val dark = COUITheme.isDark
    val dialogBlur = panelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(
        show = pending != null,
        title = stringResource(R.string.remote_model_import_title),
        backgroundModifier = dialogBlur?.backgroundModifier ?: Modifier,
        backgroundColor = dialogBlur?.backgroundColor ?: DialogDefaults.backgroundColor(),
        materialStroke = dialogBlur != null,
        onDismissRequest = onDismissRequest,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            shown?.let { current ->
                key(current) {
                    RemoteModelImportContent(
                        pending = current,
                        selected = selected,
                        onSelectionChange = { selected = it },
                        confirmText = confirmText,
                        onConfirm = { selected -> onConfirm(current, selected) },
                    )
                }
            }
        }
        DialogButtonBar(
            negative = DialogButtonBarAction(text = stringResource(R.string.action_cancel), onClick = onDismissRequest),
            positive = null,
        )
    }
}

@Composable
private fun RemoteModelImportContent(
    pending: RemoteModelImport,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    confirmText: String,
    onConfirm: (Set<String>) -> Unit,
) {
    val candidates = pending.candidates
    var query by remember { mutableStateOf("") }
    val visible = remember(candidates, query) {
        val search = ModelSearchQuery(query)
        candidates.filter { search.matches(it.displayName, it.modelId) }
    }
    val allVisibleSelected = visible.isNotEmpty() && visible.all { it.modelId in selected }
    val density = LocalDensity.current
    val sheetHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.72f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetHeight)
            .then(Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.remote_model_import_summary, candidates.size),
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                modifier = Modifier.weight(1f),
            )
            val toggleAll: () -> Unit = {
                val ids = visible.mapTo(mutableSetOf()) { it.modelId }
                onSelectionChange(if (allVisibleSelected) selected - ids else selected + ids)
            }
            val toggleText = stringResource(
                if (allVisibleSelected) R.string.page_select_none_ba20eb else R.string.page_select_all_3e44b2,
            )
            CouiButton(
                text = toggleText,
                onClick = toggleAll,
                enabled = visible.isNotEmpty(),
                role = CouiButtonRole.Primary,
                minHeight = 32.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        CouiSearchBarBox(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.ui_search_model_df5586),
            backgroundColor = Color.Transparent,
        )
        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
        ) {
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.page_no_matching_model_found_ae7e96),
                        style = COUITheme.textStyles.bodyM,
                        color = COUITheme.colorScheme.labelSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 48.dp),
                    )
                }
            }
            items(visible, key = { it.modelId }) { model ->
                val checked = model.modelId in selected
                RemoteModelRow(
                    model = model,
                    checked = checked,
                    onToggle = {
                        onSelectionChange(if (checked) selected - model.modelId else selected + model.modelId)
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoteModelRow(
    model: Model,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val name = model.displayName.ifBlank { model.modelId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .squircleSurface(color = Color.Transparent, cornerRadius = 12.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = COUITheme.textStyles.bodyM,
                color = COUITheme.colorScheme.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!name.equals(model.modelId, ignoreCase = true)) {
                Text(
                    text = model.modelId,
                    style = COUITheme.textStyles.bodyXS,
                    color = COUITheme.colorScheme.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        val dark = COUITheme.isDark
        Checkbox(
            state = ToggleableState(checked),
            onClick = null,
        )
    }
}
