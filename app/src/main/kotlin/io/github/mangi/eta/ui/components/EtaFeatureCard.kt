package io.github.mangi.eta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.foundation.LocalIndication
import io.github.suqi8.coui.kmp.squircle.SquircleShape

@Composable
internal fun EtaFeatureCard(
    title: String,
    summary: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dark = COUITheme.isDark
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 136.dp)
            .clip(SquircleShape(24.dp))
            .background(COUITheme.colorScheme.card)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = COUITheme.textStyles.headlineXS,
            color = COUITheme.colorScheme.labelPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = summary,
            style = COUITheme.textStyles.bodyS,
            color = COUITheme.colorScheme.labelSecondary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        footer?.invoke(this)
    }
}
