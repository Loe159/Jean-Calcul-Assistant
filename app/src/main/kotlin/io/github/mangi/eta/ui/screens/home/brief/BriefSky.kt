package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import io.github.mangi.eta.ui.screens.home.briefSceneAlpha
import io.github.mangi.eta.ui.screens.home.briefSkyParallax
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random
import kotlinx.coroutines.isActive

@Composable
internal fun BriefSky(
    time: LocalTime,
    dark: Boolean,
    animated: Boolean,
    scrollOffsetPx: () -> Float,
    revealProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val palette = remember(time, dark) { briefSkyPalette(time, dark) }
    val sun = remember(time) { briefSunPosition(time) }
    val moon = remember(time) { briefMoonPosition(time) }
    val seconds = remember { mutableFloatStateOf(0f) }
    val hasMotion = animated && (palette.stars > 0f || palette.clouds > 0f)
    LaunchedEffect(hasMotion) {
        if (!hasMotion) {
            seconds.floatValue = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { seconds.floatValue = ((it - start) / 1_000_000_000.0 % 2040).toFloat() }
        }
    }

    val top = skyColor(palette.top)
    val middle = skyColor(palette.middle)
    val bottom = skyColor(palette.bottom)
    Canvas(
        modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            alpha = briefSceneAlpha(revealProgress())
            translationY = briefSkyParallax(scrollOffsetPx())
        }.drawWithCache {
            val gradient = Brush.verticalGradient(0f to top, .55f to middle, 1f to bottom)
            val fade = Brush.verticalGradient(.55f to Color.White, 1f to Color.Transparent)
            onDrawWithContent {
                drawRect(gradient)
                drawContent()
                drawRect(fade, blendMode = BlendMode.DstIn)
            }
        },
    ) {
        val elapsed = if (hasMotion) seconds.floatValue else 0f
        val unit = minOf(size.width / 392f, size.height / 380f)
        val skyHeight = minOf(size.height, size.width * (380f / 392f))
        stars.forEachIndexed { index, star ->
            val presence = (palette.stars - index).coerceIn(0f, 1f)
            if (presence > 0f) {
                val twinkle = if (hasMotion) {
                    .625f + .375f * cos((elapsed / 3.4f + star.phase) * 2f * PI).toFloat()
                } else 1f
                drawCircle(
                    Color.White,
                    radius = star.radius * unit,
                    center = Offset(star.x * size.width, star.y * skyHeight),
                    alpha = presence * star.opacity * twinkle,
                )
            }
        }
        sun?.let { drawAstro(it, skyHeight, 15f * unit, Color(0xFFFFF6DD), 3.2f, .55f) }
        moon?.let {
            drawAstro(it, skyHeight, palette.moonRadius * unit, Color(0xFFF4F1E6), 3.6f, .28f, top)
        }
        clouds.forEachIndexed { index, cloud ->
            val presence = (palette.clouds - index).coerceIn(0f, 1f)
            if (presence > 0f) {
                val drift = if (hasMotion) {
                    1f - abs(((elapsed + cloud.phase) % 120f) / 60f - 1f)
                } else 0f
                val center = Offset(cloud.x * size.width + 40f * unit * drift, cloud.y * skyHeight)
                drawCloud(center, Size(183f * unit, 35f * unit), presence)
                drawCloud(center + Offset(60f * unit, -8f * unit), Size(118f * unit, 30f * unit), presence)
            }
        }
    }
}

private fun DrawScope.drawAstro(
    position: BriefAstroPosition,
    skyHeight: Float,
    radius: Float,
    color: Color,
    haloScale: Float,
    haloOpacity: Float,
    crescent: Color? = null,
) {
    if (position.opacity == 0f || radius <= 0f) return
    val center = Offset(position.x * size.width, position.y * skyHeight)
    val glowRadius = radius * haloScale
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Color.Transparent), center, glowRadius),
        radius = glowRadius, center = center, alpha = haloOpacity * position.opacity,
    )
    drawCircle(color, radius, center, alpha = .95f * position.opacity)
    if (crescent != null) {
        drawCircle(
            crescent, radius * .92f, center + Offset(radius * .45f, -radius * .2f),
            alpha = .96f * position.opacity,
        )
    }
}

private fun DrawScope.drawCloud(center: Offset, dimensions: Size, presence: Float) {
    if (dimensions.width <= 0f) return
    val radius = dimensions.width / 2f
    scale(1f, dimensions.height / dimensions.width, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = .34f), .55f to Color.White.copy(alpha = .20f),
                1f to Color.Transparent, center = center, radius = radius,
            ),
            radius = radius, center = center, alpha = presence,
        )
    }
}

private fun skyColor(rgb: Int): Color = Color(rgb or 0xff000000.toInt())

private data class Star(val x: Float, val y: Float, val radius: Float, val opacity: Float, val phase: Float)
private data class Cloud(val x: Float, val y: Float, val phase: Float)

// One deterministic field lets stars and clouds fade in place across palette changes.
private val stars = Random(485).run {
    List(34) { Star(nextFloat(), nextFloat() * 210f / 380f, .6f + nextFloat() * 1.1f, .4f + nextFloat() * .5f, nextFloat()) }
}
private val clouds = Random(291).run {
    List(4) { Cloud((-20f + nextFloat() * 260f) / 392f, (130f + it * 50f + nextFloat() * 20f) / 380f, nextFloat() * 30f) }
}
