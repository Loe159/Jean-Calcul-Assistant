package io.github.mangi.eta.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import io.github.mangi.eta.data.model.AppearanceSettings

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalBlurEnabled = staticCompositionLocalOf { true }
internal val LocalPlatformDensity = staticCompositionLocalOf<Density?> { null }
