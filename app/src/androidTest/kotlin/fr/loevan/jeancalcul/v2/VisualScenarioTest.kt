package fr.loevan.jeancalcul.v2

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisualScenarioTest {
    @Before fun prepareSyntheticPermissions() {
        if (!InstrumentationRegistry.getArguments().getString("scenario").orEmpty().startsWith("improvements_notifications")) {
            grantSyntheticNotificationPermission()
        }
    }

    @Test fun capture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = InstrumentationRegistry.getArguments().getString("scenario")
        assumeTrue("Visual scenarios require explicit opt-in", name != null)
        val scenario = name!!
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val fixture = if (scenario in setOf("message", "reset")) LocalVllmFixture(context) else null
        val improvements = if (scenario.startsWith("improvements_notifications") || scenario.endsWith("_notice"))
            io.github.mangi.eta.agent.development.DevelopmentStore(context).also {
                it.connect("synthetic-token-for-isolated-visual-test-123")
            } else null
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { activity ->
                assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 30000))
                try {
                when (scenario) {
                    "message", "reset" -> {
                        device.findObject(By.clazz("android.widget.EditText")).text = "Synthetic local request"
                        device.wait(Until.findObject(By.desc(context.getString(R.string.chat_send))), 10000).click()
                        assertTrue(device.wait(Until.hasObject(By.textContains("Synthetic local reply")), 30000))
                        activity.onActivity { current ->
                            current.window.insetsController?.hide(WindowInsets.Type.ime())
                            current.window.decorView.clearFocus()
                        }
                        device.waitForIdle()
                        assertTrue("Hiding the keyboard must keep the conversation visible",
                            device.wait(Until.hasObject(By.textContains("Synthetic local reply")), 10000))
                        if (scenario == "reset") {
                            device.findObject(By.desc(context.getString(R.string.action_more))).click()
                            device.wait(Until.findObject(By.text(context.getString(R.string.action_new_conversation))), 10000).click()
                            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000))
                            assertTrue(device.wait(Until.gone(By.textContains("Synthetic local reply")), 10000))
                        }
                    }
                    "improvements" -> {
                        activity.onActivity { it.startActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)) }
                        assertTrue(device.wait(Until.hasObject(By.text("Fine-grained GitHub token")), 15000))
                    }
                    "improvements_notifications", "improvements_notifications_dark",
                    "improvements_notifications_small_screen", "improvements_notifications_large_text" -> {
                        activity.onActivity { it.startActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)) }
                        assertTrue(device.wait(Until.hasObject(By.text("GitHub connected")), 15000))
                        val target = By.text("Allow notifications")
                        for (attempt in 0 until 4) {
                            if (device.hasObject(target)) break
                            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                                device.displayWidth / 2, device.displayHeight / 3, 25)
                        }
                        assertTrue("Notification preference must remain reachable", device.wait(Until.hasObject(target), 10000))
                    }
                    "improvement_question_notice", "improvement_finished_notice" -> {
                        val store = requireNotNull(improvements)
                        store.authorizeConversation("assistant-visual", "visual-key")
                        store.markRunUsed("visual-run")
                        if (scenario == "improvement_question_notice") {
                            store.setQuestionPending(true)
                            store.markQuestionRun("visual-run")
                        }
                        val handoff = io.github.mangi.eta.agent.runtime.AgentRuntimeWire.EntryHandoff(
                            id = "visual-key:visual-run",
                            source = io.github.mangi.eta.agent.runtime.AgentRuntimeWire.ETA_VOICE_HANDOFF_SOURCE,
                            payload = io.github.mangi.eta.agent.runtime.AgentExternalArchivePayload(
                                "Synthetic improvement", "visual-key", "Synthetic improvement").toJson(),
                        )
                        io.github.mangi.eta.agent.development.DevelopmentRunNotice.completed(context,
                            io.github.mangi.eta.agent.runtime.AgentRuntimeWire.RunRequest(
                                runId = "visual-run", prompt = "Synthetic improvement",
                                config = io.github.mangi.eta.agent.model.AgentModelClient.loadConfig(),
                                images = emptyList(), handoff = handoff,
                            ),
                            io.github.mangi.eta.agent.runtime.AgentRuntimeWire.RunResult("visual-run", true, "Synthetic result"),
                        )
                        assertTrue(device.openNotification())
                        val title = if (scenario == "improvement_question_notice")
                            "Jean-Calcul needs your answer" else "Jean-Calcul finished the improvement task"
                        assertTrue(device.wait(Until.hasObject(By.text(title)), 10000))
                    }
                    "tools" -> {
                        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                        device.wait(Until.findObject(By.text(context.getString(R.string.route_tools))), 10000).click()
                        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.capability_current_device))), 10000))
                    }
                    "settings", "providers", "appearance" -> {
                        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                        device.wait(Until.findObject(By.desc(context.getString(R.string.route_settings))), 10000).click()
                        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.ui_model_provider_e8c7f5))), 10000))
                        if (scenario == "providers") {
                            device.findObject(By.text(context.getString(R.string.ui_model_provider_e8c7f5))).click()
                            assertTrue(device.wait(Until.hasObject(By.text("ChatGPT / Codex")), 10000))
                            assertTrue(device.wait(Until.hasObject(By.text("vLLM local")), 10000))
                        }
                        if (scenario == "appearance") {
                            val target = By.text(context.getString(R.string.appearance_title))
                            var swipes = 0
                            while (swipes++ < 20) {
                                val bounds = device.findObject(target)?.visibleBounds
                                val safeTop = device.displayHeight / 4
                                val safeBottom = device.displayHeight * 3 / 4
                                if (bounds != null && bounds.height() > 0 && bounds.centerY() in safeTop..safeBottom) break
                                // A visible accessibility node can still be behind COUI's sticky header.
                                // Scroll it into the unobstructed viewport before tapping its real bounds.
                                val overshot = bounds != null && bounds.centerY() < safeTop
                                device.swipe(device.displayWidth / 2,
                                    if (overshot) device.displayHeight / 3 else device.displayHeight * 3 / 4,
                                    device.displayWidth / 2,
                                    if (overshot) device.displayHeight * 3 / 5 else device.displayHeight / 2, 30)
                                device.waitForIdle()
                            }
                            val appearance = device.wait(Until.findObject(target), 10000)
                                ?: throw AssertionError("Appearance preference must be reachable by scrolling")
                            assertTrue("Appearance preference must be clear of the sticky header before tapping",
                                appearance.visibleBounds.centerY() in device.displayHeight / 4..device.displayHeight * 3 / 4)
                            appearance.click()
                            assertTrue("Appearance navigation must show Theme mode",
                                device.wait(Until.hasObject(By.text(context.getString(R.string.appearance_theme_mode))), 10000))
                        }
                    }
                    "menu" -> {
                        device.findObject(By.desc(context.getString(R.string.action_more))).click()
                        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.action_new_conversation))), 10000))
                    }
                    "keyboard" -> {
                        device.findObject(By.clazz("android.widget.EditText")).apply { click(); text = "Synthetic multiline draft\nLine two" }
                        activity.onActivity { it.window.insetsController?.show(WindowInsets.Type.ime()) }
                        val deadline = SystemClock.uptimeMillis() + 10000
                        var imeVisible = false
                        while (!imeVisible && SystemClock.uptimeMillis() < deadline) {
                            activity.onActivity { imeVisible = it.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
                            if (!imeVisible) SystemClock.sleep(100)
                        }
                        assertTrue("The keyboard scenario requires a visible system IME", imeVisible)
                        val imePackage = android.provider.Settings.Secure.getString(
                            context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD,
                        )?.substringBefore('/') ?: error("The emulator must have a configured keyboard")
                        assertTrue("The keyboard must expose actual keys, not just reserve IME space",
                            device.wait(Until.hasObject(By.pkg(imePackage).descStartsWith("q")), 15000) ||
                                device.hasObject(By.pkg(imePackage).text("q")))
                    }
                    "regression_fixture" -> activity.onActivity { current ->
                        (current.window.decorView as ViewGroup).addView(View(current).apply { setBackgroundColor(Color.RED) }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500))
                    }
                    "empty", "dark", "small_screen", "large_text", "reduced_motion" -> Unit
                    else -> error("Unknown visual scenario: $scenario")
                }
                device.waitForIdle(3000)
                // Semantic destinations above are asserted; let their final compositor frame present.
                SystemClock.sleep(600)
                val directory = File(context.filesDir, "e2e").apply { mkdirs() }
                assertTrue("Screenshot must be a real display capture", device.takeScreenshot(File(directory, "$scenario.png")))
                } catch (failure: Throwable) {
                    val diagnostics = File(context.filesDir, "e2e").apply { mkdirs() }
                    runCatching { device.takeScreenshot(File(diagnostics, "failure-$scenario.png")) }
                    runCatching { device.dumpWindowHierarchy(File(diagnostics, "failure-$scenario.xml")) }
                    throw failure
                }
            }
        } finally {
            if (scenario.endsWith("_notice")) {
                device.pressBack()
                context.getSystemService(android.app.NotificationManager::class.java).cancel(6202)
            }
            fixture?.close()
            improvements?.disconnect()
        }
    }
}
