@file:Suppress("FunctionNaming", "LongMethod", "ktlint:standard:function-naming")

package fr.loevan.jeancalcul.feature.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.loevan.jeancalcul.domain.Message
import fr.loevan.jeancalcul.domain.MessageRole
import fr.loevan.jeancalcul.domain.MessageStatus
import fr.loevan.jeancalcul.ui.AssistantBubble
import fr.loevan.jeancalcul.ui.AssistantBubbleActions
import fr.loevan.jeancalcul.ui.AssistantBubbleKind
import fr.loevan.jeancalcul.ui.AssistantInputBar
import fr.loevan.jeancalcul.ui.ContentState
import fr.loevan.jeancalcul.ui.ContentStateMessage
import fr.loevan.jeancalcul.ui.FilterChip
import fr.loevan.jeancalcul.ui.JeanCalculButton
import fr.loevan.jeancalcul.ui.JeanCalculButtonVariant

@Composable
fun ConversationScreen(
    state: ConversationUiState,
    actions: ConversationScreenActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Conversations", style = MaterialTheme.typography.headlineMedium)
            JeanCalculButton(label = "Nouvelle", onClick = actions.newConversation)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.conversations, key = { it.id }) { conversation ->
                FilterChip(
                    label = conversation.title,
                    selected = conversation.id == state.selectedConversationId,
                    onClick = { actions.selectConversation(conversation.id) },
                )
            }
        }
        if (state.messages.isEmpty()) {
            ContentStateMessage(
                state = ContentState.Empty,
                title = "Conversation locale",
                message = "Écrivez un message. Le fournisseur actif diffusera sa réponse ici lorsqu'il sera configuré.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = Message::id) { message -> conversationMessage(message) }
            }
        }
        state.errorMessage?.let {
            ContentStateMessage(ContentState.Error, "Export impossible", it)
        }
        AssistantInputBar(
            value = state.draft,
            onValueChange = actions.draftChanged,
            onSend = actions.send,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JeanCalculButton(
                label = "Exporter",
                modifier = Modifier.weight(1f),
                variant = JeanCalculButtonVariant.Secondary,
                enabled = state.selectedConversationId != null,
                onClick = actions.export,
            )
            JeanCalculButton(
                label = "Supprimer",
                modifier = Modifier.weight(1f),
                variant = JeanCalculButtonVariant.Destructive,
                enabled = state.selectedConversationId != null,
                onClick = actions.deleteConversation,
            )
        }
    }
}

@Composable
private fun conversationMessage(message: Message) {
    AssistantBubble(
        kind = message.bubbleKind(),
        text = message.text.ifBlank { "…" },
        metadata = message.status.name.lowercase(),
        actions =
            AssistantBubbleActions(
                onStop = null,
                onRetry = null,
            ),
    )
}

private fun Message.bubbleKind(): AssistantBubbleKind =
    when {
        status == MessageStatus.STREAMING -> AssistantBubbleKind.Streaming
        status == MessageStatus.INTERRUPTED -> AssistantBubbleKind.Interrupted
        status == MessageStatus.FAILED -> AssistantBubbleKind.Error
        role == MessageRole.USER -> AssistantBubbleKind.User
        role == MessageRole.ASSISTANT -> AssistantBubbleKind.Assistant
        role == MessageRole.TOOL -> AssistantBubbleKind.Tool
        else -> AssistantBubbleKind.System
    }
