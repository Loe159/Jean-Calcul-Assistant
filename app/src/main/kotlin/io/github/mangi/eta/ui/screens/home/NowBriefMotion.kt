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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

internal fun briefAmbienceIndex(openCount: Int): Int = ((openCount - 1).coerceAtLeast(0)) % 3

internal fun briefAmbienceName(index: Int) = when (index % 3) {
    0 -> "Dégradé"
    1 -> "Halo"
    else -> "Particules"
}

/**
 * B1, B2 and B6 deliberately alternate per open. B4 applies to all three.
 * Draw 12 or fewer cheap shapes; never allocate bitmaps or blur layers.
 */
@Composable
internal fun BriefAmbientBackground(
    visible: Boolean,
    variant: Int,
    accent: Color,
    dark: Boolean,
    scrollOffsetPx: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val enabled = remember(context, visible) { briefAmbientAllowed(context) }
    if (!visible || !enabled) return
    val transition = rememberInfiniteTransition(label = "brief-ambient")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "slow-ambient-phase",
    )
    Canvas(
        modifier = modifier.fillMaxSize().graphicsLayer {
            // B4, capped to prevent exposing areas around the canvas.
            translationY = -(scrollOffsetPx * 0.05f).coerceIn(0f, 18f)
        },
    ) {
        val w = size.width
        val h = size.height
        val intensity = if (dark) 0.14f else 0.09f
        when (variant % 3) {
            0 -> {
                val shift = 0.1f + phase * 0.35f
                drawRect(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = intensity), accent.copy(alpha = 0.01f),
                            accent.copy(alpha = intensity * 0.45f)),
                        start = Offset(w * shift, 0f),
                        end = Offset(w * (1.3f - shift), h),
                    ),
                )
            }
            1 -> {
                val center = Offset(w * (0.35f + 0.35f * phase), h * (0.17f + 0.19f * phase))
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to accent.copy(alpha = intensity * 1.5f),
                        1f to Color.Transparent,
                        center = center,
                        radius = w * 0.9f,
                    ),
                    radius = w * 0.9f,
                    center = center,
                )
            }
            else -> {
                repeat(12) { index ->
                    val seed = index.toFloat()
                    val x = w * ((0.09f + seed * 0.137f) % 1f)
                    val y = h * ((0.05f + seed * 0.217f + phase * 0.04f) % 1f)
                    val shimmer = 0.6f + 0.4f * sin((phase + seed * 0.17f) * PI * 2f).toFloat()
                    drawCircle(
                        color = accent.copy(alpha = ((if (dark) 0.26f else 0.20f) * shimmer).coerceIn(0f, 0.3f)),
                        radius = (1.5f + seed % 3f) * density,
                        center = Offset(x + cos(seed * 1.7f).toFloat() * phase * 9f, y),
                    )
                }
            }
        }
    }
}

/** A4 + D6: stagger on opening and on first visibility while scrolling a lazy list. */
@Composable
internal fun BriefReveal(
    id: String,
    visible: Boolean,
    session: Int,
    order: Int,
    alreadySeen: MutableMap<String, Boolean>,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val enabled = remember(context) { briefMotionAllowed(context) }
    var revealed by remember(id, session) { mutableStateOf(false) }
    LaunchedEffect(id, session, visible, enabled) {
        if (!visible) { revealed = false; return@LaunchedEffect }
        if (!enabled || alreadySeen.containsKey(id)) {
            revealed = true
        } else {
            delay((order.coerceAtMost(6) * 75).toLong())
            revealed = true
            alreadySeen[id] = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (revealed || !enabled) 1f else 0f,
        animationSpec = tween(440, easing = FastOutSlowInEasing),
        label = "brief-card-entrance-$id",
    )
    Box(Modifier.fillMaxWidth().graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 18.dp.toPx()
    }) { content() }
}
