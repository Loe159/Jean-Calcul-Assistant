// Adapted from the COUI "coui-app" skill page scaffold (references/scaffold.md, CouiBlur.kt),
// https://github.com/suqi8/coui at df5883f7f36b075125469488c54625152e83a556, Apache-2.0.
package io.github.mangi.eta.ui.components.coui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.mangi.eta.ui.app.LocalBlurEnabled
import io.github.suqi8.coui.kmp.blur.Backdrop
import io.github.suqi8.coui.kmp.blur.LayerBackdrop
import io.github.suqi8.coui.kmp.blur.MaterialBlendMode
import io.github.suqi8.coui.kmp.blur.MaterialBlurStyle
import io.github.suqi8.coui.kmp.blur.ViewBlurStyle
import io.github.suqi8.coui.kmp.blur.couiMaterialBlur
import io.github.suqi8.coui.kmp.blur.couiViewBlur
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.blur.rememberLayerBackdrop
import io.github.suqi8.coui.kmp.effect.MaterialBackdropProvider
import io.github.suqi8.coui.kmp.effect.MaterialBackdropSpec
import io.github.suqi8.coui.kmp.effect.MaterialBlurBlendMode
import io.github.suqi8.coui.kmp.effect.MaterialBlurProvider
import io.github.suqi8.coui.kmp.effect.MaterialBlurSpec
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The backdrop recording the current page, provided by the page scaffolds so dialogs, sheets and
 * menus opened from the page can blur it.
 */
internal val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Returns a backdrop recording a page over [background], or null when the blur is off. */
@Composable
internal fun rememberPageBackdrop(
    background: Color = COUITheme.colorScheme.backgroundWithCard,
): LayerBackdrop? {
    if (!LocalBlurEnabled.current || !isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/**
 * Bridges a backdrop to coui-ui's `LocalMaterialBackdrop` (buttons, floating bars, toolbars).
 * The backdrop must record what is behind those elements, never the elements themselves.
 */
@Composable
internal fun rememberMaterialBackdropProvider(backdrop: Backdrop?): MaterialBackdropProvider? {
    val active = LocalBlurEnabled.current && backdrop != null && isRuntimeShaderSupported()
    return remember(backdrop, active) {
        if (active && backdrop != null) {
            MaterialBackdropProvider { spec, shape ->
                Modifier.couiViewBlur(backdrop = backdrop, style = spec.toViewBlurStyle(), shape = shape)
            }
        } else {
            null
        }
    }
}

/** Bridges a backdrop to coui-ui's `LocalPopupMaterialBlur` (dropdowns, list popups, context menus). */
@Composable
internal fun rememberPopupMaterialBlur(backdrop: Backdrop?): MaterialBlurProvider? {
    val active = LocalBlurEnabled.current && backdrop != null && isRuntimeShaderSupported()
    return remember(backdrop, active) {
        if (active && backdrop != null) {
            MaterialBlurProvider { spec, shape ->
                Modifier.couiMaterialBlur(backdrop = backdrop, style = spec.toMaterialBlurStyle(), shape = shape)
            }
        } else {
            null
        }
    }
}

/** The COUI 17 blurred panel of a dialog or bottom sheet. */
@Immutable
internal class PanelBlur(val backgroundModifier: Modifier, val backgroundColor: Color)

/**
 * Returns the panel blur for the page behind, or null to keep the opaque COUI surface.
 *
 * @param style e.g. `MaterialBlurDefaults::dialog` or `MaterialBlurDefaults::bottomSheet`.
 */
@Composable
internal fun panelBlur(style: (isDark: Boolean) -> MaterialBlurStyle): PanelBlur? {
    val backdrop = LocalPageBackdrop.current
    if (!LocalBlurEnabled.current || backdrop == null || !isRuntimeShaderSupported()) return null
    return PanelBlur(
        backgroundModifier = Modifier.couiMaterialBlur(
            backdrop = backdrop,
            style = style(COUITheme.colorScheme.isDark),
        ),
        backgroundColor = Color.Transparent,
    )
}

private fun MaterialBackdropSpec.toViewBlurStyle(): ViewBlurStyle = ViewBlurStyle(
    blurRadiusPx = blurRadiusPx,
    layer1 = layer1,
    mode1 = mode1,
    layer2 = layer2,
    mode2 = mode2,
)

private fun MaterialBlurSpec.toMaterialBlurStyle(): MaterialBlurStyle = MaterialBlurStyle(
    blurRadiusPx = blurRadiusPx,
    mixColor = mixColor,
    blendColor = blendColor,
    blendMode = when (blendMode) {
        MaterialBlurBlendMode.Overlay -> MaterialBlendMode.Overlay
        MaterialBlurBlendMode.ColorDodge -> MaterialBlendMode.ColorDodge
        MaterialBlurBlendMode.GlowOverlay -> MaterialBlendMode.GlowOverlay
        MaterialBlurBlendMode.GlowColorDodge -> MaterialBlendMode.GlowColorDodge
    },
)
