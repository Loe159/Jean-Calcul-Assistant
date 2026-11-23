package io.github.mangi.eta.ui.screens.home.brief

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference

/** Lists the examples and the vehicle connection entry when it is available. */
@Composable
internal fun BriefPreviewsSection(
    count: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    includesCarConnection: Boolean = false,
) {
    val examples = count - if (includesCarConnection) 1 else 0
    val summary = listOfNotNull(
        briefPreviewCountLabel(examples).takeIf { examples > 0 },
        "Voiture à connecter".takeIf { includesCarConnection },
    ).joinToString(" · ")
    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        PreferenceCategory(title = "Aperçus")
        Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(0.dp)) {
            SwitchPreference(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                title = "Afficher les aperçus",
                summary = summary,
            )
        }
    }
}

internal fun briefPreviewCountLabel(count: Int): String =
    if (count == 1) "1 carte d’exemple" else "$count cartes d’exemple"
