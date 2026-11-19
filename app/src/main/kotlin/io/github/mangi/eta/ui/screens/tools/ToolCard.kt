package io.github.mangi.eta.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.utils.CouiHapticEffect
import io.github.suqi8.coui.kmp.utils.rememberCouiHaptic
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.tool.AgentToolCapabilities
import io.github.mangi.eta.agent.tool.RootRequirement
import io.github.mangi.eta.ui.components.ItemDescriptionDialog
import io.github.mangi.eta.ui.components.iconForTool
import io.github.mangi.eta.ui.model.AgentToolsAction
import io.github.mangi.eta.ui.model.ToolItemUi
import io.github.mangi.eta.ui.model.actualToolName
import io.github.mangi.eta.ui.model.toolCardAction
import io.github.mangi.eta.ui.model.toolCardRequirement
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import androidx.compose.foundation.LocalIndication

@Composable
internal fun ToolSwitchRow(
    tool: ToolItemUi,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    rootGranted: Boolean,
    capabilities: AgentToolCapabilities,
    onAction: (AgentToolsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDescription by remember(tool.id) { mutableStateOf(false) }
    val description = if (!rootGranted && tool.id == "terminal") {
        stringResource(R.string.capability_terminal_ordinary_summary)
    } else {
        tool.summary
    }
    val requirementText = toolRequirementText(tool.id, rootGranted, capabilities)
    val action = toolCardAction(tool.id, capabilities)
    val actionLabel = when (action) {
        AgentToolsAction.OpenWebToolsSettings -> stringResource(R.string.web_tools_configure_action)
        AgentToolsAction.OpenBrowser -> stringResource(R.string.action_open_browser)
        AgentToolsAction.OpenPermissions -> stringResource(R.string.tools_manage_permissions)
        AgentToolsAction.OpenEnhancements -> null
        else -> null
    }
    val iconVector = iconForTool(tool.id)

    val dark = COUITheme.isDark
    val haptic = rememberCouiHaptic()
    val interactionSource = remember { MutableInteractionSource() }
    var optimisticChecked by remember(checked) { mutableStateOf(checked) }
    val commitToggle: (Boolean) -> Unit = { target ->
        optimisticChecked = target
        onCheckedChange(target)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = optimisticChecked,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = true,
                role = Role.Switch,
                onValueChange = { target ->
                    haptic(CouiHapticEffect.Switch)
                    commitToggle(target)
                },
            )
            .padding(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (optimisticChecked) COUITheme.colorScheme.primary else COUITheme.colorScheme.labelSecondary.copy(alpha = 0.65f),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = tool.title,
                    style = COUITheme.textStyles.headlineXS,
                    color = COUITheme.colorScheme.labelPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (requirementText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(COUITheme.colorScheme.labelPrimary.copy(alpha = 0.06f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = requirementText,
                            style = COUITheme.textStyles.bodyS,
                            color = COUITheme.colorScheme.labelSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Text(
                text = description,
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (action != null && actionLabel != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { onAction(action) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = actionLabel,
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = COUITheme.colorScheme.primary,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { showDescription = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = stringResource(R.string.ui_description_named, tool.title),
                modifier = Modifier.size(16.dp),
                tint = COUITheme.colorScheme.labelSecondary.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = optimisticChecked,
            onCheckedChange = commitToggle,
        )
    }

    if (showDescription) {
        ItemDescriptionDialog(
            title = tool.title,
            description = listOfNotNull(description, requirementText).joinToString("\n\n"),
            onDismiss = { showDescription = false },
        )
    }
}

@Composable
private fun toolRequirementText(id: String, rootGranted: Boolean, capabilities: AgentToolCapabilities): String? {
    val requirement = toolCardRequirement(id)
    val unavailableCode = capabilities.unavailableCode(actualToolName(id))
    return when {
        !rootGranted && requirement.rootRequirement == RootRequirement.REQUIRED -> stringResource(R.string.capability_root_required)
        !capabilities.accessibilityAvailable && requirement.accessibility -> stringResource(R.string.capability_accessibility_required)
        unavailableCode == "NOTIFICATION_ACCESS_REQUIRED" -> stringResource(R.string.capability_notification_access_required)
        unavailableCode == "APP_USAGE_ACCESS_REQUIRED" -> stringResource(R.string.capability_usage_access_required)
        unavailableCode == "LOCATION_PERMISSION_REQUIRED" -> stringResource(R.string.capability_location_access_required)
        requirement.colorOs -> stringResource(R.string.capability_coloros_required)
        !rootGranted && requirement.rootRequirement == RootRequirement.PARTIAL -> stringResource(R.string.capability_root_partial)
        else -> null
    }
}
