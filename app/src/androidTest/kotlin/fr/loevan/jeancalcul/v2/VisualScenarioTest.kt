package fr.loevan.jeancalcul.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Only the local E2E runner opts into screenshot scenarios. No production hooks. */
class VisualScenarioTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun capture() {
        val args = InstrumentationRegistry.getArguments()
        val scenario = args.getString("scenario")
        assumeTrue("Opt in with -e scenario", scenario != null)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        rule.onNodeWithTag("empty").assertIsDisplayed()
        val headerTop = rule.onNodeWithTag("header").fetchSemanticsNode().positionOnScreen.y
        when (scenario) {
            "empty", "dark", "small_screen", "large_text", "reduced_motion" -> Unit
            "message", "reset" -> {
                rule.onNodeWithTag("composer").performTextInput("Bonjour V2 — test synthétique")
                rule.onNodeWithTag("send").assertIsEnabled().performClick()
                rule.waitUntil(5_000) { rule.onAllNodesWithTag("retry").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("Bonjour V2 — test synthétique").assertIsDisplayed()
                // Android Back belongs to the system, outside the Compose semantics tree.
                device.pressBack()
                rule.waitUntil(10_000) {
                    var hidden = false
                    rule.runOnUiThread {
                        hidden = rule.activity.window.decorView.rootWindowInsets
                            ?.isVisible(WindowInsets.Type.ime()) == false
                    }
                    hidden
                }
                rule.waitForIdle()
                rule.onNodeWithTag("navigation").assertIsDisplayed()
                if (scenario == "reset") {
                    rule.onNodeWithTag("reset").performClick()
                    rule.onNodeWithText("Effacer").performClick()
                    rule.waitUntil(5_000) { rule.onAllNodesWithTag("empty").fetchSemanticsNodes().isNotEmpty() }
                    rule.onNodeWithTag("empty").assertIsDisplayed()
                    rule.onNodeWithText("Bonjour V2 — test synthétique").assertDoesNotExist()
                }
            }
            "settings", "provider_dark", "provider_small_screen", "provider_large_text" -> {
                rule.onNodeWithTag("settings").performClick()
                rule.onNodeWithText("Préférences").assertIsDisplayed()
                rule.onNodeWithTag("composer").assertDoesNotExist()
                if (scenario != "settings") rule.onNodeWithTag("configure_provider").performClick()
            }
            "menu" -> rule.onNodeWithTag("options").performClick()
            "gallery", "dialog" -> {
                rule.onNodeWithTag("settings").performClick()
                rule.onNodeWithTag("gallery").performScrollTo().performClick()
                if (scenario == "dialog") rule.onNodeWithTag("gallery_dialog").performClick()
            }
            "keyboard" -> {
                rule.onNodeWithTag("composer").performClick().performTextInput("Clavier synthétique")
                rule.waitUntil(10_000) {
                    var visible = false
                    rule.runOnUiThread {
                        visible = rule.activity.window.decorView.rootWindowInsets
                            ?.isVisible(WindowInsets.Type.ime()) == true
                    }
                    visible
                }
                rule.onNodeWithTag("send").assertIsDisplayed()
                rule.onNodeWithTag("header").assertIsDisplayed()
            }
            "regression_fixture" -> {
                // Deliberate, test-only occlusion: native vision must flag the hidden composer.
                rule.activity.runOnUiThread {
                    rule.activity.setContentView(androidx.compose.ui.platform.ComposeView(rule.activity).apply {
                        setContent {
                            Box(Modifier.fillMaxSize()) {
                                V2App()
                                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .height(180.dp).background(Color.Red))
                            }
                        }
                    })
                }
            }
            else -> error("Unknown E2E scenario: $scenario")
        }
        rule.waitForIdle()
        device.waitForIdle()
        // Let system insets / IME animations settle before taking a full-device capture.
        android.os.SystemClock.sleep(600)
        // Compose idleness is not a GPU presentation fence. COUI records content
        // into graphics layers; wait for two submitted frames before reading pixels.
        if (scenario != "dialog") repeat(2) {
            val committed = CountDownLatch(1)
            rule.runOnUiThread {
                val view = rule.activity.window.decorView
                view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                view.postInvalidateOnAnimation()
            }
            assertTrue("No rendered frame committed before capture", committed.await(10, TimeUnit.SECONDS))
        }
        if (scenario == "keyboard") {
            assertEquals("Keyboard must not pan the header off screen", headerTop,
                rule.onNodeWithTag("header").fetchSemanticsNode().positionOnScreen.y, 1f)
        }
        val directory = File(rule.activity.filesDir, "e2e").apply { mkdirs() }
        val destination = File(directory, "$scenario.png")
        var captured = false
        // UiDevice 2.3.0 ignores Bitmap.compress's boolean result. Never accept
        // its false-positive success (observed as a zero-byte file on emulator).
        repeat(3) { attempt ->
            if (!captured) {
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (bitmap != null) {
                    val software = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    try {
                        if (software != null) {
                            val bytes = ByteArrayOutputStream()
                            if (software.compress(Bitmap.CompressFormat.PNG, 100, bytes)) {
                                val data = bytes.toByteArray()
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                                if (bounds.outWidth == software.width && bounds.outHeight == software.height) {
                                    val temporary = File(directory, "$scenario.tmp")
                                    FileOutputStream(temporary).use { stream ->
                                        stream.write(data)
                                        stream.fd.sync()
                                    }
                                    captured = temporary.renameTo(destination) && destination.length() > 0
                                }
                            }
                        }
                    } finally {
                        software?.recycle()
                        bitmap.recycle()
                    }
                }
                if (!captured && attempt < 2) android.os.SystemClock.sleep(500)
            }
        }
        assertTrue("No complete PNG after three bounded screenshot attempts: $scenario", captured)
    }
}
