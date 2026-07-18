@file:Suppress(
    "FunctionNaming",
    "LongParameterList",
    "MaxLineLength",
    "TooManyFunctions",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package fr.loevan.jeancalcul.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

enum class JeanCalculButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Destructive,
}

@Composable
fun JeanCalculButton(
    label: String,
    modifier: Modifier = Modifier,
    variant: JeanCalculButtonVariant = JeanCalculButtonVariant.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val targetModifier = modifier.defaultMinSize(minHeight = JeanCalculDesign.tokens.spacing.touchTarget)
    when (variant) {
        JeanCalculButtonVariant.Primary ->
            Button(modifier = targetModifier, enabled = enabled, onClick = onClick) {
                Text(label)
            }
        JeanCalculButtonVariant.Secondary ->
            OutlinedButton(modifier = targetModifier, enabled = enabled, onClick = onClick) { Text(label) }

        JeanCalculButtonVariant.Ghost ->
            TextButton(modifier = targetModifier, enabled = enabled, onClick = onClick) {
                Text(label)
            }
        JeanCalculButtonVariant.Destructive ->
            Button(
                modifier = targetModifier,
                enabled = enabled,
                onClick = onClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    ),
            ) { Text(label) }
    }
}

@Composable
fun CircularIconButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = modifier.size(JeanCalculDesign.tokens.spacing.touchTarget),
        variant = GlassSurfaceVariant.Overlay,
        state = if (enabled) GlassSurfaceState.Normal else GlassSurfaceState.Disabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        onClick = if (enabled) onClick else null,
    ) {
        Text(text = label, modifier = Modifier.align(Alignment.Center), maxLines = 1)
    }
}

data class NavigationItem(
    val id: String,
    val label: String,
)

@Composable
fun FloatingBottomNavigation(
    items: List<NavigationItem>,
    selectedId: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), variant = GlassSurfaceVariant.Navigation) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                TextButton(
                    modifier = Modifier.defaultMinSize(minHeight = JeanCalculDesign.tokens.spacing.touchTarget),
                    onClick = { onSelect(item.id) },
                ) {
                    Text(if (item.id == selectedId) "• ${item.label}" else item.label)
                }
            }
        }
    }
}

@Composable
fun AssistantInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Écrire à Jean Calcul",
    onSend: (() -> Unit)? = null,
    onVoice: (() -> Unit)? = null,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), variant = GlassSurfaceVariant.Overlay) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
                singleLine = false,
                maxLines = 3,
            )
            onVoice?.let { CircularIconButton(label = "Voix", onClick = it) }
            onSend?.let { CircularIconButton(label = "Envoyer", onClick = it) }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        GlassSurface(variant = GlassSurfaceVariant.Panel) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    state: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            description?.let { Text(text = it, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium) }
            state?.let { Text(text = it, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
        }
        trailing()
    }
}

data class SegmentedControlOption(
    val id: String,
    val label: String,
)

@Composable
fun SegmentedControl(
    options: List<SegmentedControlOption>,
    selectedId: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            GlassSurface(
                modifier =
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = JeanCalculDesign.tokens.spacing.touchTarget)
                        .selectable(selected = option.id == selectedId, onClick = { onSelect(option.id) })
                        .semantics { contentDescription = option.label },
                variant = if (option.id == selectedId) GlassSurfaceVariant.Selected else GlassSurfaceVariant.Interactive,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                Text(text = option.label, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun JeanCalculToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = JeanCalculDesign.tokens.spacing.touchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(text = it, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier =
                Modifier.semantics {
                    contentDescription = "$label : ${if (checked) "activé" else "désactivé"}"
                },
        )
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.defaultMinSize(minHeight = JeanCalculDesign.tokens.spacing.touchTarget),
        variant = if (selected) GlassSurfaceVariant.Selected else GlassSurfaceVariant.Interactive,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick,
    ) {
        Text(text = label, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun JeanCalculTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = JeanCalculDesign.tokens.spacing.touchTarget),
        label = { Text(label) },
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        visualTransformation = visualTransformation,
    )
}

enum class ContentState {
    Empty,
    Loading,
    Error,
}

@Composable
fun ContentStateMessage(
    state: ContentState,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        variant = GlassSurfaceVariant.Panel,
        state = if (state == ContentState.Error) GlassSurfaceState.Error else GlassSurfaceState.Normal,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text(text = message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            onRetry?.let {
                JeanCalculButton(
                    label = "Réessayer",
                    variant = JeanCalculButtonVariant.Secondary,
                    onClick = it,
                )
            }
        }
    }
}
