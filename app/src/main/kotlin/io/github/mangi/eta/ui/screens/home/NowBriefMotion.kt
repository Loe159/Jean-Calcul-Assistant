package io.github.mangi.eta.ui.screens.home

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.screens.home.effect.BgEffectConfig
import io.github.mangi.eta.ui.screens.home.effect.BgEffectPainter
import io.github.mangi.eta.ui.screens.home.effect.DeviceType
import io.github.mangi.eta.ui.screens.home.effect.bgEffectDraw
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import kotlinx.coroutines.delay

/** Respects the system animation setting and battery saver. */
internal fun briefMotionAllowed(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f) > 0f

internal fun briefAmbientAllowed(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    return briefMotionAllowed(context) && power?.isPowerSaveMode != true
}

/** Process memory for the briefing entrance. */
internal object BriefMotionSession {
    var hasOpened by mutableStateOf(false)
    var hasClosedAfterOpening by mutableStateOf(false)
    val revealedCards = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
}

internal fun shouldAnimateBriefEntrance(hasOpened: Boolean): Boolean = !hasOpened

internal object BriefMotionTiming {
    const val TITLE_MS = 230
    const val CARD_MS = 190
    const val CARD_STAGGER_MS = 20
    const val CARD_MAX_DELAY_MS = 80
    const val COUNTER_MS = 300
    const val BAR_MS = 280
    const val NOTIFICATION_MS = 210
}

internal fun briefHaloAlpha(revealProgress: Float): Float =
    revealProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

/** The COUI Labs OS2 shader, using its phone/tablet and light/dark presets. */
@Composable
internal fun BriefAmbientBackground(
    visible: Boolean,
    dark: Boolean,
    scrollOffsetPx: Float,
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val deviceType = if (configuration.screenWidthDp >= 600) DeviceType.PAD else DeviceType.PHONE
    val preset = remember(deviceType, dark) { BgEffectConfig.get(deviceType, dark) }
    val shaderSupported = remember { isRuntimeShaderSupported() }
    val painter = if (shaderSupported) remember { BgEffectPainter() } else null
    val playing = visible && remember(context, visible) { briefAmbientAllowed(context) }
    val baseColor = if (dark) Color.Black else Color(0xFFF0F1F2)
    if (painter == null) {
        Spacer(modifier.fillMaxSize().background(baseColor))
        return
    }
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = briefHaloAlpha(revealProgress())
                translationY = -(scrollOffsetPx * 0.026f).coerceIn(0f, 12f)
            }
            .bgEffectDraw(
                painter = painter,
                preset = preset,
                deviceType = deviceType,
                isDarkTheme = dark,
                surface = baseColor,
                effectBackground = true,
                isFullSize = true,
                playing = playing,
                alpha = { 1f },
            ),
    )
}

/** A4 + D6: stagger on opening and on first visibility while scrolling a lazy list. */
@Composable
internal fun BriefReveal(
    id: String,
    visible: Boolean,
    order: Int,
    alreadySeen: MutableMap<String, Boolean>,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val enabled = remember(context) { briefMotionAllowed(context) }
    var revealed by remember(id) {
        mutableStateOf(alreadySeen.containsKey(id) || BriefMotionSession.hasClosedAfterOpening)
    }
    LaunchedEffect(id, visible, enabled) {
        if (!visible || revealed) return@LaunchedEffect
        if (enabled && !BriefMotionSession.hasClosedAfterOpening &&
            !alreadySeen.containsKey(id)) {
            delay((order * BriefMotionTiming.CARD_STAGGER_MS)
                .coerceAtMost(BriefMotionTiming.CARD_MAX_DELAY_MS).toLong())
        }
        alreadySeen[id] = true
        revealed = true
    }
    val progress by animateFloatAsState(
        targetValue = if (revealed || !enabled) 1f else 0f,
        animationSpec = tween(BriefMotionTiming.CARD_MS, easing = FastOutSlowInEasing),
        label = "brief-card-entrance-$id",
    )
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 8.dp.toPx()
    }) { content() }
}
