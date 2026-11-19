package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.model.ActiveRunSummaryUi
import io.github.mangi.eta.ui.model.RunStatusUi
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
fun AgentStatusCard(
    activeRun: ActiveRunSummaryUi,
    onOpenRun: () -> Unit,
    onStopRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        onClick = onOpenRun,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(status = activeRun.status)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activeRun.title,
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.headlineXS,
                    color = COUITheme.colorScheme.labelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = activeRun.currentStep,
                style = COUITheme.textStyles.bodyM,
                color = COUITheme.colorScheme.labelSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activeRun.elapsedLabel,
                    modifier = Modifier.weight(1f),
                    style = COUITheme.textStyles.bodyS,
                    color = COUITheme.colorScheme.labelSecondary,
                )
                if (activeRun.status == RunStatusUi.Running) {
                    TextButton(
                        text = stringResource(R.string.ui_stop_a17f70),
                        onClick = onStopRun,
                        colors = ButtonDefaults.textButtonColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: RunStatusUi) {
    when (status) {
        RunStatusUi.Running -> InfiniteProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = COUITheme.colorScheme.primary,
        )
        RunStatusUi.Success -> Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = COUITheme.colorScheme.primary,
        )
        RunStatusUi.Failed -> Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = COUITheme.colorScheme.primary,
        )
        RunStatusUi.Cancelled -> Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = COUITheme.colorScheme.labelSecondary,
        )
    }
}
