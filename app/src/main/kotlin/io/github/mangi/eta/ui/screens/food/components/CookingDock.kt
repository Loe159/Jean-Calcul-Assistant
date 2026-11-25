package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.food.CookingStatus
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.ui.screens.food.*
import io.github.suqi8.coui.kmp.theme.COUITheme

internal val LocalCookingDock = compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
internal fun WithCookingDock(onResume: (String) -> Unit, content: @Composable () -> Unit) {
    val repo = foodRuntime().repository
    val session by remember(repo) { repo.activeSession() }.collectAsState(initial = null)
    val current = session?.takeIf { it.status == CookingStatus.ACTIVE }
    val now = rememberCookingClock(current)
    val projection = current?.toActiveCookingUi()
    val timers = current?.activeTimers().orEmpty().map { it.toTimerUi(now) }
    val next = timers.firstOrNull { it.state == TimerState.FINISHED }
        ?: timers.filter { it.state == TimerState.RUNNING }.minByOrNull { it.secondsLeft }
        ?: timers.firstOrNull()
    CompositionLocalProvider(
        LocalCookingDock provides {
            AnimatedVisibility(projection != null, enter = slideInVertically { it / 2 } + fadeIn(), exit = fadeOut()) {
                if (projection != null) CookingDockContent(projection, next) { onResume(projection.sessionId) }
            }
        },
        content = content,
    )
}

@Composable
internal fun CookingDockContent(session: ActiveCookingUi, next: CookingTimerUi?, onResume: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(COUITheme.colorScheme.backgroundWithCard)
        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        ActiveCookingBanner(session, next, onResume)
    }
}
