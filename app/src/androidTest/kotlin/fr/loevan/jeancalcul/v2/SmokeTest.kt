package fr.loevan.jeancalcul.v2

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Test fun localVllmWithoutKeyStreamsAndPersistsARealConversation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        LocalVllmFixture(context).use { fixture ->
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                val input = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 30000)
                assertNotNull("Chat input must be visible", input)
                input!!.text = "Synthetic local request"
                assertTrue(device.wait(Until.hasObject(By.desc(context.getString(R.string.chat_send))), 10000))
                device.findObject(By.desc(context.getString(R.string.chat_send))).click()
                assertTrue("Local server response must be rendered", device.wait(Until.hasObject(By.textContains("Synthetic local reply")), 30000))
                assertTrue("The real HTTP transport must have run", fixture.requests.get() > 0)
            }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                assertTrue("Reply must survive activity recreation", device.wait(Until.hasObject(By.textContains("Synthetic local reply")), 15000))
            }
        }
    }
    @Test fun settingsShowOnlyTheSelectedConversationProviders() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            assertTrue(device.wait(Until.hasObject(By.desc(context.getString(R.string.action_conversation_history))), 30000))
            device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
            val settings = device.wait(Until.findObject(By.desc(context.getString(R.string.route_settings))), 10000)
            assertNotNull(settings); settings!!.click()
            val providers = device.wait(Until.findObject(By.text(context.getString(R.string.ui_model_provider_e8c7f5))), 10000)
            assertNotNull(providers); providers!!.click()
            assertTrue(device.wait(Until.hasObject(By.text("ChatGPT / Codex")), 10000))
            assertTrue(device.wait(Until.hasObject(By.text("vLLM local")), 10000))
            assertFalse(device.hasObject(By.text("Anthropic")))
            assertFalse(device.hasObject(By.text("Grok")))
        }
    }
}
