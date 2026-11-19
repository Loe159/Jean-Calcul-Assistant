package io.github.mangi.eta.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.basic.SmallTitle

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    SmallTitle(
        text = text,
        modifier = modifier,
    )
}
