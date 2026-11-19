package io.github.mangi.eta.ui.screens.skills

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.ItemDescriptionDialog
import io.github.mangi.eta.ui.model.SkillItemUi
import io.github.suqi8.coui.kmp.basic.DropdownEntry
import io.github.suqi8.coui.kmp.basic.DropdownItem
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.More
import io.github.suqi8.coui.kmp.menu.OverlayIconDropdownMenu
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun SkillSwitchRow(
    skill: SkillItemUi,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var showDescription by remember(skill.id) { mutableStateOf(false) }
    val description = skill.description.ifBlank { stringResource(R.string.skills_no_description) }
    val viewDescText = stringResource(R.string.ui_view_description)
    val deleteText = stringResource(R.string.ui_delete_3755f5)
    val currentDelete by rememberUpdatedState(onDelete)
    val entries = remember(viewDescText, deleteText, onDelete != null, enabled) {
        buildList {
            add(DropdownEntry(items = listOf(DropdownItem(text = viewDescText, onClick = { showDescription = true }))))
            if (onDelete != null) {
                add(
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = deleteText,
                                enabled = enabled,
                                alert = true,
                                onClick = { currentDelete?.invoke() },
                            ),
                        ),
                    ),
                )
            }
        }
    }
    SwitchPreference(
        checked = skill.enabled,
        onCheckedChange = onToggle,
        title = skill.name,
        summary = description,
        enabled = enabled,
        endActions = {
            OverlayIconDropdownMenu(entries = entries, minWidth = 36.dp, minHeight = 36.dp) {
                Icon(
                    imageVector = COUIIcons.Basic.More,
                    contentDescription = stringResource(R.string.skills_more_named, skill.name),
                    modifier = Modifier.size(20.dp),
                    tint = COUITheme.colorScheme.labelSecondary,
                )
            }
        },
    )
    if (showDescription) {
        ItemDescriptionDialog(
            title = skill.name,
            description = description,
            onDismiss = { showDescription = false },
        )
    }
}
