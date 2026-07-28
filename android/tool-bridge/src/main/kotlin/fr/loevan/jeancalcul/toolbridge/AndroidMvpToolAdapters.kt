package fr.loevan.jeancalcul.toolbridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import fr.loevan.jeancalcul.domain.MvpToolSchemas
import fr.loevan.jeancalcul.domain.ToolAndroidPermissions
import fr.loevan.jeancalcul.domain.ToolAuditLogger
import fr.loevan.jeancalcul.domain.ToolAvailabilityContext
import fr.loevan.jeancalcul.domain.ToolDeviceCapabilities
import fr.loevan.jeancalcul.feature.tasks.SharedPreferencesLocalTaskStore
import java.time.Clock
import java.time.ZonedDateTime

class AudioManagerMediaPlaybackController(
    private val audioManager: AudioManager,
) : MediaPlaybackController {
    override fun dispatchPlayPause(): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0),
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0),
        )
        return true
    }
}

class AndroidSettingsPanelLauncher(
    private val context: Context,
) : SettingsPanelLauncher {
    override fun open(panel: MvpToolSchemas.SettingsPanel): Boolean {
        val intent =
            when (panel) {
                MvpToolSchemas.SettingsPanel.APPLICATION_DETAILS ->
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                MvpToolSchemas.SettingsPanel.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
                MvpToolSchemas.SettingsPanel.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                MvpToolSchemas.SettingsPanel.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                MvpToolSchemas.SettingsPanel.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
                MvpToolSchemas.SettingsPanel.DATE_TIME -> Intent(Settings.ACTION_DATE_SETTINGS)
                MvpToolSchemas.SettingsPanel.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }
}

class AndroidInstalledAppLauncher(
    private val context: Context,
) : InstalledAppLauncher {
    override fun canLaunch(packageName: String): Boolean = launchIntent(packageName) != null

    override fun launch(packageName: String): Boolean {
        val intent = launchIntent(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        context.startActivity(intent)
        return true
    }

    private fun launchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(
            packageName,
        )
}

class CameraManagerFlashlightController(
    private val cameraManager: CameraManager,
) : FlashlightController {
    override fun primaryCameraId(): String? =
        runCatching {
            cameraManager.cameraIdList
                .sortedBy { cameraId ->
                    val facing = cameraManager.getCameraCharacteristics(cameraId)[CameraCharacteristics.LENS_FACING]
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) 0 else 1
                }.firstOrNull { cameraId ->
                    cameraManager.getCameraCharacteristics(cameraId)[CameraCharacteristics.FLASH_INFO_AVAILABLE] == true
                }
        }.getOrNull()

    override fun setEnabled(
        cameraId: String,
        enabled: Boolean,
    ) {
        try {
            cameraManager.setTorchMode(cameraId, enabled)
        } catch (error: CameraAccessException) {
            throw IllegalStateException("The camera flashlight is unavailable.", error)
        }
    }
}

class AndroidBatteryStatusSource(
    private val context: Context,
) : BatteryStatusSource {
    override fun read(): BatteryStatus? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.toBatteryStatus()

    private fun Intent.toBatteryStatus(): BatteryStatus? {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return if (level < 0 || scale <= 0) {
            null
        } else {
            BatteryStatus(
                levelPercent = (level * 100 / scale).coerceIn(0, 100),
                isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL,
                isPowerSaveMode = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true,
            )
        }
    }
}

class SystemLocalTimeSource(
    private val clock: Clock = Clock.systemDefaultZone(),
) : LocalTimeSource {
    override fun read(): LocalTimeSnapshot {
        val now = ZonedDateTime.now(clock)
        return LocalTimeSnapshot(
            epochMillis = now.toInstant().toEpochMilli(),
            isoLocalDateTime = now.toOffsetDateTime().toString(),
            timeZoneId = now.zone.id,
        )
    }
}

fun createAndroidMvpToolRegistry(
    context: Context,
    auditLogger: ToolAuditLogger? = null,
    clock: () -> Long = System::currentTimeMillis,
): ToolRegistry {
    val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
    val dependencies =
        MvpToolDependencies(
            mediaPlaybackController = AudioManagerMediaPlaybackController(audioManager),
            settingsPanelLauncher = AndroidSettingsPanelLauncher(context),
            installedAppLauncher = AndroidInstalledAppLauncher(context),
            flashlightController =
                context.getSystemService(CameraManager::class.java)
                    ?.let(::CameraManagerFlashlightController)
                    ?: UnavailableFlashlightController,
            batteryStatusSource = AndroidBatteryStatusSource(context),
            localTimeSource = SystemLocalTimeSource(),
            localTaskStore = SharedPreferencesLocalTaskStore(context),
        )
    val registrations =
        volumeToolRegistrations(AudioManagerVolumeController(audioManager)) +
            mvpToolRegistrations(dependencies, clock)
    return auditLogger?.let { ToolRegistry(registrations, it, clock) } ?: ToolRegistry(registrations, clock = clock)
}

private object UnavailableFlashlightController : FlashlightController {
    override fun primaryCameraId(): String? = null

    override fun setEnabled(
        cameraId: String,
        enabled: Boolean,
    ) = Unit
}

fun androidMvpToolAvailabilityContext(
    context: Context,
    isDeviceLocked: Boolean,
    isAppForeground: Boolean = true,
): ToolAvailabilityContext {
    val packageManager = context.packageManager
    val capabilities =
        buildSet {
            add(ToolDeviceCapabilities.BATTERY_STATUS)
            add(ToolDeviceCapabilities.LOCAL_TASKS)
            add(ToolDeviceCapabilities.LOCAL_TIME)
            add(ToolDeviceCapabilities.MEDIA_CONTROL)
            add(ToolDeviceCapabilities.SETTINGS_PANEL)
            add(ToolDeviceCapabilities.VOLUME_READ)
            add(ToolDeviceCapabilities.VOLUME_WRITE)
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                add(ToolDeviceCapabilities.FLASHLIGHT)
            }
            val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            if (packageManager.queryIntentActivities(launcherQuery, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()) {
                add(ToolDeviceCapabilities.APP_LAUNCHER)
            }
        }
    val grantedPermissions =
        buildSet {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                add(ToolAndroidPermissions.CAMERA)
            }
        }
    return ToolAvailabilityContext(
        deviceCapabilities = capabilities,
        grantedAndroidPermissions = grantedPermissions,
        isDeviceLocked = isDeviceLocked,
        isAppForeground = isAppForeground,
    )
}
