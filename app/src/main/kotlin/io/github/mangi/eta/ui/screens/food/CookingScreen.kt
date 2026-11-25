package io.github.mangi.eta.ui.screens.food

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Alarm
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.icon.extended.Timer
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

internal enum class CookingSheet { INGREDIENTS, STEPS, HOW_TO, ASSISTANT, OPTIONS, INGREDIENT, STOP }

internal data class CookingScreenState(
    val title: String,
    val index: Int,
    val count: Int,
    val step: CookingStepUi?,
    val completed: Set<Int>,
    val timers: List<CookingTimerUi> = emptyList(),
    val drawerExpanded: Boolean = false,
    val largeText: Boolean = false,
    val busy: Boolean = false,
    val exactAlarms: Boolean = true,
    val notificationsDenied: Boolean = false,
    val stepTimer: CookingTimerUi? = null,
)

@Composable
internal fun CookingScreen(
    state: CookingScreenState,
    onMinimize: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSheet: (CookingSheet) -> Unit,
    onIngredient: (String) -> Unit,
    onTimerDrawer: (Boolean) -> Unit,
    onStartTimer: () -> Unit,
    onNewTimer: (Int) -> Unit,
    onTextSize: () -> Unit,
    onAllowExact: () -> Unit,
    timerActions: TimerActions,
    overlay: @Composable () -> Unit = {},
) {
    val colors = COUITheme.colorScheme
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val scroll = rememberScrollState()
    LaunchedEffect(state.index) { scroll.scrollTo(0) }

    Scaffold(containerColor = colors.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).safeDrawingPadding()) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                val alarm = state.timers.firstOrNull { it.state == TimerState.FINISHED }
                AnimatedVisibility(
                    alarm != null,
                    enter = slideInVertically(spring(0.8f, 438.65f)) { -it } + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    var shown by remember { mutableStateOf(alarm) }
                    SideEffect { if (alarm != null) shown = alarm }
                    shown?.let { timer -> TimerAlarm(timer, timerActions) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CuisineIconButton(CuisineIcons.ChevronDown, stringResource(R.string.food_minimize_cooking), onMinimize,
                        size = 48.dp, neutralFocus = true)
                    Column(Modifier.weight(1f)) {
                        // One line each: at 200 % font the bar keeps its height and the step stays readable.
                        Text(state.title, style = COUITheme.textStyles.headlineS, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.food_step_of_count, state.index + 1, state.count),
                            style = COUITheme.textStyles.bodyS, color = colors.labelSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CuisineIconButton(COUIIcons.Notes, stringResource(R.string.food_steps),
                        { onSheet(CookingSheet.STEPS) }, size = 48.dp)
                    CuisineIconButton(CuisineIcons.Basket, stringResource(R.string.food_ingredients),
                        { onSheet(CookingSheet.INGREDIENTS) }, size = 48.dp)
                    Box {
                        val alarm = state.timers.any { it.state == TimerState.FINISHED }
                        val label = if (state.timers.isEmpty()) stringResource(R.string.food_timers)
                        else stringResource(R.string.food_timer_count, state.timers.size)
                        CuisineIconButton(COUIIcons.Timer, label, { onTimerDrawer(!state.drawerExpanded) },
                            size = 48.dp, ink = if (alarm) cuisineTint(colors.orange) else colors.labelPrimary)
                        if (state.timers.isNotEmpty()) {
                            Surface(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).clearAndSetSemantics {},
                                shape = SquircleShape(8.dp),
                                color = if (alarm) colors.containerOrange else colors.primary,
                                contentColor = if (alarm) colors.labelPrimary else colors.onPrimary,
                            ) {
                                Text(state.timers.size.toString(), Modifier.padding(horizontal = 5.dp), style = COUITheme.textStyles.caption)
                            }
                        }
                    }
                }
                StepProgress(state.index, state.count, Modifier.padding(top = 4.dp, bottom = 12.dp), state.completed)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TimerDrawer(state.timers, state.drawerExpanded, timerActions, onTimerDrawer, onNewTimer = onNewTimer,
                        minControlHeight = 56.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        Button(onTextSize, minHeight = 56.dp, material = ButtonMaterial.None,
                            colors = ButtonDefaults.buttonColors(color = colors.background.copy(alpha = 0f))) {
                            Text(stringResource(if (state.largeText) R.string.food_text_large else R.string.food_text_normal),
                                color = colors.labelSecondary, style = COUITheme.textStyles.bodyS)
                        }
                        CuisineIconButton(COUIIcons.More, stringResource(R.string.food_cooking_options),
                            { onSheet(CookingSheet.OPTIONS) }, size = 56.dp)
                    }
                    AnimatedContent(
                        state.step,
                        modifier = Modifier.fillMaxWidth().clipToBounds().pointerInput(state.busy, state.index) {
                            if (!state.busy) {
                                var drag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { drag = 0f },
                                    onDragEnd = {
                                        if (drag < -70.dp.toPx()) next()
                                        if (drag > 70.dp.toPx()) previous()
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    drag += amount
                                }
                            }
                        },
                        contentKey = { it?.step?.id },
                        transitionSpec = {
                            val direction = if ((targetState?.number ?: 0) > (initialState?.number ?: 0)) 1 else -1
                            (slideInHorizontally(spring(1f, 438.65f)) { it * direction } + fadeIn()) togetherWith
                                (slideOutHorizontally(spring(1f, 438.65f)) { -it * direction } + fadeOut())
                        },
                        label = "Cooking step",
                    ) { step ->
                        if (step != null) {
                            CookingStep(
                                step, textScale = if (state.largeText) CookingTextScale.LARGE else CookingTextScale.NORMAL,
                                timer = state.stepTimer?.takeIf { it.stepNumber == step.number },
                                onStartTimer = if (state.busy) null else onStartTimer,
                                onOpenTimers = { onTimerDrawer(true) },
                                onIngredient = onIngredient,
                                onHowTo = if (step.step.image != null || step.step.tips.isNotEmpty()) {
                                    { onSheet(CookingSheet.HOW_TO) }
                                } else null,
                                onAssistant = { onSheet(CookingSheet.ASSISTANT) },
                            )
                        }
                    }
                    if (!state.exactAlarms) {
                        FoodSupporting(stringResource(R.string.food_exact_alarms))
                        FoodAction(R.string.food_allow_exact, onClick = onAllowExact)
                    }
                    if (state.notificationsDenied) FoodSupporting(stringResource(R.string.food_notifications_denied))
                }
                StepNavigator(state.index, state.count, onPrevious, onNext,
                    Modifier.padding(top = 8.dp, bottom = 12.dp), enabled = !state.busy)
            }

        }
        overlay()
    }
}

@Composable
private fun TimerAlarm(timer: CookingTimerUi, actions: TimerActions) {
    val colors = COUITheme.colorScheme
    val bell = remember { Animatable(0f) }
    LaunchedEffect(timer.id) {
        bell.snapTo(0f)
        bell.animateTo(14f, spring(0.7f, 631.65f))
        bell.animateTo(0f, spring(0.28f, 631.65f))
    }
    Card(Modifier.fillMaxWidth().padding(top = 4.dp), colors = CardDefaults.defaultColors(color = colors.containerOrange)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(COUIIcons.Alarm, null, Modifier.size(24.dp).rotate(bell.value), tint = cuisineTint(colors.orange))
                Column {
                    FoodHeading(timer.label)
                    FoodSupporting(stringResource(R.string.food_timer_finished))
                }
            }
            Button({ actions.add(timer.id, 1) }, minHeight = 56.dp, minWidth = 56.dp) {
                Text(stringResource(R.string.food_timer_add_minute))
            }
            Button({ actions.cancel(timer.id) }, minHeight = 56.dp, minWidth = 56.dp,
                colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(stringResource(R.string.food_timer_stop))
            }
        }
    }
}

internal fun CookingScreenState.advanceToFinish() = index >= count - 1
