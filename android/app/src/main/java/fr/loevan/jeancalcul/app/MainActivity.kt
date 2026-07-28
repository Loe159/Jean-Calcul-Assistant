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
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import fr.loevan.jeancalcul.domain.AppearanceSettings
import fr.loevan.jeancalcul.domain.AppearanceTheme
import fr.loevan.jeancalcul.feature.conversation.ConversationScreenActions
import fr.loevan.jeancalcul.feature.conversation.ConversationViewModel
import fr.loevan.jeancalcul.feature.settings.SettingsScreenActions
import fr.loevan.jeancalcul.feature.settings.SettingsViewModel
import fr.loevan.jeancalcul.ui.ThemeMode
import fr.loevan.jeancalcul.ui.VisualEffects
import fr.loevan.jeancalcul.ui.jeanCalculTheme

@AndroidEntryPoint
@Suppress("TooManyFunctions")
class MainActivity : ComponentActivity() {
    private val conversationViewModel: ConversationViewModel by viewModels()
    private val auditViewModel: AuditViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
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
        setAppContent()
        if (intent.action == ACTION_REQUEST_MICROPHONE_PERMISSION) {
            requestMicrophonePermission()
        }
    }

    private fun setAppContent() {
        setContent {
            val conversationState by conversationViewModel.uiState.collectAsState()
            val auditState by auditViewModel.uiState.collectAsState()
            val settingsState by settingsViewModel.uiState.collectAsState()
            jeanCalculTheme(
                themeMode = settingsState.settings.appearance.theme.themeMode,
                visualEffects = settingsState.settings.appearance.visualEffects,
            ) {
                mainAppContent(
                    state =
                        MainAppUiState(
                            assistantRoleStatus = assistantRoleStatus,
                            microphonePermissionGranted = microphonePermissionGranted,
                            conversation = conversationState,
                            audit = auditState,
                            settings = settingsState,
                        ),
                    actions = mainAppActions(),
                )
            }
        }
    }

    private fun mainAppActions() =
        MainAppActions(
            onboarding =
                OnboardingActions(
                    requestRole = ::requestAssistantRole,
                    requestMicrophonePermission = ::requestMicrophonePermission,
                    openSystemSettings = ::openVoiceInputSettings,
                ),
            conversation =
                ConversationScreenActions(
                    selectConversation = conversationViewModel::selectConversation,
                    newConversation = conversationViewModel::newConversation,
                    deleteConversation = conversationViewModel::deleteSelected,
                    draftChanged = conversationViewModel::updateDraft,
                    send = conversationViewModel::saveDraft,
                    export = { conversationViewModel.exportSelected(::shareConversation) },
                ),
            audit =
                AuditScreenActions(
                    timeWindowChanged = auditViewModel::setTimeWindow,
                    toolNameChanged = auditViewModel::setToolName,
                    outcomeChanged = auditViewModel::setOutcome,
                    retentionChanged = auditViewModel::setRetentionDays,
                    loadMore = auditViewModel::loadMore,
                    purgeExpired = auditViewModel::purgeExpired,
                    export = { auditViewModel.export(::shareAudit) },
                ),
            settings =
                SettingsScreenActions(
                    saveProvider = settingsViewModel::saveProvider,
                    deleteProvider = settingsViewModel::deleteProvider,
                    testConnection = settingsViewModel::testConnection,
                    saveModel = settingsViewModel::saveModel,
                    duplicateModel = settingsViewModel::duplicateModel,
                    activateModel = settingsViewModel::activateModel,
                    deleteModel = settingsViewModel::deleteModel,
                    saveAgent = settingsViewModel::saveAgent,
                    duplicateAgent = settingsViewModel::duplicateAgent,
                    activateAgent = settingsViewModel::activateAgent,
                    setAgentPermission = settingsViewModel::setAgentPermission,
                    deleteAgent = settingsViewModel::deleteAgent,
                    updateVoice = settingsViewModel::updateVoice,
                    updateAppearance = settingsViewModel::updateAppearance,
                    clearError = settingsViewModel::clearError,
                ),
        )

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

    private fun shareConversation(
        title: String,
        json: String,
    ) {
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, json)
            }
        startActivity(Intent.createChooser(shareIntent, "Exporter la conversation"))
    }

    private fun shareAudit(json: String) {
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "Jean Calcul — journal d'audit")
                putExtra(Intent.EXTRA_TEXT, json)
            }
        startActivity(Intent.createChooser(shareIntent, "Exporter le journal d'audit"))
    }

    private companion object {
        const val LOG_TAG = "AssistantRole"
        const val ACTION_REQUEST_MICROPHONE_PERMISSION =
            "fr.loevan.jeancalcul.action.REQUEST_MICROPHONE_PERMISSION"
    }
}

private val AppearanceTheme.themeMode: ThemeMode
    get() =
        when (this) {
            AppearanceTheme.SYSTEM -> ThemeMode.System
            AppearanceTheme.DARK -> ThemeMode.Dark
            AppearanceTheme.LIGHT -> ThemeMode.Light
        }

private val AppearanceSettings.visualEffects: VisualEffects
    get() = VisualEffects(reduceMotion, blurEnabled, shadersEnabled, highContrast)
