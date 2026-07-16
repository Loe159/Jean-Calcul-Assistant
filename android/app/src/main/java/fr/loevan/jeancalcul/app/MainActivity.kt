package fr.loevan.jeancalcul.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import fr.loevan.jeancalcul.ui.jeanCalculTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private lateinit var assistantRoleController: AssistantRoleController
    private lateinit var assistantRoleGateway: AssistantRoleGateway
    private var assistantRoleStatus by mutableStateOf<AssistantRoleStatus>(AssistantRoleStatus.Unavailable)
    private var microphonePermissionGranted by mutableStateOf(false)

    private val requestAssistantRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultName = if (result.resultCode == Activity.RESULT_OK) "granted" else "not granted"
            Log.i(LOG_TAG, "Assistant role request completed: $resultName")
            refreshAssistantRoleStatus()
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            microphonePermissionGranted = granted
            Log.i(LOG_TAG, "Microphone permission request completed: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistantRoleGateway = createAssistantRoleGateway(this)
        assistantRoleController = AssistantRoleController(assistantRoleGateway)
        refreshAssistantRoleStatus()
        microphonePermissionGranted = hasMicrophonePermission()
        setContent {
            jeanCalculTheme {
                assistantRoleOnboarding(
                    status = assistantRoleStatus,
                    microphonePermissionGranted = microphonePermissionGranted,
                    onRequestRole = ::requestAssistantRole,
                    onRequestMicrophonePermission = ::requestMicrophonePermission,
                    onOpenSystemSettings = ::openVoiceInputSettings,
                )
            }
        }
        if (intent.action == ACTION_REQUEST_MICROPHONE_PERMISSION) {
            requestMicrophonePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::assistantRoleController.isInitialized) {
            refreshAssistantRoleStatus()
        }
        microphonePermissionGranted = hasMicrophonePermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_REQUEST_MICROPHONE_PERMISSION) {
            requestMicrophonePermission()
        }
    }

    private fun refreshAssistantRoleStatus() {
        assistantRoleStatus = assistantRoleController.status()
    }

    private fun requestAssistantRole() {
        runCatching { assistantRoleGateway.createRequestIntent() }
            .onSuccess { requestAssistantRole.launch(it) }
            .onFailure { error ->
                Log.e(LOG_TAG, "Unable to request the assistant role", error)
                openVoiceInputSettings()
            }
    }

    private fun requestMicrophonePermission() {
        if (hasMicrophonePermission()) {
            microphonePermissionGranted = true
            return
        }
        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openVoiceInputSettings() {
        val voiceInputSettings = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        try {
            startActivity(voiceInputSettings)
        } catch (error: ActivityNotFoundException) {
            Log.w(LOG_TAG, "Voice input settings are unavailable; opening general settings", error)
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private companion object {
        const val LOG_TAG = "AssistantRole"
        const val ACTION_REQUEST_MICROPHONE_PERMISSION =
            "fr.loevan.jeancalcul.action.REQUEST_MICROPHONE_PERMISSION"
    }
}
