package fr.loevan.jeancalcul.v2

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.relocation.BringIntoViewModifierNode

/**
 * The composer is positioned above the IME by its parent. The text field may
 * scroll internally, but cursor relocation must not pan the entire Android
 * window and move the header off screen.
 */
internal object ComposerViewport : ModifierNodeElement<ComposerViewportNode>() {
    override fun create() = ComposerViewportNode()
    override fun update(node: ComposerViewportNode) = Unit
    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

internal class ComposerViewportNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        // Consume only at this fixed viewport boundary, after the field's own
        // scrolling responders. Insets, not ViewRoot cursor panning, own layout.
    }
}
