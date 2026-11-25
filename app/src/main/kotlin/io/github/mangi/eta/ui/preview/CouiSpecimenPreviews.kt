package io.github.mangi.eta.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.PreferenceFooter
import io.github.mangi.eta.ui.components.coui.PreferenceValueText
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

/*
 * Design-system specimens: the canonical way this app assembles COUI.
 * They are the visual reference cited by DESIGN.md — render them before inventing a pattern:
 *   android preview list :app | grep Specimen
 */

/** A settings-style page: category title, card, CardListGroup, preference rows, footer. */
@CouiPhonePreviews
@CouiStressPreviews
@Composable
private fun SettingsPageSpecimenPreview() {
    CouiPreviewTheme {
        CouiScaffoldPage(title = "Réglages", onBack = {}) {
            PreferenceCategory(title = "Modèles")
            Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = "Fournisseurs de modèles",
                            summary = "ChatGPT / Codex, vLLM",
                            startAction = { SpecimenIcon(Icons.Rounded.Cloud) },
                            onClick = {},
                        )
                    }
                    item {
                        SwitchPreference(
                            title = "Réflexion approfondie par défaut",
                            startAction = { SpecimenIcon(Icons.Rounded.Psychology) },
                            checked = true,
                            onCheckedChange = {},
                        )
                    }
                    item {
                        BasicComponent(
                            title = "Version",
                            startAction = { SpecimenIcon(Icons.Rounded.Info) },
                            endActions = { PreferenceValueText(value = "2.0.0-preview") },
                        )
                    }
                }
            }
            PreferenceFooter(text = "Les clés restent chiffrées sur l’appareil.")
        }
    }
}

/** Buttons in every role this app uses, enabled and disabled. */
@CouiComponentPreviews
@Composable
private fun ButtonsSpecimenPreview() {
    CouiPreviewTheme(fullScreen = false) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Boutons", style = COUITheme.textStyles.headlineXS, color = COUITheme.colorScheme.labelPrimary)
            CouiButtonRole.entries.forEach { role ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CouiButton(text = role.name, onClick = {}, role = role)
                    CouiButton(text = "Désactivé", onClick = {}, role = role, enabled = false)
                }
            }
        }
    }
}

@Composable
private fun SpecimenIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = COUITheme.colorScheme.primary,
    )
}
