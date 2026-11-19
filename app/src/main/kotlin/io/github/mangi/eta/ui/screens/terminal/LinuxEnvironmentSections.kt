package io.github.mangi.eta.ui.screens.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.PreferenceValueText
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

@Composable
internal fun LinuxEnvironmentStatusCard(
    title: String,
    mode: String,
    summary: String,
    busy: Boolean,
    message: String?,
    actionText: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = COUITheme.textStyles.headlineXS)
                Text(
                    text = mode,
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) InfiniteProgressIndicator(size = 20.dp)
                Text(
                    text = summary,
                    style = COUITheme.textStyles.bodyM,
                    color = COUITheme.colorScheme.labelSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
            message?.let {
                Text(
                    text = it,
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
            }
            actionText?.let {
                CouiButton(
                    text = it,
                    enabled = actionEnabled,
                    role = CouiButtonRole.Primary,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        CardListGroup {
            item {
                body()
            }
        }
    }
}

@Composable
internal fun LinuxEnvironmentConfiguration(
    distribution: LinuxDistribution,
    backend: LinuxExecutionBackend,
    rootGranted: Boolean,
    enabled: Boolean,
    onDistributionSelected: (LinuxDistribution) -> Unit,
    onBackendSelected: (LinuxExecutionBackend) -> Unit,
) {
    val distributions = LinuxDistribution.entries
    val backends = listOf(LinuxExecutionBackend.PROOT, LinuxExecutionBackend.CHROOT)
    PreferenceCategory(title = stringResource(R.string.linux_environment_configuration))
    Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        CardListGroup {
            item {
                OverlayDropdownPreference(
                    title = stringResource(R.string.linux_distribution_title),
                    items = distributions.map { it.displayName() },
                    selectedIndex = distributions.indexOf(distribution),
                    enabled = enabled,
                    onSelectedIndexChange = { onDistributionSelected(distributions[it]) },
                )
            }
            if (rootGranted || backend == LinuxExecutionBackend.CHROOT) {
                item {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.capability_linux_backend),
                        items = backends.map { it.displayName() },
                        selectedIndex = backends.indexOf(backend),
                        enabled = enabled,
                        onSelectedIndexChange = { index ->
                            val chosen = backends.getOrNull(index) ?: return@OverlayDropdownPreference
                            if (chosen == LinuxExecutionBackend.PROOT || rootGranted) {
                                onBackendSelected(chosen)
                            }
                        },
                    )
                }
            } else {
                item {
                    BasicComponent(
                        title = stringResource(R.string.capability_linux_backend),
                        endActions = {
                            PreferenceValueText(value = backend.displayName())
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun LinuxDistribution.displayName(): String = stringResource(
    when (this) {
        LinuxDistribution.ALPINE -> R.string.linux_distribution_alpine
        LinuxDistribution.DEBIAN -> R.string.linux_distribution_debian
    },
)

@Composable
internal fun LinuxExecutionBackend.displayName(): String = stringResource(
    when (this) {
        LinuxExecutionBackend.PROOT -> R.string.capability_linux_proot
        LinuxExecutionBackend.CHROOT -> R.string.capability_linux_chroot
    },
)
