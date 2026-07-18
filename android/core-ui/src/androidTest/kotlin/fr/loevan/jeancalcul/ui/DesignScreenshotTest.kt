package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pixel-level screenshot goldens for the readable tonal fallback, independent from GPU blur. */
@RunWith(AndroidJUnit4::class)
class DesignScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun darkNoBlurFallbackMatchesGoldenSurfacePixel() {
        setFallbackContent(themeMode = ThemeMode.Dark)

        assertEquals(
            Color(0xFF181C1D).toArgb(),
            composeRule.onNodeWithTag(FALLBACK_TAG).captureToImage().centerPixel(),
        )
    }

    @Test
    fun lightNoBlurFallbackMatchesGoldenSurfacePixel() {
        setFallbackContent(themeMode = ThemeMode.Light)

        assertEquals(
            Color(0xFFF7F9FA).toArgb(),
            composeRule.onNodeWithTag(FALLBACK_TAG).captureToImage().centerPixel(),
        )
    }

    private fun setFallbackContent(themeMode: ThemeMode) {
        composeRule.setContent {
            fallbackGolden(themeMode = themeMode)
        }
    }

    private companion object {
        const val FALLBACK_TAG = "glass-fallback-golden"
    }
}

@Composable
private fun fallbackGolden(themeMode: ThemeMode) {
    jeanCalculTheme(
        themeMode = themeMode,
        visualEffects = VisualEffects(blurEnabled = false, shadersEnabled = false),
    ) {
        GlassSurface(
            modifier = Modifier.size(120.dp).testTag("glass-fallback-golden"),
            variant = GlassSurfaceVariant.FallbackOpaque,
            content = { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) },
        )
    }
}

private fun ImageBitmap.centerPixel(): Int {
    val bitmap = asAndroidBitmap()
    return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
}
