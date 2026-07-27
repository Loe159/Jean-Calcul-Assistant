@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.feature.conversation.ConversationScreen
import fr.loevan.jeancalcul.feature.conversation.ConversationScreenActions
import fr.loevan.jeancalcul.feature.conversation.ConversationUiState
import fr.loevan.jeancalcul.ui.FloatingBottomNavigation
import fr.loevan.jeancalcul.ui.NavigationItem

@Composable
internal fun mainAppContent(
    assistantRoleStatus: AssistantRoleStatus,
    microphonePermissionGranted: Boolean,
    conversationState: ConversationUiState,
    onboardingActions: OnboardingActions,
    conversationActions: ConversationScreenActions,
) {
    var page by rememberSaveable { mutableStateOf(PAGE_ASSISTANT) }
    Box(modifier = Modifier.fillMaxSize()) {
        when (page) {
            PAGE_CONVERSATIONS ->
                ConversationScreen(
                    state = conversationState,
                    actions = conversationActions,
                    modifier = Modifier.padding(bottom = 80.dp),
                )

            else ->
                assistantRoleOnboarding(
                    status = assistantRoleStatus,
                    microphonePermissionGranted = microphonePermissionGranted,
                    onRequestRole = onboardingActions.requestRole,
                    onRequestMicrophonePermission = onboardingActions.requestMicrophonePermission,
                    onOpenSystemSettings = onboardingActions.openSystemSettings,
                )
        }
        FloatingBottomNavigation(
            items =
                listOf(
                    NavigationItem(PAGE_ASSISTANT, "Assistant"),
                    NavigationItem(PAGE_CONVERSATIONS, "Conversations"),
                ),
            selectedId = page,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            onSelect = { page = it },
        )
    }
}

internal data class OnboardingActions(
    val requestRole: () -> Unit,
    val requestMicrophonePermission: () -> Unit,
    val openSystemSettings: () -> Unit,
)

private const val PAGE_ASSISTANT = "assistant"
private const val PAGE_CONVERSATIONS = "conversations"
