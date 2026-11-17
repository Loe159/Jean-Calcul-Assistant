package io.github.mangi.eta.agent.device.notifications

import android.content.Context
import org.json.JSONObject
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
    private fun prefs() = app.getSharedPreferences("notification_local", Context.MODE_PRIVATE)

    @Test fun oldObservationFlagMigratesWithoutActivatingFilter() {
        prefs().edit().clear().putBoolean("observation_enabled", true).commit()
        assertEquals(NotificationFilterMode.OBSERVE, NotificationFilterSettings.load(app).mode)
    }

    @Test fun allAppsConfigPersistsWithoutPackageIdentifiers() {
        prefs().edit().clear().commit()
        val config = NotificationFilterConfig(
            mode = NotificationFilterMode.PERSONALIZED,
            filterAds = true, filterSpam = true,
            onlyQuietHours = true, quietFrom = 23, quietUntil = 6,
            action = NotificationFilterAction.DISMISS,
        )
        NotificationFilterSettings.save(app, config)
        assertEquals(config, NotificationFilterSettings.load(app))
        val stored = JSONObject(prefs().getString("filter_config_v2", "{}")!!)
        assertFalse(stored.has("apps"))
        assertEquals("DISMISS", stored.getString("action"))
        NotificationFilterSettings.setMode(app, NotificationFilterMode.OFF)
        assertEquals(NotificationFilterMode.OFF, NotificationFilterSettings.load(app).mode)
        assertEquals(NotificationFilterAction.SNOOZE_1H,
            NotificationFilterSettings.load(app).action)
    }

    @Test fun v1PersonalizedDeletionIsNotBroadenedWithoutNewConfirmation() {
        prefs().edit().clear().putString("filter_config_v1", JSONObject()
            .put("mode", "PERSONALIZED").put("action", "DISMISS")
            .put("ads", true).put("spam", true)
            .put("apps", JSONObject().put("com.example.shop", "FILTER").put("com.example.bank", "KEEP"))
            .toString()).commit()
        val migrated = NotificationFilterSettings.load(app)
        assertEquals(NotificationFilterMode.PERSONALIZED, migrated.mode)
        assertEquals(NotificationFilterAction.SNOOZE_1H, migrated.action)
        assertTrue(migrated.filterSpam)
        // Saving the migrated settings persists the new all-apps format.
        NotificationFilterSettings.save(app, migrated)
        assertFalse(prefs().contains("filter_config_v1"))
        assertEquals(NotificationFilterAction.SNOOZE_1H,
            NotificationFilterSettings.load(app).action)
    }

    @Test(expected = IllegalArgumentException::class)
    fun permanentDeleteCannotBeEnabledInCautious() {
        NotificationFilterSettings.save(app,
            NotificationFilterConfig(mode = NotificationFilterMode.CAUTIOUS,
                action = NotificationFilterAction.DISMISS))
    }

    @Test fun malformedPreferencesFailClosed() {
        prefs().edit().clear().putString("filter_config_v2", "{junk").commit()
        assertEquals(NotificationFilterMode.OFF, NotificationFilterSettings.load(app).mode)
    }
}
