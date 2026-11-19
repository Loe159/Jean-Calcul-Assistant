package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.LocalCardListPosition
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * One row of a card group that is too long for a single `CardListGroup`, emitted as its own lazy
 * item: the card segment (rounded only at the group's ends) and the row position, as in the COUI
 * lazy card list sample. Rows read the position through [LocalCardListPosition].
 */
@Composable
internal fun EtaPreferenceGroupItem(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    hasLeadingIcon: Boolean = false,
    content: @Composable () -> Unit,
) {
    val radius = CardDefaults.CornerRadius
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(radius)
        isFirst -> RoundedCornerShape(topStart = radius, topEnd = radius)
        isLast -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        else -> RectangleShape
    }
    val position = when {
        isFirst && isLast -> CardListPosition.Full
        isFirst -> CardListPosition.Head
        isLast -> CardListPosition.Tail
        else -> CardListPosition.Middle
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = if (isLast) 16.dp else 0.dp)
            .clip(shape)
            .background(COUITheme.colorScheme.cardBackground),
    ) {
        CompositionLocalProvider(LocalCardListPosition provides position) {
            content()
        }
        if (!isLast) {
            HorizontalDivider(
                modifier = Modifier.padding(start = if (hasLeadingIcon) 56.dp else 16.dp, end = 16.dp),
            )
        }
    }
}
