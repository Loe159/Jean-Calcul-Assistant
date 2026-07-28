package fr.loevan.jeancalcul.feature.settings

import fr.loevan.jeancalcul.domain.AgentPermission
import fr.loevan.jeancalcul.domain.AppearanceSettings
import fr.loevan.jeancalcul.domain.VoiceSettings

data class SettingsScreenActions(
    val saveProvider: (ProviderDraft, CharArray?) -> Unit,
    val deleteProvider: (String) -> Unit,
    val testConnection: (String) -> Unit,
    val saveModel: (ModelProfileDraft) -> Unit,
    val duplicateModel: (String) -> Unit,
    val activateModel: (String) -> Unit,
    val deleteModel: (String) -> Unit,
    val saveAgent: (AgentProfileDraft) -> Unit,
    val duplicateAgent: (String) -> Unit,
    val activateAgent: (String) -> Unit,
    val setAgentPermission: (String, AgentPermission, Boolean) -> Unit,
    val deleteAgent: (String) -> Unit,
    val updateVoice: (VoiceSettings) -> Unit,
    val updateAppearance: (AppearanceSettings) -> Unit,
    val clearError: () -> Unit,
)
