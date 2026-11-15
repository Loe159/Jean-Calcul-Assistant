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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Before fun prepareSyntheticPermissions() = grantSyntheticNotificationPermission()

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

    @Test fun ownAppPowerContextCapturesTheScreenAndProtectsCredentialPages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { activity ->
            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 30000))
            var capture: io.github.mangi.eta.agent.development.OwnAppScreenContext.Capture? = null
            activity.onActivity {
                assertNull(io.github.mangi.eta.agent.development.OwnAppScreenContext.capture(false, false))
                capture = io.github.mangi.eta.agent.development.OwnAppScreenContext.capture(true, true)
            }
            assertNotNull(capture)
            assertEquals("Home", capture!!.screen)
            assertNotNull("Our own app must provide a fallback screenshot", capture!!.bitmap)
            val entry = io.github.mangi.eta.agent.voice.EtaAssistantScreenContext(0, emptyList())
            try {
                entry.acceptOwnApp(capture!!)
                entry.acceptScreenshot(null)
                val snapshot = kotlinx.coroutines.runBlocking { entry.snapshot() }
                assertNotNull(snapshot.image)
                assertEquals("Home", snapshot.appScreen)
                assertTrue(snapshot.text.contains("Jean-Calcul"))
                val config = io.github.mangi.eta.agent.model.AgentModelClient.loadConfig()
                val request = io.github.mangi.eta.agent.runtime.AgentRuntimeWire.RunRequest(
                    runId = "synthetic-improvement", prompt = "There is a bug here", config = config,
                    images = emptyList(), appDevelopmentScreen = snapshot.appScreen,
                )
                val restored = io.github.mangi.eta.agent.runtime.AgentRuntimeWire.runRequestFromBundle(
                    io.github.mangi.eta.agent.runtime.AgentRuntimeWire.toLegacyBundle(request),
                )
                assertEquals("Home", restored.appDevelopmentScreen)
            } finally { entry.close() }
            activity.onActivity { it.startActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)) }
            assertTrue(device.wait(Until.hasObject(By.text("Fine-grained GitHub token")), 15000))
            activity.onActivity {
                val private = io.github.mangi.eta.agent.development.OwnAppScreenContext.capture(true, true)
                assertNotNull(private)
                assertTrue(private!!.sensitive)
                assertNull(private.bitmap)
                assertTrue(private.text.contains("withheld"))
            }
        }
    }
}
