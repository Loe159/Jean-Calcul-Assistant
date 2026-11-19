package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.PermissionHealthUiState
import io.github.mangi.eta.ui.model.PermissionStatusUi
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun PermissionHealthCard(
    state: PermissionHealthUiState,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val issueCount = state.items.count { it.status != PermissionStatusUi.Available }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onOpenPermissions,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ui_permission_health_3048bb),
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.headlineXS,
                    color = COUITheme.colorScheme.labelPrimary,
                )
                Text(
                    text = if (issueCount == 0) {
                        stringResource(R.string.permission_health_ok)
                    } else {
                        pluralStringResource(R.plurals.permission_health_issues, issueCount, issueCount)
                    },
                    style = COUITheme.textStyles.bodyM,
                    color = if (issueCount == 0) {
                        COUITheme.colorScheme.labelSecondary
                    } else {
                        COUITheme.colorScheme.primary
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            state.items.take(3).forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PermissionStatusIcon(item.status)
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.bodyM,
                        color = COUITheme.colorScheme.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusLabel(item.status),
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PermissionStatusIcon(status: PermissionStatusUi) {
    val tint = when (status) {
        PermissionStatusUi.Available -> COUITheme.colorScheme.primary
        PermissionStatusUi.Warning -> COUITheme.colorScheme.primary
        PermissionStatusUi.Missing,
        PermissionStatusUi.Disabled -> COUITheme.colorScheme.labelSecondary
    }
    when (status) {
        PermissionStatusUi.Available -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        PermissionStatusUi.Warning -> Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        PermissionStatusUi.Missing,
        PermissionStatusUi.Disabled -> Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
    }
}

@Composable
private fun statusLabel(status: PermissionStatusUi): String = stringResource(when (status) {
    PermissionStatusUi.Available -> R.string.permission_status_ok
    PermissionStatusUi.Missing -> R.string.permission_status_missing
    PermissionStatusUi.Warning -> R.string.permission_status_warning
    PermissionStatusUi.Disabled -> R.string.permission_status_disabled
})
