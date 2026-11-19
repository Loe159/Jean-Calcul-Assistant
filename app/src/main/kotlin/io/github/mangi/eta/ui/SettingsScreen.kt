package io.github.mangi.eta.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.ImportContacts
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.accessibility.AccessibilityProtectionClient
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService
import io.github.mangi.eta.config.PowerAssistantTarget
import io.github.mangi.eta.config.Prefs
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.systemizer.GoogleAppSystemizerInstaller
import io.github.mangi.eta.systemizer.RootManager
import io.github.mangi.eta.systemizer.SystemizerInstallResult
import io.github.mangi.eta.ui.app.EnhancementSettingsHistory
import io.github.mangi.eta.ui.app.rememberDeviceCapabilities
import io.github.mangi.eta.ui.components.LanguagePreference
import io.github.mangi.eta.ui.components.StatusSuccess
import io.github.mangi.eta.ui.components.StatusWarning
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.mangi.eta.ui.components.coui.PreferenceValueText
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

private object SettingsIconColors {
    val Blue = Color(0xFF4285F4)
    val Green = StatusSuccess
    val Orange = Color(0xFFFA7B17)
    val Yellow = StatusWarning
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsIcon(
    icon: ImageVector,
    tint: Color,
    enabled: Boolean = true,
) {
    val glyphSize = when (icon) {
        Icons.AutoMirrored.Rounded.MenuBook -> 22.dp
        else -> 24.dp
    }
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(glyphSize),
            tint = if (enabled) tint else COUITheme.colorScheme.disabledNeutral,
        )
    }
}

/**
 * 模块配置界面。
 *
 * 开关默认值由 [Prefs.Keys.BOOLEAN_DEFAULTS] 统一定义。Eta Runtime 自己消费的开关写入
 * App 本地配置；仅 Hook 消费的开关通过 RemotePreferences 提交到 LSPosed。
 */
@Composable
internal fun SettingsScreen(
    context: Context,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageContent(context = context, onNavigate = onNavigate, onBack = onBack)
}

@Composable
private fun SettingsPageContent(
    context: Context,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val capabilities = rememberDeviceCapabilities()
    val enhancementHistory = remember(context.applicationContext) { EnhancementSettingsHistory(context) }
    var hasConnectedFramework by remember { mutableStateOf(false) }
    var hasUsedSystemizer by remember { mutableStateOf(false) }
    var showSystemizerDialog by remember { mutableStateOf(false) }
    var installingSystemizer by remember { mutableStateOf(false) }

    // 悬浮窗权限状态：授权后从系统设置返回时（ON_RESUME）刷新。
    var overlayGranted by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    var accessibilityGranted by remember {
        mutableStateOf(isAgentAccessibilityEnabled(context))
    }
    var accessibilityProtectionEnabled by remember {
        mutableStateOf(AccessibilityProtectionClient.isEnabled(context))
    }
    var accessibilityProtectionPending by remember { mutableStateOf(false) }
    val openAssistantSettings: () -> Unit = {
        val failed = runCatching {
            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }.isFailure
        if (failed) {
            Toast.makeText(context, context.getString(R.string.settings_open_assistant_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // 关于组：版本信息与更新检查。结果对话框在列表外渲染，状态需要页面级 owner。
    val appPackageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val appVersionName = appPackageInfo.versionName.orEmpty()
    val appVersionSummary = "${appPackageInfo.versionName} (${appPackageInfo.longVersionCode})"
    val openUrl: (String) -> Unit = { url ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    val checkingUpdate = false
    val checkForUpdate: () -> Unit = {
        io.github.mangi.eta.data.update.PreviewUpdates.check(context, manual = true)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = android.provider.Settings.canDrawOverlays(context)
                accessibilityGranted = isAgentAccessibilityEnabled(context)
                accessibilityProtectionEnabled =
                    AccessibilityProtectionClient.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Provider / Model 选中状态展示
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val selectedProviderId by RuntimeConfigRepository.selectedProviderIdFlow()
        .collectAsState(initial = null)
    val selectedModelId by RuntimeConfigRepository.selectedModelIdFlow()
        .collectAsState(initial = null)
    val selectedProvider = remember(providers, selectedProviderId) {
        providers.find { it.id == selectedProviderId }
    }
    val selectedModel = remember(selectedProvider, selectedModelId) {
        selectedProvider?.models?.find { it.id == selectedModelId }
    }
    val providerSummary = selectedProvider?.let { provider ->
        "${provider.name} / ${selectedModel?.displayName ?: stringResource(R.string.settings_model_not_selected)}"
    } ?: stringResource(R.string.settings_not_configured)

    // prefs 绑定到 XposedService：service 到达时切换到 RemotePreferences（跨进程提交到
    // LSPosed 数据库）；未就绪时保持 null，UI 禁止修改。
    var prefs by remember { mutableStateOf(Prefs.remotePreferencesForUi(EtaApp.serviceInstance)) }
    val agentPrefs = remember { Prefs.localAgentPreferences() }
    var powerAssistantTarget by remember(prefs) {
        mutableStateOf(prefs?.let(Prefs::powerAssistantTarget) ?: enhancementHistory.powerTarget())
    }
    DisposableEffect(prefs) {
        val targetPrefs = prefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == Prefs.Keys.POWER_KEY_ASSISTANT_TARGET ||
                key == Prefs.Keys.POWER_KEY_TAKEOVER
            ) {
                powerAssistantTarget = Prefs.powerAssistantTarget(changedPrefs)
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(Unit) {
        val listener = object : EtaApp.ServiceStateListener {
            override fun onServiceStateChanged(service: io.github.libxposed.service.XposedService?) {
                prefs = Prefs.remotePreferencesForUi(service)
                prefs?.let { connected ->
                    enhancementHistory.captureConnected(connected)
                    hasConnectedFramework = true
                }
                Prefs.reconcileAgentPreferences(service)
                coroutineScope.launch {
                    RuntimeConfigRepository.ensureDefaults(service)
                }
            }
        }
        EtaApp.addServiceStateListener(listener, notifyImmediately = true)
        onDispose { EtaApp.removeServiceStateListener(listener) }
    }
    val powerAssistantTargets = PowerAssistantTarget.entries
    val powerAssistantItems = powerAssistantTargets.map { target ->
        target.displayName(context)
    }
    val onPowerTargetSelect: (Int) -> Unit = { index ->
        val target = powerAssistantTargets.getOrNull(index)
        val targetPrefs = prefs
        if (target != null && targetPrefs != null) {
            if (putStringSync(
                    prefs = targetPrefs,
                    key = Prefs.Keys.POWER_KEY_ASSISTANT_TARGET,
                    value = target.persistedValue,
                )
            ) {
                powerAssistantTarget = target
                enhancementHistory.recordCommittedTarget(target)
            } else {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.settings_write_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val onAccessibilityProtectionToggle: (Boolean) -> Unit = { enabled ->
        if (!accessibilityProtectionPending) {
            accessibilityProtectionPending = true
            AccessibilityProtectionClient.setEnabled(
                context = context,
                enabled = enabled,
            ) { result ->
                accessibilityProtectionPending = false
                accessibilityProtectionEnabled = result.enabled
                accessibilityGranted = isAgentAccessibilityEnabled(context)
                val failureMessage = when (result.status) {
                    AccessibilityProtectionClient.ControlStatus.APPLIED -> null
                    AccessibilityProtectionClient.ControlStatus.UNAVAILABLE ->
                        context.getString(R.string.accessibility_protection_unavailable)
                    AccessibilityProtectionClient.ControlStatus.REJECTED ->
                        context.getString(R.string.accessibility_protection_rejected)
                }
                if (failureMessage != null) {
                    Toast.makeText(
                        context.applicationContext,
                        failureMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    CouiScaffoldPage(
        title = stringResource(R.string.ui_set_up_7debf9),
        onBack = onBack,
    ) {
        PreferenceCategory(title = stringResource(R.string.settings_llm_providers))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_model_provider_e8c7f5),
                        summary = providerSummary,
                        startAction = { Icon(imageVector = Icons.Rounded.Cloud, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.ModelProviders) },
                    )
                }
                item {
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.ui_deep_thinking_enabled_by_default_c032d6),
                        key = Prefs.Keys.AGENT_THINKING_ENABLED,
                        icon = Icons.Rounded.Psychology,
                        iconTint = SettingsIconColors.Blue,
                    )
                }
            }
        }

        PreferenceCategory(title = stringResource(R.string.settings_context_extensions))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.settings_auto_compaction),
                        key = Prefs.Keys.AGENT_AUTO_COMPACTION_ENABLED,
                        icon = Icons.Rounded.Layers,
                        iconTint = SettingsIconColors.Blue,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.conversation_title_settings_title),
                        startAction = { Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.ConversationTitleSettings) },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_memory_b55ff5),
                        startAction = {
                            Icon(imageVector = Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Orange)
                        },
                        onClick = { onNavigate(AppRoute.Memory) },
                    )
                }
                item {
                    ArrowPreference(
                        title = "Subagent",
                        startAction = { Icon(imageVector = Icons.Rounded.Hub, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.Subagents) },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.route_skills),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.ImportContacts, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Green)
                        },
                        onClick = { onNavigate(AppRoute.Skills) },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.route_mcp_servers),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue)
                        },
                        onClick = { onNavigate(AppRoute.McpServers) },
                    )
                }
            }
        }

        PreferenceCategory(title = stringResource(R.string.route_tools))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.settings_tools_list),
                        startAction = { Icon(imageVector = Icons.Rounded.Dashboard, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Green) },
                        onClick = { onNavigate(AppRoute.Tools) },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.web_tools_settings_title),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.TravelExplore, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue)
                        },
                        onClick = { onNavigate(AppRoute.WebToolsSettings) },
                    )
                }
                item {
                    SwitchPref(
                        context = context,
                        prefs = agentPrefs,
                        title = stringResource(R.string.settings_virtual_display_title),
                        key = Prefs.Keys.AGENT_VIRTUAL_DISPLAY_ENABLED,
                        icon = Icons.Rounded.Layers,
                        iconTint = SettingsIconColors.Blue,
                    )
                }
            }
        }

        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.capability_enhancements),
                        startAction = { Icon(imageVector = Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.SystemEnhance) },
                    )
                }
            }
        }

        PreferenceCategory(title = stringResource(R.string.ui_system_assistant_takes_over_f46043))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_eta_system_assistant_003e9b),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.SupportAgent, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Green)
                        },
                        onClick = openAssistantSettings,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.assistant_overlay_settings_title),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue)
                        },
                        onClick = { onNavigate(AppRoute.AssistantOverlaySettings) },
                    )
                }
            }
        }







        PreferenceCategory(title = "Notifications")
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = "Notifications intelligentes",
                        summary = "Classification locale · modes et règles",
                        startAction = { Icon(imageVector = Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.IntelligentNotifications) },
                    )
                }
            }
        }
        PreferenceCategory(title = "App improvements")
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = "Improve Jean-Calcul",
                        summary = "Report an issue, test its update and validate the PR",
                        startAction = { Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.AppImprovements) },
                    )
                }
            }
        }
        PreferenceCategory(title = stringResource(R.string.settings_general))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.appearance_title),
                        startAction = { Icon(imageVector = Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Orange) },
                        onClick = { onNavigate(AppRoute.AppearanceSettings) },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.speech_settings_title),
                        startAction = { Icon(imageVector = Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { onNavigate(AppRoute.SpeechSettings) },
                    )
                }
                item {
                    LanguagePreference(
                        iconTint = SettingsIconColors.Blue,
                        startAction = {
                            Icon(imageVector = Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue)
                        },
                    )
                }
            }
        }

        val accEnabled = accessibilityGranted || AgentAccessibilityService.isAvailable()
        PreferenceCategory(title = stringResource(R.string.ui_permissions_560165))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_floating_window_permissions_076b77),
                        startAction = { Icon(imageVector = Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        endActions = {
                            PreferenceValueText(value = stringResource(
                            if (overlayGranted) R.string.status_authorized else R.string.status_unauthorized,
                        ), color = if (overlayGranted) COUITheme.colorScheme.labelSecondary else COUITheme.colorScheme.error)
                        },
                        onClick = {
                            if (!overlayGranted) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_accessibility_enhancement_tools_8fd257),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.AccessibilityNew, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue)
                        },
                        endActions = {
                            PreferenceValueText(value = stringResource(
                            if (accEnabled) R.string.status_enabled else R.string.status_disabled,
                        ), color = if (accEnabled) COUITheme.colorScheme.labelSecondary else COUITheme.colorScheme.primary)
                        },
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                    )
                }
                if (AccessibilityProtectionClient.isSupported() && (prefs != null || hasConnectedFramework)) {
                    item {
                        SwitchPreference(
                            title = stringResource(R.string.ui_enforce_accessibility_55e838),
                            checked = accessibilityProtectionEnabled,
                            onCheckedChange = onAccessibilityProtectionToggle,
                            startAction = {
                                Icon(imageVector = Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (prefs != null && !accessibilityProtectionPending) SettingsIconColors.Blue else COUITheme.colorScheme.disabledNeutral)
                            },
                            enabled = prefs != null && !accessibilityProtectionPending,
                        )
                    }
                }
            }
        }

        PreferenceCategory(title = stringResource(R.string.ui_about_bed172))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    BasicComponent(
                        title = stringResource(R.string.ui_about_version_title),
                        summary = appVersionSummary,
                        startAction = { Icon(imageVector = Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_about_update_title),
                        summary = if (checkingUpdate) {
                            stringResource(R.string.ui_about_update_checking)
                        } else {
                            null
                        },
                        startAction = {
                            Icon(imageVector = Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (!checkingUpdate) SettingsIconColors.Green else COUITheme.colorScheme.disabledNeutral)
                        },
                        onClick = checkForUpdate,
                        enabled = !checkingUpdate,
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_about_feedback_title),
                        startAction = {
                            Icon(imageVector = Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Orange)
                        },
                        onClick = { openUrl("https://github.com/Loe159/Jean-Calcul-Assistant/issues") },
                    )
                }
                item {
                    ArrowPreference(
                        title = stringResource(R.string.ui_about_github_star_title),
                        summary = stringResource(R.string.ui_about_github_star_hint),
                        startAction = { Icon(imageVector = Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(24.dp), tint = SettingsIconColors.Blue) },
                        onClick = { openUrl("https://github.com/Loe159/Jean-Calcul-Assistant") },
                    )
                }
            }
        }

        CouiPageBottomSpacer()
    }

    SystemizerConfirmDialog(
        show = showSystemizerDialog,
        installing = installingSystemizer,
        onDismissRequest = {
            if (!installingSystemizer) {
                showSystemizerDialog = false
            }
        },
        onConfirm = {
            if (installingSystemizer) return@SystemizerConfirmDialog
            if (!capabilities.root.isGranted) {
                showSystemizerDialog = false
                onNavigate(AppRoute.SystemEnhance)
                return@SystemizerConfirmDialog
            }
            enhancementHistory.recordSystemizerUse()
            hasUsedSystemizer = true
            showSystemizerDialog = false
            installingSystemizer = true
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    GoogleAppSystemizerInstaller(context.applicationContext).install()
                }
                installingSystemizer = false
                Toast.makeText(
                    context.applicationContext,
                    result.toToastMessage(context),
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
    )

}

@Composable
private fun SystemizerConfirmDialog(
    show: Boolean,
    installing: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val confirmText = if (installing) {
        stringResource(R.string.status_processing)
    } else {
        stringResource(R.string.action_confirm)
    }
    CouiConfirmDialog(
        visible = show,
        title = stringResource(R.string.ui_convert_google_apps_to_system_apps_0f6d89),
        message = stringResource(R.string.ui_system_applications_have_voice_wake_up_permissions_f_0190f2),
        confirmText = confirmText,
        confirmEnabled = !installing,
        cancelEnabled = !installing,
        onConfirm = onConfirm,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun SwitchPref(
    context: Context,
    prefs: SharedPreferences?,
    title: String,
    summary: String? = null,
    key: String,
    icon: ImageVector,
    iconTint: Color,
) {
    val enabled = prefs != null
    val history = remember(context.applicationContext) { EnhancementSettingsHistory(context) }
    val default = Prefs.Keys.BOOLEAN_DEFAULTS[key] ?: true
    var checked by remember(prefs, key) {
        mutableStateOf(prefs?.getBoolean(key, default) ?: history.checked(key, default))
    }
    DisposableEffect(prefs, key) {
        val targetPrefs = prefs ?: return@DisposableEffect onDispose {}
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, changedKey ->
            if (changedKey == key) {
                checked = changedPrefs.getBoolean(key, default)
            }
        }
        targetPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { targetPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val onToggle: (Boolean) -> Unit = { value ->
        val targetPrefs = prefs
        if (targetPrefs != null) {
            if (putBooleanSync(targetPrefs, key, value)) {
                checked = value
                history.recordCommittedBoolean(key, value)
                if (key in Prefs.Keys.LOCAL_AGENT_KEYS) {
                    Prefs.reconcileAgentPreferences(EtaApp.serviceInstance)
                }
            } else {
                Toast.makeText(
                    context.applicationContext,
                    context.getString(R.string.settings_write_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onToggle,
        startAction = { Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (enabled) iconTint else COUITheme.colorScheme.disabledNeutral) },
        enabled = enabled,
    )
}

private fun putBooleanSync(
    prefs: SharedPreferences,
    key: String,
    value: Boolean,
): Boolean =
    runCatching { prefs.edit().putBoolean(key, value).commit() }.getOrDefault(false)

private fun putStringSync(
    prefs: SharedPreferences,
    key: String,
    value: String,
): Boolean =
    runCatching { prefs.edit().putString(key, value).commit() }.getOrDefault(false)

private fun PowerAssistantTarget.displayName(context: Context): String =
    when (this) {
        PowerAssistantTarget.OEM -> context.getString(R.string.power_assistant_system_default)
        PowerAssistantTarget.GEMINI -> "Gemini"
        PowerAssistantTarget.ETA -> "Jean-Calcul"
    }

private fun isAgentAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(
        context,
        AgentAccessibilityService::class.java,
    ).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun SystemizerInstallResult.toToastMessage(context: Context): String =
    when (this) {
        SystemizerInstallResult.AlreadySystemized -> context.getString(R.string.systemizer_already_system)
        SystemizerInstallResult.GoogleAppMissing -> context.getString(R.string.systemizer_google_missing)
        SystemizerInstallResult.UnsupportedRootManager -> context.getString(R.string.systemizer_root_manager_missing)
        SystemizerInstallResult.KernelSuMetamoduleMissing -> context.getString(R.string.systemizer_metamodule_missing)
        is SystemizerInstallResult.RootPermissionUnavailable -> when (rootManager) {
            RootManager.KERNEL_SU -> context.getString(R.string.systemizer_grant_kernelsu)
            RootManager.MAGISK -> context.getString(R.string.systemizer_grant_magisk)
            RootManager.UNSUPPORTED -> context.getString(R.string.systemizer_root_denied)
        }
        is SystemizerInstallResult.InstalledRebootRequired -> context.getString(R.string.systemizer_installed)
        is SystemizerInstallResult.Failed -> commandOutput
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?.let { "$message：$it" }
            ?: message
    }
