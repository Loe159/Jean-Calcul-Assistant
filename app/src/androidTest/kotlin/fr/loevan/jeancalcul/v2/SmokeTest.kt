package fr.loevan.jeancalcul.v2

import android.view.WindowInsets
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun initialState() {
        rule.onNodeWithTag("empty").assertExists()
        rule.onNodeWithTag("composer").assertExists()
    }
    @Test fun composeAndReset() {
        rule.onNodeWithTag("composer").performTextInput("Bonjour V2")
        rule.onNodeWithTag("send").performClick()
        rule.onNodeWithTag("messages").assertExists()
        rule.onNodeWithTag("reset").performClick()
        rule.onNodeWithText("Effacer").performClick()
        rule.onNodeWithTag("empty").assertExists()
    }
    @Test fun resetCanBeCancelledAndDraftSurvivesNavigation() {
        rule.onNodeWithTag("send").assertIsNotEnabled()
        rule.onNodeWithTag("composer").performTextInput("Brouillon")
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithText("Préférences").assertIsDisplayed()
        // An idle accessibility tree does not mean that a pending IME show/hide finished.
        rule.waitUntil(10_000) {
            var hidden = false
            rule.runOnUiThread {
                hidden = rule.activity.window.decorView.rootWindowInsets
                    ?.isVisible(WindowInsets.Type.ime()) == false
            }
            hidden
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithText("Brouillon").assertExists()
        rule.onNodeWithTag("reset").performClick()
        rule.onNodeWithText("Annuler").performClick()
        rule.onNodeWithText("Brouillon").assertExists()
    }
    @Test fun nativeMenuGalleryAndDialog() {
        rule.onNodeWithTag("options").performClick()
        rule.onNodeWithText("Galerie COUI").performClick()
        rule.onNodeWithTag("design_gallery").assertExists()
        rule.onNodeWithTag("gallery_dialog").performClick()
        rule.onNodeWithText("Dialogue COUI").assertExists()
        rule.onNodeWithText("Fermer").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Fermer").fetchSemanticsNodes().isEmpty() }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithText("Préférences").assertExists()
    }
    @Test fun settingsScreen() {
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("composer").assertDoesNotExist()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithTag("composer").assertExists()
    }
    @Test fun multilineComposerDoesNotPanHeader() {
        val top = rule.onNodeWithTag("header").fetchSemanticsNode().positionOnScreen.y
        rule.onNodeWithTag("composer").performTextInput((1..8).joinToString("\n") { "Ligne synthétique $it" })
        rule.waitUntil(10_000) {
            var shown = false
            rule.runOnUiThread {
                shown = rule.activity.window.decorView.rootWindowInsets
                    ?.isVisible(WindowInsets.Type.ime()) == true
            }
            shown
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        rule.onNodeWithTag("send").assertIsDisplayed()
        assertEquals("Multiline input must keep the header anchored", top,
            rule.onNodeWithTag("header").fetchSemanticsNode().positionOnScreen.y, 1f)
    }
}
