package io.github.mangi.eta.ui.pages.providers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.HorizontalDivider

internal fun LazyListScope.providerHeadersEditor(
    headers: List<ProviderHeaderDraft>,
    onHeadersChange: (List<ProviderHeaderDraft>) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    item(key = "custom_headers") {
        ProviderHeadersSection(
            headers = headers,
            onHeadersChange = onHeadersChange,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
    }
}

@Composable
internal fun ProviderHeadersSection(
    headers: List<ProviderHeaderDraft>,
    onHeadersChange: (List<ProviderHeaderDraft>) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    ProviderSection(title = "Custom headers") {
        val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)
        BasicComponent(
            title = if (headers.isEmpty()) "Not set" else "Configured ${headers.size} entries",
            summary = "You can override User-Agent. Authentication and transport headers are managed by the app.",
            endActions = {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = COUITheme.colorScheme.labelSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation),
                )
            },
            onClick = { onExpandedChange(!expanded) },
        )
        if (expanded) {
            headers.forEach { row ->
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
                ProviderHeaderRow(
                    row = row,
                    onNameChange = { value ->
                        onHeadersChange(headers.map {
                            if (it.id == row.id) it.copy(header = it.header.copy(name = value)) else it
                        })
                    },
                    onValueChange = { value ->
                        onHeadersChange(headers.map {
                            if (it.id == row.id) it.copy(header = it.header.copy(value = value)) else it
                        })
                    },
                    onRemove = { onHeadersChange(headers.filterNot { it.id == row.id }) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
            BasicComponent(
                title = "Add header",
                titleColor = BasicComponentDefaults.titleColor(color = COUITheme.colorScheme.primary),
                onClick = { onHeadersChange(headers + ProviderHeaderDraft()) },
            )
        }
    }
}

@Composable
private fun ProviderHeaderRow(
    row: ProviderHeaderDraft,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var visible by remember(row.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CouiTextField(
                value = row.header.name,
                onValueChange = onNameChange,
                label = "Name",
                singleLine = true,
            )
            CouiTextField(
                value = row.header.value,
                onValueChange = onValueChange,
                label = "Value",
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(28.dp)
                            .clickable(role = Role.Button) { visible = !visible },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = stringResource(
                                if (visible) R.string.page_hide_bb0e7e else R.string.page_show_71b677,
                            ),
                            modifier = Modifier.size(20.dp),
                            tint = COUITheme.colorScheme.labelSecondary,
                        )
                    }
                },
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.ui_delete_3755f5),
                modifier = Modifier.size(22.dp),
                tint = COUITheme.colorScheme.labelSecondary,
            )
        }
    }
}
