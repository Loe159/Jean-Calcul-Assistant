package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Shared Compose theme for the app and transparent assistant session. */
@Composable
fun jeanCalculTheme(
    themeMode: ThemeMode = ThemeMode.System,
    visualEffects: VisualEffects = VisualEffects(),
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Dark -> true
            ThemeMode.Light -> false
        }
    val colors = if (isDark) JeanCalculDarkColorScheme else JeanCalculLightColorScheme

    CompositionLocalProvider(
        LocalJeanCalculDesignTokens provides designTokens(isDark),
        LocalVisualEffects provides visualEffects,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = JeanCalculTypography,
            shapes = JeanCalculShapes,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                content = content,
            )
        }
    }
}

object JeanCalculDesign {
    val tokens: JeanCalculDesignTokens
        @Composable get() = LocalJeanCalculDesignTokens.current

    val effects: VisualEffects
        @Composable get() = LocalVisualEffects.current
}
