package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The appearance setting is deliberately independent from Android feature state. */
enum class ThemeMode {
    System,
    Dark,
    Light,
}

/**
 * Accessibility and rendering switches supplied by the host application.
 *
 * Compose has no portable backdrop-blur API. [blurEnabled] therefore selects the translucent
 * treatment only; every surface retains a tonal fallback. [shadersEnabled] controls Canvas
 * gradients and never controls meaning or legibility.
 */
@Immutable
data class VisualEffects(
    val reduceMotion: Boolean = false,
    val blurEnabled: Boolean = true,
    val shadersEnabled: Boolean = true,
    val highContrast: Boolean = false,
)

@Immutable
data class ResolvedVisualEffects(
    val animate: Boolean,
    val useGradients: Boolean,
    val useTranslucency: Boolean,
)

fun VisualEffects.resolved(): ResolvedVisualEffects =
    ResolvedVisualEffects(
        animate = !reduceMotion,
        useGradients = shadersEnabled && !reduceMotion,
        useTranslucency = blurEnabled && !highContrast,
    )

@Immutable
data class JeanCalculSemanticColors(
    val success: Color,
    val information: Color,
    val warning: Color,
    val offline: Color,
    val privacy: Color,
    val riskR0: Color,
    val riskR1: Color,
    val riskR2: Color,
    val riskR3: Color,
    val riskR4: Color,
    val riskR5: Color,
)

@Immutable
data class JeanCalculSpacing(
    val unit: Dp = 8.dp,
    val screen: Dp = 24.dp,
    val gutter: Dp = 16.dp,
    val floating: Dp = 12.dp,
    val section: Dp = 48.dp,
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class JeanCalculOpacity(
    val glass: Float = 0.03f,
    val glassInteractive: Float = 0.05f,
    val topHighlight: Float = 0.15f,
    val ambientViolet: Float = 0.15f,
    val ambientBlue: Float = 0.10f,
    val sideHighlight: Float = 0.08f,
    val bottomBorder: Float = 0.03f,
    val diffuseShadow: Float = 0.32f,
)

@Immutable
data class JeanCalculElevation(
    val panel: Dp = 2.dp,
    val card: Dp = 6.dp,
    val overlay: Dp = 12.dp,
    val modal: Dp = 16.dp,
    val interactive: Dp = 8.dp,
    val selected: Dp = 10.dp,
    val navigation: Dp = 14.dp,
)

/** Tonal layers used to preserve depth when translucency is unavailable. */
@Immutable
data class JeanCalculMaterialColors(
    val surfaceLowest: Color = Color(0xFF0B0F10),
    val surfaceLow: Color = Color(0xFF181C1D),
    val surface: Color = Color(0xFF1C2021),
    val surfaceHigh: Color = Color(0xFF272B2C),
    val surfaceHighest: Color = Color(0xFF313536),
    val ambientBlue: Color = Color(0xFF0164B4),
    val ambientViolet: Color = Color(0xFF8A5BF5),
    val topReflection: Color = Color.White,
    val diffuseShadow: Color = Color.Black,
)

@Immutable
data class JeanCalculMotion(
    val pressMillis: Int = 120,
    val enterMillis: Int = 220,
    val sheetMillis: Int = 260,
    val pulseMillis: Int = 1_200,
    val breatheMillis: Int = 4_000,
    val shimmerMillis: Int = 2_400,
)

@Immutable
data class JeanCalculDesignTokens(
    val semantic: JeanCalculSemanticColors,
    val spacing: JeanCalculSpacing = JeanCalculSpacing(),
    val opacity: JeanCalculOpacity = JeanCalculOpacity(),
    val elevation: JeanCalculElevation = JeanCalculElevation(),
    val motion: JeanCalculMotion = JeanCalculMotion(),
    val materials: JeanCalculMaterialColors = JeanCalculMaterialColors(),
)

val JeanCalculDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFE2E2E8),
        onPrimary = Color(0xFF2F3035),
        primaryContainer = Color(0xFFC6C6CC),
        onPrimaryContainer = Color(0xFF2F3035),
        secondary = Color(0xFFA4C9FF),
        onSecondary = Color(0xFF00315D),
        secondaryContainer = Color(0xFF224A79),
        onSecondaryContainer = Color(0xFFD3E3FF),
        tertiary = Color(0xFFD0BCFF),
        onTertiary = Color(0xFF3C0091),
        tertiaryContainer = Color(0xFF4F2A75),
        onTertiaryContainer = Color(0xFFE9DDFF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF101415),
        onBackground = Color(0xFFE0E3E4),
        surface = Color(0xFF181C1D),
        onSurface = Color(0xFFE0E3E4),
        surfaceDim = Color(0xFF101415),
        surfaceBright = Color(0xFF363A3B),
        surfaceContainerLowest = Color(0xFF0B0F10),
        surfaceContainerLow = Color(0xFF181C1D),
        surfaceContainer = Color(0xFF1C2021),
        surfaceContainerHigh = Color(0xFF272B2C),
        surfaceContainerHighest = Color(0xFF313536),
        surfaceVariant = Color(0xFF313536),
        onSurfaceVariant = Color(0xFFC6C6CB),
        surfaceTint = Color(0xFFC6C6CC),
        outline = Color(0xFF909095),
        outlineVariant = Color(0xFF46474B),
        inverseSurface = Color(0xFFE0E3E4),
        inverseOnSurface = Color(0xFF2D3132),
    )

val JeanCalculLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF45474C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE2E2E8),
        onPrimaryContainer = Color(0xFF2F3035),
        secondary = Color(0xFF285E92),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD3E3FF),
        onSecondaryContainer = Color(0xFF00315D),
        tertiary = Color(0xFF6F45B7),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE9DDFF),
        onTertiaryContainer = Color(0xFF3C0091),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF7F9FA),
        onBackground = Color(0xFF181C1D),
        surface = Color(0xFFF7F9FA),
        onSurface = Color(0xFF181C1D),
        surfaceDim = Color(0xFFDCE1E3),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F4F5),
        surfaceContainer = Color(0xFFEBEFF1),
        surfaceContainerHigh = Color(0xFFE4E9EB),
        surfaceContainerHighest = Color(0xFFDDE3E5),
        surfaceVariant = Color(0xFFE1E4E5),
        onSurfaceVariant = Color(0xFF44474A),
        surfaceTint = Color(0xFF45474C),
        outline = Color(0xFF74777A),
        outlineVariant = Color(0xFFC4C7C9),
        inverseSurface = Color(0xFF2D3132),
        inverseOnSurface = Color(0xFFEEF1F2),
    )

val JeanCalculDarkSemanticColors =
    JeanCalculSemanticColors(
        success = Color(0xFF8FD8A6),
        information = Color(0xFFA4C9FF),
        warning = Color(0xFFFFDDA2),
        offline = Color(0xFFC6C6CB),
        privacy = Color(0xFFD0BCFF),
        riskR0 = Color(0xFF8FD8A6),
        riskR1 = Color(0xFFA4C9FF),
        riskR2 = Color(0xFFD0BCFF),
        riskR3 = Color(0xFFFFDDA2),
        riskR4 = Color(0xFFFFB4AB),
        riskR5 = Color(0xFFFF8A80),
    )

val JeanCalculLightSemanticColors =
    JeanCalculSemanticColors(
        success = Color(0xFF146C2E),
        information = Color(0xFF185EA8),
        warning = Color(0xFF7A4F00),
        offline = Color(0xFF5D6064),
        privacy = Color(0xFF6741A3),
        riskR0 = Color(0xFF146C2E),
        riskR1 = Color(0xFF185EA8),
        riskR2 = Color(0xFF6741A3),
        riskR3 = Color(0xFF7A4F00),
        riskR4 = Color(0xFFB3261E),
        riskR5 = Color(0xFF8B0000),
    )

val JeanCalculTypography =
    androidx.compose.material3.Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                letterSpacing = (-0.96).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.32).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.6.sp,
            ),
    )

val JeanCalculShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

private val JeanCalculLightMaterialColors =
    JeanCalculMaterialColors(
        surfaceLowest = Color(0xFFFFFFFF),
        surfaceLow = Color(0xFFF1F4F5),
        surface = Color(0xFFEBEFF1),
        surfaceHigh = Color(0xFFE4E9EB),
        surfaceHighest = Color(0xFFDDE3E5),
        ambientBlue = Color(0xFF185EA8),
        ambientViolet = Color(0xFF6F45B7),
        topReflection = Color.White,
        diffuseShadow = Color(0xFF172126),
    )

internal fun designTokens(isDark: Boolean): JeanCalculDesignTokens =
    JeanCalculDesignTokens(
        semantic = if (isDark) JeanCalculDarkSemanticColors else JeanCalculLightSemanticColors,
        materials = if (isDark) JeanCalculMaterialColors() else JeanCalculLightMaterialColors,
    )

val LocalJeanCalculDesignTokens = staticCompositionLocalOf { designTokens(isDark = true) }
val LocalVisualEffects = staticCompositionLocalOf { VisualEffects() }
