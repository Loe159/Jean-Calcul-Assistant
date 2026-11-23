package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.CookingTimer
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.ui.screens.food.CookingProgressState
import io.github.mangi.eta.ui.screens.food.IngredientPortion
import io.github.mangi.eta.ui.screens.food.secondsLeft
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun CookingProgress(progress: CookingProgressState) {
    FoodHeading(stringResource(R.string.food_step_position, progress.index + 1, progress.steps.size))
    val width by animateFloatAsState(progress.ratio, tween(180), label = "cooking-progress")
    Box(Modifier.fillMaxWidth().height(7.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(progress.ratio, 0f..1f)
    }.squircleSurface(COUITheme.colorScheme.primary.copy(alpha = 0.18f), cornerRadius = 4.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(width).squircleSurface(COUITheme.colorScheme.primary, cornerRadius = 4.dp))
    }
    FoodSupporting(stringResource(R.string.food_remaining, progress.remainingMinutes))
}

@Composable
internal fun StepIngredientList(ingredients: List<IngredientPortion>) {
    if (ingredients.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ingredients.forEach {
            Text(
                stringResource(R.string.food_consumed_amount, it.ingredient.name, it.display()),
                modifier = Modifier.squircleSurface(COUITheme.colorScheme.primary.copy(alpha = 0.12f), cornerRadius = 14.dp)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                style = COUITheme.textStyles.bodyS,
            )
        }
    }
}

@Composable
internal fun StepNavigator(
    progress: CookingProgressState,
    busy: Boolean,
    canFinish: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val last = progress.index == progress.steps.lastIndex
    if (canFinish) {
        FoodAction(R.string.food_finish, primary = true, enabled = !busy, onClick = onFinish)
        if (!last) FoodAction(R.string.food_step_done, enabled = !busy, onClick = onDone)
    } else {
        FoodAction(R.string.food_step_done, primary = true, enabled = !busy, onClick = onDone)
        if (last) FoodAction(R.string.food_finish, enabled = !busy, onClick = onFinish)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FoodButton(stringResource(R.string.food_previous), onPrevious, enabled = !busy && progress.index > 0)
        FoodButton(stringResource(R.string.food_next), onNext, enabled = !busy && !last)
        FoodButton(stringResource(R.string.food_step_skip), onSkip, enabled = !busy)
    }
}

internal data class TimerActions(
    val pause: (String) -> Unit,
    val resume: (String) -> Unit,
    val add: (String, Int) -> Unit,
    val cancel: (String) -> Unit,
)

@Composable
internal fun TimerStack(timers: List<CookingTimer>, actions: TimerActions? = null) {
    if (timers.isEmpty()) return
    val ticking = timers.any { it.state == TimerState.RUNNING }
    val now by produceState(System.currentTimeMillis(), ticking) {
        value = System.currentTimeMillis()
        if (ticking) while (true) {
            delay(250)
            value = System.currentTimeMillis()
        }
    }
    FoodHeading(stringResource(R.string.food_timers))
    timers.forEach { timer ->
        key(timer.id) { CookingTimer(timer, now, actions) }
    }
}

@Composable
internal fun CookingTimer(timer: CookingTimer, nowMillis: Long, actions: TimerActions?) {
    val seconds = timer.secondsLeft(nowMillis)
    val time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(timer.label, Modifier.weight(1f))
        Text(time, style = COUITheme.textStyles.headlineXS)
    }
    if (actions != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (timer.state == TimerState.PAUSED) {
                FoodButton(stringResource(R.string.food_timer_resume), { actions.resume(timer.id) })
            } else {
                FoodButton(stringResource(R.string.food_timer_pause), { actions.pause(timer.id) })
            }
            for (minutes in listOf(1, 5)) {
                FoodButton(stringResource(R.string.food_timer_plus, minutes), { actions.add(timer.id, minutes) })
            }
            FoodButton(stringResource(R.string.food_timer_cancel), { actions.cancel(timer.id) })
        }
    }
}
