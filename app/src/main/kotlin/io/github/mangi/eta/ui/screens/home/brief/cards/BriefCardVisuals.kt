package io.github.mangi.eta.ui.screens.home.brief.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.BriefMotionTiming
import io.github.mangi.eta.ui.screens.home.brief.BriefDemoTone
import io.github.mangi.eta.ui.screens.home.brief.briefTintedLabel
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BriefDemoTone.color(): Color = with(COUITheme.colorScheme) {
    when (this@color) {
        BriefDemoTone.Blue -> blue
        BriefDemoTone.Azure -> azure
        BriefDemoTone.Orange -> orange
        BriefDemoTone.Violet -> violet
        BriefDemoTone.Purple -> purple
        BriefDemoTone.Green -> green
        BriefDemoTone.Red -> red
        BriefDemoTone.Yellow -> yellow
    }
}

@Composable
internal fun briefAnimatedProgress(progress: Float, motionAllowed: Boolean): State<Float> {
    val animation = remember(motionAllowed) { Animatable(if (motionAllowed) 0f else progress) }
    val immediate = if (motionAllowed) null else remember(progress) { androidx.compose.runtime.mutableFloatStateOf(progress) }
    LaunchedEffect(animation, progress, motionAllowed) {
        if (motionAllowed) animation.animateTo(progress, tween(1000, easing = BriefMotionTiming.EaseMove))
    }
    return immediate ?: animation.asState()
}

@Composable
internal fun briefAmbientCycle(
    enabled: Boolean,
    periodMillis: Int,
    label: String,
    reverse: Boolean = false,
): State<Float> {
    if (!enabled) return remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = label,
    )
}

internal data class BriefRing(val progress: Float, val color: Color)

@Composable
internal fun BriefRings(
    rings: List<BriefRing>,
    diameter: Dp,
    thickness: Dp,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tracks = COUITheme.colorScheme.labelPrimary.copy(alpha = .10f)
    val animated = rings.map { briefAnimatedProgress(it.progress, motionAllowed) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = thickness.toPx()
            rings.forEachIndexed { index, ring ->
                val inset = stroke / 2 + 2.dp.toPx() + index * (stroke + 3.dp.toPx())
                val length = size.minDimension - 2 * inset
                val topLeft = Offset((size.width - length) / 2, (size.height - length) / 2)
                val bounds = Size(length, length)
                drawArc(tracks, -90f, 360f, false, topLeft, bounds, style = Stroke(stroke))
                val sweep = animated[index].value * 360f
                if (sweep > 0f) {
                    drawArc(ring.color, -90f, sweep, false, topLeft, bounds, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

@Composable
internal fun BriefGauge(
    progress: Float,
    colors: List<Color>,
    motionAllowed: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    marker: Float? = null,
) {
    val track = COUITheme.colorScheme.labelPrimary.copy(alpha = .09f)
    val markerColor = COUITheme.colorScheme.labelPrimary.copy(alpha = .75f)
    val animated = briefAnimatedProgress(progress, motionAllowed)
    Canvas(modifier.semantics {
        if (description != null) contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
    }) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val width = size.width * animated.value
        if (width > 0f) {
            val filled = Size(width, size.height)
            if (colors.size == 1) drawRoundRect(colors.first(), size = filled, cornerRadius = radius)
            else drawRoundRect(Brush.horizontalGradient(colors, endX = size.width * progress), size = filled, cornerRadius = radius)
        }
        if (marker != null) {
            val x = (size.width - 2.dp.toPx()).coerceAtLeast(0f) * marker.coerceIn(0f, 1f) + 1.dp.toPx()
            drawLine(markerColor, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun BriefSparkline(
    values: List<Float>,
    tint: Color,
    description: String,
    modifier: Modifier = Modifier,
    bars: List<Float> = emptyList(),
    barTint: Color = tint,
) {
    if (values.size < 2) return
    Canvas(modifier.semantics { contentDescription = description }) {
        val low = values.min()
        val range = (values.max() - low).coerceAtLeast(1f)
        val inset = 4.dp.toPx()
        val width = (size.width - 2 * inset).coerceAtLeast(0f)
        val height = (size.height - 2 * inset).coerceAtLeast(0f)
        val step = width / values.lastIndex
        val points = values.mapIndexed { index, value ->
            Offset(inset + step * index, inset + height * (1f - (value - low) / range) * .84f)
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.zipWithNext().forEach { (start, end) ->
                val middle = (start.x + end.x) / 2
                cubicTo(middle, start.y, middle, end.y, end.x, end.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(tint.copy(alpha = .24f), Color.Transparent)))
        points.zip(bars).forEach { (point, fraction) ->
            val barHeight = height * fraction.coerceIn(0f, 1f) * .46f
            val barWidth = minOf(step * .4f, 16.dp.toPx())
            val x = point.x.coerceIn(barWidth / 2f, size.width - barWidth / 2f)
            drawRoundRect(barTint, Offset(x - barWidth / 2f, size.height - barHeight), Size(barWidth, barHeight), CornerRadius(3.dp.toPx()))
        }
        drawPath(line, tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(tint, 3.dp.toPx(), points.last())
    }
}

@Composable
internal fun BriefSection(title: String, detail: String? = null) {
    val theme = COUITheme
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f), color = theme.colorScheme.labelSecondary, style = theme.textStyles.headlineXS)
        if (detail != null) Text(detail, color = theme.colorScheme.labelSecondary, style = theme.textStyles.bodyXS)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BriefMetric(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null) {
    val theme = COUITheme
    Column(
        modifier.briefInset(16.dp).padding(horizontal = 9.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = theme.colorScheme.labelSecondary, style = theme.textStyles.bodyXS)
        // The unit is a smaller, separate word: when space runs out it wraps whole, never letter by letter.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), itemVerticalAlignment = Alignment.Bottom) {
            Text(value, color = theme.colorScheme.labelPrimary, style = theme.textStyles.headlineS, softWrap = false)
            if (unit != null) {
                Text(
                    unit, Modifier.padding(bottom = 1.dp),
                    color = theme.colorScheme.labelSecondary, style = theme.textStyles.bodyS, softWrap = false,
                )
            }
        }
    }
}

@Composable
internal fun BriefIconTile(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).squircleSurface(tint.copy(alpha = .22f), cornerRadius = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = briefTintedLabel(tint))
    }
}

@Composable
internal fun BriefDemoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.sizeIn(minHeight = 48.dp),
        enabled = enabled,
        minWidth = 0.dp,
        minHeight = 36.dp,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp))
            Text(text, style = COUITheme.textStyles.buttonM)
        }
    }
}

@Composable
internal fun BriefCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    checkFirst: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .sizeIn(minHeight = 48.dp).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (checkFirst) Checkbox(ToggleableState(checked), onClick = null)
        Box(Modifier.weight(1f)) { content() }
        if (!checkFirst) Checkbox(ToggleableState(checked), onClick = null)
    }
}

/** Neutral inset panel inside a card; colour stays on the icon and label, not the surface. */
@Composable
internal fun Modifier.briefInset(cornerRadius: Dp = 16.dp): Modifier =
    squircleSurface(COUITheme.colorScheme.labelPrimary.copy(alpha = .045f), cornerRadius = cornerRadius)

/**
 * Leading icon, text and a trailing action. From a 150 % font scale the action drops below the text,
 * so a narrow text column never breaks words letter by letter.
 */
@Composable
internal fun BriefInlineAction(
    modifier: Modifier,
    leading: @Composable () -> Unit,
    text: @Composable () -> Unit,
    action: (@Composable () -> Unit)?,
    spacing: Dp = 8.dp,
) {
    val stacked = action != null && LocalDensity.current.fontScale >= 1.5f
    if (stacked) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
                leading()
                Box(Modifier.weight(1f)) { text() }
            }
            Box(Modifier.align(Alignment.End)) { action() }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Box(Modifier.weight(1f)) { text() }
            action?.invoke()
        }
    }
}

/**
 * Metrics side by side; from a 150 % font scale they stack full width so labels never break mid-word.
 * [content] receives the per-item modifier for a given row weight.
 */
@Composable
internal fun BriefMetricRow(modifier: Modifier = Modifier, content: @Composable (item: (Float) -> Modifier) -> Unit) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content { Modifier.fillMaxWidth() }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content { Modifier.weight(it) }
        }
    }
}
