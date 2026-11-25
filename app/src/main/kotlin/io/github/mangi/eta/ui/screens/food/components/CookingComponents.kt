package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.food.CookingTimer
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.food.*
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Alarm
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Timer
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.popup.OverlayDropdownPopup
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.delay

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
    val model = timer.toTimerUi(nowMillis)
    val seconds = model.secondsLeft
    val time = timerClock(seconds, padMinutes = false)
    val paused = model.state == TimerState.PAUSED
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

@Composable
internal fun CookingStep(
    model: CookingStepUi,
    modifier: Modifier = Modifier,
    textScale: CookingTextScale = CookingTextScale.NORMAL,
    timer: CookingTimerUi? = null,
    onStartTimer: (() -> Unit)? = null,
    onOpenTimers: (() -> Unit)? = null,
    onIngredient: ((String) -> Unit)? = null,
    onHowTo: (() -> Unit)? = null,
    onAssistant: (() -> Unit)? = null,
    loading: Boolean = false,
) {
    if (loading) {
        CuisineLoading(modifier.heightIn(min = 140.dp), lines = 3)
        return
    }
    val colors = COUITheme.colorScheme
    val styles = COUITheme.textStyles
    val scale = textScale.factor
    val instruction = buildAnnotatedString {
        var cursor = 0
        model.quantityRanges.forEach { range ->
            append(model.instruction, cursor, range.first)
            append(model.instruction.substring(range).replace(' ', '\u00a0'))
            cursor = range.last + 1
            addStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline,
                    background = colors.primary.copy(alpha = 0.2f),
                ),
                range.first, cursor,
            )
        }
        append(model.instruction, cursor, model.instruction.length)
    }
    Column(
        modifier.fillMaxWidth().background(colors.background).padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = SquircleShape(18.dp), color = colors.primary, contentColor = colors.onPrimary) {
                Text(model.number.toString(), Modifier.widthIn(min = 32.dp).padding(4.dp).clearAndSetSemantics {},
                    color = colors.onPrimary, style = styles.buttonL, textAlign = TextAlign.Center)
            }
            Text(model.step.title, Modifier.semantics { heading() }, style = styles.headlineS)
        }
        Text(
            instruction, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = styles.displayXS.copy(
                fontSize = styles.displayXS.fontSize * scale,
                lineHeight = styles.displayXS.lineHeight * scale,
                fontWeight = FontWeight.Normal,
            ),
        )
        val heat = model.heat ?: model.step.temperature?.value?.let { stringResource(R.string.food_temperature, it.toString()) }
        val equipment = stringArrayResource(R.array.food_equipment_names)
        val tools = model.tool ?: model.step.equipment.joinToString(" · ") { equipment[it.ordinal] }.ifBlank { null }
        if (heat != null || tools != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (heat != null) CuisineBadge(heat, icon = CuisineIcons.Flame)
                if (tools != null) CuisineBadge(tools, icon = CuisineIcons.Pot)
            }
        }
        if (model.ingredients.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FoodSupporting(stringResource(R.string.food_for_this_step))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.ingredients.forEach { line ->
                        val label = stringResource(R.string.food_consumed_amount, line.ingredient.name, line.display())
                        if (onIngredient == null) CuisineBadge(label)
                        else Button({ onIngredient(line.ingredient.id) }, minWidth = 48.dp, minHeight = 56.dp,
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(label, style = styles.buttonM)
                        }
                    }
                }
            }
        }
        val suggestion = model.step.timerSuggestion
        if (timer != null && onOpenTimers != null) {
            CookingTimerAction(timer.label, timer.statusLabel(), onOpenTimers,
                timer = timer)
        } else if (suggestion != null && onStartTimer != null) {
            val duration = timerDuration(suggestion.durationSeconds.toLong())
            CookingTimerAction(stringResource(R.string.food_timer_launch, duration),
                stringResource(R.string.food_timer_named_duration, suggestion.label, duration), onStartTimer)
        }
        if (onHowTo != null || onAssistant != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onHowTo != null) CookingLink(stringResource(R.string.food_how_to), onHowTo)
                if (onAssistant != null) CookingLink(stringResource(R.string.food_ask_assistant), onAssistant)
            }
        }
    }
}

@Composable
private fun CookingLink(label: String, onClick: () -> Unit) {
    val colors = COUITheme.colorScheme
    Button(
        onClick, minWidth = 56.dp, minHeight = 56.dp, material = ButtonMaterial.None,
        colors = ButtonDefaults.buttonColors(color = colors.background.copy(alpha = 0f)),
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(label, style = COUITheme.textStyles.buttonM, color = cuisineTint(colors.primary))
    }
}

@Composable
private fun CookingTimerAction(title: String, summary: String, onClick: () -> Unit, timer: CookingTimerUi? = null) {
    val colors = COUITheme.colorScheme
    val alarm = timer?.state == TimerState.FINISHED
    val fill = when (timer?.state) {
        TimerState.PAUSED -> colors.container8
        TimerState.FINISHED -> colors.containerOrange
        else -> colors.primary.copy(alpha = 0.17f)
    }
    Card(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { role = Role.Button },
        colors = CardDefaults.defaultColors(color = fill), onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (alarm) COUIIcons.Alarm else COUIIcons.Timer, null, tint = cuisineTint(if (alarm) colors.orange else colors.primary))
            Column(Modifier.weight(1f)) {
                Text(title, style = COUITheme.textStyles.headlineS)
                FoodSupporting(summary)
                if (timer != null && !alarm) FoodSupporting(timer.remainingLabel())
            }
        }
    }
}

@Composable
internal fun StepProgress(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    completed: Set<Int> = emptySet(),
    onSelect: ((Int) -> Unit)? = null,
) {
    if (count <= 0) return
    val current = index.coerceIn(0, count - 1)
    val description = stringResource(R.string.food_step_of_count, current + 1, count)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val interactive = onSelect != null && maxWidth >= 48.dp * count + 4.dp * (count - 1)
        val actions = if (onSelect != null && !interactive) {
            (0 until count).map { position ->
                CustomAccessibilityAction(stringResource(R.string.food_step_of_count, position + 1, count)) {
                    onSelect(position)
                    true
                }
            }
        } else emptyList()
        val indicatorSemantics = Modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo((current + 1).toFloat(), 0f..count.toFloat())
            contentDescription = description
            customActions = actions
        }
        val transparent = COUITheme.colorScheme.background.copy(alpha = 0f)
        Row(
            Modifier.fillMaxWidth().then(indicatorSemantics),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(count) { position ->
                val progress = when {
                    position in completed -> 1f
                    position == current -> 0.5f
                    else -> 0f
                }
                if (interactive) {
                    val text = stringResource(R.string.food_step_of_count, position + 1, count)
                    val status = stringResource(when {
                        position in completed -> R.string.food_step_done
                        position == current -> R.string.food_step_current
                        else -> R.string.food_step_upcoming
                    })
                    Button(
                        onClick = { onSelect(position) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = text; stateDescription = status },
                        minWidth = 48.dp, minHeight = 56.dp, material = ButtonMaterial.None,
                        colors = ButtonDefaults.buttonColors(color = transparent),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clearAndSetSemantics {}, progress, height = 5.dp)
                    }
                } else {
                    LinearProgressIndicator(Modifier.weight(1f).clearAndSetSemantics {}, progress, height = 5.dp)
                }
            }
        }
    }
}

@Composable
internal fun StepNavigator(
    index: Int,
    count: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    nextPressed: Boolean = false,
) {
    val last = count > 0 && index >= count - 1
    val colors = COUITheme.colorScheme
    val style = COUITheme.textStyles.buttonL
    val stack = LocalDensity.current.fontScale > 1.3f
    val previous: @Composable (Modifier) -> Unit = {
        Button(onPrev, it, minWidth = 56.dp, minHeight = 64.dp, material = ButtonMaterial.None, enabled = enabled && index > 0 && count > 0,
            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
            Icon(COUIIcons.ChevronBackward, null, Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.food_previous_step_button), style = style, textAlign = TextAlign.Center)
        }
    }
    val next: @Composable (Modifier) -> Unit = {
        val buttonColors = if (nextPressed) ButtonDefaults.buttonColorsPrimary(color = colors.primary.copy(alpha = 0.75f))
            else ButtonDefaults.buttonColorsPrimary()
        Button(onNext, it, minWidth = 56.dp, minHeight = 64.dp, material = ButtonMaterial.None, enabled = enabled && count > 0,
            colors = buttonColors, insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
            Text(stringResource(if (last) R.string.food_finish_short else R.string.food_next_step_button),
                style = style, textAlign = TextAlign.Center)
            Spacer(Modifier.width(6.dp))
            Icon(if (last) COUIIcons.Ok else COUIIcons.ChevronForward, null, Modifier.size(20.dp))
        }
    }
    if (stack) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            previous(Modifier.fillMaxWidth())
            next(Modifier.fillMaxWidth())
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            previous(Modifier.weight(0.43f))
            next(Modifier.weight(0.57f))
        }
    }
}

@CouiComponentPreviews
@Composable
private fun CookingStepPreview() = FoodComponentPreview {
    CookingStep(FoodPreviewData.cooking, onStartTimer = {}, onHowTo = {}, onAssistant = {})
}

@CouiComponentPreviews
@Composable
private fun CookingStepVariantsPreview() = FoodComponentPreview {
    CookingStep(FoodPreviewData.cooking, textScale = CookingTextScale.LARGE, timer = FoodPreviewData.running,
        onOpenTimers = {}, onIngredient = {}, onHowTo = {})
}

@CouiComponentPreviews
@Composable
private fun CookingStepNoTimerPreview() = FoodComponentPreview {
    CookingStep(FoodPreviewData.noTimerStep, onAssistant = {})
    CookingStep(FoodPreviewData.cooking, loading = true)
}

@CouiStressPreviews
@CuisineNarrowFontPreview
@Composable
private fun CookingStepStressPreview() = FoodComponentPreview {
    CookingStep(FoodPreviewData.cooking)
}

@CouiComponentPreviews
@Composable
private fun StepProgressPreview() = FoodComponentPreview {
    StepProgress(2, 7, completed = setOf(0, 1))
    StepProgress(0, 7)
    StepProgress(6, 7, completed = (0..5).toSet())
    StepProgress(2, 4, completed = setOf(0), onSelect = {})
}

@CouiComponentPreviews
@Composable
private fun StepNavigatorPreview() = FoodComponentPreview {
    StepNavigator(2, 7, {}, {})
    StepNavigator(0, 7, {}, {})
    StepNavigator(6, 7, {}, {})
    StepNavigator(2, 7, {}, {}, nextPressed = true)
    StepNavigator(2, 7, {}, {}, enabled = false)
}

@CouiStressPreviews
@CuisineNarrowFontPreview
@Composable
private fun StepNavigatorStressPreview() = FoodComponentPreview {
    StepNavigator(2, 7, {}, {})
    StepNavigator(6, 7, {}, {})
}

@CouiComponentPreviews
@Composable
private fun CookingStepPortionsPreview() = FoodComponentPreview {
    CookingStep(FoodPreviewData.cookingFourPortions, onStartTimer = {})
}
