package fr.loevan.jeancalcul.v2

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test

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
        rule.onNodeWithTag("empty").assertExists()
    }
    @Test fun settingsScreen() {
        rule.onNodeWithTag("settings").performClick()
        rule.onNodeWithTag("composer").assertDoesNotExist()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithTag("composer").assertExists()
    }
}
