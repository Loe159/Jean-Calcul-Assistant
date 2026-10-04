package fr.loevan.jeancalcul.feature.conversation

data class ConversationScreenActions(
    val selectConversation: (String) -> Unit,
    val newConversation: () -> Unit,
    val deleteConversation: () -> Unit,
    val draftChanged: (String) -> Unit,
    val send: () -> Unit,
    val cancel: () -> Unit,
    val retry: (String) -> Unit,
    val export: () -> Unit,
)
