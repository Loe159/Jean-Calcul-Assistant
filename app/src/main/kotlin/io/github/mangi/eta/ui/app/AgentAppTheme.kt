package io.github.mangi.eta.ui.app

import android.util.DisplayMetrics
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.effect.LocalCOUIMaterialEffectEnabled
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.data.model.DEFAULT_INTERFACE_SCALE
import io.github.mangi.eta.data.model.normalizeInterfaceScale
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeColorSpec
import io.github.suqi8.coui.kmp.theme.ThemeController
import io.github.suqi8.coui.kmp.theme.ThemePaletteStyle
import io.github.suqi8.coui.kmp.theme.platformDynamicColors

@Composable
fun AgentAppTheme(
    appearance: AppearanceSettings,
    applyInterfaceScale: Boolean,
    onResolvedDarkModeChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appearance.themeMode) {
        AppearanceThemeMode.SYSTEM -> systemDark
        AppearanceThemeMode.LIGHT -> false
        AppearanceThemeMode.DARK -> true
    }
    val colorSchemeMode = when {
        !appearance.monetEnabled && appearance.themeMode == AppearanceThemeMode.LIGHT -> ColorSchemeMode.Light
        !appearance.monetEnabled && appearance.themeMode == AppearanceThemeMode.DARK -> ColorSchemeMode.Dark
        !appearance.monetEnabled -> ColorSchemeMode.System
        appearance.themeMode == AppearanceThemeMode.LIGHT -> ColorSchemeMode.MonetLight
        appearance.themeMode == AppearanceThemeMode.DARK -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
    val systemSeedColor = if (
        appearance.monetEnabled &&
        appearance.accentColor == AppearanceAccentColor.SYSTEM &&
        appearance.paletteStyle != AppearancePaletteStyle.TONAL_SPOT
    ) {
        platformDynamicColors(isDark).primary
    } else {
        null
    }
    val keyColor = when {
        !appearance.monetEnabled -> null
        appearance.accentColor == AppearanceAccentColor.SYSTEM -> systemSeedColor
        else -> appearance.accentColor.seedColor()
    }
    val controller = remember(colorSchemeMode, keyColor, appearance.paletteStyle, isDark) {
        ThemeController(
            colorSchemeMode = colorSchemeMode,
            keyColor = keyColor,
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = appearance.paletteStyle.toCouiPaletteStyle(),
            isDark = isDark,
        )
    }

    LaunchedEffect(isDark) { onResolvedDarkModeChange(isDark) }

    val initialDisplayDensity = remember(context) { context.resources.displayMetrics.density }
    val baseDensity = if (configuration.densityDpi > 0) {
        configuration.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    } else {
        initialDisplayDensity
    }
    val effectiveScale = if (applyInterfaceScale) {
        normalizeInterfaceScale(appearance.interfaceScale)
    } else {
        DEFAULT_INTERFACE_SCALE
    }
    val targetDensity = baseDensity * effectiveScale
    val platformDensity = remember(context, baseDensity, configuration.fontScale) {
        val saved = context.resources.displayMetrics.density
        context.resources.displayMetrics.density = baseDensity
        Density(context).also { context.resources.displayMetrics.density = saved }
    }
    if (applyInterfaceScale) {
        // 独立窗口（Dialog / Popup）的新 AndroidComposeView 从宿主 Context.resources 读取密度；
        // 以 configuration.densityDpi / 160f 为 100% 尺寸基准同步写入，保证页面与独立窗口缩放一致。
        context.resources.displayMetrics.density = targetDensity
        @Suppress("DEPRECATION")
        context.resources.displayMetrics.scaledDensity = targetDensity * configuration.fontScale
    }
    DisposableEffect(context, applyInterfaceScale, baseDensity, configuration.fontScale) {
        onDispose {
            if (applyInterfaceScale) {
                context.resources.displayMetrics.density = baseDensity
                @Suppress("DEPRECATION")
                context.resources.displayMetrics.scaledDensity = baseDensity * configuration.fontScale
            }
        }
    }
    val appDensity = remember(context, targetDensity, configuration.fontScale, applyInterfaceScale) {
        if (applyInterfaceScale) {
            context.resources.displayMetrics.density = targetDensity
            Density(context)
        } else {
            platformDensity
        }
    }

    COUITheme(controller = controller) {
        val baseColors = COUITheme.colorScheme
        val pureBlack = appearance.monetEnabled && appearance.pureBlackEnabled && isDark
        val colors = remember(baseColors, pureBlack) {
            if (pureBlack) {
                baseColors.copy(background = Color.Black, backgroundWithCard = Color.Black)
            } else {
                baseColors
            }
        }
        val blurEnabled = isRuntimeShaderSupported()
        COUITheme(colors = colors) {
            CompositionLocalProvider(
                LocalCOUIMaterialEffectEnabled provides blurEnabled,
                LocalAppearanceSettings provides appearance,
                LocalBlurEnabled provides blurEnabled,
                LocalPlatformDensity provides platformDensity,
                LocalDensity provides appDensity,
            ) {
                content()
            }
        }
    }
}

private fun AppearancePaletteStyle.toCouiPaletteStyle(): ThemePaletteStyle = when (this) {
    AppearancePaletteStyle.TONAL_SPOT -> ThemePaletteStyle.TonalSpot
    AppearancePaletteStyle.NEUTRAL -> ThemePaletteStyle.Neutral
    AppearancePaletteStyle.VIBRANT -> ThemePaletteStyle.Vibrant
    AppearancePaletteStyle.EXPRESSIVE -> ThemePaletteStyle.Expressive
    AppearancePaletteStyle.RAINBOW -> ThemePaletteStyle.Rainbow
    AppearancePaletteStyle.FRUIT_SALAD -> ThemePaletteStyle.FruitSalad
    AppearancePaletteStyle.MONOCHROME -> ThemePaletteStyle.Monochrome
    AppearancePaletteStyle.FIDELITY -> ThemePaletteStyle.Fidelity
    AppearancePaletteStyle.CONTENT -> ThemePaletteStyle.Content
}

private fun AppearanceAccentColor.seedColor(): Color = when (this) {
    AppearanceAccentColor.SYSTEM, AppearanceAccentColor.BLUE -> Color(0xFF4285F4)
    AppearanceAccentColor.PURPLE -> Color(0xFF7C4DFF)
    AppearanceAccentColor.PINK -> Color(0xFFE91E63)
    AppearanceAccentColor.RED -> Color(0xFFEA4335)
    AppearanceAccentColor.ORANGE -> Color(0xFFFF5722)
    AppearanceAccentColor.YELLOW -> Color(0xFFFBBC04)
    AppearanceAccentColor.GREEN -> Color(0xFF34A853)
    AppearanceAccentColor.TEAL -> Color(0xFF00BCD4)
}
