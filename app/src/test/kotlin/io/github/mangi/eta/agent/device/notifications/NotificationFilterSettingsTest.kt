package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationFilterSettingsTest {
    private val app by lazy { RuntimeEnvironment.getApplication() }

    @Test fun oldObservationFlagMigratesWithoutActivatingFilter() {
        app.getSharedPreferences("notification_local", Context.MODE_PRIVATE).edit().clear()
            .putBoolean("observation_enabled", true).commit()
        assertEquals(NotificationFilterMode.OBSERVE, NotificationFilterSettings.load(app).mode)
    }

    @Test fun personalizedRulesPersistAcrossReload() {
        val config = NotificationFilterConfig(mode = NotificationFilterMode.PERSONALIZED,
            filterSpam = true, onlyQuietHours = true, quietFrom = 23, quietUntil = 6,
            appRules = mapOf("com.example.shop" to NotificationAppRule.FILTER,
                "com.example.bank" to NotificationAppRule.KEEP))
        NotificationFilterSettings.save(app, config)
        assertEquals(config, NotificationFilterSettings.load(app))
        NotificationFilterSettings.setMode(app, NotificationFilterMode.OFF)
        assertEquals(NotificationFilterMode.OFF, NotificationFilterSettings.load(app).mode)
        assertTrue(NotificationFilterSettings.load(app).appRules.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun permanentDeleteCannotBeEnabledInCautious() {
        NotificationFilterSettings.save(app,
            NotificationFilterConfig(mode = NotificationFilterMode.CAUTIOUS,
                action = NotificationFilterAction.DISMISS))
    }

    @Test fun malformedPreferencesFailClosed() {
        app.getSharedPreferences("notification_local", Context.MODE_PRIVATE).edit()
            .putString("filter_config_v1", "{junk").commit()
        assertEquals(NotificationFilterMode.OFF, NotificationFilterSettings.load(app).mode)
        assertFalse(NotificationFilterSettings.isValidPackageName("abc;rm -rf"))
        assertTrue(NotificationFilterSettings.isValidPackageName("com.example.store"))
    }
}
