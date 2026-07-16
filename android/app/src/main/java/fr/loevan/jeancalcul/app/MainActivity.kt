package fr.loevan.jeancalcul.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
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

    private val requestAssistantRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultName = if (result.resultCode == Activity.RESULT_OK) "granted" else "not granted"
            Log.i(LOG_TAG, "Assistant role request completed: $resultName")
            refreshAssistantRoleStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistantRoleGateway = createAssistantRoleGateway(this)
        assistantRoleController = AssistantRoleController(assistantRoleGateway)
        refreshAssistantRoleStatus()
        setContent {
            jeanCalculTheme {
                AssistantRoleOnboarding(
                    status = assistantRoleStatus,
                    onRequestRole = ::requestAssistantRole,
                    onOpenSystemSettings = ::openVoiceInputSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::assistantRoleController.isInitialized) {
            refreshAssistantRoleStatus()
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
    }
}
