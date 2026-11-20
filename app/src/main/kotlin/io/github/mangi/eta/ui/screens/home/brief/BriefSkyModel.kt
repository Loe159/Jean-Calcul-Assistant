package io.github.mangi.eta.ui.screens.home.brief

import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MINUTES_PER_DAY = 24 * 60
private const val TRANSITION_MINUTES = 60

internal data class BriefSkyPalette(
    val top: Int,
    val middle: Int,
    val bottom: Int,
    val stars: Float,
    val clouds: Float,
    val moonRadius: Float = 11f,
)

private val night = BriefSkyPalette(0x050619, 0x141c52, 0x2b2f82, 34f, 0f, moonRadius = 13f)
private val dawn = BriefSkyPalette(0x2b3a8a, 0xe8808a, 0xffc27a, 4f, 2f)
private val day = BriefSkyPalette(0x2f7de1, 0x76bdf8, 0xd7efff, 0f, 4f)
private val dusk = BriefSkyPalette(0x241a5e, 0x7a3f9e, 0xff8a5c, 14f, 2f)
private val skyTransitions = listOf(330 to dawn, 540 to day, 1080 to dusk, 1290 to night)

internal fun briefSkyPalette(time: LocalTime, dark: Boolean): BriefSkyPalette {
    val minute = time.minuteOfDay
    var previous = night
    for ((center, next) in skyTransitions) {
        if (minute < center - TRANSITION_MINUTES) break
        if (minute < center + TRANSITION_MINUTES) {
            val progress = smoothStep((minute - center + TRANSITION_MINUTES) / (2 * TRANSITION_MINUTES))
            return BriefSkyPalette(
                top = mixRgb(previous.top, next.top, progress),
                middle = mixRgb(previous.middle, next.middle, progress),
                bottom = mixRgb(previous.bottom, next.bottom, progress),
                stars = interpolate(previous.stars, next.stars, progress),
                clouds = interpolate(previous.clouds, next.clouds, progress),
                moonRadius = interpolate(previous.moonRadius, next.moonRadius, progress),
            ).forTheme(dark)
        }
        previous = next
    }
    return previous.forTheme(dark)
}

private fun BriefSkyPalette.forTheme(dark: Boolean): BriefSkyPalette = if (dark) {
    copy(top = mixRgb(0, top, .78f), middle = mixRgb(0, middle, .62f), bottom = mixRgb(0, bottom, .5f))
} else {
    copy(
        top = mixRgb(top, 0xffffff, .72f),
        middle = mixRgb(middle, 0xffffff, .75f),
        bottom = mixRgb(bottom, 0xffffff, .75f),
        stars = 0f,
    )
}

internal data class BriefAstroPosition(val x: Float, val y: Float, val opacity: Float)

internal fun briefSunPosition(time: LocalTime): BriefAstroPosition? =
    celestialPosition(time, rise = 270, set = 1170)

internal fun briefMoonPosition(time: LocalTime): BriefAstroPosition? =
    celestialPosition(time, rise = 1050, set = 390 + MINUTES_PER_DAY)

private fun celestialPosition(time: LocalTime, rise: Int, set: Int): BriefAstroPosition? {
    val minute = time.minuteOfDay
    val adjusted = if (set > MINUTES_PER_DAY && minute < rise) minute + MINUTES_PER_DAY else minute
    if (adjusted !in rise.toFloat()..set.toFloat()) return null
    val progress = (adjusted - rise) / (set - rise)
    val fade = minOf(adjusted - rise, set - adjusted) / TRANSITION_MINUTES
    return BriefAstroPosition(
        x = interpolate(.86f, .15f, progress),
        y = .36f - .20f * sin(progress * PI).toFloat(),
        opacity = smoothStep(fade),
    )
}

private val LocalTime.minuteOfDay: Float
    get() = toSecondOfDay() / 60f

private fun smoothStep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun interpolate(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private fun mixRgb(start: Int, end: Int, progress: Float): Int {
    fun channel(shift: Int): Int {
        val a = (start shr shift and 0xff).toFloat()
        val b = (end shr shift and 0xff).toFloat()
        return interpolate(a, b, progress).roundToInt() shl shift
    }
    return channel(16) or channel(8) or channel(0)
}
