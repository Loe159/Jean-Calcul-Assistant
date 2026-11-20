package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

@Composable
internal fun rememberBriefSummary(
    source: BriefSummarySource,
    input: BriefSummaryInput,
    active: Boolean,
    pairingRevision: Long,
): BriefSummary {
    val latestInput by rememberUpdatedState(input)
    var remote by remember(source, pairingRevision) { mutableStateOf<BriefSummary?>(null) }
    LaunchedEffect(source, active, pairingRevision) {
        if (!active) return@LaunchedEffect
        var lastAttempt: BriefSummaryScope? = null
        snapshotFlow { latestInput.now to latestInput.scope }.filter { (_, scope) ->
            lastAttempt?.accepts(scope) != true || remote?.remote?.until?.let { !Instant.now().isBefore(it) } == true
        }.collectLatest {
            while (true) {
                val current = latestInput
                lastAttempt = current.scope
                val result = source.summarize(current)
                val validity = result.remote
                if (validity == null) {
                    remote = null
                    break
                }
                val latestScope = latestInput.scope
                if (!validity.scope.accepts(latestScope)) {
                    if (current.scope != latestScope) continue
                    remote = null
                    break
                }
                lastAttempt = validity.scope
                remote = result
                // The only timed refresh is scheduled for this successful response's expiry.
                var remaining = Duration.between(Instant.now(), validity.until)
                while (!remaining.isNegative && !remaining.isZero) {
                    delay(remaining.toMillis().coerceAtLeast(1))
                    remaining = Duration.between(Instant.now(), validity.until)
                }
                remote = null
            }
        }
    }
    return remote?.takeIf { it.remote?.accepts(input, Instant.now()) == true }
        ?: remember(input.scope, input.showNotificationCount) { localBriefSummary(input) }
}
