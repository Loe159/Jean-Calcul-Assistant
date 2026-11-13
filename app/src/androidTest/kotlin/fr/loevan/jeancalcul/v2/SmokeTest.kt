package fr.loevan.jeancalcul.v2

import android.view.WindowInsets
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class SmokeTest {
    @get:Rule(order = 0) val fixture = object : org.junit.rules.ExternalResource() {
        override fun before() {
            FileSessionStore(InstrumentationRegistry.getInstrumentation().targetContext).save(Session())
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun keystoreCredentialsAndHistoryRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = ProviderSettings(context)
        try {
            settings.save(ProviderConfig("https://localhost/v1", "synthetic", true), "synthetic-key-not-a-secret")
            assertEquals("synthetic-key-not-a-secret", ProviderSettings(context).credential())
            val xml = java.io.File(context.applicationInfo.dataDir, "shared_prefs/provider.xml").readText()
            org.junit.Assert.assertFalse(xml.contains("synthetic-key-not-a-secret"))
            settings.save(settings.config(), "")
            assertEquals("", settings.credential())
            val session = Session(messages = listOf(ChatMessage("user", "Persisté")), status = RunStatus.RUNNING)
            FileSessionStore(context).save(session)
            assertEquals(RunStatus.INTERRUPTED, ConversationRuntime(FileSessionStore(context)).session.value.status)
        } finally {
            context.getSharedPreferences("provider", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun historySurvivesRecreationAndFailureCanRetry() {
        rule.onNodeWithTag("composer").performTextInput("Persistance synthétique")
        rule.onNodeWithTag("send").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("retry").fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Persistance synthétique").assertExists()
        rule.onNodeWithTag("retry").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("retry").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, rule.onAllNodesWithText("Persistance synthétique").fetchSemanticsNodes().size)
    }
    @Test fun initialState() {
        rule.onNodeWithTag("empty").assertExists()
        rule.onNodeWithTag("composer").assertExists()
    }
    @Test fun composeAndReset() {
        rule.onNodeWithTag("composer").performTextInput("Bonjour V2")
        rule.onNodeWithTag("send").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("messages").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("reset").performClick()
        rule.onNodeWithText("Effacer").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("empty").fetchSemanticsNodes().isNotEmpty() }
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
        // UiDevice injects Back asynchronously; Compose idle alone can precede delivery.
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("composer").fetchSemanticsNodes().size == 1 }
        } catch (error: Throwable) {
            throw AssertionError("After system Back: finishing=${rule.activity.isFinishing}, destroyed=${rule.activity.isDestroyed}, lifecycle=${rule.activity.lifecycle.currentState}; ${rule.onRoot(useUnmergedTree = true).printToString()}", error)
        }
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
