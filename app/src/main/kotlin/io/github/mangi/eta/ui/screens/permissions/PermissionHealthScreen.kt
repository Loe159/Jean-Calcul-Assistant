package io.github.mangi.eta.ui.screens.permissions

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.color
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.label
import io.github.mangi.eta.ui.model.PermissionHealthAction
import io.github.mangi.eta.ui.model.PermissionHealthItemUi
import io.github.mangi.eta.ui.model.PermissionHealthUiState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.mangi.eta.ui.components.coui.PreferenceValueText
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

@Composable
fun PermissionHealthScreen(
    state: PermissionHealthUiState,
    onAction: (PermissionHealthAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    CouiScaffoldPage(
        title = stringResource(R.string.ui_permission_health_3048bb),
        onBack = { onAction(PermissionHealthAction.NavigateBack) },
        modifier = modifier,
    ) {
        PreferenceCategory(title = stringResource(R.string.ui_permissions_and_status_35f368))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                state.items.forEachIndexed { index, item ->
                    item {
                        PermissionItemRow(
                            item = item,
                            onActionClick = { onAction(PermissionHealthAction.OpenItemAction(item.id)) },
                        )
                    }
                    if (index < state.items.lastIndex) {

                    }
                }
            }
        }
        CouiPageBottomSpacer()
    }
}

@Composable
private fun PermissionItemRow(
    item: PermissionHealthItemUi,
    onActionClick: () -> Unit,
) {
    ArrowPreference(
        title = item.title,
        summary = item.summary.takeIf { it.isNotBlank() },
        endActions = {
            PreferenceValueText(value = item.status.label(), color = item.status.color())
        },
        onClick = onActionClick,
    )
}
