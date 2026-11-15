package fr.loevan.jeancalcul.v2

import android.content.Intent
import android.graphics.Color
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
    @Before fun prepareSyntheticPermissions() = grantSyntheticNotificationPermission()

    @Test fun capture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val name = InstrumentationRegistry.getArguments().getString("scenario")
        assumeTrue("Visual scenarios require explicit opt-in", name != null)
        val scenario = name!!
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val fixture = if (scenario in setOf("message", "reset")) LocalVllmFixture(context) else null
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
                    "tools" -> {
                        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                        device.wait(Until.findObject(By.text(context.getString(R.string.route_tools))), 10000).click()
                    }
                    "settings", "providers", "appearance" -> {
                        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                        device.wait(Until.findObject(By.desc(context.getString(R.string.route_settings))), 10000).click()
                        if (scenario == "providers") device.wait(Until.findObject(By.text(context.getString(R.string.ui_model_provider_e8c7f5))),10000).click()
                        if (scenario == "appearance") {
                            val scrollable = device.findObject(By.scrollable(true))
                            scrollable?.scrollUntil(androidx.test.uiautomator.Direction.DOWN, Until.findObject(By.text(context.getString(R.string.appearance_title))))
                            device.wait(Until.findObject(By.text(context.getString(R.string.appearance_title))),10000).click()
                        }
                    }
                    "menu" -> device.findObject(By.desc(context.getString(R.string.action_more))).click()
                    "keyboard" -> device.findObject(By.clazz("android.widget.EditText")).apply { click(); text = "Synthetic multiline draft\nLine two" }
                    "regression_fixture" -> activity.onActivity { current ->
                        (current.window.decorView as ViewGroup).addView(View(current).apply { setBackgroundColor(Color.RED) }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500))
                    }
                    "empty", "dark", "small_screen", "large_text", "reduced_motion" -> Unit
                    else -> error("Unknown visual scenario: $scenario")
                }
                device.waitForIdle(3000)
                val directory = File(context.filesDir, "e2e").apply { mkdirs() }
                assertTrue("Screenshot must be a real display capture", device.takeScreenshot(File(directory, "$scenario.png")))
                } catch (failure: Throwable) {
                    val diagnostics = File(context.filesDir, "e2e").apply { mkdirs() }
                    runCatching { device.takeScreenshot(File(diagnostics, "failure-$scenario.png")) }
                    runCatching { device.dumpWindowHierarchy(File(diagnostics, "failure-$scenario.xml")) }
                    throw failure
                }
            }
        } finally { fixture?.close() }
    }
}
