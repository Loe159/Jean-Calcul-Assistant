package fr.loevan.jeancalcul.assistant.session

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionActivityIntentInstrumentedTest {
    @Test
    fun microphone_permission_request_uses_a_normal_application_activity_launch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val intent = microphonePermissionRequestIntent(context)

        assertEquals(
            "fr.loevan.jeancalcul.action.REQUEST_MICROPHONE_PERMISSION",
            intent.action,
        )
        assertEquals("fr.loevan.jeancalcul.app.MainActivity", intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(intent.categories.orEmpty().contains(Intent.CATEGORY_VOICE))
    }
}
