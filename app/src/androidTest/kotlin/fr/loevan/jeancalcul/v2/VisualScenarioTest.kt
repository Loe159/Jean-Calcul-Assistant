package fr.loevan.jeancalcul.v2

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
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.Assert.assertTrue
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
        when (scenario) {
            "empty", "dark", "small_screen", "large_text", "reduced_motion" -> Unit
            "message", "reset" -> {
                rule.onNodeWithTag("composer").performTextInput("Bonjour V2 — test synthétique")
                rule.onNodeWithTag("send").assertIsEnabled().performClick()
                rule.onNodeWithText("Bonjour V2 — test synthétique").assertIsDisplayed()
                // Android Back belongs to the system, outside the Compose semantics tree.
                device.pressBack()
                rule.waitForIdle()
                if (scenario == "reset") {
                    rule.onNodeWithTag("reset").performClick()
                    rule.onNodeWithTag("empty").assertIsDisplayed()
                    rule.onNodeWithText("Bonjour V2 — test synthétique").assertDoesNotExist()
                }
            }
            "settings" -> {
                rule.onNodeWithTag("settings").performClick()
                rule.onNodeWithText("Préférences").assertIsDisplayed()
                rule.onNodeWithTag("composer").assertDoesNotExist()
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
        val directory = File(rule.activity.filesDir, "e2e").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$scenario.png")))
    }
}
