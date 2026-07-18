@file:Suppress("FunctionNaming", "LongParameterList", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val effects = JeanCalculDesign.effects.resolved()
    val shape = glassShape(variant)
    val opaque = variant == GlassSurfaceVariant.FallbackOpaque || !effects.useTranslucency
    val background = glassBackground(variant, state, opaque)
    val borderColor = glassBorder(variant, state)
    val interactionModifier =
        if (onClick != null && state != GlassSurfaceState.Disabled && state != GlassSurfaceState.Loading) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(background)
                .border(width = 1.dp, color = borderColor, shape = shape)
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

@Composable
private fun glassBackground(
    variant: GlassSurfaceVariant,
    state: GlassSurfaceState,
    opaque: Boolean,
): Color {
    val colors = MaterialTheme.colorScheme
    val opacity = JeanCalculDesign.tokens.opacity
    if (opaque) {
        return when (variant) {
            GlassSurfaceVariant.Overlay,
            GlassSurfaceVariant.Navigation,
            GlassSurfaceVariant.Modal,
            -> colors.surfaceVariant

            else -> colors.surface
        }
    }
    val base =
        when (variant) {
            GlassSurfaceVariant.Overlay,
            GlassSurfaceVariant.Navigation,
            GlassSurfaceVariant.Modal,
            -> colors.surfaceVariant.copy(alpha = 0.88f)

            GlassSurfaceVariant.Selected -> colors.tertiaryContainer.copy(alpha = 0.34f)
            GlassSurfaceVariant.Interactive -> colors.onSurface.copy(alpha = opacity.glassInteractive)
            else -> colors.onSurface.copy(alpha = opacity.glass)
        }
    return when (state) {
        GlassSurfaceState.Pressed -> colors.secondary.copy(alpha = 0.18f)
        GlassSurfaceState.Focused -> colors.tertiary.copy(alpha = 0.16f)
        GlassSurfaceState.Disabled -> colors.surfaceVariant.copy(alpha = 0.55f)
        GlassSurfaceState.Error -> colors.errorContainer.copy(alpha = 0.42f)
        else -> base
    }
}

@Composable
private fun glassBorder(
    variant: GlassSurfaceVariant,
    state: GlassSurfaceState,
): Color {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        GlassSurfaceState.Error -> colors.error
        GlassSurfaceState.Focused -> colors.secondary
        GlassSurfaceState.Pressed -> colors.secondary.copy(alpha = 0.85f)
        GlassSurfaceState.Disabled -> colors.outlineVariant.copy(alpha = 0.45f)
        else ->
            when (variant) {
                GlassSurfaceVariant.Selected -> colors.tertiary.copy(alpha = 0.85f)
                GlassSurfaceVariant.Modal,
                GlassSurfaceVariant.Overlay,
                GlassSurfaceVariant.Navigation,
                -> colors.onSurface.copy(alpha = JeanCalculDesign.tokens.opacity.topHighlight)

                else -> colors.outlineVariant.copy(alpha = 0.78f)
            }
    }
}

private fun glassShape(variant: GlassSurfaceVariant): Shape =
    when (variant) {
        GlassSurfaceVariant.Navigation,
        GlassSurfaceVariant.Overlay,
        -> RoundedCornerShape(percent = 50)

        GlassSurfaceVariant.Modal -> JeanCalculShapes.extraLarge
        else -> JeanCalculShapes.medium
    }
