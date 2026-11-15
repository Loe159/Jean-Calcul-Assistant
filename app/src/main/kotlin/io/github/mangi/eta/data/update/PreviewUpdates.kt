package io.github.mangi.eta.data.update

import android.content.Context
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PreviewUpdateState(
    val update: PreviewUpdate? = null,
    val downloading: Boolean = false,
    val file: File? = null,
    val error: String? = null,
    val automatic: Boolean = false,
)

internal object PreviewUpdates {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PreviewUpdateState())
    val state = mutableState.asStateFlow()
    private var checking = false
    private var checkedAt = 0L
    private var dismissedVersion = 0L

    fun check(context: Context, manual: Boolean = false) {
        val app = context.applicationContext
        if (checking || mutableState.value.downloading) return
        if (!manual && System.currentTimeMillis() - checkedAt < 60 * 1000L) return
        checking = true
        checkedAt = System.currentTimeMillis()
        scope.launch {
            try {
                val update = withContext(Dispatchers.IO) { AppUpdateChecker.fetchPreview() }
                @Suppress("DEPRECATION")
                val installed = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
                val development = io.github.mangi.eta.agent.development.DevelopmentStore(app)
                val proposal = development.proposal().takeIf { development.enabled }
                val expectedCommit = proposal?.takeUnless { it.optBoolean("merged") || it.optBoolean("closed") }?.optString("head")
                if (!expectedCommit.isNullOrBlank() && update != null && update.commit != expectedCommit) {
                    if (manual) Toast.makeText(app, "Your improvement preview is not published yet. Check App improvements for progress.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (update != null && update.versionCode > installed) {
                    if (manual || update.versionCode != dismissedVersion) {
                        val current = mutableState.value
                        if (current.update?.versionCode != update.versionCode || current.file?.exists() != true) {
                            mutableState.value = PreviewUpdateState(update = update)
                            download(app, automatic = !manual)
                        }
                    }
                } else if (manual) {
                    Toast.makeText(app, "You are using the latest available preview", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (manual) mutableState.value = PreviewUpdateState(error = "Could not check for updates. Try again later.")
            } finally { checking = false }
        }
    }

    fun download(context: Context, automatic: Boolean = false) {
        val update = mutableState.value.update ?: return
        if (mutableState.value.downloading) return
        mutableState.value = PreviewUpdateState(update, downloading = true, automatic = automatic)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { PreviewUpdateInstaller.download(context.applicationContext, update) }
                mutableState.value = PreviewUpdateState(update = update, file = file)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.value = if (automatic) PreviewUpdateState() else PreviewUpdateState(error = error.message ?: "Update download failed. Try again.")
            }
        }
    }

    fun dismiss() {
        if (!mutableState.value.downloading) {
            dismissedVersion = mutableState.value.update?.versionCode ?: dismissedVersion
            mutableState.value = PreviewUpdateState()
        }
    }
}
