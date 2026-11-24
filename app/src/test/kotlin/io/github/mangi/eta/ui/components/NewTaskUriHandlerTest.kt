package io.github.mangi.eta.ui.components

import android.app.Application
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class NewTaskUriHandlerTest {
    @Test
    fun opensLinksInANewTaskFromANonActivityContext() {
        val application = RuntimeEnvironment.getApplication()
        NewTaskUriHandler(application).openUri("https://example.org/recette")

        val started = shadowOf(application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.org/recette", started.dataString)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
