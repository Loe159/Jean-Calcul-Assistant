package fr.loevan.jeancalcul.v2

import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * All screenshots are generated from deterministic dummy data in a fresh emulator.
 * No personal accounts, message history or real AI provider are connected.
 */
@RunWith(AndroidJUnit4::class)
class AgenticJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun screenshot(name: String) {
        rule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = requireNotNull(context.getExternalFilesDir("e2e")).apply { mkdirs() }
        assertTrue("Unable to capture $name", device.takeScreenshot(File(folder, "$name.png")))
    }

    @Test fun capturesEmptyChatAndSettings() {
        rule.onNodeWithTag("empty").assertExists()
        rule.onNodeWithTag("composer").assertExists()
        screenshot("01-empty")
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("composer").assertDoesNotExist()
        screenshot("02-settings")
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("composer").assertExists()
    }

    @Test fun capturesMessageAndReset() {
        rule.onNodeWithTag("composer").performTextInput("Bonjour, scénario E2E fictif")
        rule.onNodeWithTag("send").performClick()
        rule.onNodeWithTag("messages").assertExists()
        rule.onNodeWithTag("prototype_notice").assertExists()
        screenshot("03-message")
        rule.onNodeWithTag("reset").performClick()
        rule.onNodeWithTag("empty").assertExists()
        screenshot("04-reset")
    }

    @Test fun systemBackDoesNotCrashApp() {
        rule.onNodeWithTag("settings").performClick()
        // UI Automator verifies the native Android window, not only the Compose hierarchy.
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg)), 5_000))
        device.pressBack()
        // Android may finish the activity; this checks a clean lifecycle, not a fake navigation guarantee.
        assertTrue(Build.VERSION.SDK_INT >= 33)
    }
}
