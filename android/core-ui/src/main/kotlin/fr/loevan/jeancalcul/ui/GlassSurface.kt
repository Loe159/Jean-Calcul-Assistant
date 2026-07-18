@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "ktlint:standard:function-naming",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlassSurfaceVariant {
    Panel,
    Card,
    Interactive,
    Selected,
    Overlay,
    Navigation,
    Modal,
    FallbackOpaque,
}

enum class GlassSurfaceState {
    Normal,
    Pressed,
    Focused,
    Disabled,
    Loading,
    Error,
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    variant: GlassSurfaceVariant = GlassSurfaceVariant.Panel,
    state: GlassSurfaceState = GlassSurfaceState.Normal,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val visualEffects = JeanCalculDesign.effects
    val effects = visualEffects.resolved()
    val shape = glassShape(variant)
    val opaque = variant == GlassSurfaceVariant.FallbackOpaque || !effects.useTranslucency
    val material = glassMaterial(variant, state, opaque, visualEffects.highContrast)
    // This is a static tonal approximation, not backdrop blur. Reduced motion can keep it because
    // it neither animates nor creates a continuous rendering workload.
    val useTonalGradient = effects.useGradients
    val background =
        if (useTonalGradient) {
            Brush.linearGradient(
                0.0f to material.tonalStart,
                0.42f to material.tonalCenter,
                1.0f to material.tonalEnd,
                start = Offset.Zero,
                end = Offset.Infinite,
            )
        } else {
            SolidColor(material.tonalCenter)
        }
    val haloColor =
        if (material.hasContextualHalo && effects.useGradients) {
            material.haloColor.copy(alpha = JeanCalculDesign.tokens.opacity.ambientViolet)
        } else {
            material.shadowColor
        }
    val interactionModifier =
        if (onClick != null && state != GlassSurfaceState.Disabled && state != GlassSurfaceState.Loading) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier =
                modifier
                    .shadow(
                        elevation = material.elevation,
                        shape = shape,
                        clip = false,
                        ambientColor = haloColor,
                        spotColor = material.shadowColor,
                    )
                    .clip(shape)
                    .background(background)
                    .glassEdges(
                        variant = variant,
                        material = material,
                        useGradients = effects.useGradients,
                    )
                    .then(interactionModifier)
                    .semantics {
                        if (state == GlassSurfaceState.Loading) {
                            contentDescription = "Chargement"
                        }
                    }
                    .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun glassMaterial(
    variant: GlassSurfaceVariant,
    state: GlassSurfaceState,
    opaque: Boolean,
    highContrast: Boolean,
): GlassMaterial {
    val colors = MaterialTheme.colorScheme
    val tokens = JeanCalculDesign.tokens
    val materials = tokens.materials
    val opacity = tokens.opacity
    val alpha = if (opaque) 1f else glassAlpha(variant)
    val base =
        when (variant) {
            GlassSurfaceVariant.Panel -> Triple(materials.surfaceLow, materials.surface, materials.surfaceLowest)
            GlassSurfaceVariant.Card -> Triple(materials.surfaceHigh, materials.surface, materials.surfaceLow)
            GlassSurfaceVariant.Interactive ->
                Triple(materials.surfaceHighest, materials.surfaceHigh, materials.surface)

            GlassSurfaceVariant.Selected ->
                Triple(
                    lerp(materials.surfaceHigh, materials.ambientViolet, 0.18f),
                    lerp(materials.surface, materials.ambientBlue, 0.12f),
                    materials.surfaceLow,
                )

            GlassSurfaceVariant.Overlay ->
                Triple(materials.surfaceHighest, materials.surfaceHigh, materials.surfaceLow)

            GlassSurfaceVariant.Navigation ->
                Triple(materials.surfaceHigh, materials.surface, materials.surfaceLowest)

            GlassSurfaceVariant.Modal ->
                Triple(materials.surfaceHighest, materials.surfaceHigh, materials.surface)

            GlassSurfaceVariant.FallbackOpaque ->
                Triple(materials.surfaceHigh, materials.surface, materials.surfaceLow)
        }

    val stateTint =
        when (state) {
            GlassSurfaceState.Pressed -> colors.secondary to 0.14f
            GlassSurfaceState.Focused -> materials.ambientViolet to 0.16f
            GlassSurfaceState.Disabled -> colors.background to 0.34f
            GlassSurfaceState.Loading -> materials.ambientBlue to 0.06f
            GlassSurfaceState.Error -> colors.error to 0.13f
            GlassSurfaceState.Normal -> Color.Transparent to 0f
        }
    val tonalStart = lerp(base.first, stateTint.first, stateTint.second).copy(alpha = alpha)
    val tonalCenter = lerp(base.second, stateTint.first, stateTint.second * 0.72f).copy(alpha = alpha)
    val tonalEnd = lerp(base.third, stateTint.first, stateTint.second * 0.38f).copy(alpha = alpha)
    val accentColor =
        when {
            state == GlassSurfaceState.Error -> colors.error
            state == GlassSurfaceState.Focused || variant == GlassSurfaceVariant.Selected ->
                materials.ambientViolet

            state == GlassSurfaceState.Pressed -> colors.secondary
            else -> materials.topReflection
        }
    val emphasis = if (highContrast) 1.9f else 1f
    val topAlpha = (topHighlightAlpha(variant) * emphasis).coerceAtMost(0.72f)
    val sideAlpha = (sideHighlightAlpha(variant, opacity) * emphasis).coerceAtMost(0.48f)
    val bottomAlpha = (bottomBorderAlpha(variant, opacity) * emphasis).coerceAtMost(0.32f)
    val disabledMultiplier = if (state == GlassSurfaceState.Disabled) 0.45f else 1f
    return GlassMaterial(
        tonalStart = tonalStart,
        tonalCenter = tonalCenter,
        tonalEnd = tonalEnd,
        topHighlight = accentColor.copy(alpha = topAlpha * disabledMultiplier),
        sideBorder = accentColor.copy(alpha = sideAlpha * disabledMultiplier),
        bottomBorder = accentColor.copy(alpha = bottomAlpha * disabledMultiplier),
        shadowColor =
            materials.diffuseShadow.copy(
                alpha =
                    if (highContrast) 0.48f else opacity.diffuseShadow,
            ),
        haloColor = materials.ambientViolet,
        elevation = glassElevation(variant, state, tokens.elevation, highContrast),
        hasContextualHalo =
            state == GlassSurfaceState.Focused ||
                (
                    variant == GlassSurfaceVariant.Selected &&
                        (state == GlassSurfaceState.Normal || state == GlassSurfaceState.Loading)
                ),
        reflectionAlpha = if (highContrast) 0.42f else topAlpha * 0.58f,
    )
}

private fun Modifier.glassEdges(
    variant: GlassSurfaceVariant,
    material: GlassMaterial,
    useGradients: Boolean,
): Modifier =
    drawWithCache {
        val borderWidth = 1.dp.toPx()
        val topWidth = 1.15.dp.toPx()
        val edgeBand = 18.dp.toPx().coerceAtMost(size.height / 2f)
        val reflectionInset = 16.dp.toPx().coerceAtMost(size.width * 0.22f)
        val radius =
            when (variant) {
                GlassSurfaceVariant.Navigation,
                GlassSurfaceVariant.Overlay,
                -> size.height / 2f

                GlassSurfaceVariant.Modal -> 24.dp.toPx()
                else -> 12.dp.toPx()
            }
        val cornerRadius = CornerRadius(radius, radius)
        val reflectionBrush: Brush =
            if (useGradients) {
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        material.topHighlight,
                        material.topHighlight.copy(alpha = material.reflectionAlpha),
                        Color.Transparent,
                    ),
                )
            } else {
                SolidColor(material.topHighlight.copy(alpha = material.reflectionAlpha))
            }
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                color = material.bottomBorder,
                cornerRadius = cornerRadius,
                style = Stroke(borderWidth),
            )
            clipRect(top = edgeBand, bottom = size.height - edgeBand) {
                drawRoundRect(
                    color = material.sideBorder,
                    cornerRadius = cornerRadius,
                    style = Stroke(borderWidth),
                )
            }
            clipRect(bottom = edgeBand) {
                drawRoundRect(
                    brush = reflectionBrush,
                    cornerRadius = cornerRadius,
                    style = Stroke(topWidth),
                )
            }
            if (size.width > reflectionInset * 2f) {
                drawLine(
                    brush = reflectionBrush,
                    start = Offset(reflectionInset, topWidth),
                    end = Offset(size.width - reflectionInset, topWidth),
                    strokeWidth = topWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

private fun glassAlpha(variant: GlassSurfaceVariant): Float =
    when (variant) {
        GlassSurfaceVariant.Panel -> 0.72f
        GlassSurfaceVariant.Card -> 0.80f
        GlassSurfaceVariant.Interactive -> 0.84f
        GlassSurfaceVariant.Selected -> 0.90f
        GlassSurfaceVariant.Overlay -> 0.91f
        GlassSurfaceVariant.Navigation -> 0.94f
        GlassSurfaceVariant.Modal -> 0.96f
        GlassSurfaceVariant.FallbackOpaque -> 1f
    }

private fun topHighlightAlpha(variant: GlassSurfaceVariant): Float =
    when (variant) {
        GlassSurfaceVariant.Panel -> 0.10f
        GlassSurfaceVariant.Card -> 0.15f
        GlassSurfaceVariant.Interactive -> 0.17f
        GlassSurfaceVariant.Selected -> 0.24f
        GlassSurfaceVariant.Overlay -> 0.20f
        GlassSurfaceVariant.Navigation -> 0.18f
        GlassSurfaceVariant.Modal -> 0.22f
        GlassSurfaceVariant.FallbackOpaque -> 0.14f
    }

private fun sideHighlightAlpha(
    variant: GlassSurfaceVariant,
    opacity: JeanCalculOpacity,
): Float =
    when (variant) {
        GlassSurfaceVariant.Panel -> opacity.sideHighlight * 0.55f
        GlassSurfaceVariant.Card -> opacity.sideHighlight * 0.78f
        GlassSurfaceVariant.Interactive -> opacity.sideHighlight
        GlassSurfaceVariant.Selected -> opacity.sideHighlight * 1.32f
        GlassSurfaceVariant.Overlay -> opacity.sideHighlight
        GlassSurfaceVariant.Navigation -> opacity.sideHighlight * 0.88f
        GlassSurfaceVariant.Modal -> opacity.sideHighlight * 1.18f
        GlassSurfaceVariant.FallbackOpaque -> opacity.sideHighlight * 0.72f
    }

private fun bottomBorderAlpha(
    variant: GlassSurfaceVariant,
    opacity: JeanCalculOpacity,
): Float =
    when (variant) {
        GlassSurfaceVariant.Panel -> opacity.bottomBorder * 0.55f
        GlassSurfaceVariant.Card -> opacity.bottomBorder * 0.82f
        GlassSurfaceVariant.Interactive -> opacity.bottomBorder
        GlassSurfaceVariant.Selected -> opacity.bottomBorder * 1.22f
        GlassSurfaceVariant.Overlay -> opacity.bottomBorder
        GlassSurfaceVariant.Navigation -> opacity.bottomBorder * 0.90f
        GlassSurfaceVariant.Modal -> opacity.bottomBorder * 1.10f
        GlassSurfaceVariant.FallbackOpaque -> opacity.bottomBorder * 0.78f
    }

private fun glassElevation(
    variant: GlassSurfaceVariant,
    state: GlassSurfaceState,
    elevation: JeanCalculElevation,
    highContrast: Boolean,
): Dp {
    if (state == GlassSurfaceState.Disabled) return 0.dp
    val base =
        when (variant) {
            GlassSurfaceVariant.Panel -> elevation.panel
            GlassSurfaceVariant.Card -> elevation.card
            GlassSurfaceVariant.Interactive -> elevation.interactive
            GlassSurfaceVariant.Selected -> elevation.selected
            GlassSurfaceVariant.Overlay -> elevation.overlay
            GlassSurfaceVariant.Navigation -> elevation.navigation
            GlassSurfaceVariant.Modal -> elevation.modal
            GlassSurfaceVariant.FallbackOpaque -> elevation.card
        }
    return when {
        highContrast -> base + 2.dp
        state == GlassSurfaceState.Pressed -> (base - 2.dp).coerceAtLeast(0.dp)
        state == GlassSurfaceState.Focused -> base + 2.dp
        else -> base
    }
}

private data class GlassMaterial(
    val tonalStart: Color,
    val tonalCenter: Color,
    val tonalEnd: Color,
    val topHighlight: Color,
    val sideBorder: Color,
    val bottomBorder: Color,
    val shadowColor: Color,
    val haloColor: Color,
    val elevation: Dp,
    val hasContextualHalo: Boolean,
    val reflectionAlpha: Float,
)

private fun glassShape(variant: GlassSurfaceVariant): Shape =
    when (variant) {
        GlassSurfaceVariant.Navigation,
        GlassSurfaceVariant.Overlay,
        -> RoundedCornerShape(percent = 50)

        GlassSurfaceVariant.Modal -> JeanCalculShapes.extraLarge
        else -> JeanCalculShapes.medium
    }
