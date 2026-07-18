@file:Suppress("MagicNumber")

package fr.loevan.jeancalcul.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Stable visual contracts complementing the device-side complete-component render analysis. */
class DesignGoldenSpecTest {
    @Test
    fun `dark palette keeps obsidian hierarchy and blue violet identity`() {
        val materials = designTokens(isDark = true).materials

        assertEquals(Color(0xFF101415), JeanCalculDarkColorScheme.background)
        assertEquals(Color(0xFFE0E3E4), JeanCalculDarkColorScheme.onSurface)
        assertEquals(Color(0xFFA4C9FF), JeanCalculDarkColorScheme.secondary)
        assertEquals(Color(0xFFD0BCFF), JeanCalculDarkColorScheme.tertiary)
        assertNotEquals(materials.ambientBlue, materials.ambientViolet)
        assertTrue(materials.ambientViolet.alpha > 0f)
        assertEquals(5, materials.tonalLayers().distinct().size)
    }

    @Test
    fun `light palette is derived and retains readable tonal hierarchy`() {
        val materials = designTokens(isDark = false).materials

        assertNotEquals(JeanCalculDarkColorScheme.background, JeanCalculLightColorScheme.background)
        assertTrue(JeanCalculLightColorScheme.background.luminance() > 0.8)
        assertTrue(JeanCalculLightColorScheme.onSurface.luminance() < 0.2)
        assertEquals(5, materials.tonalLayers().distinct().size)
        assertTrue(materials.ambientViolet.alpha > 0f)
    }

    @Test
    fun `glass elevations preserve panel card selected overlay and modal separation`() {
        val elevation = JeanCalculElevation()

        assertTrue(elevation.panel < elevation.card)
        assertTrue(elevation.card < elevation.selected)
        assertTrue(elevation.selected < elevation.overlay)
        assertTrue(elevation.overlay < elevation.modal)
    }

    @Test
    fun `reduced effects remove animation and shaders but preserve tonal translucency`() {
        val resolved = VisualEffects(reduceMotion = true).resolved()

        assertFalse(resolved.animate)
        assertFalse(resolved.useGradients)
        assertTrue(resolved.useTranslucency)
    }

    @Test
    fun `no blur no shader fallback is opaque while high contrast is also tonal`() {
        val noBlur = VisualEffects(blurEnabled = false, shadersEnabled = false).resolved()
        val highContrast = VisualEffects(highContrast = true).resolved()

        assertFalse(noBlur.useTranslucency)
        assertFalse(noBlur.useGradients)
        assertFalse(highContrast.useTranslucency)
    }

    @Test
    fun `fallback text contrast exceeds WCAG AA`() {
        val darkMaterials = designTokens(isDark = true).materials
        val lightMaterials = designTokens(isDark = false).materials

        assertTrue(contrastRatio(JeanCalculDarkColorScheme.onSurface, darkMaterials.surface) >= 4.5)
        assertTrue(contrastRatio(JeanCalculLightColorScheme.onSurface, lightMaterials.surface) >= 4.5)
    }

    @Test
    fun `public state and deterministic motion contracts remain complete`() {
        assertTrue(GradientOrbState.entries.containsAll(gradientOrbGoldenStates))
        assertTrue(VoiceWaveState.entries.containsAll(voiceWaveGoldenStates))
        assertTrue(OrbMotionMode.entries.containsAll(orbMotionGoldenModes))
    }

    @Test
    fun `minimum touch target remains 48 dp`() {
        assertTrue(JeanCalculSpacing().touchTarget >= 48.dp)
    }

    @Test
    fun `core ui has no feature or business module dependency`() {
        val coreUi = locateCoreUiDirectory()
        val buildFile = coreUi.resolve("build.gradle.kts").readText()
        val sourceImports =
            coreUi
                .resolve("src/main")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.useLines { lines ->
                        lines.filter { it.startsWith("import ") }.toList().asSequence()
                    }
                }
                .toList()
        val forbiddenPackages =
            listOf(
                "fr.loevan.jeancalcul.app",
                "fr.loevan.jeancalcul.assistant",
                "fr.loevan.jeancalcul.data",
                "fr.loevan.jeancalcul.domain",
                "fr.loevan.jeancalcul.feature",
                "fr.loevan.jeancalcul.tool",
            )

        assertFalse(
            "core-ui build must not depend on another project",
            Regex("project\\s*\\(").containsMatchIn(buildFile),
        )
        assertTrue(
            "core-ui source imported a feature or business package: $sourceImports",
            sourceImports.none { declaration -> forbiddenPackages.any(declaration::contains) },
        )
    }

    private companion object {
        val gradientOrbGoldenStates =
            setOf(
                GradientOrbState.Idle,
                GradientOrbState.Invoked,
                GradientOrbState.Listening,
                GradientOrbState.Transcribing,
                GradientOrbState.Thinking,
                GradientOrbState.ProposingAction,
                GradientOrbState.WaitingApproval,
                GradientOrbState.Executing,
                GradientOrbState.Speaking,
                GradientOrbState.Completed,
                GradientOrbState.Cancelled,
                GradientOrbState.Error,
                GradientOrbState.Offline,
            )
        val voiceWaveGoldenStates =
            setOf(
                VoiceWaveState.Waiting,
                VoiceWaveState.Listening,
                VoiceWaveState.Silence,
                VoiceWaveState.Speaking,
                VoiceWaveState.Bluetooth,
                VoiceWaveState.MicrophoneUnavailable,
                VoiceWaveState.Static,
            )
        val orbMotionGoldenModes =
            setOf(
                OrbMotionMode.Animated,
                OrbMotionMode.Reduced,
                OrbMotionMode.Static,
                OrbMotionMode.NoShader,
            )
    }
}

private fun JeanCalculMaterialColors.tonalLayers(): List<Color> =
    listOf(surfaceLowest, surfaceLow, surface, surfaceHigh, surfaceHighest)

private fun contrastRatio(
    first: Color,
    second: Color,
): Double {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.luminance(): Double =
    listOf(red, green, blue)
        .map { channel ->
            if (channel <= 0.04045f) {
                channel / 12.92
            } else {
                Math.pow(((channel + 0.055) / 1.055).toDouble(), 2.4)
            }
        }
        .let { channels -> channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722 }

private fun locateCoreUiDirectory(): File {
    val start = File(System.getProperty("user.dir").orEmpty()).absoluteFile
    val located =
        generateSequence(start) { it.parentFile }
            .take(7)
            .flatMap { directory ->
                sequenceOf(directory.resolve("android/core-ui"), directory.resolve("core-ui"), directory)
            }
            .firstOrNull { candidate ->
                candidate.name == "core-ui" && candidate.resolve("build.gradle.kts").isFile
            }
    return located ?: error("Unable to locate android/core-ui from $start")
}
