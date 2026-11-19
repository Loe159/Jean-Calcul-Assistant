package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.EmptyStateSizeType
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.squircle.squircleBackground
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * Empty list placeholder built on the native COUI [EmptyState]; the optional [icon] becomes a
 * compact illustration tile so a list without content stays lighter than the real content.
 */
@Composable
internal fun ListEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    EmptyState(
        title = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        subtitle = summary,
        actionText = actionText,
        onActionClick = onActionClick,
        sizeType = EmptyStateSizeType.Medium,
        illustrationWidth = 56.dp,
        illustrationHeight = 56.dp,
        illustration = icon?.let { vector ->
            {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .squircleBackground(COUITheme.colorScheme.surfaceWithCard, 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = vector,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = COUITheme.colorScheme.labelSecondary,
                    )
                }
            }
        },
    )
}
