package io.github.mangi.eta.ui.preview

import io.github.mangi.eta.ui.model.AgentChatHomeUiState
import io.github.mangi.eta.ui.model.AgentChatMessageUi
import io.github.mangi.eta.ui.model.AgentChatUiState
import io.github.mangi.eta.ui.model.AgentToolsUiState
import io.github.mangi.eta.ui.model.ConversationModeUi
import io.github.mangi.eta.ui.model.ConversationPaneUiState
import io.github.mangi.eta.ui.model.ConversationSummaryUi
import io.github.mangi.eta.ui.model.PermissionHealthItemUi
import io.github.mangi.eta.ui.model.PermissionHealthUiState
import io.github.mangi.eta.ui.model.PermissionStatusUi
import io.github.mangi.eta.ui.model.ThinkingMessageUi
import io.github.mangi.eta.ui.model.TokenUsageUi
import io.github.mangi.eta.ui.model.ToolActivityMessageUi
import io.github.mangi.eta.ui.model.ToolActivityStatusUi
import io.github.mangi.eta.ui.model.ToolGroupUi
import io.github.mangi.eta.ui.model.ToolItemUi
import io.github.mangi.eta.ui.model.UserMessageUi
import io.github.mangi.eta.ui.model.AgentMessageUi

internal object FakeAgentUiStates {

    val conversations = ConversationPaneUiState(
        selectedConversationId = "c-001",
        searchQuery = "",
        conversations = listOf(
            ConversationSummaryUi(
                id = "c-001",
                title = "Let Jean-Calcul control your phone",
                preview = "Ready to use the screen and tools for your next task",
                timeLabel = "Now",
                mode = ConversationModeUi.PhoneAgent,
                isPinned = true,
                isActiveRun = true,
            ),
            ConversationSummaryUi(
                id = "c-002",
                title = "Today's schedule and reminders",
                preview = "Check the weather, sync the calendar and set a departure reminder",
                timeLabel = "10:41",
                mode = ConversationModeUi.Chat,
                isPinned = true,
            ),
            ConversationSummaryUi(
                id = "c-003",
                title = "Open the music app and play daily recommendations",
                preview = "Completed 4 tool calls in 8 seconds",
                timeLabel = "10:23",
                mode = ConversationModeUi.PhoneAgent,
            ),
            ConversationSummaryUi(
                id = "c-004",
                title = "Analyze the current screenshot",
                preview = "Summarize the page structure and identify button hierarchy issues",
                timeLabel = "Yesterday",
                mode = ConversationModeUi.Chat,
            ),
            ConversationSummaryUi(
                id = "c-005",
                title = "Check the Alpine terminal environment",
                preview = "List commands, Python/Node versions and background jobs",
                timeLabel = "Friday",
                mode = ConversationModeUi.Terminal,
            ),
            ConversationSummaryUi(
                id = "c-006",
                title = "Organize downloads every evening",
                preview = "Scan and sort images, installers and documents on a schedule",
                timeLabel = "5-18",
                mode = ConversationModeUi.Automation,
            ),
        ),
    )

    val chatHome: AgentChatHomeUiState = AgentChatHomeUiState(
        messages = emptyList(),
        input = "",
        isStreaming = false,
        thinkingEnabled = false,
    )

    val chat = AgentChatUiState(
        messages = listOf(
            UserMessageUi(
                id = "m-01",
                content = "Open battery optimization settings",
            ),
            AgentMessageUi(
                id = "m-02",
                content = "The phone currently shows the **home screen**:\n\n| Item | Content |\n| --- | --- |\n| Time | 09:55 |\n| Network | 5G + Wi-Fi |\n| Battery | 68% |",
                usage = TokenUsageUi(
                    contextTokens = 17100,
                    inputTokens = 9000,
                    outputTokens = 111,
                    reasoningTokens = 8200,
                ),
            ),
            ThinkingMessageUi(
                id = "thinking-01",
                content = "The user wants to inspect the screen. Call observe_screen first, then summarize the visible content.",
                isStreaming = false,
                elapsedSeconds = 24,
                collapsed = true,
            ),
            ToolActivityMessageUi(
                id = "tools-01",
                toolName = "observe_screen",
                status = ToolActivityStatusUi.Success,
                argumentsSummary = "{\"include_screenshot\":true}",
                resultSummary = "ok=true, chars=1820, images=1",
                imageCount = 1,
            ),
        ),
        input = "",
        isStreaming = false,
        thinkingEnabled = true,
    )

    val permissionHealth = PermissionHealthUiState(
        items = listOf(
            PermissionHealthItemUi(
                id = "accessibility",
                title = "Accessibility service",
                summary = "Enabled; the agent can interact with the UI",
                status = PermissionStatusUi.Available,
                primaryActionLabel = null,
            ),
            PermissionHealthItemUi(
                id = "overlay",
                title = "Overlay permission",
                summary = "Granted; task overlays are available",
                status = PermissionStatusUi.Available,
                primaryActionLabel = null,
            ),
            PermissionHealthItemUi(
                id = "notification",
                title = "Notification permission",
                summary = "Notify when background tasks finish",
                status = PermissionStatusUi.Available,
                primaryActionLabel = null,
            ),
            PermissionHealthItemUi(
                id = "location",
                title = "Location permission",
                summary = "Read only when the agent uses the location tool",
                status = PermissionStatusUi.Available,
                primaryActionLabel = null,
            ),
            PermissionHealthItemUi(
                id = "root",
                title = "Root permission",
                summary = "Not granted; some terminal commands are restricted",
                status = PermissionStatusUi.Warning,
                primaryActionLabel = "Check",
            ),
            PermissionHealthItemUi(
                id = "shizuku",
                title = "Shizuku",
                summary = "Not configured; ADB capabilities are unavailable",
                status = PermissionStatusUi.Disabled,
                primaryActionLabel = "Configure",
            ),
            PermissionHealthItemUi(
                id = "xposed",
                title = "Hook / Xposed",
                summary = "Framework inactive; system enhancements are unavailable",
                status = PermissionStatusUi.Missing,
                primaryActionLabel = "View",
            ),
            PermissionHealthItemUi(
                id = "background",
                title = "Background execution",
                summary = "Battery optimization may interrupt long tasks",
                status = PermissionStatusUi.Warning,
                primaryActionLabel = "Settings",
            ),
        ),
    )

    val tools = AgentToolsUiState(
        groups = listOf(
            ToolGroupUi(
                id = "screen",
                title = "Screen interaction",
                tools = listOf(
                    ToolItemUi("observe_screen", "Observe screen", "Read UI nodes and include the screenshot when needed"),
                    ToolItemUi("tap_element", "Tap", "Tap coordinates or an element"),
                    ToolItemUi("long_press", "Long press", "Long press an element"),
                    ToolItemUi("swipe", "Swipe", "Swipe, scroll and navigation gestures"),
                ),
            ),
            ToolGroupUi(
                id = "input",
                title = "Text and keys",
                tools = listOf(
                    ToolItemUi("input_text", "Type text", "Type into the focused field"),
                    ToolItemUi("paste_text", "Paste text", "Insert text using the clipboard"),
                    ToolItemUi("wait_for_text", "Wait for text", "Wait for text to appear on screen"),
                ),
            ),
            ToolGroupUi(
                id = "web",
                title = "Web browsing",
                tools = listOf(
                    ToolItemUi("browser_use", "Agent browser", "Browse offscreen with a session you can take over"),
                    ToolItemUi("browser_read", "Read page", "Extract content from the rendered page"),
                    ToolItemUi("browser_interact", "Web interaction", "Find, click and type into elements"),
                    ToolItemUi("browser_screenshot", "Page screenshot", "Send the browser viewport to the vision model"),
                ),
            ),
            ToolGroupUi(
                id = "app",
                title = "Apps and URIs",
                tools = listOf(
                    ToolItemUi("get_current_context", "Time and location", "Read system time and recent location"),
                    ToolItemUi("launch_app", "Open app", "Launch an app by package name"),
                    ToolItemUi("open_uri", "Open with app", "Hand off to an external app"),
                ),
            ),
            ToolGroupUi(
                id = "terminal",
                title = "Terminal and files",
                tools = listOf(
                    ToolItemUi("terminal", "Terminal command", "Run shell commands in Android, Alpine or Debian"),
                ),
            ),
        ),
    )
}
