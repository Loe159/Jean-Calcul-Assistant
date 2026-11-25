package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.ui.screens.food.FoodComponentPreview
import io.github.mangi.eta.ui.screens.food.FoodPreviewData
import io.github.mangi.eta.food.TimerState
import io.github.mangi.eta.ui.screens.food.ActiveCookingUi
import io.github.mangi.eta.ui.screens.food.CookingTimerUi
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Alarm
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.util.Locale

@Composable
internal fun CookingTimerChip(
    timer: CookingTimerUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = COUITheme.colorScheme
    val alarm = timer.state == TimerState.FINISHED
    val fill = when (timer.state) {
        TimerState.RUNNING -> colors.primary.copy(alpha = 0.16f)
        TimerState.FINISHED -> colors.containerOrange
        else -> colors.container8
    }
    val label = timer.accessibleLabel()
    Card(
        modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
            .semantics { contentDescription = label; role = Role.Button },
        cornerRadius = 24.dp, colors = CardDefaults.defaultColors(color = fill),
        onClick = onClick,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerRing(timer, 24.dp)
            if (!compact) Text(timer.label, style = COUITheme.textStyles.bodyS, modifier = Modifier.widthIn(max = 144.dp))
            Text(if (alarm) timer.statusLabel() else timerClock(timer.secondsLeft),
                style = COUITheme.textStyles.headlineXS.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun TimerRing(timer: CookingTimerUi, size: Dp) {
    val colors = COUITheme.colorScheme
    val fill = when (timer.state) {
        TimerState.RUNNING -> colors.primary
        TimerState.FINISHED -> colors.orange
        else -> colors.labelSecondary
    }
    Box(Modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = timer.progress, size = size, strokeWidth = 2.dp,
            colors = ProgressIndicatorDefaults.circularProgressIndicatorColors(
                foregroundColor = fill, backgroundColor = fill.copy(alpha = 0.23f),
            ),
        )
        val icon = when (timer.state) {
            TimerState.FINISHED -> COUIIcons.Alarm
            TimerState.PAUSED -> COUIIcons.Pause
            else -> null
        }
        if (icon != null) Icon(icon, null, Modifier.size(size / 2), tint = cuisineTint(fill))
    }
}

@Composable
internal fun TimerDrawer(
    timers: List<CookingTimerUi>,
    expanded: Boolean,
    actions: TimerActions,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNewTimer: ((Int) -> Unit)? = null,
    minControlHeight: Dp = 48.dp,
) {
    val visible = timers.filterNot { it.state == TimerState.CANCELLED }
    if (visible.isEmpty() && !expanded) return
    val alarms = visible.count { it.state == TimerState.FINISHED }
    val colors = COUITheme.colorScheme
    val largeText = LocalDensity.current.fontScale > 1.3f
    val chips: @Composable () -> Unit = {
        visible.forEach { timer ->
            key(timer.id) {
                CookingTimerChip(timer, { onExpandedChange(true) }, Modifier.heightIn(min = minControlHeight))
            }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (visible.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (largeText) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips()
                    }
                } else {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips()
                    }
                }
                CuisineIconButton(
                    if (expanded) CuisineIcons.ChevronUp else CuisineIcons.ChevronDown,
                    stringResource(if (expanded) R.string.food_timers_collapse else R.string.food_timers_expand),
                    { onExpandedChange(!expanded) },
                    fill = colors.container8, size = minControlHeight,
                )
            }
        }
        if (expanded) {
            Card {
                if (alarms > 1) {
                    CuisineBadge(
                        pluralStringResource(R.plurals.food_timers_finished, alarms, alarms), Modifier.padding(12.dp),
                        COUIIcons.Alarm, cuisineTint(colors.orange), colors.containerOrange,
                    )
                }
                CardListGroup {
                    visible.forEach { timer -> item { TimerDrawerRow(timer, actions, minControlHeight) } }
                }
                if (onNewTimer != null) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FoodSupporting(stringResource(R.string.food_timer_new))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 5, 10).forEach { minutes ->
                                TimerControl(stringResource(R.string.food_minutes, minutes), { onNewTimer(minutes) }, minHeight = minControlHeight)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerDrawerRow(timer: CookingTimerUi, actions: TimerActions, minControlHeight: Dp) {
    val paused = timer.state == TimerState.PAUSED
    val alarm = timer.state == TimerState.FINISHED
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerRing(timer, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(timer.label, style = COUITheme.textStyles.headlineXS)
                val step = timer.stepNumber?.let { stringResource(R.string.food_step_number, it) }
                FoodSupporting(listOfNotNull(timer.statusLabel(), step).joinToString(" · "))
            }
            Box {
                val time = timer.remainingLabel()
                Text(timerClock(timer.secondsLeft), Modifier.clearAndSetSemantics { contentDescription = time },
                    style = COUITheme.textStyles.displayXS)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!alarm) {
                TimerControl(
                    stringResource(if (paused) R.string.food_brief_resume else R.string.food_timer_pause),
                    { if (paused) actions.resume(timer.id) else actions.pause(timer.id) }, minHeight = minControlHeight,
                )
            }
            TimerControl(stringResource(R.string.food_timer_plus, 1), { actions.add(timer.id, 1) }, minHeight = minControlHeight)
            if (!alarm) TimerControl(stringResource(R.string.food_timer_plus, 5), { actions.add(timer.id, 5) }, minHeight = minControlHeight)
            TimerControl(
                stringResource(R.string.food_timer_cancel), { actions.cancel(timer.id) },
                Modifier.semantics { role = Role.Button }, minHeight = minControlHeight,
            )
        }
    }
}

@Composable
private fun TimerControl(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: Dp = 48.dp) {
    Button(onClick, modifier, minWidth = 48.dp, minHeight = minHeight,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, style = COUITheme.textStyles.buttonM)
    }
}

@Composable
internal fun ActiveCookingBanner(
    session: ActiveCookingUi,
    nextTimer: CookingTimerUi?,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
) {
    val colors = COUITheme.colorScheme
    val alarm = nextTimer?.state == TimerState.FINISHED
    val progress = if (session.count > 0) ((session.index + 1f) / session.count).coerceIn(0f, 1f) else 0f
    val step = stringResource(R.string.food_step_of_count, session.index + 1, session.count)
    val title = stringResource(R.string.food_banner_session, session.title,
        stringResource(R.string.food_step_position, session.index + 1, session.count))
    val detail = nextTimer?.let {
        stringResource(R.string.food_banner_timer, it.label, when (it.state) {
            TimerState.FINISHED -> it.statusLabel()
            TimerState.PAUSED -> stringResource(R.string.food_banner_timer_paused, timerClock(it.secondsLeft))
            else -> timerClock(it.secondsLeft)
        })
    } ?: session.stepTitle
    val resume = stringResource(R.string.food_brief_resume)
    val talkBack = listOfNotNull(stringResource(R.string.food_cooking_in_progress),
        session.title, step, nextTimer?.accessibleLabel() ?: detail, resume).joinToString(" · ")
    val ink = if (alarm) colors.orange else colors.green
    Card(
        modifier.fillMaxWidth().heightIn(min = 64.dp).semantics {
            contentDescription = talkBack
            role = Role.Button
        },
        colors = CardDefaults.defaultColors(color = when {
            pressed -> colors.cardPressed
            alarm -> colors.containerOrange
            else -> colors.cardBackground
        }),
        onClick = onResume,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(12.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = progress, size = 42.dp, strokeWidth = 2.dp,
                    colors = ProgressIndicatorDefaults.circularProgressIndicatorColors(
                        foregroundColor = ink, backgroundColor = ink.copy(alpha = 0.22f)))
                Icon(if (alarm) COUIIcons.Alarm else CuisineIcons.Pot, null, Modifier.size(21.dp), tint = cuisineTint(ink))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // The banner docks over every Cuisine page: keep it compact at large font scales.
                Text(title, style = COUITheme.textStyles.headlineXS, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(
                    detail, style = COUITheme.textStyles.bodyS, color = colors.labelSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            CuisineBadge(resume, icon = COUIIcons.Play, ink = cuisineTint(colors.primary),
                fill = colors.primary.copy(alpha = 0.14f), compact = true)
        }
    }
}

internal fun timerClock(seconds: Long, padMinutes: Boolean = true): String =
    String.format(Locale.ROOT, if (padMinutes) "%02d:%02d" else "%d:%02d", seconds / 60, seconds % 60)

@Composable
internal fun timerDuration(seconds: Long): String = when {
    seconds % 60 == 0L -> stringResource(R.string.food_minutes, seconds / 60)
    seconds < 60 -> pluralStringResource(R.plurals.food_timer_seconds, seconds.toInt(), seconds)
    else -> stringResource(R.string.food_timer_duration, seconds / 60, seconds % 60)
}

@Composable
internal fun CookingTimerUi.remainingLabel() =
    stringResource(R.string.food_timer_remaining_detail, timerDuration(secondsLeft))

@Composable
internal fun CookingTimerUi.statusLabel() = stringResource(when (state) {
    TimerState.RUNNING -> R.string.food_timer_running
    TimerState.PAUSED -> R.string.food_timer_paused_short
    TimerState.FINISHED -> R.string.food_timer_finished_short
    TimerState.CANCELLED -> R.string.food_timer_cancelled
})

@Composable
private fun CookingTimerUi.accessibleLabel(): String =
    listOfNotNull(label, statusLabel(), if (state == TimerState.FINISHED) null else remainingLabel()).joinToString(" · ")

@CouiComponentPreviews
@Composable
private fun CookingTimerChipPreview() = FoodComponentPreview {
    CookingTimerChip(FoodPreviewData.running, {})
    CookingTimerChip(FoodPreviewData.paused, {})
    CookingTimerChip(FoodPreviewData.done, {})
    CookingTimerChip(FoodPreviewData.running, {}, compact = true)
}

@CouiComponentPreviews
@Composable
private fun TimerDrawerPreview() = FoodComponentPreview {
    val actions = TimerActions({}, {}, { _, _ -> }, {})
    val timers = listOf(FoodPreviewData.running, FoodPreviewData.paused)
    TimerDrawer(emptyList(), false, actions, {})
    TimerDrawer(timers, false, actions, {})
    TimerDrawer(timers, true, actions, {}, onNewTimer = {})
}

@CouiComponentPreviews
@Composable
private fun TimerDrawerAlarmsPreview() = FoodComponentPreview {
    val actions = TimerActions({}, {}, { _, _ -> }, {})
    TimerDrawer(listOf(FoodPreviewData.done, FoodPreviewData.doneToo), true, actions, {})
}

@CouiComponentPreviews
@Composable
private fun ActiveCookingBannerPreview() = FoodComponentPreview {
    ActiveCookingBanner(FoodPreviewData.session, FoodPreviewData.running, {})
    ActiveCookingBanner(FoodPreviewData.session, FoodPreviewData.done, {})
    ActiveCookingBanner(FoodPreviewData.session, null, {})
    ActiveCookingBanner(FoodPreviewData.session, FoodPreviewData.paused, {}, pressed = true)
}
