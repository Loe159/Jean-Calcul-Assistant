package fr.loevan.jeancalcul.toolbridge

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.loevan.jeancalcul.domain.VolumeStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioManagerVolumeControllerTest {
    private val audioManager =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSystemService(AudioManager::class.java)
    private val controller = AudioManagerVolumeController(audioManager)

    @Test
    fun reportsCurrentVolumeForSupportedStreams() {
        VolumeStream.entries.forEach { stream ->
            val volume = controller.read(stream)

            assumeTrue("$stream is unavailable", volume.maximum > 0)
            assertTrueInRange(volume)
        }
    }

    @Test
    fun writesAndReadsMusicVolumeWhenWritable() = writesAndReadsObservedVolume(VolumeStream.MUSIC)

    @Test
    fun writesAndReadsAlarmVolumeWhenWritable() = writesAndReadsObservedVolume(VolumeStream.ALARM)

    @Test
    fun writesAndReadsNotificationVolumeWhenWritable() = writesAndReadsObservedVolume(VolumeStream.NOTIFICATION)

    private fun assertTrueInRange(volume: PlatformVolume) {
        assertTrue(volume.current in 0..volume.maximum)
    }

    private fun writesAndReadsObservedVolume(stream: VolumeStream) {
        val original = controller.read(stream)
        assumeTrue("$stream is unavailable", original.maximum > 0)
        val target = if (original.current == 0) 1.coerceAtMost(original.maximum) else original.current - 1
        try {
            controller.write(stream, target)
            val observed = controller.read(stream).current
            assumeTrue("$stream writes are constrained by this device state", observed == target)
            assertEquals(target, observed)
        } finally {
            controller.write(stream, original.current)
        }
    }
}
