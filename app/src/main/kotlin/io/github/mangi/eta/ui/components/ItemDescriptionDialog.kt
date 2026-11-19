package io.github.mangi.eta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.CouiNoticeDialog

@Composable
internal fun ItemDescriptionDialog(
    title: String,
    description: String,
    onDismiss: () -> Unit,
) {
    CouiNoticeDialog(
        visible = true,
        title = title,
        message = description,
        confirmText = stringResource(R.string.action_close),
        onDismissRequest = onDismiss,
    )
}
