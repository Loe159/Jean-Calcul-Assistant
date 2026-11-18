package io.github.mangi.eta.ui.components

internal data class ConversationPaneMotion(
    val progress: Float,
    val foregroundOffset: Float,
    val panelOffset: Float,
)

internal fun conversationPaneMotion(offset: Float, paneWidth: Float): ConversationPaneMotion {
    val width = paneWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
    val displacement = if (offset.isFinite()) offset.coerceIn(0f, width) else 0f
    return ConversationPaneMotion(
        progress = if (width > 0f) displacement / width else 0f,
        foregroundOffset = displacement,
        panelOffset = displacement - width,
    )
}

/** Mirrored fullscreen panel for Now Brief, adjacent to the chat during every drag frame. */
internal fun briefPaneMotion(offset: Float, viewportWidth: Float): ConversationPaneMotion {
    val width = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
    val displacement = if (offset.isFinite()) offset.coerceIn(-width, 0f) else 0f
    return ConversationPaneMotion(
        progress = if (width > 0f) -displacement / width else 0f,
        foregroundOffset = displacement,
        panelOffset = displacement + width,
    )
}
