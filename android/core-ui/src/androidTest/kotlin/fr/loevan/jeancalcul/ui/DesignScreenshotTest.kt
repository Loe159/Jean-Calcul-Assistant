@file:Suppress(
    "FunctionNaming",
    "LongMethod",
    "MagicNumber",
    "TooManyFunctions",
    "ktlint:standard:function-naming",
)

package fr.loevan.jeancalcul.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import android.graphics.Color as AndroidColor

/** Deterministic component renders checked by regions and color families, without binary goldens. */
@RunWith(AndroidJUnit4::class)
class DesignScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun darkGlassSurfaceHasTonalDepthHighlightAndNonUniformMaterial() {
        setTestFrame(tag = FRAME_TAG, width = 260.dp, height = 180.dp) {
            GlassSurface(
                modifier = Modifier.fillMaxSize().testTag(COMPONENT_TAG),
                variant = GlassSurfaceVariant.Card,
            ) {}
        }

        val stats = capture(COMPONENT_TAG).visualStats()
        assertTrue("glass needs several tonal layers", stats.quantizedColorCount >= 6)
        assertTrue("glass must not be a uniform Material card", stats.luminanceRange >= 8)
        assertTrue("top, center and bottom must remain optically separated", stats.bandSpread >= 2.0)
    }

    @Test
    fun noBlurFallbackKeepsAnOpticalEdgeAndReadableText() {
        setTestFrame(
            tag = FRAME_TAG,
            width = 280.dp,
            height = 170.dp,
            visualEffects = VisualEffects(blurEnabled = false, shadersEnabled = false),
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxSize().testTag(COMPONENT_TAG),
                variant = GlassSurfaceVariant.FallbackOpaque,
            ) {
                Text("Fallback sans blur lisible", modifier = Modifier.testTag(TEXT_TAG))
            }
        }

        val stats = capture(COMPONENT_TAG).visualStats()
        composeRule.onNodeWithTag(TEXT_TAG).assertIsDisplayed()
        assertTrue("opaque fallback still needs a visible edge", stats.edgeToCenterDelta >= 1.0)
        assertTrue(
            "fallback text must contrast with its tonal surface: $stats",
            stats.luminanceRange >= 80,
        )
    }

    @Test
    fun idleOrbKeepsALayeredBlueCore() {
        setOrb(GradientOrbState.Idle, amplitude = 0f, progress = 0.17f)

        val stats = capture(ORB_TAG).visualStats()
        assertTrue("idle orb lost its blue identity", stats.bluePixelCount >= 80)
        assertTrue("idle orb became a flat circle", stats.quantizedColorCount >= 10)
    }

    @Test
    fun listeningOrbUsesAmplitudeAndKeepsMicrophoneSemantics() {
        setOrb(GradientOrbState.Listening, amplitude = 0.82f, progress = 0.43f)

        val stats = capture(ORB_TAG).visualStats()
        val description = contentDescription(ORB_TAG)
        assertTrue("listening orb needs a strong blue core", stats.bluePixelCount >= 120)
        assertTrue("listening must explicitly expose active microphone", description.contains("Microphone actif"))
    }

    @Test
    fun thinkingOrbShowsVioletHaloWithoutLosingBlueCore() {
        setOrb(GradientOrbState.Thinking, amplitude = 0.36f, progress = 0.61f)

        val stats = capture(ORB_TAG).visualStats()
        assertTrue("thinking state lost its violet activity halo", stats.violetPixelCount >= 20)
        assertTrue("thinking state must keep the core primarily blue", stats.bluePixelCount >= 60)
        assertFalse(contentDescription(ORB_TAG).contains("Microphone actif"))
    }

    @Test
    fun microphoneActiveSemanticsExistOnlyForListeningStates() {
        setTestFrame(tag = FRAME_TAG, width = 320.dp, height = 640.dp) {
            Column {
                GradientOrbState.entries.forEach { state ->
                    GradientOrb(
                        state = state,
                        modifier = Modifier.testTag("orb-${state.name}"),
                        motionMode = OrbMotionMode.Static,
                        orbSize = 20.dp,
                    )
                }
                VoiceWaveState.entries.forEach { state ->
                    VoiceWave(
                        state = state,
                        modifier = Modifier.size(40.dp, 12.dp).testTag("wave-${state.name}"),
                        progress = 0.37f,
                    )
                }
            }
        }

        GradientOrbState.entries.forEach { state ->
            val isActive = contentDescription("orb-${state.name}").contains("Microphone actif")
            assertTrue("orb $state has invalid microphone semantics", isActive == (state == GradientOrbState.Listening))
        }
        VoiceWaveState.entries.forEach { state ->
            val isActive = contentDescription("wave-${state.name}").contains("Microphone actif")
            assertTrue("wave $state has invalid microphone semantics", isActive == (state == VoiceWaveState.Listening))
        }
    }

    @Test
    fun compactOverlayRendersAsACompleteFloatingControl() {
        setTestFrame(tag = FRAME_TAG, width = 380.dp, height = 116.dp) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().testTag(COMPONENT_TAG),
                variant = GlassSurfaceVariant.Overlay,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GradientOrb(
                        state = GradientOrbState.Listening,
                        amplitude = 0.7f,
                        progress = 0.42f,
                        motionMode = OrbMotionMode.Static,
                        orbSize = 48.dp,
                    )
                    VoiceWave(
                        state = VoiceWaveState.Listening,
                        modifier = Modifier.size(72.dp, 28.dp),
                        amplitude = 0.7f,
                        progress = 0.42f,
                        barCount = 7,
                    )
                    Text("J'écoute", modifier = Modifier.weight(1f))
                    CircularIconButton(
                        label = "Fermer",
                        modifier = Modifier.testTag(ACTION_TAG),
                        onClick = {},
                    )
                }
            }
        }

        val stats = capture(FRAME_TAG).visualStats()
        assertTrue("overlay needs a blue activity signal", stats.bluePixelCount >= 100)
        assertTrue("overlay must visually separate several layers", stats.quantizedColorCount >= 16)
        composeRule.onNodeWithTag(ACTION_TAG).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun approvalSheetRendersTheWholeDecisionHierarchy() {
        setTestFrame(tag = FRAME_TAG, width = 400.dp, height = 520.dp) {
            ApprovalSheet(
                state = ApprovalSheetState.Biometric,
                action = approvalAction(),
                justification = "Jean Calcul doit confirmer la destination et le niveau de risque.",
                modifier = Modifier.fillMaxWidth().testTag(COMPONENT_TAG),
                onApprove = {},
                onReject = {},
            )
        }

        val stats = capture(COMPONENT_TAG).visualStats()
        composeRule.onNodeWithText("Confirmer avec Android").assertIsDisplayed()
        composeRule.onNodeWithText("Justification").assertIsDisplayed()
        composeRule.onNodeWithText("Continuer").assertIsDisplayed()
        assertTrue("focused approval surface needs a contained violet signal", stats.violetPixelCount >= 10)
        assertTrue("approval must contain multiple complete visual regions", stats.quantizedColorCount >= 18)
    }

    @Test
    fun lightThemeKeepsGlassDepthInsteadOfInvertingTheDarkPalette() {
        setTestFrame(tag = FRAME_TAG, width = 280.dp, height = 180.dp, themeMode = ThemeMode.Light) {
            GlassSurface(
                modifier = Modifier.fillMaxSize().testTag(COMPONENT_TAG),
                variant = GlassSurfaceVariant.Selected,
            ) {
                Text("Surface claire sélectionnée")
            }
        }

        val stats = capture(COMPONENT_TAG).visualStats()
        assertTrue("light surface should remain light", stats.meanLuminance >= 150.0)
        assertTrue("light glass still needs depth", stats.luminanceRange >= 35)
        assertTrue("selected light state needs violet", stats.violetPixelCount >= 5)
    }

    @Test
    fun reducedEffectsRemainLayeredAndDeterministic() {
        setTestFrame(
            tag = FRAME_TAG,
            width = 300.dp,
            height = 210.dp,
            visualEffects = VisualEffects(reduceMotion = true),
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxSize().testTag(COMPONENT_TAG),
                variant = GlassSurfaceVariant.Selected,
            ) {
                GradientOrb(
                    state = GradientOrbState.Thinking,
                    amplitude = 0.5f,
                    progress = 0.5f,
                    motionMode = OrbMotionMode.Reduced,
                    orbSize = 150.dp,
                )
            }
        }

        val stats = capture(COMPONENT_TAG).visualStats()
        assertTrue("reduced mode must retain a readable tonal hierarchy", stats.luminanceRange >= 30)
        assertTrue("reduced orb must retain its blue core", stats.bluePixelCount >= 60)
        assertTrue("reduced selected state must retain a violet cue", stats.violetPixelCount >= 10)
    }

    @Test
    fun largeFontReflowsInsideGlassAndKeepsPrimaryTargetAtLeast48Dp() {
        setTestFrame(tag = FRAME_TAG, width = 360.dp, height = 300.dp, fontScale = 1.6f) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), variant = GlassSurfaceVariant.Modal) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Confirmer l'envoi de ce message français volontairement long à la destination affichée.",
                        modifier = Modifier.testTag(TEXT_TAG),
                    )
                    JeanCalculButton(
                        label = "Continuer",
                        modifier = Modifier.fillMaxWidth().testTag(ACTION_TAG),
                        onClick = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TEXT_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ACTION_TAG).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        assertTrue("large-font composition lost contrast", capture(FRAME_TAG).visualStats().luminanceRange >= 80)
    }

    private fun setOrb(
        state: GradientOrbState,
        amplitude: Float,
        progress: Float,
    ) {
        setTestFrame(tag = FRAME_TAG, width = 210.dp, height = 210.dp) {
            GradientOrb(
                state = state,
                modifier = Modifier.testTag(ORB_TAG),
                amplitude = amplitude,
                progress = progress,
                motionMode = OrbMotionMode.Static,
                orbSize = 180.dp,
            )
        }
    }

    private fun setTestFrame(
        tag: String,
        width: Dp,
        height: Dp,
        themeMode: ThemeMode = ThemeMode.Dark,
        visualEffects: VisualEffects = VisualEffects(),
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                jeanCalculTheme(themeMode = themeMode, visualEffects = visualEffects) {
                    Box(
                        modifier =
                            Modifier
                                .size(width, height)
                                .background(MaterialTheme.colorScheme.background)
                                .testTag(tag)
                                .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun capture(tag: String): ImageBitmap = composeRule.onNodeWithTag(tag).captureToImage()

    private fun contentDescription(tag: String): String =
        composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString()

    private companion object {
        const val ACTION_TAG = "action"
        const val COMPONENT_TAG = "component"
        const val FRAME_TAG = "frame"
        const val ORB_TAG = "orb"
        const val TEXT_TAG = "text"
    }
}

private fun approvalAction() =
    ActionCardData(
        title = "Envoyer le message",
        summary = "Le contenu sera partagé avec une destination distante.",
        risk = ActionRisk.R3,
        origin = "Conversation",
        state = ActionCardState.BiometricRequired,
    )

private data class VisualStats(
    val meanLuminance: Double,
    val luminanceRange: Int,
    val quantizedColorCount: Int,
    val bluePixelCount: Int,
    val violetPixelCount: Int,
    val bandSpread: Double,
    val edgeToCenterDelta: Double,
)

private fun ImageBitmap.visualStats(): VisualStats {
    val bitmap = asAndroidBitmap()
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val luminances = IntArray(pixels.size)
    val colors = HashSet<Int>()
    var blueCount = 0
    var violetCount = 0
    pixels.forEachIndexed { index, pixel ->
        val red = AndroidColor.red(pixel)
        val green = AndroidColor.green(pixel)
        val blue = AndroidColor.blue(pixel)
        luminances[index] = luminance(red, green, blue)
        colors += ((red / 16) shl 8) or ((green / 16) shl 4) or (blue / 16)
        if (blue > red + 18 && blue > green + 10) blueCount++
        if (blue > green + 18 && red > green + 5) violetCount++
    }
    val top = bitmap.bandMean(luminances, 0, bitmap.height / 4)
    val middle = bitmap.bandMean(luminances, bitmap.height * 3 / 8, bitmap.height * 5 / 8)
    val bottom = bitmap.bandMean(luminances, bitmap.height * 3 / 4, bitmap.height)
    return VisualStats(
        meanLuminance = luminances.average(),
        luminanceRange = (luminances.maxOrNull() ?: 0) - (luminances.minOrNull() ?: 0),
        quantizedColorCount = colors.size,
        bluePixelCount = blueCount,
        violetPixelCount = violetCount,
        bandSpread = maxOf(top, middle, bottom) - minOf(top, middle, bottom),
        edgeToCenterDelta = bitmap.edgeToCenterDelta(luminances),
    )
}

private fun Bitmap.bandMean(
    luminances: IntArray,
    startY: Int,
    endY: Int,
): Double {
    var sum = 0L
    var count = 0
    for (y in startY until endY.coerceAtMost(height)) {
        for (x in 0 until width) {
            sum += luminances[y * width + x]
            count++
        }
    }
    return if (count == 0) 0.0 else sum.toDouble() / count
}

private fun Bitmap.edgeToCenterDelta(luminances: IntArray): Double {
    val edge = ArrayList<Int>()
    val center = ArrayList<Int>()
    for (y in 0 until height) {
        for (x in 0 until width) {
            val value = luminances[y * width + x]
            if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) edge += value
            if (x in width / 3 until width * 2 / 3 && y in height / 3 until height * 2 / 3) center += value
        }
    }
    return abs(edge.average() - center.average())
}

private fun luminance(
    red: Int,
    green: Int,
    blue: Int,
): Int = (red * 2126 + green * 7152 + blue * 722) / 10_000
