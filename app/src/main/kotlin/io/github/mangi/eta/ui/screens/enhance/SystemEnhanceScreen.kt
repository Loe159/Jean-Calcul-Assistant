package io.github.mangi.eta.ui.screens.enhance

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.description
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.model.AgentSystemEnhanceAction
import androidx.compose.foundation.layout.padding
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup

@Composable
fun SystemEnhanceScreen(onAction: (AgentSystemEnhanceAction) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val capabilities = rememberDeviceCapabilities()
    val canRequestRoot = capabilities.root.suPresent && !capabilities.root.isGranted
    CouiScaffoldPage(
        title = stringResource(R.string.capability_enhancements),
        onBack = { onAction(AgentSystemEnhanceAction.NavigateBack) },
        modifier = modifier,
    ) {
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    BasicComponent(
                        title = "Root",
                        summary = capabilities.root.description(context),
                        endActions = {
                            CouiButton(
                                text = stringResource(if (canRequestRoot) R.string.capability_root_request else R.string.capability_root_refresh),
                                enabled = !capabilities.root.isChecking,
                                minHeight = 32.dp,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                onClick = { onAction(if (canRequestRoot) AgentSystemEnhanceAction.RequestRoot else AgentSystemEnhanceAction.RefreshRoot) },
                            )
                        },
                    )
                }
                item {
                    BasicComponent(
                        title = stringResource(R.string.capability_xposed_service),
                        summary = stringResource(if (capabilities.xposedConnected) R.string.capability_xposed_connected else R.string.capability_xposed_disconnected),
                    )
                }
            }
        }
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    BasicComponent(
                        title = stringResource(R.string.settings_virtual_display_title),
                        summary = "Root or LSPosed is required for virtual-display tasks. Enable it in Settings.",
                    )
                }
            }
        }
        CouiPageBottomSpacer()
    }
}
