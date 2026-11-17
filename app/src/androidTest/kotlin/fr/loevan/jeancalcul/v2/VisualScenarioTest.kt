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
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
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
                            val newChat = device.wait(
                                Until.findObject(By.desc(context.getString(R.string.action_new_conversation))), 10000,
                            ) ?: throw AssertionError("New conversation button is not accessible")
                            newChat.click()
                            assertNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000))
                            assertTrue(device.wait(Until.gone(By.textContains("Synthetic local reply")), 10000))
                        }
                    }
                    "improvements" -> {
                        activity.onActivity { it.startActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)) }
                        assertTrue(device.wait(Until.hasObject(By.text("Fine-grained GitHub token")), 15000))
                    }
                    "tools" -> {
                        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                        device.wait(Until.findObject(By.text(context.getString(R.string.route_tools))), 10000).click()
                        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.capability_current_device))), 10000))
                    }
                    "settings", "providers", "appearance", "notifications" -> {
                        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                        device.wait(Until.findObject(By.desc(context.getString(R.string.route_settings))), 10000).click()
                        assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.ui_model_provider_e8c7f5))), 10000))
                        if (scenario == "notifications") {
                            val target = By.text("Notifications intelligentes")
                            var attempts = 0
                            while (!device.hasObject(target) && attempts++ < 15) {
                                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                                    device.displayWidth / 2, device.displayHeight / 3, 26)
                                device.waitForIdle(750)
                            }
                            val item = device.wait(Until.findObject(target), 10000)
                                ?: throw AssertionError("Notification preferences are not reachable")
                            item.click()
                            assertTrue("Offline model install entry should appear",
                                device.wait(Until.hasObject(By.text("Installer le modèle")), 10000))
                            assertTrue("Manual input should be visible",
                                device.wait(Until.hasObject(By.text("Titre et contenu d'une notification")), 10000))
                            val safeMode = By.text("2 · Prudent")
                            val personalMode = By.text("3 · Personnalisé")
                            var modeScrolls = 0
                            var prudentSeen = false
                            var personalSeen = false
                            while (modeScrolls++ < 12) {
                                prudentSeen = prudentSeen || device.hasObject(safeMode)
                                personalSeen = personalSeen || device.hasObject(personalMode)
                                if (prudentSeen && personalSeen) break
                                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                                    device.displayWidth / 2, device.displayHeight / 3, 26)
                                device.waitForIdle(750)
                            }
                            assertTrue("Prudent mode must be reachable", prudentSeen)
                            assertTrue("Personalized mode must be reachable", personalSeen)
                        }
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
                    "brief" -> {
                        // Discovery controls are present before swiping.
                        assertTrue("Page indicator exposes the second page",
                            device.wait(Until.hasObject(By.desc("Chat, page 1 sur 2")), 8000))
                        assertTrue("No right-edge peek affordance",
                            !device.hasObject(By.desc("Ouvrir mon briefing, page à droite")))
                        // Native mirrored drag: home -> left reveals the full-screen COUI briefing.
                        val midY = device.displayHeight / 2
                        device.swipe(device.displayWidth * 4 / 5, midY,
                            device.displayWidth / 6, midY, 30)
                        assertTrue("Left swipe must reveal Now Brief",
                            device.wait(Until.hasObject(By.text("Mon briefing")), 12000))
                        assertTrue("Settings card must not appear in briefing",
                            !device.hasObject(By.text("Notifications intelligentes")))
                        assertTrue(device.hasObject(By.text("Votre tranquillité")))
                        assertTrue("Single halo ambient, no cycle labels",
                            !device.hasObject(By.textContains("Ambiance ")))
                        // Return to the conversation with the same spring gesture.
                        device.swipe(device.displayWidth / 6, midY,
                            device.displayWidth * 4 / 5, midY, 30)
                        assertTrue("Right swipe must return to conversation",
                            device.wait(Until.gone(By.text("Mon briefing")), 12000))
                        // Re-open using the accessible button; screenshot shows the briefing.
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        assertTrue(device.wait(Until.hasObject(By.text("Mon briefing")), 12000))
                        assertTrue("Brief stays accessible on second opening",
                            device.hasObject(By.text("Votre tranquillité")))
                    }
                    "brief_notifications" -> {
                        val repo = NotificationHistoryRepository(context)
                        val now = System.currentTimeMillis()
                        repo.record("brief-test-1", "com.android.settings",
                            "Test de sécurité", "Réglage de test", null, now)
                        repo.record("brief-test-2", "com.android.settings",
                            "Mise à jour", "Version de test", null, now - 100)
                        repo.record("brief-test-3", "com.android.systemui",
                            "Information", "Alerte synthétique", null, now - 200)
                        NotificationActivityFeed.notifyChanged()
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        assertTrue("Three notifications must be visible without classification",
                            device.wait(Until.hasObject(By.textContains("3 affichées")), 15000))
                        val app = device.wait(Until.findObject(By.textContains("2 notifications")), 10000)
                            ?: throw AssertionError("App grouping did not aggregate the two records")
                        app.click()
                        assertTrue("Expanded group exposes the actual notification title",
                            device.wait(Until.hasObject(By.text("Test de sécurité")), 10000))
                        val remove = device.wait(Until.findObject(By.desc("Retirer du briefing")), 8000)
                            ?: throw AssertionError("Explicit dismissal control missing")
                        remove.click()
                        assertTrue("Only one entry should be removed",
                            device.wait(Until.hasObject(By.textContains("2 affichées")), 8000))
                    }
                    "menu" -> {
                        assertTrue("New conversation is a direct toolbar action",
                            device.hasObject(By.desc(context.getString(R.string.action_new_conversation))))
                        assertTrue("Three-dot overflow is absent",
                            !device.hasObject(By.desc(context.getString(R.string.action_more))))
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
        } finally { fixture?.close() }
    }
}
