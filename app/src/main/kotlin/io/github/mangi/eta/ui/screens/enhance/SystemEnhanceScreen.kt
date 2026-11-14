package io.github.mangi.eta.ui.screens.enhance

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itos.coui.layout.Group
import com.itos.coui.layout.Divider
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.app.description
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.model.AgentSystemEnhanceAction

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
        Group {
            CouiPreference(
                title = "Root",
                summary = capabilities.root.description(context),
                showChevron = false,
                trailing = {
                    CouiButton(
                        text = stringResource(if (canRequestRoot) R.string.capability_root_request else R.string.capability_root_refresh),
                        enabled = !capabilities.root.isChecking,
                        minHeight = 32.dp,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        onClick = { onAction(if (canRequestRoot) AgentSystemEnhanceAction.RequestRoot else AgentSystemEnhanceAction.RefreshRoot) },
                    )
                },
            )
            Divider()
            CouiPreference(
                title = stringResource(R.string.capability_xposed_service),
                summary = stringResource(if (capabilities.xposedConnected) R.string.capability_xposed_connected else R.string.capability_xposed_disconnected),
                showChevron = false,
            )
        }
        Group {
            CouiPreference(title = stringResource(R.string.settings_virtual_display_title),
                summary = "Root ou LSPosed est nécessaire pour exécuter les tâches sur un écran virtuel. Activation dans les réglages.", showChevron = false)
        }
        CouiPageBottomSpacer()
    }
}
