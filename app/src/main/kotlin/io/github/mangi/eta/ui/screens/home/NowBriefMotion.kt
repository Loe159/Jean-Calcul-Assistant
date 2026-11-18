package io.github.mangi.eta.ui.screens.home

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * The display settings and battery saver always win over the experimental effects.
 * No infinite animation is even composed while the brief is closed.
 */
internal fun briefMotionAllowed(context: Context): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale > 0f
}

internal fun briefAmbientAllowed(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    return briefMotionAllowed(context) && power?.isPowerSaveMode != true
}

/**
 * Process-only memory. It intentionally resets when Android kills the app process,
 * not when the user switches between chat and briefing.
 */
internal object BriefMotionSession {
    var hasOpened by mutableStateOf(false)
    var hasClosedAfterOpening by mutableStateOf(false)
    val revealedCards = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
}

internal fun shouldAnimateBriefEntrance(hasOpened: Boolean): Boolean = !hasOpened

/** Fast, coordinated entrance: the whole first viewport is readable in ~300 ms. */
internal object BriefMotionTiming {
    const val TITLE_MS = 230
    const val CARD_MS = 190
    const val CARD_STAGGER_MS = 20
    const val CARD_MAX_DELAY_MS = 80
    const val COUNTER_MS = 300
    const val BAR_MS = 280
    const val NOTIFICATION_MS = 210
}

/** Direct finger-to-halo mapping, including cancellation and spring settling. */
internal fun briefHaloAlpha(revealProgress: Float): Float =
    revealProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

/**
 * Single, slowly moving ambient halo (B2), plus capped scroll parallax (B4).
 * No bitmap allocation or GPU blur.
 */
@Composable
internal fun BriefAmbientBackground(
    visible: Boolean,
    dark: Boolean,
    scrollOffsetPx: Float,
    revealProgress: () -> Float = { if (visible) 1f else 0f },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Only animate the ambient colors when fully open; while dragging, use
    // the static halo and let the compositing layer track the finger directly.
    val enabled = remember(context, visible) { visible && briefAmbientAllowed(context) }
    // Reduced-motion and battery-saver still get the same static glass backdrop,
    // but no infinite transition is composed.
    val animatedPhase = if (enabled) {
        val transition = rememberInfiniteTransition(label = "brief-ambient")
        val movingPhase by transition.animateFloat(
            initialValue = 0.5f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(16000, easing = LinearEasing), RepeatMode.Reverse,
            ),
            label = "slow-ambient-phase",
        )
        movingPhase
    } else 0.5f
    // No phase jump when switching between a static finger-driven halo and
    // the slow breathing animation (especially on programmatic Back).
    val phase by animateFloatAsState(
        targetValue = animatedPhase,
        animationSpec = tween(360, easing = LinearEasing),
        label = "brief-ambient-phase-continuity",
    )
    Canvas(
        modifier = modifier.fillMaxSize().graphicsLayer {
            // Match the same anchored-draggable X fraction on every drag frame.
            // Alpha is read in the layer, not separately animated on a timer.
            this.alpha = briefHaloAlpha(revealProgress())
            translationY = -(scrollOffsetPx * 0.026f).coerceIn(0f, 12f)
        },
    ) {
        val w = size.width
        val h = size.height
        // Ambient navy backdrop. The original COUI theme still controls the
        // top bar and the typography, including its translucent scroll effect.
        if (dark) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFF1D2636), Color(0xFF20223B), Color(0xFF201F33)),
                ),
            )
        } else {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFFF2F6FB), Color(0xFFF5F3FA), Color(0xFFF8F6F3)),
                ),
            )
        }

        val green = Color(0xFF4BCDA2)
        val orange = Color(0xFFFFAE78)
        val violet = Color(0xFF8A84DC)
        fun glow(center: Offset, radius: Float, tint: Color, opacity: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = opacity), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        val breathe = (phase - 0.5f)
        glow(
            center = Offset(w * (0.05f + breathe * 0.09f), h * 0.06f),
            radius = w * 1.06f,
            tint = green,
            opacity = if (dark) 0.23f else 0.095f,
        )
        glow(
            center = Offset(w * (0.97f - breathe * 0.10f), h * 0.74f),
            radius = w * 1.22f,
            tint = orange,
            opacity = if (dark) 0.18f else 0.085f,
        )
        glow(
            center = Offset(w * 0.44f, h * (0.46f + breathe * 0.12f)),
            radius = w * 0.94f,
            tint = violet,
            opacity = if (dark) 0.14f else 0.07f,
        )
    }
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
