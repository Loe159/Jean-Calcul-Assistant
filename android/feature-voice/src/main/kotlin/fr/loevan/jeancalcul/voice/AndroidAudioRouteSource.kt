package fr.loevan.jeancalcul.voice

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import fr.loevan.jeancalcul.domain.VoiceAudioRoute
import fr.loevan.jeancalcul.domain.VoiceAudioRouteSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAudioRouteSource(
    context: Context,
) : VoiceAudioRouteSource {
    private val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
    private val mutableRoute = MutableStateFlow(currentRoute())
    private var registered = false
    private val callback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }

    override val route: StateFlow<VoiceAudioRoute> = mutableRoute.asStateFlow()

    override fun start() {
        if (!registered) {
            audioManager.registerAudioDeviceCallback(callback, null)
            registered = true
        }
        refresh()
    }

    override fun stop() {
        if (registered) {
            audioManager.unregisterAudioDeviceCallback(callback)
            registered = false
        }
    }

    override fun release() = stop()

    private fun refresh() {
        mutableRoute.value = currentRoute()
    }

    private fun currentRoute(): VoiceAudioRoute =
        classifyAudioRoute(
            audioManager
                .getDevices(AudioManager.GET_DEVICES_INPUTS)
                .filter(AudioDeviceInfo::isSource)
                .map(AudioDeviceInfo::getType)
                .toSet(),
        )
}

internal fun classifyAudioRoute(deviceTypes: Set<Int>): VoiceAudioRoute =
    when {
        deviceTypes.any { it in BLUETOOTH_INPUT_TYPES } -> VoiceAudioRoute.BLUETOOTH
        deviceTypes.any { it in WIRED_INPUT_TYPES } -> VoiceAudioRoute.WIRED
        AudioDeviceInfo.TYPE_BUILTIN_MIC in deviceTypes -> VoiceAudioRoute.BUILT_IN
        else -> VoiceAudioRoute.UNKNOWN
    }

private val BLUETOOTH_INPUT_TYPES =
    setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )
private val WIRED_INPUT_TYPES =
    setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )
