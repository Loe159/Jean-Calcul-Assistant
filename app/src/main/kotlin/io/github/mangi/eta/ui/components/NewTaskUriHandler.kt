package io.github.mangi.eta.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.UriHandler

/**
 * Opens links from Compose hosted outside an Activity (system overlay windows). Compose's default
 * handler starts the activity from the host context, which crashes from a Service without
 * FLAG_ACTIVITY_NEW_TASK.
 */
internal class NewTaskUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No browser or handler for this link: ignore the tap rather than crash the overlay.
        }
    }
}
