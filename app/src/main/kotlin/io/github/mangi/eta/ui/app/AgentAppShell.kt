package io.github.mangi.eta.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.mangi.eta.data.model.TtsProvider
import io.github.mangi.eta.data.repository.SpeechSettingsRepository
import io.github.mangi.eta.ui.components.AgentTopBarChatHeader
import io.github.mangi.eta.ui.components.ChatActionIcons
import io.github.mangi.eta.ui.components.ChatImagePreviewHost
import io.github.mangi.eta.ui.components.ConversationSidePaneScaffold
import io.github.mangi.eta.ui.components.RoleplayChatUi
import io.github.mangi.eta.ui.components.coui.CouiBackButton
import io.github.mangi.eta.ui.screens.home.NotificationFilter
import io.github.mangi.eta.ui.screens.home.NowBriefScreen
import io.github.mangi.eta.ui.model.AgentModelPickerUiState
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.navigation.AppRoute
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.TopAppBarDefaults
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Agent App 统一壳层。
 *
 * - 负责全局 Scaffold、状态栏/横向安全边距、顶层工具栏。
 * - 首页工具栏只保留历史入口与溢出菜单，保持聊天舞台干净。
 * - 非首页子路由统一提供返回按钮与标题，避免每个页面各自像独立设置页。
 * - Settings 由标准二级页骨架自己提供 TopAppBar，壳层在此路由不重复绘制。
 */
@Composable
internal fun AgentAppShell(
    currentRoute: AppRoute?,
    isCurrentRoute: Boolean,
    conversationPaneState: ConversationPaneUiState?,
    isConversationPaneOpen: Boolean,
    onBack: () -> Unit,
    onOpenConversationPane: () -> Unit,
    onDismissConversationPane: () -> Unit,
    onSearchConversations: (String) -> Unit,
    onNewConversation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onLaunchKimiWeb: () -> Unit,
    kimiWebLabel: String,
    canStopKimiWeb: Boolean,
    onStopKimiWeb: () -> Unit,
    onRefreshKimiWeb: () -> Unit,
    onOpenBrowser: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onConversationTogglePin: (ConversationSummaryUi) -> Unit,
    onConversationRename: (ConversationSummaryUi) -> Unit,
    onConversationExport: (ConversationSummaryUi) -> Unit,
    onConversationDelete: (ConversationSummaryUi) -> Unit,
    onOpenTools: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenCharacters: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFoodHome: () -> Unit,
    onOpenFoodRoute: (AppRoute) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenModelProviders: () -> Unit,
    modifier: Modifier = Modifier,
    homeTitle: String? = null,
    homeCharacter: RoleplayChatUi? = null,
    homeModelPickerState: AgentModelPickerUiState? = null,
    isHomeStreaming: Boolean = false,
    hasPermissionAttention: Boolean = false,
    onHomeModelSelected: (String) -> Unit = {},
    onOpenCharacter: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var briefOpen by remember { mutableStateOf(false) }
    var briefNotificationPage by remember { mutableStateOf<NotificationFilter?>(null) }
    val closeBrief: () -> Unit = {
        briefNotificationPage = null
        briefOpen = false
    }
    val pageContent: @Composable () -> Unit = {
        val isHome = currentRoute is AppRoute.Home
        val barColor = COUITheme.colorScheme.background
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = barColor,
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
            topBar = {
                when {
                    currentRoute is AppRoute.Settings -> Unit
                    isHome -> HomeTopBar(
                        color = barColor,
                        onOpenConversationPane = onOpenConversationPane,
                        onOpenBrief = { briefOpen = true },
                        onNewConversation = onNewConversation,
                    ) {
                        if (homeModelPickerState != null) {
                            AgentTopBarChatHeader(
                                conversationTitle = homeTitle,
                                character = homeCharacter,
                                state = homeModelPickerState,
                                isStreaming = isHomeStreaming,
                                onModelSelected = onHomeModelSelected,
                                onOpenCharacter = onOpenCharacter,
                            )
                        } else {
                            Text(
                                text = titleForRoute(currentRoute),
                                style = COUITheme.textStyles.headlineS,
                                color = COUITheme.colorScheme.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    else -> SmallTopAppBar(
                        title = titleForRoute(currentRoute),
                        color = barColor,
                        navigationIcon = { CouiBackButton(onClick = onBack) },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                content(padding)
            }
        }
    }

    ChatImagePreviewHost(modifier = modifier) {
        if (conversationPaneState != null && currentRoute is AppRoute.Home) {
            ConversationSidePaneScaffold(
                state = conversationPaneState,
                visible = isConversationPaneOpen,
                briefVisible = briefOpen,
                backHandlerEnabled = isCurrentRoute,
                onOpenBrief = { briefOpen = true },
                onDismissBrief = closeBrief,
                onBackFromBriefPage = if (briefNotificationPage != null) ({ briefNotificationPage = null }) else null,
                onOpen = onOpenConversationPane,
                onDismiss = onDismissConversationPane,
                onSearchChange = onSearchConversations,
                onNewConversation = onNewConversation,
                onConversationSelected = onSelectConversation,
                onConversationTogglePin = onConversationTogglePin,
                onConversationRename = onConversationRename,
                onConversationExport = onConversationExport,
                onConversationDelete = onConversationDelete,
                onOpenSettings = onOpenSettings,
                onOpenFood = onOpenFoodHome,
                onOpenModelProviders = onOpenModelProviders,
                onOpenTools = onOpenTools,
                onOpenSkills = onOpenSkills,
                onOpenCharacters = onOpenCharacters,
                onOpenPermissions = onOpenPermissions,
                activeModelLabel = homeModelPickerState?.selectedModel?.displayName,
                hasPermissionAttention = hasPermissionAttention,
                briefContent = { revealProgress, onItemTouchChange ->
                    NowBriefScreen(
                        visible = briefOpen,
                        notificationPage = briefNotificationPage,
                        onOpenNotifications = { briefNotificationPage = it },
                        onCloseNotifications = { briefNotificationPage = null },
                        revealProgress = revealProgress,
                        onNotificationTouchChange = onItemTouchChange,
                        onManageFiltering = onOpenNotifications,
                        onOpenCarSettings = {
                            closeBrief()
                            onOpenSettings()
                        },
                        onClose = closeBrief,
                        onOpenFoodRoute = { route ->
                            closeBrief()
                            onOpenFoodRoute(route)
                        },
                    )
                },
            ) {
                pageContent()
            }
        } else {
            pageContent()
        }
    }
}



/** The home bar: conversation pane, centered chat header, briefing and new conversation actions. */
@Composable
private fun HomeTopBar(
    color: Color,
    onOpenConversationPane: () -> Unit,
    onOpenBrief: () -> Unit,
    onNewConversation: () -> Unit,
    centerContent: @Composable () -> Unit,
) {
    val tint = COUITheme.colorScheme.labelPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .height(56.dp)
            .clipToBounds()
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = TopAppBarDefaults.HorizontalPaddingCompact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenConversationPane) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ShortText,
                contentDescription = stringResource(R.string.action_conversation_history),
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            centerContent()
        }
        IconButton(onClick = onOpenBrief) {
            Icon(
                imageVector = Icons.Rounded.DashboardCustomize,
                contentDescription = "Ouvrir mon briefing",
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
        IconButton(onClick = onNewConversation) {
            Icon(
                imageVector = ChatActionIcons.NewChat,
                contentDescription = stringResource(R.string.action_new_conversation),
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
    }
}



@Composable
private fun titleForRoute(route: AppRoute?): String = when (route) {
    is AppRoute.Home, is AppRoute.Browser -> ""
    is AppRoute.Terminal -> stringResource(R.string.route_terminal)
    AppRoute.FoodHome -> stringResource(R.string.food_home)
    is AppRoute.Inventory -> stringResource(R.string.food_inventory)
    is AppRoute.RecipeDetail -> stringResource(R.string.food_detail)
    is AppRoute.RecipeGenerate -> stringResource(R.string.food_generate)
    AppRoute.FoodPreferences -> stringResource(R.string.food_preferences)
    is AppRoute.CookingSession -> stringResource(R.string.food_cooking_session)
    is AppRoute.Tools -> stringResource(R.string.route_tools)
    is AppRoute.WebToolsSettings -> stringResource(R.string.web_tools_settings_title)
    is AppRoute.Skills -> stringResource(R.string.route_skills)
    is AppRoute.Subagents -> "Subagent"
    is AppRoute.ConversationTitleSettings -> stringResource(R.string.conversation_title_settings_title)
    is AppRoute.IntelligentNotifications -> "Notifications intelligentes"
    is AppRoute.AppImprovements -> "App improvements"
    is AppRoute.AssistantOverlaySettings -> stringResource(R.string.assistant_overlay_settings_title)
    is AppRoute.Characters -> "Characters"
    is AppRoute.CharacterDetail -> "Character details"
    is AppRoute.CharacterEditor -> "Edit character"
    is AppRoute.CharacterPersona -> "My persona"
    is AppRoute.CharacterMemory -> "Story memory"
    is AppRoute.Permissions -> stringResource(R.string.route_permissions)
    is AppRoute.SystemEnhance -> stringResource(R.string.route_system_enhancements)
    is AppRoute.Settings -> stringResource(R.string.route_settings)
    is AppRoute.SpeechSettings -> stringResource(R.string.speech_settings_title)
    is AppRoute.SpeechRecognition -> stringResource(R.string.speech_recognition_title)
    is AppRoute.SpeechSynthesis -> stringResource(R.string.speech_synthesis_title)
    is AppRoute.AppearanceSettings -> stringResource(R.string.appearance_title)
    is AppRoute.DataBackup -> stringResource(R.string.data_backup_title)
    is AppRoute.Memory -> stringResource(R.string.route_memory)
    is AppRoute.LinuxEnvironment -> stringResource(R.string.route_linux_environment)
    is AppRoute.Workspace -> stringResource(R.string.capability_workspace)
    is AppRoute.SharedFolders -> stringResource(R.string.route_shared_folders)
    is AppRoute.LinuxFiles -> stringResource(R.string.route_linux_files)
    is AppRoute.ModelProviders -> stringResource(R.string.route_model_providers)
    is AppRoute.CommunityCatalog -> "Add from models.dev"
    is AppRoute.CommunityCatalogProvider -> "Select model"
    is AppRoute.McpServers -> stringResource(R.string.route_mcp_servers)
    is AppRoute.McpServerDetail -> stringResource(R.string.route_mcp_server_detail)
    is AppRoute.ModelProviderDetail -> stringResource(R.string.route_provider_details)
    is AppRoute.ModelProviderNew -> stringResource(R.string.route_new_provider)
    null -> stringResource(R.string.app_name)
}
