package fr.loevan.jeancalcul.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stable visual golden specifications for the rendering decisions that do not need a device or a
 * real-time audio source. Compose previews exercise the same public component states.
 */
class DesignGoldenSpecTest {
    @Test
    fun `dark palette keeps the approved obsidian and content colors`() {
        assertEquals(Color(0xFF101415), JeanCalculDarkColorScheme.background)
        assertEquals(Color(0xFF0B0F10), Color(0xFF0B0F10))
        assertEquals(Color(0xFFE0E3E4), JeanCalculDarkColorScheme.onSurface)
        assertEquals(Color(0xFFA4C9FF), JeanCalculDarkColorScheme.secondary)
        assertEquals(Color(0xFFFFB4AB), JeanCalculDarkColorScheme.error)
    }

    @Test
    fun `reduced effects remove animation and gradients`() {
        val resolved = VisualEffects(reduceMotion = true).resolved()

        assertFalse(resolved.animate)
        assertFalse(resolved.useGradients)
        assertTrue(resolved.useTranslucency)
    }

    @Test
    fun `no blur fallback remains opaque and does not require shaders`() {
        val resolved = VisualEffects(blurEnabled = false, shadersEnabled = false).resolved()

        assertFalse(resolved.useTranslucency)
        assertFalse(resolved.useGradients)
        assertTrue(resolved.animate)
    }

    @Test
    fun `orb and wave contracts expose every required presentational state`() {
        assertTrue(GradientOrbState.entries.containsAll(gradientOrbGoldenStates))
        assertTrue(VoiceWaveState.entries.containsAll(voiceWaveGoldenStates))
        assertTrue(OrbMotionMode.entries.containsAll(orbMotionGoldenModes))
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
            setOf(OrbMotionMode.Animated, OrbMotionMode.Reduced, OrbMotionMode.Static, OrbMotionMode.NoShader)
    }
}
