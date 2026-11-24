package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.CookingTimer
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.food.CookingProgressState
import io.github.mangi.eta.ui.screens.food.IngredientPortion
import io.github.mangi.eta.ui.screens.food.secondsLeft
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.LinearProgressIndicator
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.popup.OverlayDropdownPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun CookingProgress(
    progress: CookingProgressState,
    busy: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FoodHeading(stringResource(R.string.food_step_position, progress.index + 1, progress.steps.size))
                FoodSupporting(stringResource(R.string.food_remaining, progress.remainingMinutes))
            }
            CouiTopAppBarIconButton(
                COUIIcons.ChevronBackward, stringResource(R.string.food_previous), onPrevious,
                Modifier.size(48.dp), enabled = !busy && progress.index > 0,
            )
            CouiTopAppBarIconButton(
                COUIIcons.ChevronForward, stringResource(R.string.food_next), onNext,
                Modifier.size(48.dp), enabled = !busy && progress.index < progress.steps.lastIndex,
            )
        }
        LinearProgressIndicator(progress = progress.ratio, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun StepIngredientList(ingredients: List<IngredientPortion>) {
    if (ingredients.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ingredients.forEach {
            FoodSupporting(stringResource(R.string.food_consumed_amount, it.ingredient.name, it.display()))
        }
    }
}

@Composable
internal fun StepActions(
    progress: CookingProgressState,
    busy: Boolean,
    canFinish: Boolean,
    onDone: () -> Unit,
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
    timers.forEachIndexed { index, timer ->
        if (index > 0 && actions != null) HorizontalDivider()
        key(timer.id) { CookingTimer(timer, now, actions) }
    }
}

@Composable
private fun CookingTimer(timer: CookingTimer, nowMillis: Long, actions: TimerActions?) {
    val seconds = timer.secondsLeft(nowMillis)
    val time = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    val paused = timer.state == TimerState.PAUSED
    val duration = stringResource(R.string.food_timer_time_remaining, seconds / 60, seconds % 60)
    val timeModifier = Modifier.clearAndSetSemantics { contentDescription = duration }
    val status = if (paused) stringResource(R.string.food_timer_paused, timer.label) else timer.label
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actions == null) {
            Text(status, Modifier.weight(1f), style = COUITheme.textStyles.bodyM)
            Text(time, timeModifier, style = COUITheme.textStyles.headlineXS)
        } else {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(status, style = COUITheme.textStyles.bodyM)
                Text(time, timeModifier, style = COUITheme.textStyles.headlineXS)
            }
            CouiTopAppBarIconButton(
                if (paused) COUIIcons.Play else COUIIcons.Pause,
                stringResource(if (paused) R.string.food_timer_resume_named else R.string.food_timer_pause_named, timer.label),
                onClick = { if (paused) actions.resume(timer.id) else actions.pause(timer.id) },
                modifier = Modifier.size(48.dp),
            )
            TimerMenu(timer, actions)
        }
    }
}

@Composable
private fun TimerMenu(timer: CookingTimer, actions: TimerActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CouiTopAppBarIconButton(
            COUIIcons.More, stringResource(R.string.food_timer_options, timer.label),
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        )
        OverlayDropdownPopup(
            show = expanded,
            onDismiss = { expanded = false },
            onDismissFinished = {},
            maxHeight = null,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
            collapseOnSelection = true,
            entry = DropdownEntry(
                items = listOf(
                    DropdownItem(stringResource(R.string.food_timer_plus, 1), onClick = { actions.add(timer.id, 1) }),
                    DropdownItem(stringResource(R.string.food_timer_plus, 5), onClick = { actions.add(timer.id, 5) }),
                    DropdownItem(stringResource(R.string.food_timer_cancel), alert = true, onClick = { actions.cancel(timer.id) }),
                ),
            ),
        )
    }
}
