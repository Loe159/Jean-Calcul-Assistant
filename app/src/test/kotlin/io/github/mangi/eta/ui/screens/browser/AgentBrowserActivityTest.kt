package io.github.mangi.eta.ui.screens.browser

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AgentBrowserActivityTest {
    @Test
    fun browserWindowIsPrivateAndSharesTheSessionProcess() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(context, AgentBrowserActivity::class.java)
        val info = context.packageManager.getActivityInfo(component, 0)
        assertFalse(info.exported)
        // L'activity ne déclare pas android:process : elle tourne donc dans le
        // process principal, dont le nom est le packageName (= applicationId).
        // On compare à context.packageName plutôt qu'à applicationInfo.processName,
        // que Robolectric laisse à null alors qu'un vrai device y met le packageName.
        assertEquals(context.packageName, info.processName)
    }

    @Test
    fun explicitLaunchKeepsConversationIdentity() {
        val context = RuntimeEnvironment.getApplication()
        val intent = AgentBrowserActivity.createIntent(context, "conversation-a")
        assertEquals(ComponentName(context, AgentBrowserActivity::class.java), intent.component)
        assertEquals("conversation-a", intent.getStringExtra("io.github.mangi.eta.extra.BROWSER_SESSION_KEY"))
    }

    @Test
    fun missingSessionDoesNotOpenAnUnrelatedPage() {
        val controller = Robolectric.buildActivity(AgentBrowserActivity::class.java, Intent()).create()
        try {
            assertTrue(controller.get().isFinishing)
        } finally {
            controller.destroy()
        }
    }
}
