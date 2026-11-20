package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.home.BriefMotionTiming
import io.github.suqi8.coui.kmp.basic.ChipDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.SuggestionChip
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Back
import io.github.suqi8.coui.kmp.icon.extended.Tune
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val BriefHeroKey = "brief:hero"
internal const val BriefSettingsLabel = "Personnaliser le brief"
private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

@Composable
internal fun BriefHeader(
    now: LocalDateTime,
    moment: BriefMoment,
    summary: List<BriefSummaryPart>,
    shortcuts: List<BriefCardId>,
    active: Boolean,
    animateSky: Boolean,
    motionAllowed: Boolean,
    scrollOffsetPx: () -> Float,
    revealProgress: () -> Float,
    onClose: () -> Unit,
    onCustomize: () -> Unit,
    onShortcut: (BriefCardId) -> Unit,
) {
    val colors = COUITheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 340.dp).clipToBounds()) {
        BriefSky(
            time = now.toLocalTime(),
            dark = COUITheme.isDark,
            animated = animateSky && motionAllowed,
            scrollOffsetPx = { if (motionAllowed) scrollOffsetPx() else 0f },
            revealProgress = revealProgress,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 4.dp, bottom = 22.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BriefHeaderButton(back = true, onClick = onClose)
                Spacer(Modifier.weight(1f))
                BriefHeaderButton(back = false, onClick = onCustomize)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                val date = now.format(dateFormat).replaceFirstChar { it.uppercase(Locale.FRENCH) }
                Text(
                    "$date · ${now.format(timeFormat)}",
                    style = COUITheme.textStyles.bodyS,
                    color = colors.labelSecondary,
                )
                Text(
                    moment.greeting,
                    style = COUITheme.textStyles.headlineL.copy(
                        fontSize = 35.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.labelPrimary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp).semantics { heading() },
                )
                val highlight = when (moment) {
                    BriefMoment.MORNING -> colors.orange
                    BriefMoment.DAY -> colors.azure
                    BriefMoment.EVENING -> colors.blue
                    BriefMoment.NIGHT -> colors.violet
                }
                BriefSummaryText(summary, highlight, active, motionAllowed)
            }
            if (shortcuts.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp)
                        .horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    shortcuts.forEach { card ->
                        val tint = card.tint()
                        val foreground = briefTintedLabel(tint)
                        SuggestionChip(
                            label = if (card == BriefCardId.NOTIFICATIONS) "Voir les notifications" else card.title,
                            onClick = { onShortcut(card) },
                            minHeight = 36.dp,
                            cornerRadius = 18.dp,
                            colors = ChipDefaults.chipColors(
                                containerColor = tint.copy(alpha = .22f),
                                contentColor = foreground,
                            ),
                            icon = { Icon(card.icon, null, tint = foreground, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BriefMinibar(
    now: LocalDateTime,
    moment: BriefMoment,
    onClose: () -> Unit,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = COUITheme.colorScheme
    Row(
        modifier.fillMaxWidth().background(colors.backgroundWithCard)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BriefHeaderButton(back = true, onClick = onClose)
        Column(Modifier.weight(1f)) {
            Text(moment.greeting, style = COUITheme.textStyles.headlineXS, color = colors.labelPrimary)
            Text(now.format(timeFormat), style = COUITheme.textStyles.bodyXS, color = colors.labelSecondary)
        }
        BriefHeaderButton(back = false, onClick = onCustomize)
    }
}

@Composable
private fun BriefHeaderButton(back: Boolean, onClick: () -> Unit) {
    val colors = COUITheme.colorScheme
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Spacer(Modifier.size(36.dp).squircleSurface(colors.labelPrimary.copy(alpha = .10f), cornerRadius = 18.dp))
        CouiTopAppBarIconButton(
            icon = if (back) COUIIcons.Back else COUIIcons.Tune,
            contentDescription = if (back) "Retour à la conversation" else BriefSettingsLabel,
            onClick = onClick,
            iconSize = 21.dp,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun BriefSummaryText(
    summary: List<BriefSummaryPart>,
    tint: Color,
    active: Boolean,
    motionAllowed: Boolean,
) {
    if (summary.isEmpty()) return
    val words = remember(summary) {
        summary.flatMap { part ->
            part.text.split(' ').filter(String::isNotEmpty).map { part.copy(text = it) }
        }
    }
    if (words.isEmpty()) return
    var shown by rememberSaveable { mutableStateOf(false) }
    val elapsed = remember { Animatable(0f) }
    LaunchedEffect(active, motionAllowed, words) {
        if (!active || shown) return@LaunchedEffect
        if (motionAllowed) {
            val duration = BriefMotionTiming.WORD_START_MS + BriefMotionTiming.WORD_MS +
                (words.size - 1) * BriefMotionTiming.WORD_STAGGER_MS
            elapsed.snapTo(0f)
            elapsed.animateTo(duration.toFloat(), tween(duration, easing = LinearEasing))
        }
        shown = true
    }

    val colors = COUITheme.colorScheme
    FlowRow(
        Modifier.clearAndSetSemantics { text = AnnotatedString(summary.joinToString(" ") { it.text }) },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        words.forEachIndexed { index, word ->
            val start = BriefMotionTiming.WORD_START_MS + index * BriefMotionTiming.WORD_STAGGER_MS
            Text(
                text = word.text,
                style = COUITheme.textStyles.bodyM.copy(
                    fontSize = 16.5.sp, lineHeight = 23.5.sp,
                    fontWeight = if (word.highlighted) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (word.highlighted) colors.labelPrimary else colors.labelPrimaryVariant,
                modifier = Modifier.graphicsLayer {
                    val fraction = if (!active || !motionAllowed || shown) 1f else {
                        BriefMotionTiming.EaseMove.transform(
                            ((elapsed.value - start) / BriefMotionTiming.WORD_MS).coerceIn(0f, 1f),
                        )
                    }
                    alpha = fraction
                    translationY = (1f - fraction) * 6.dp.toPx()
                }.then(
                    if (word.highlighted) Modifier.drawBehind {
                        drawRect(
                            tint.copy(alpha = .32f),
                            topLeft = Offset(0f, size.height * .62f),
                            size = Size(size.width, size.height * .38f),
                        )
                    } else Modifier,
                ),
            )
        }
    }
}
