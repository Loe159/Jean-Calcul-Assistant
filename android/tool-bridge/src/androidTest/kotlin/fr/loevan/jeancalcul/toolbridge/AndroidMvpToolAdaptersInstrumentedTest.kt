package fr.loevan.jeancalcul.toolbridge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.loevan.jeancalcul.domain.ToolDeviceCapabilities
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMvpToolAdaptersInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun batteryAndLocalTimeUseAndroidLocalSources() {
        val battery = AndroidBatteryStatusSource(context).read()
        val localTime = SystemLocalTimeSource().read()

        assertNotNull(battery)
        assertTrue(requireNotNull(battery).levelPercent in 0..100)
        assertTrue(localTime.epochMillis > 0)
        assertTrue(localTime.isoLocalDateTime.isNotBlank())
        assertTrue(localTime.timeZoneId.isNotBlank())
    }

    @Test
    fun availabilityAlwaysAdvertisesLocalNonHardwareCapabilities() {
        val availability = androidMvpToolAvailabilityContext(context, isDeviceLocked = false)

        assertTrue(ToolDeviceCapabilities.BATTERY_STATUS in availability.deviceCapabilities)
        assertTrue(ToolDeviceCapabilities.LOCAL_TASKS in availability.deviceCapabilities)
        assertTrue(ToolDeviceCapabilities.LOCAL_TIME in availability.deviceCapabilities)
        assertTrue(ToolDeviceCapabilities.MEDIA_CONTROL in availability.deviceCapabilities)
        assertTrue(ToolDeviceCapabilities.SETTINGS_PANEL in availability.deviceCapabilities)
    }
}
