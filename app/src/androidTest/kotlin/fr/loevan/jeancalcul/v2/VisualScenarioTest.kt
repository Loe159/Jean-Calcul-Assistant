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
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.MainActivity
import io.github.mangi.eta.data.repository.NotificationHistoryRepository
import io.github.mangi.eta.agent.device.notifications.NotificationActivityFeed
import io.github.mangi.eta.agent.device.notifications.NotificationFilterAction
import io.github.mangi.eta.agent.device.notifications.NotificationFilterMode
import io.github.mangi.eta.agent.device.notifications.NotificationFilterSettings
import org.junit.Assert.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisualScenarioTest {
    private fun openNotificationSettings(device: UiDevice, context: android.content.Context) {
        device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
        device.wait(Until.findObject(By.desc(context.getString(R.string.route_settings))), 10000).click()
        val target = By.text("Notifications intelligentes")
        // A row can be in the accessibility tree while hidden under COUI's collapsing header,
        // and long swipes overshoot it. Scan with short steps, downwards then back up, and only
        // tap a row whose centre is inside the unobstructed middle of the screen.
        val safeTop = device.displayHeight / 4
        val safeBottom = device.displayHeight * 3 / 4
        fun visibleTarget() = device.findObject(target)?.takeIf { row ->
            row.visibleBounds.height() > 0 && row.visibleBounds.centerY() in safeTop..safeBottom
        }
        var item = device.wait(Until.findObject(target), 3000)?.let { visibleTarget() }
        for (reverse in listOf(false, true)) {
            if (item != null) break
            for (step in 0 until 24) {
                val startY = if (reverse) device.displayHeight / 4 else device.displayHeight * 3 / 4
                device.swipe(device.displayWidth / 2, startY,
                    device.displayWidth / 2, device.displayHeight / 2, 30)
                device.waitForIdle(750)
                item = visibleTarget()
                if (item != null) break
            }
        }
        (item ?: throw AssertionError("Notification preferences are not reachable")).click()
    }

    private fun jumpToBriefNotifications(device: UiDevice) {
        val shortcut = device.wait(Until.findObject(By.text("Voir les notifications")), 10000)
            ?: throw AssertionError("Missing shortcut to the Notifications card")
        shortcut.click()
    }

    // Some devices deliver Compose accessibility updates late; drop UiAutomation's node cache so
    // lookups see the screen as drawn rather than a previous frame of the hierarchy.
    private fun freshObject(device: UiDevice, selector: BySelector): UiObject2? {
        if (android.os.Build.VERSION.SDK_INT >= 34) InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
        return device.findObject(selector)
    }

    private fun findBriefControl(device: UiDevice, selector: BySelector): UiObject2 {
        // The first drags can be consumed by the collapsing sky header, so allow more of them
        // and check once more after the last one.
        repeat(17) { attempt ->
            device.waitForIdle(500)
            val row = freshObject(device, selector)
            val bounds = row?.visibleBounds
            if (row != null && bounds != null && bounds.height() > 0 &&
                bounds.centerY() in device.displayHeight / 5..device.displayHeight * 4 / 5
            ) return row
            if (attempt == 16) return@repeat
            val start = if (bounds != null && bounds.centerY() < device.displayHeight / 5) {
                device.displayHeight / 3
            } else device.displayHeight * 3 / 4
            device.swipe(device.displayWidth / 2, start, device.displayWidth / 2, device.displayHeight / 2, 26)
        }
        throw AssertionError("Brief control is not reachable: $selector")
    }

    private fun openBriefNotificationList(device: UiDevice) {
        findBriefControl(device, By.textStartsWith("Tout voir")).click()
        assertTrue("The Notifications page opens in the briefing pane",
            device.wait(Until.hasObject(By.text("Regrouper par application")), 8000))
    }

    private fun runFoodScenario(
        device: UiDevice,
        context: android.content.Context,
        activity: ActivityScenario<MainActivity>,
        fixture: LocalFoodFixture,
    ) {
        fun text(id: Int) = By.text(context.getString(id))
        fun find(selector: BySelector, reverseFirst: Boolean = false): UiObject2 {
            val middle = device.displayHeight / 2
            val x = device.displayWidth / 2
            for (reverse in listOf(reverseFirst, !reverseFirst)) {
                repeat(14) {
                    val row = freshObject(device, selector)
                    if (row != null && row.visibleBounds.centerY() in middle / 3..middle * 5 / 3 &&
                        row.visibleBounds.height() > 0) return row
                    val start = if (reverse) middle * 2 / 3 else middle * 3 / 2
                    device.swipe(x, start, x, middle, 22)
                    device.waitForIdle(350)
                }
            }
            throw AssertionError("Cuisine control is not reachable: $selector")
        }
        fun click(selector: BySelector, reverseFirst: Boolean = false) = find(selector, reverseFirst).click()
        fun await(selector: BySelector) = assertTrue(device.wait(Until.hasObject(selector), 10000))
        fun captureStage(name: String) {
            device.waitForIdle()
            SystemClock.sleep(250)
            val directory = File(context.filesDir, "e2e").apply { mkdirs() }
            assertTrue("Cuisine display capture $name", device.takeScreenshot(File(directory, "$name.png")))
        }

        val briefEntry = By.desc("Ouvrir mon briefing")
        val briefOpened = By.desc("Personnaliser le brief")
        fun openBrief() {
            device.wait(Until.findObject(briefEntry), 10000).click()
            assertTrue("Now Brief did not open", device.wait(Until.hasObject(briefOpened), 12000))
        }
        // Brief controls are reached with forward-only scrolls: a downward drag at the top
        // of the feed hands the gesture back to the home pager and closes the Brief.
        openBrief()
        findBriefControl(device, text(R.string.food_brief_open))
        captureStage("food_brief_recommendation")
        findBriefControl(device, text(R.string.food_brief_open)).click()
        await(text(R.string.food_start_cooking))
        click(text(R.string.food_portions))
        checkNotNull(device.wait(Until.findObject(By.text("4")), 5000)).click()
        find(By.textContains("300 g"))
        captureStage("food_recipe")
        assertTrue(fixture.completions.isEmpty())
        click(text(R.string.food_start_cooking), reverseFirst = true)
        await(By.text("Faire chauffer l’eau"))
        click(By.textStartsWith("Lancer "))
        device.wait(Until.findObject(text(R.string.food_next_step_button)), 5000).click()
        await(By.text("Cuire le riz"))
        click(By.textStartsWith("Lancer "))
        val repo = (context.applicationContext as io.github.mangi.eta.EtaApp).food.repository
        val sessionId = runBlocking {
            kotlinx.coroutines.withTimeout(5_000) { repo.activeSessions().first { it.isNotEmpty() }.single().id }
        }
        val timerCount = runBlocking {
            kotlinx.coroutines.withTimeout(5_000) { repo.session(sessionId).first { (it?.timers?.size ?: 0) >= 2 }?.timers?.size }
        }
        assertEquals(2, timerCount)
        await(By.desc(context.getString(R.string.food_timer_count, 2)))
        captureStage("food_timers")
        assertTrue(fixture.completions.isEmpty())
        // The active session leads the Brief feed; capture it, then resume from the card.
        // Never back out of the app: stop at the Brief or reopen it from home.
        for (attempt in 0 until 3) {
            if (device.hasObject(briefOpened)) break
            if (device.hasObject(briefEntry)) { openBrief(); break }
            device.pressBack()
            device.waitForIdle(500)
            assertEquals("Back navigation left the app", context.packageName, device.currentPackageName)
        }
        assertTrue("Now Brief is not shown", device.hasObject(briefOpened))
        findBriefControl(device, text(R.string.food_brief_resume))
        captureStage("food_brief_active")
        findBriefControl(device, text(R.string.food_brief_resume)).click()
        await(By.text("Cuire le riz"))
        activity.recreate()
        await(By.text("Cuire le riz"))
        device.wait(Until.findObject(text(R.string.food_finish_short)), 5000).click()
        find(text(R.string.food_consumed))
        captureStage("food_summary")
        assertTrue(fixture.completions.isEmpty())
        click(text(R.string.food_stock_update))
        await(text(R.string.food_succeeded))
        val completion = fixture.completions.single()
        assertEquals(4, completion.servingsMade)
        assertEquals(300.0, completion.consumptions.single().quantity.amount, 0.0)
        assertEquals(2, completion.session.timers.size)
        assertTrue(completion.session.timers.all { it.state == io.github.mangi.eta.food.TimerState.CANCELLED })
        assertTrue(fixture.feedback.any { it.action == io.github.mangi.eta.food.BriefFeedbackAction.OPENED })
        assertTrue(fixture.feedback.any { it.action == io.github.mangi.eta.food.BriefFeedbackAction.COOKED })
    }

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
        val foodFixture = if (scenario == "food_cooking") LocalFoodFixture(context) else null
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
                    "improvements_notifications", "improvements_notifications_dark",
                    "improvements_notifications_small_screen", "improvements_notifications_large_text" -> {
                        activity.onActivity { it.startActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_APP_IMPROVEMENTS)) }
                        assertTrue(device.wait(Until.hasObject(By.text("Describe a bug or feature")), 15000))
                        val connected = By.text("GitHub connected")
                        val target = By.text("Allow notifications")
                        var connectedSeen = false
                        var targetSeen = false
                        for (attempt in 0 until 36) {
                            connectedSeen = connectedSeen || device.hasObject(connected)
                            targetSeen = targetSeen || device.hasObject(target)
                            if (connectedSeen && targetSeen) break
                            device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3,
                                device.displayWidth / 2, device.displayHeight / 2, 30)
                            device.waitForIdle(750)
                        }
                        assertTrue("GitHub connection status must remain reachable", connectedSeen)
                        assertTrue("Notification preference must remain reachable", targetSeen)
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
                    "settings", "providers", "appearance", "notifications", "notifications_modes",
                    "notifications_large_text" -> {
                        if (scenario in setOf("notifications", "notifications_modes", "notifications_large_text")) {
                            openNotificationSettings(device, context)
                            assertTrue("The Android removal consent must be reachable",
                                device.wait(Until.hasObject(By.text("Retrait automatique")), 10000))
                            assertFalse("Intermediate observation modes must be hidden",
                                device.hasObject(By.text("Prudent")))
                            assertFalse("Manual notification simulator must be removed",
                                device.hasObject(By.text("Tester une notification")))
                        } else {
                            device.findObject(By.desc(context.getString(R.string.action_conversation_history))).click()
                            device.wait(Until.findObject(By.desc(context.getString(R.string.route_settings))), 10000).click()
                            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.ui_model_provider_e8c7f5))), 10000))
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
                    "brief", "brief_dark", "brief_large_text" -> {
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
                            device.wait(Until.hasObject(By.desc("Personnaliser le brief")), 12000))
                        assertTrue("Settings card must not appear in briefing",
                            !device.hasObject(By.text("Notifications intelligentes")))
                        assertFalse("Filtering settings are not part of the briefing",
                            device.hasObject(By.text("Filtrage des notifications")))
                        assertFalse("The sky has no cycle labels",
                            device.hasObject(By.textContains("Ambiance ")))
                        assertFalse("The introductory assistant badge is absent",
                            device.hasObject(By.text("Assistant à l’écoute")))
                        // Return to the conversation with the same spring gesture.
                        device.swipe(device.displayWidth / 6, midY,
                            device.displayWidth * 4 / 5, midY, 30)
                        assertTrue("Right swipe must return to conversation",
                            device.wait(Until.gone(By.desc("Personnaliser le brief")), 12000))
                        // Re-open using the accessible button; screenshot shows the briefing.
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        assertTrue(device.wait(Until.hasObject(By.desc("Personnaliser le brief")), 12000))
                        assertTrue("Brief stays accessible on second opening",
                            device.hasObject(By.desc("Personnaliser le brief")))
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
                        jumpToBriefNotifications(device)
                        assertTrue("Unclassified notifications are counted in the Today preview",
                            device.wait(Until.hasObject(By.text("3 Aujourd’hui")), 5000))
                        openBriefNotificationList(device)
                        assertTrue("The default list retains its priority sections",
                            device.wait(Until.hasObject(By.text("À voir aujourd'hui")), 6000))
                        device.wait(Until.findObject(By.text("Maintenant")), 5000).click()
                        assertTrue("The Now filter shows an empty list when every copy is unclassified",
                            device.wait(Until.hasObject(By.text("Aucune notification récente")), 6000))
                        device.wait(Until.findObject(By.text("Tout")), 5000).click()
                        val groupSwitch = By.text("Regrouper par application")
                        device.wait(Until.findObject(groupSwitch), 5000).click()
                        assertTrue("The app group has the exact count and starts collapsed",
                            device.wait(Until.hasObject(By.text("2 notifications")), 6000) &&
                                !device.hasObject(By.text("Test de sécurité")))
                        device.wait(Until.findObject(groupSwitch), 5000).click()
                        val row = findBriefControl(device, By.text("Test de sécurité"))
                        val bounds = row.visibleBounds
                        val remove = device.findObjects(By.desc("Actions pour cette notification"))
                            .firstOrNull { it.visibleBounds.centerY() in bounds.top - 48..bounds.bottom + 48 }
                            ?: throw AssertionError("Missing action menu for the synthetic row")
                        remove.click()
                        device.wait(Until.findObject(By.text("Archiver la copie")), 5000).click()
                        assertTrue("The archived row leaves the list",
                            device.wait(Until.gone(By.text("Test de sécurité")), 8000))
                        assertEquals(2, repo.recent(100).size)
                        assertEquals("brief-test-1", repo.archived(100).single().key)

                        device.pressBack()
                        assertTrue("System back returns to the card with refreshed counters",
                            device.wait(Until.hasObject(By.text("2 Aujourd’hui")), 8000))
                    }
                    "brief_archive", "brief_archive_dark", "brief_archive_large_text" -> {
                        val repo = NotificationHistoryRepository(context)
                        val now = System.currentTimeMillis()
                        repo.record("archive-synthetic", "com.android.settings",
                            "Notification archivée", "Archive consultable dans les paramètres", null, now)
                        NotificationActivityFeed.notifyChanged()
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        jumpToBriefNotifications(device)
                        openBriefNotificationList(device)
                        findBriefControl(device, By.desc("Actions pour cette notification")).click()
                        device.wait(Until.findObject(By.text("Archiver la copie")), 5000).click()
                        assertTrue(device.wait(Until.gone(By.text("Notification archivée")), 8000))
                        device.pressBack()
                        assertTrue(device.wait(Until.gone(By.text("Regrouper par application")), 5000))
                        device.pressBack()
                        assertTrue(device.wait(Until.hasObject(By.desc("Ouvrir mon briefing")), 8000))
                        openNotificationSettings(device, context)
                        val archiveLink = By.text("Archives du briefing")
                        var attempts = 0
                        while (!device.hasObject(archiveLink) && attempts++ < 8) {
                            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                                device.displayWidth / 2, device.displayHeight / 2, 32)
                            device.waitForIdle(750)
                        }
                        val link = device.wait(Until.findObject(archiveLink), 5000)
                            ?: throw AssertionError("Archive entry must be reachable in settings")
                        link.click()
                        val archivedTitle = By.text("Notification archivée")
                        val archivedRow = device.wait(Until.findObject(archivedTitle), 10000)
                            ?: throw AssertionError("Archived copy must be readable in settings")
                        archivedRow.click()
                        assertTrue("Archived copy can be restored without a confirmation dialog",
                            device.wait(Until.hasObject(By.text("Restaurer dans Mon briefing")), 5000))
                    }
                    "brief_archive_restore" -> {
                        val repo = NotificationHistoryRepository(context)
                        val now = System.currentTimeMillis()
                        repo.record("restore-synthetic", "com.android.settings",
                            "Archive à restaurer", "Contenu local conservé", null, now)
                        NotificationActivityFeed.notifyChanged()
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        jumpToBriefNotifications(device)
                        val thread = By.textContains("Archive à restaurer")
                        val y = findBriefControl(device, thread).visibleBounds.centerY()
                        device.swipe(device.displayWidth / 6, y, device.displayWidth * 5 / 6, y, 30)
                        device.wait(Until.findObject(By.text("Annuler")), 5000).click()
                        assertTrue("The snackbar restores the archived thread",
                            device.wait(Until.hasObject(thread), 8000))
                        openBriefNotificationList(device)
                        findBriefControl(device, By.desc("Actions pour cette notification")).click()
                        device.wait(Until.findObject(By.text("Archiver la copie")), 5000).click()
                        assertTrue(device.wait(Until.gone(By.text("Archive à restaurer")), 8000))
                        device.pressBack()
                        assertTrue(device.wait(Until.gone(By.text("Regrouper par application")), 5000))
                        device.pressBack()
                        assertTrue(device.wait(Until.hasObject(By.desc("Ouvrir mon briefing")), 8000))
                        openNotificationSettings(device, context)
                        val archiveLink = By.text("Archives du briefing")
                        var attempts = 0
                        while (!device.hasObject(archiveLink) && attempts++ < 8) {
                            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                                device.displayWidth / 2, device.displayHeight / 2, 32)
                            device.waitForIdle(750)
                        }
                        val link = device.wait(Until.findObject(archiveLink), 5000)
                            ?: throw AssertionError("Archive entry must be reachable in settings")
                        link.click()
                        val row = device.wait(Until.findObject(By.text("Archive à restaurer")), 10000)
                            ?: throw AssertionError("Archived copy must be visible")
                        row.click()
                        device.wait(Until.findObject(By.text("Restaurer dans Mon briefing")), 5000).click()
                        var restored = false
                        repeat(30) {
                            if (repo.recent(100).singleOrNull()?.key == "restore-synthetic") restored = true
                            if (!restored) SystemClock.sleep(100)
                        }
                        assertTrue("Restoration returns the copy to briefing", restored)
                        assertTrue("Restoration removes the archive entry", repo.archived(100).isEmpty())
                    }
                    "notifications_no_confirmation" -> {
                        openNotificationSettings(device, context)
                        assertTrue(device.wait(Until.hasObject(By.text("Retrait automatique")), 10000))
                        assertFalse(device.hasObject(By.text("Prudent")))
                        assertFalse(device.hasObject(By.text("Tester une notification")))
                    }
                    "brief_decisions" -> {
                        // Synthetic local history; no real Android notifications
                        // are suppressed by this visual / status test.
                        val repo = NotificationHistoryRepository(context)
                        val now = System.currentTimeMillis()
                        repo.record("deleted-ad", "com.android.settings",
                            "Publicité test", "Réduction commerciale", null, now)
                        assertTrue(repo.updateDecision(
                            "deleted-ad", now,
                            NotificationHistoryRepository.DecisionState.DISMISSED,
                            "a promotional advertisement", 0.9998,
                        ))
                        NotificationActivityFeed.notifyChanged()
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        assertTrue(device.wait(Until.hasObject(By.desc("Personnaliser le brief")), 12000))
                        jumpToBriefNotifications(device)
                        openBriefNotificationList(device)
                        val marker = By.textContains("1 notification")
                        var attempts = 0
                        while (!device.hasObject(marker) && attempts++ < 12) {
                            device.swipe(
                                device.displayWidth / 2, device.displayHeight * 3 / 4,
                                device.displayWidth / 2, device.displayHeight / 3, 26,
                            )
                            device.waitForIdle()
                        }
                        assertTrue("Confirmed removals remain in the category history",
                            device.wait(Until.hasObject(marker), 5000))
                    }
                    "food_cooking" -> runFoodScenario(device, context, activity, checkNotNull(foodFixture))
                    "brief_mastra" -> {
                        // Synthetic decisions as returned by the homelab; nothing is sent
                        // over the network and no real Android notification is touched.
                        val repo = NotificationHistoryRepository(context)
                        val sync = io.github.mangi.eta.agent.device.notifications.remote.MastraNotificationSync(context)
                        val now = System.currentTimeMillis()
                        fun decide(key: String, pkg: String, title: String, text: String, priority: String,
                                   topic: String, subtype: String, action: String, reason: String,
                                   actions: List<io.github.mangi.eta.agent.device.notifications.remote.ActionProposal> = emptyList()) {
                            repo.record(key, pkg, title, text, null, now, fingerprint = "fp-$key", queueForRemote = true)
                            val p = repo.pendingRemote(50).single { it.key == key }
                            val n = sync.toRemote(p)
                            repo.applyRemoteDecision(io.github.mangi.eta.agent.device.notifications.remote.RemoteDecision(
                                id = n.id, revision = n.revision, contentHash = n.contentHash, topic = topic, subtype = subtype,
                                priority = io.github.mangi.eta.agent.device.notifications.remote.Priority.valueOf(priority),
                                interest = io.github.mangi.eta.agent.device.notifications.remote.Interest.NEUTRAL,
                                recommendedAndroidAction = io.github.mangi.eta.agent.device.notifications.remote.AndroidAction.valueOf(action),
                                protected = false, reason = reason,
                                uncertainty = io.github.mangi.eta.agent.device.notifications.remote.Uncertainty.LOW,
                                source = io.github.mangi.eta.agent.device.notifications.remote.DecisionSource.RULE,
                                actions = actions, policyVersion = "rules-2026.11.19+p0", decidedAt = "2026-11-19T18:30:06+01:00",
                            )) { sync.toRemote(it).contentHash }
                        }
                        decide("mastra-github", "com.android.settings", "Review requested on PR #42", "homelab-ai",
                            "NOW", "development", "action_required", "KEEP", "Intervention demandée sur le développement",
                            listOf(io.github.mangi.eta.agent.device.notifications.remote.ActionProposal(
                                actionId = "act_0123456789abcdef01234567",
                                type = io.github.mangi.eta.agent.device.notifications.remote.ActionType.CREATE_TASK,
                                status = "PROPOSED", label = "Créer une tâche",
                                payload = kotlinx.serialization.json.buildJsonObject {
                                    put("title", kotlinx.serialization.json.JsonPrimitive("Traiter : Review requested on PR #42"))
                                }, requiresApproval = true)))
                        decide("mastra-snap", "com.android.systemui", "Suggestion d'ami", "Découvrez Paul",
                            "LOW", "social", "friend_suggestion", "DISMISS", "Suggestion sociale sans intérêt personnel")
                        repo.setAndroidActionState("mastra-snap", now,
                            NotificationHistoryRepository.AndroidActionState.CONFIRMED, "Retirée des notifications Android")
                        repo.record("mastra-pending", "com.android.settings", "Nouveautés", "Les nouveautés de la semaine",
                            null, now - 1, fingerprint = "fp-pending", queueForRemote = true)
                        NotificationActivityFeed.notifyChanged()
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        assertTrue(device.wait(Until.hasObject(By.desc("Personnaliser le brief")), 12000))
                        jumpToBriefNotifications(device)
                        openBriefNotificationList(device)
                        assertTrue("Mastra NOW tier is shown", device.wait(Until.hasObject(By.text("À traiter maintenant")), 8000))
                        val pending = By.textContains("Analyse en attente")
                        var attempts = 0
                        while (!device.hasObject(pending) && attempts++ < 8) {
                            device.swipe(device.displayWidth / 2, device.displayHeight * 2 / 3,
                                device.displayWidth / 2, device.displayHeight / 2, 26)
                        }
                        assertTrue("Pending analysis is explicit",
                            device.wait(Until.hasObject(pending), 8000))
                    }
                    "brief_swipe_isolation" -> {
                        val repo = NotificationHistoryRepository(context)
                        val now = System.currentTimeMillis()
                        repo.record("swipe-synthetic", "com.android.settings",
                            "Test de glissement", "Test local", null, now)
                        NotificationActivityFeed.notifyChanged()
                        device.findObject(By.desc("Ouvrir mon briefing")).click()
                        assertTrue(device.wait(Until.hasObject(By.desc("Personnaliser le brief")), 12000))
                        jumpToBriefNotifications(device)
                        openBriefNotificationList(device)
                        val selector = By.text("Test de glissement")
                        val row = findBriefControl(device, selector)
                        val y = row.visibleBounds.centerY()
                        device.swipe(device.displayWidth / 6, y, device.displayWidth * 5 / 6, y, 30)
                        assertTrue("Swiping archives the notification",
                            device.wait(Until.gone(selector), 10000))
                        assertEquals("swipe-synthetic", repo.archived().single().key)
                        assertTrue("Swiping a row keeps the dedicated page open",
                            device.hasObject(By.text("Regrouper par application")))
                        device.pressBack()
                        assertTrue("Back returns to the briefing",
                            device.wait(Until.hasObject(By.text("0 Aujourd’hui")), 8000))
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
        } finally {
            if (scenario.endsWith("_notice")) {
                device.pressBack()
                context.getSystemService(android.app.NotificationManager::class.java).cancel(6202)
            }
            fixture?.close()
            foodFixture?.close()
            improvements?.disconnect()
        }
    }
}
