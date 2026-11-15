package io.github.mangi.eta.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import io.github.mangi.eta.data.update.PreviewUpdateInstaller
import io.github.mangi.eta.data.update.PreviewUpdates
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog

@Composable
internal fun PreviewUpdateDialog() {
    val context = LocalContext.current
    val state by PreviewUpdates.state.collectAsState()
    fun installReadyUpdate() {
        runCatching {
            PreviewUpdates.state.value.file?.let { file -> PreviewUpdateInstaller.install(context, file) }
        }.onFailure {
            Toast.makeText(context, "Could not open the Android installer. Try again.", Toast.LENGTH_LONG).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) {
            installReadyUpdate()
        }
    }
    if (state.update == null && state.error == null) return
    CouiConfirmDialog(
        visible = true,
        title = if (state.error != null) "Update unavailable" else "Jean-Calcul update",
        message = state.error ?: when {
            state.downloading -> "Downloading and verifying the update…"
            state.file != null -> "The update is ready. Android will ask you to confirm installation. Your conversations and settings are kept."
            else -> "Version ${state.update?.versionName} is available from GitHub Actions. Download and install it here."
        },
        confirmText = when {
            state.error != null -> "Close"
            state.downloading -> "Downloading…"
            state.file != null -> "Install update"
            else -> "Download update"
        },
        confirmEnabled = !state.downloading,
        cancelEnabled = !state.downloading,
        onConfirm = {
            when {
                state.error != null -> PreviewUpdates.dismiss()
                state.file == null -> PreviewUpdates.download(context)
                !context.packageManager.canRequestPackageInstalls() -> permission.launch(PreviewUpdateInstaller.permissionIntent(context))
                else -> installReadyUpdate()
            }
        },
        onDismissRequest = { PreviewUpdates.dismiss() },
    )
}
