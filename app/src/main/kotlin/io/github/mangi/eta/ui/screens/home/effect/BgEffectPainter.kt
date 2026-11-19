// Copyright 2026, compose-coui-ui contributors
// SPDX-License-Identifier: Apache-2.0
package io.github.mangi.eta.ui.screens.home.effect

import androidx.compose.ui.graphics.Brush
import io.github.suqi8.coui.kmp.blur.RuntimeShader
import io.github.suqi8.coui.kmp.blur.asBrush
import kotlin.math.cos
import kotlin.math.sin

internal class BgEffectPainter {

    val runtimeShader by lazy {
        RuntimeShader(OS2_BG_FRAG).also {
            initStaticUniforms(it)
        }
    }

    val brush: Brush get() = runtimeShader.asBrush()

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val pointsAnimBuffer = FloatArray(8)

    private var animTime = Float.NaN
    private var isDarkCached: Boolean? = null
    private var deviceTypeCached: DeviceType? = null

    private var presetApplied = false

    private var cachedLogoHeight = Float.NaN
    private var cachedTotalHeight = Float.NaN
    private var cachedTotalWidth = Float.NaN

    private var cachedPointsAnimTime = Float.NaN
    private var cachedPointsAnimPreset: BgEffectConfig.Config? = null

    companion object {

        private const val U_TRANSLATE_Y = 0f
        private const val U_ALPHA_MULTI = 1f
        private const val U_NOISE_SCALE = 1.5f
        private const val U_POINT_RADIUS_MULTI = 1f
    }

    private fun initStaticUniforms(shader: RuntimeShader) {
        shader.setFloatUniform("uTranslateY", U_TRANSLATE_Y)
        shader.setFloatUniform("uNoiseScale", U_NOISE_SCALE)
        shader.setFloatUniform("uPointRadiusMulti", U_POINT_RADIUS_MULTI)
        shader.setFloatUniform("uAlphaMulti", U_ALPHA_MULTI)
    }

    fun updateResolution(width: Float, height: Float) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        runtimeShader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        runtimeShader.setFloatUniform("uAnimTime", animTime)
    }

    fun updatePointsAnim(time: Float, preset: BgEffectConfig.Config) {
        if (cachedPointsAnimTime == time && cachedPointsAnimPreset === preset) return

        val offset = preset.pointOffset
        var i = 0
        while (i < 4) {
            val srcX = preset.points[i * 3]
            val srcY = preset.points[i * 3 + 1]
            val animX = srcX + sin(time + srcY) * offset
            val animY = srcY + cos(time + animX) * offset
            pointsAnimBuffer[i * 2] = animX
            pointsAnimBuffer[i * 2 + 1] = animY
            i++
        }
        runtimeShader.setFloatUniform("uPointsAnim", pointsAnimBuffer)

        cachedPointsAnimTime = time
        cachedPointsAnimPreset = preset
    }

    fun updateBoundIfNeeded(
        logoHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        if (cachedLogoHeight == logoHeight &&
            cachedTotalHeight == totalHeight &&
            cachedTotalWidth == totalWidth
        ) {
            return
        }

        updateBound(logoHeight, totalHeight, totalWidth)
        runtimeShader.setFloatUniform("uBound", bound)

        cachedLogoHeight = logoHeight
        cachedTotalHeight = totalHeight
        cachedTotalWidth = totalWidth
    }

    fun updatePresetIfNeeded(deviceType: DeviceType, isDark: Boolean) {
        if (presetApplied && isDarkCached == isDark && deviceTypeCached == deviceType) return

        applyPreset(deviceType, isDark)

        isDarkCached = isDark
        deviceTypeCached = deviceType
        presetApplied = true
    }

    private fun applyPreset(deviceType: DeviceType, isDark: Boolean) {
        val preset = BgEffectConfig.get(deviceType, isDark)

        runtimeShader.setFloatUniform("uPoints", preset.points)
        runtimeShader.setFloatUniform("uColors", preset.colors)
        runtimeShader.setFloatUniform("uLightOffset", preset.lightOffset)
        runtimeShader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
    }

    private fun updateBound(
        logoHeight: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        val heightRatio = logoHeight / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspectRatio = totalWidth / totalHeight
            val contentCenterY = 1f - heightRatio / 2f
            bound[0] = 0f
            bound[1] = contentCenterY - aspectRatio / 2f
            bound[2] = 1f
            bound[3] = aspectRatio
        }
    }
}
