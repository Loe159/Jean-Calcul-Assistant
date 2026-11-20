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

/**
 * Fold for the demo cards. Real data stays above it; everything below is labelled fictional once,
 * here, instead of a badge repeated on every card.
 */
@Composable
internal fun BriefPreviewsSection(
    count: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        PreferenceCategory(title = "Aperçus")
        Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(0.dp)) {
            SwitchPreference(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                title = "Afficher les aperçus",
                summary = "${briefPreviewCountLabel(count)} · données fictives, pas les tiennes",
            )
        }
    }
}

internal fun briefPreviewCountLabel(count: Int): String =
    if (count == 1) "1 carte d’exemple" else "$count cartes d’exemple"
